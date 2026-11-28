package io.kalo.platform.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kalo.content.block.Block;
import io.kalo.content.block.definition.BlockDefinition;
import io.kalo.content.block.definition.BlockModelDefinition;
import io.kalo.content.block.definition.JavaBlockMode;
import io.kalo.content.item.Item;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.content.vfx.definition.VfxDefinition;
import io.kalo.model.ModelTextureResolver;
import io.kalo.pack.Json;
import io.kalo.pack.PackMeta;
import io.kalo.platform.java.BlockStateAllocator;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.logging.Logger;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Compiles the shared IR into a Bedrock resource pack and a Geyser v2 item mapping.
 *
 * <p>A Bedrock pack is <em>not</em> a Java pack with extra files. It has its own layout —
 * {@code manifest.json} at the root, textures under {@code textures/}, no
 * {@code assets/} tree at all — so this builds its own container and pulls only the
 * source images it needs out of the Java pack. Copying the Java pack wholesale shipped
 * tens of kilobytes of {@code assets/minecraft/...} that Bedrock silently ignores.</p>
 *
 * <p>Accumulate with {@link #add} for each content type, then call {@link #finish} once.
 * The manifest, texture atlas and mapping file are written by {@code finish} rather than
 * by each {@code add}, because writing them per call meant the last content type to
 * compile erased every earlier type's mappings.</p>
 */
public final class BedrockPackCompiler {
    private static final Logger LOGGER = Logger.getLogger(BedrockPackCompiler.class.getName());

    private final ResourcePack javaSource;
    private final ResourcePack target;
    private final TrackedPack pack;
    private final BedrockRegistrationSnapshot.Generation generation;

    private final JsonObject textureData = new JsonObject();
    private final JsonObject mappedItems = new JsonObject();

    private final JsonObject blockDefinitions = new JsonObject();
    private final JsonObject terrainTextureData = new JsonObject();
    private final JsonArray mappedBlocks = new JsonArray();
    /** java content key -> Bedrock geometry identifier, for the extension to apply. */
    private final JsonObject geometries = new JsonObject();
    /** Compiled Blockbench block assets, keyed by the Java content key. */
    private final Map<String, BlockbenchBlockAssets> blockbenchBlocks = new TreeMap<>();
    /** Animated blocks rendered through a Bedrock client entity. */
    private final List<BedrockAnimatedBlockRegistration> animatedBlocks = new ArrayList<>();
    /** Custom mobs rendered through a Bedrock client entity. */
    private final List<BedrockMobRegistration> mobs = new ArrayList<>();
    /** Vehicles and emote props rendered through a Bedrock client entity. */
    private final List<BedrockRigRegistration> rigs = new ArrayList<>();
    /** All items seen in this compiler pass, for state-model references. */
    private final Map<Key, Item> itemIndex = new TreeMap<>();
    /** Embedded first-texture bytes for block inventory icons, keyed by block shorthand. */
    private final Map<String, byte[]> embeddedBlockIcons = new TreeMap<>();
    /** Rendered isometric previews for Blockbench block item icons. */
    private final Map<String, byte[]> renderedBlockIcons = new TreeMap<>();
    private final List<BedrockBlockRegistration> blockRegistrations = new ArrayList<>();
    private final List<BedrockItemRegistration> itemRegistrations = new ArrayList<>();
    private final List<BedrockPlacedModelRegistration> placedModels = new ArrayList<>();
    private int attachables;

    private int skipped;

    public BedrockPackCompiler(@NotNull ResourcePack javaSource, @NotNull ResourcePack pack) {
        this.javaSource = javaSource;
        this.target = pack;
        this.pack = new TrackedPack(pack);
        this.generation = BedrockRegistrationSnapshot.beginGeneration();
        BedrockVfxRegistry.clear();
    }

    /** Adds one content type's items to the mapping. Safe to call repeatedly. */
    public void add(@NotNull Iterable<? extends Item> items) {
        add(items, Set.of());
    }

    /**
     * @param skip Java content keys that did not compile on the Java side. Bedrock must
     *             not publish an item the Java player standing next to them cannot see.
     */
    public void add(@NotNull Iterable<? extends Item> items, @NotNull Set<String> skip) {
        List<Item> indexed = new ArrayList<>();
        items.forEach(indexed::add);
        for (Item item : indexed) {
            itemIndex.put(item.key(), item);
        }
        for (Item item : indexed) {
            ItemDefinition definition = item.definition();

            if (!definition.bedrock().enabled()) {
                continue;
            }
            if (skip.contains(definition.key().asString())) {
                skipped++;
                continue;
            }
            if (definition.model() instanceof ModelDefinition.Vanilla) {
                // Looks like a vanilla item on Java, so Bedrock already renders it
                // correctly with no custom item at all.
                continue;
            }
            Key previousOwner = pack.owner(definition.key());
            try {
                switch (definition.model()) {
                    case ModelDefinition.Sprite sprite -> addSpriteItem(definition, sprite);
                    case ModelDefinition.Custom custom -> {
                        if (!addCustomItem(definition, custom)) {
                            skipped++;
                            LOGGER.warning("Skipped Bedrock 3D item " + definition.key()
                                    + ": missing geometry/texture or unsupported texture layout");
                        }
                    }
                    case ModelDefinition.Blockbench blockbench -> {
                        if (!addBlockbenchItem(definition, blockbench)) {
                            skipped++;
                            LOGGER.warning("Skipped Bedrock Blockbench item " + definition.key()
                                    + ": missing geometry/texture or unsupported construct");
                        }
                    }
                    case ModelDefinition.Vanilla ignored -> { /* already handled above */ }
                }
            } finally {
                pack.owner(previousOwner);
            }
        }
    }

    /**
     * Compile a model observed on an existing server ItemStack, without creating
     * or giving a Kalo gameplay item. Geyser uses the observed Java material and
     * item_model component for matching. Native Kalo item identifiers win collisions.
     */
    public boolean addExternalItem(@NotNull ItemDefinition definition) {
        if (!definition.bedrock().enabled()
                || itemRegistrations.stream().anyMatch(item ->
                        item.model().equals(definition.key().asString())
                        || item.bedrockIdentifier().equals(definition.key().asString()))) {
            return false;
        }
        Key previousOwner = pack.owner(definition.key());
        try {
            return switch (definition.model()) {
                case ModelDefinition.Sprite sprite -> {
                    String path = "assets/" + sprite.texture().namespace()
                            + "/textures/" + sprite.texture().value() + ".png";
                    if (javaSource.file(path) == null) yield false;
                    addSpriteItem(definition, sprite);
                    yield true;
                }
                case ModelDefinition.Custom custom -> addCustomItem(definition, custom);
                case ModelDefinition.Blockbench blockbench -> addBlockbenchItem(definition, blockbench);
                case ModelDefinition.Vanilla ignored -> false;
            };
        } finally {
            pack.owner(previousOwner);
        }
    }

    /**
     * Compile a selector branch with its own synthetic Bedrock identifier while
     * preserving the original Java item_model and Geyser predicates.
     */
    public boolean addExternalVariant(@NotNull io.kalo.converter.ExternalModelVariants.Variant variant) {
        ItemDefinition definition = variant.definition();
        if (!addExternalItem(definition)) return false;
        String bedrockId = definition.key().asString();
        int index = -1;
        for (int i = itemRegistrations.size()-1; i >= 0; i--) {
            if (itemRegistrations.get(i).bedrockIdentifier().equals(bedrockId)) {
                index = i; break;
            }
        }
        if (index < 0) throw new IllegalStateException("Missing compiled Bedrock registration");
        BedrockItemRegistration old = itemRegistrations.get(index);
        itemRegistrations.set(index, new BedrockItemRegistration(old.javaItem(),
                old.bedrockIdentifier(), variant.javaModel().asString(), old.icon(),
                old.displayName(), old.maxStackSize(), old.maxDamage(), old.enchantmentGlint(),
                variant.predicates()));

        // Files and in-process API must use the same predicate metadata.
        JsonArray candidates = mappedItems.getAsJsonArray(old.javaItem());
        for (JsonElement candidate : candidates) {
            JsonObject mapping = candidate.getAsJsonObject();
            if (!bedrockId.equals(mapping.get("bedrock_identifier").getAsString())) continue;
            mapping.addProperty("model", variant.javaModel().asString());
            if (!variant.predicates().isEmpty()) {
                JsonArray predicates = new JsonArray();
                variant.predicates().forEach(p -> predicates.add(p.toJson()));
                mapping.add("predicate", predicates.size() == 1 ? predicates.get(0) : predicates);
                if (predicates.size() > 1) mapping.addProperty("predicate_strategy", "and");
            }
            return true;
        }
        throw new IllegalStateException("Bedrock mapping missing after variant compilation");
    }

    /**
     * Legacy CustomModelData has no item_model data component. Convert the
     * observed Java carrier's selected override, then use Geyser's legacy
     * registration instead of pretending it is a modern item_model definition.
     */
    public boolean addLegacyExternalItem(@NotNull io.kalo.converter.ExternalLegacyItems.Model legacy) {
        if (!addExternalItem(legacy.definition())) return false;
        String identifier = legacy.definition().key().asString();
        for (int index = itemRegistrations.size()-1; index>=0; index--) {
            BedrockItemRegistration previous = itemRegistrations.get(index);
            if (!identifier.equals(previous.bedrockIdentifier())) continue;
            itemRegistrations.set(index, new BedrockItemRegistration(previous.javaItem(),
                    previous.bedrockIdentifier(), previous.model(), previous.icon(),
                    previous.displayName(), previous.maxStackSize(), previous.maxDamage(),
                    previous.enchantmentGlint(), List.of(), legacy.customModelData()));

            JsonArray candidates = mappedItems.getAsJsonArray(previous.javaItem());
            for (JsonElement entry : candidates) {
                JsonObject mapping = entry.getAsJsonObject();
                if (!identifier.equals(mapping.get("bedrock_identifier").getAsString())) continue;
                mapping.remove("model");
                mapping.addProperty("type", "legacy");
                mapping.addProperty("custom_model_data", legacy.customModelData());
                return true;
            }
            throw new IllegalStateException("Missing mapping after legacy item compilation");
        }
        throw new IllegalStateException("Missing record after legacy item compilation");
    }

    private void addSpriteItem(@NotNull ItemDefinition definition, @NotNull ModelDefinition.Sprite sprite) {
        Key key = definition.key();
        String icon = definition.bedrock().iconOverride() != null
                ? definition.bedrock().iconOverride()
                : key.namespace() + "_" + key.value();
        registerItem(definition, icon);
        registerItemTexture(icon, sprite.texture());
    }

    /** Common item mapping path: sprites and 3D items must use identical Geyser definitions. */
    private void registerItem(@NotNull ItemDefinition definition, @NotNull String icon) {
        Key key = definition.key();
        String bedrockId = key.namespace() + ":" + key.value();
        String javaItem = "minecraft:" + definition.java().baseMaterial().name().toLowerCase(Locale.ROOT);
        JsonObject mapping = new JsonObject();
        mapping.addProperty("type", "definition");
        mapping.addProperty("model", key.asString());
        mapping.addProperty("bedrock_identifier", bedrockId);

        // Geyser v2 reads platform-specific presentation fields from
        // `bedrock_options`. A top-level `icon` is accepted by Gson but ignored by the
        // mapping reader, whose fallback changes `testpack:ruby_sword` into
        // `testpack.ruby_sword`; that does not match the atlas key generated below.
        JsonObject bedrockOptions = new JsonObject();
        bedrockOptions.addProperty("icon", icon);
        mapping.add("bedrock_options", bedrockOptions);

        String displayName = plainName(definition);
        if (displayName != null) {
            // Geyser shows this verbatim, so it has to be the rendered name — passing the
            // key would literally display "testpack:ruby_sword" in the Bedrock inventory.
            mapping.addProperty("display_name", displayName);
        }

        JsonObject components = new JsonObject();
        // Bedrock rejects a durable item that also stacks: "Stack size must be 1 when max
        // damage is above 0". A bow declares max stack 1 on Java, but a pack that leaves
        // the default 64 still produces a mapping Geyser throws away at startup.
        int stackSize = definition.behaviour().maxDurability() != null
                || definition.java().baseMaterial().getMaxDurability() > 0
                ? 1 : definition.behaviour().maxStackSize();
        components.addProperty("minecraft:max_stack_size", stackSize);
        if (definition.behaviour().maxDurability() != null) {
            // This section describes the Java component patch Geyser matches, not the
            // Bedrock component it eventually synthesises. `minecraft:durability` is
            // not a Java data component and makes Geyser reject the whole definition.
            components.addProperty("minecraft:max_damage", definition.behaviour().maxDurability());
        }
        if (definition.display().enchantmentGlint()) {
            components.addProperty("minecraft:enchantment_glint_override", true);
        }
        mapping.add("components", components);

        JsonArray definitions = mappedItems.has(javaItem)
                ? mappedItems.getAsJsonArray(javaItem)
                : new JsonArray();
        definitions.add(mapping);
        mappedItems.add(javaItem, definitions);

        itemRegistrations.add(new BedrockItemRegistration(
                javaItem, bedrockId, key.asString(), icon, displayName,
                definition.behaviour().maxStackSize(), definition.behaviour().maxDurability(),
                definition.display().enchantmentGlint()));
    }

    /**
     * Static cuboid 3D items share the existing Java -> Bedrock cuboid converter.
     * Unlike blocks, held items need a Bedrock attachable bound to the equipped item slot.
     * This does not pretend to port mod-specific animation or logic.
     *
     * <p>Reject unsupported models <em>before</em> publishing mappings or writing pack
     * entries. For multiple texture sheets, the atlas builder places every face's
     * resolved source in a deterministic sheet and adjusts its UV origin.</p>
     */
    private boolean addCustomItem(@NotNull ItemDefinition definition,
                                  @NotNull ModelDefinition.Custom custom) {
        Key model = custom.model();
        Writable source = javaSource.file("assets/" + model.namespace()
                + "/models/" + model.value() + ".json");
        if (source == null) {
            return false;
        }
        try {
            JsonObject javaModel = BedrockItemModelResolver.resolve(javaSource, model);
            if (javaModel == null) return false;
            Key key = definition.key();
            String geometryId = BedrockGeometry.identifierFor(key.namespace(), key.value());
            JsonObject geometry = BedrockGeometry.convert(geometryId, javaModel);
            if (geometry == null) return false;
            Key texture = singleSheet(custom, javaModel);
            BedrockItemAtlas.Result atlas = null;
            if (texture == null) {
                atlas = BedrockItemAtlas.build(custom, javaModel, geometry, javaSource);
                if (atlas == null) return false;
                texture = atlas.iconSource();
            }
            if (javaSource.file("assets/" + texture.namespace()
                    + "/textures/" + texture.value() + ".png") == null) return false;

            // Items bind to the holder's equipped slot; blocks do not.
            JsonObject bone = geometry.getAsJsonArray("minecraft:geometry").get(0)
                    .getAsJsonObject().getAsJsonArray("bones").get(0).getAsJsonObject();
            bone.addProperty("binding", "q.item_slot_to_bone_name(context.item_slot)");
            // Block geometry can name each Java texture slot separately. A held
            // single-sheet attachable instead has one Material.default, so normalize
            // all of its converted face material slots to that Bedrock material.
            for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                JsonObject faces = cubeElement.getAsJsonObject().getAsJsonObject("uv");
                if (faces == null) continue;
                for (var face : faces.entrySet()) {
                    face.getValue().getAsJsonObject().addProperty("material_instance", "default");
                }
            }
            String shorthand = key.namespace() + "_" + key.value();
            String icon = definition.bedrock().iconOverride() != null
                    ? definition.bedrock().iconOverride() : shorthand;

            // The model texture and the inventory icon are two different images: the model
            // samples UV islands out of the sheet, the icon shows the whole object. They
            // must never share a path — doing so made the rendered icon overwrite the
            // texture the held attachable reads, so the item rendered broken in hand.
            String modelTexture = "textures/models/items/" + shorthand;
            if (atlas != null) {
                pack.file(modelTexture + ".png", Writable.bytes(atlas.image()));
            } else {
                copyTexture(texture, modelTexture + ".png");
            }

            // Bound geometry needs its own hold animations; vanilla player-skeleton holds
            // do not position a custom model and leave it at raw scale/orientation.
            String rootBone = bone.get("name").getAsString();
            io.kalo.model.bedrock.BedrockHoldAnimations.Result hold =
                    io.kalo.model.bedrock.BedrockHoldAnimations.build(rootBone,
                            io.kalo.model.bedrock.BedrockHoldAnimations.parse(
                                    javaModel, "firstperson_righthand"),
                            io.kalo.model.bedrock.BedrockHoldAnimations.parse(
                                    javaModel, "thirdperson_righthand"),
                            shorthand,
                            definition.bedrock().scale() != null
                                    ? definition.bedrock().scale()
                                    : autoHandScale(javaModel),
                            definition.bedrock().rotation());
            pack.file("animations/" + shorthand + ".animation.json",
                    Json.writable(hold.json()));
            pack.file("models/entity/" + shorthand + ".geo.json", Json.writable(geometry));
            pack.file("attachables/" + shorthand + ".json", Json.writable(
                    BedrockAttachable.heldItem(key, geometryId, modelTexture, Map.of(),
                            hold.ids(), List.of())));
            // The model's texture is a sheet of UV islands, not an icon. Copying it made
            // every 3D item look like a circuit board in the Bedrock hotbar, so render the
            // authored cubes instead and fall back to the sheet only if that fails.
            byte[] rendered = JavaModelIcon.render(javaModel, custom, javaSource);
            if (rendered != null) {
                pack.file("textures/items/" + icon + ".png", Writable.bytes(rendered));
                registerItemAtlas(icon);
            } else {
                registerItemTexture(icon, texture);
            }
            registerItem(definition, icon);
            // armorCount is the number of wearable armor pieces, not all attachables.
            return true;
        } catch (Exception e) {
            LOGGER.warning("Cannot convert Bedrock 3D item " + definition.key()
                    + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Compiles the Blockbench models named by `place.states[].model`. Each state is a
     * normal Kalo item key; Java swaps the display stack and Bedrock swaps to the
     * matching client entity.
     */
    private void addStateVariants(@NotNull ItemDefinition definition,
                                  @NotNull String baseShorthand) {
        if (definition.placement() == null || definition.placement().states().isEmpty()) {
            return;
        }
        int index = 0;
        for (var state : definition.placement().states()) {
            index++;
            if (state.model() == null || state.model().equals(definition.key())) {
                continue;
            }
            Item variant = itemIndex.get(state.model());
            if (variant == null
                    || !(variant.definition().model() instanceof ModelDefinition.Blockbench blockbench)) {
                LOGGER.warning("Placed model " + definition.key() + " state " + index
                        + " names " + state.model() + ", which is not a registered Blockbench item");
                continue;
            }
            try {
                io.kalo.model.ModelIR model = io.kalo.model.ModelRegistry.load(javaSource,
                        blockbench.source());
                String stem = baseShorthand + "_state" + index;
                String entityId = "kalo:model_" + sanitize(stem);
                String geometryId = "geometry.kalo." + sanitize(stem) + ".placed";
                io.kalo.model.bedrock.BedrockModelCompiler.Result compiled =
                        io.kalo.model.bedrock.BedrockModelCompiler.compile(model, geometryId,
                                blockbench.textures(), variant.key().namespace(), stem);
                if (!compiled.compiled()) {
                    LOGGER.warning("Could not compile placed-model state " + state.model()
                            + ": " + compiled.errorSummary());
                    continue;
                }
                boolean missing = false;
                for (ModelTextureResolver.ResolvedTexture texture : compiled.textures()) {
                    if (texture.embeddedPng() == null
                            && javaSource.file(texturePath(texture.key())) == null) {
                        missing = true;
                        break;
                    }
                }
                if (missing) {
                    continue;
                }

                Map<String, String> materials = new TreeMap<>();
                for (ModelTextureResolver.ResolvedTexture texture : compiled.textures()) {
                    String destination = "textures/items/" + stem + "_" + texture.material();
                    materials.put(texture.material(), destination);
                    if (texture.embeddedPng() != null) {
                        pack.file(destination + ".png", Writable.bytes(texture.embeddedPng()));
                    } else {
                        copyTexture(texture.key(), destination + ".png");
                    }
                }
                if (materials.isEmpty()) {
                    continue;
                }
                String defaultTexture = materials.values().iterator().next();

                pack.file("models/entity/" + stem + "_placed.geo.json",
                        Json.writable(compiled.geometry()));
                if (compiled.animations() != null) {
                    pack.file("animations/" + stem + ".animation.json",
                            Json.writable(compiled.animations()));
                }
                Map<String, String> animations = new TreeMap<>();
                compiled.animationIds().forEach((name, id) -> {
                    if (!name.startsWith("hold_")) {
                        animations.put(name, id);
                    }
                });
                List<String> loops = new ArrayList<>(compiled.loopAnimations());
                loops.removeIf(name -> name.startsWith("hold_"));
                writeControllers(stem, compiled, loops);
                JsonObject entity = placedEntity(entityId, geometryId, defaultTexture,
                        materials, animations, loops);
                pack.file("entity/" + stem + ".entity.json", Json.writable(entity));
                placedModels.add(new BedrockPlacedModelRegistration(variant.key(), entityId,
                        geometryId, defaultTexture, animations, loops,
                        state.scale() != null ? state.scale() : definition.placement().scale()));
            } catch (Exception error) {
                LOGGER.warning("Could not compile placed-model state " + state.model()
                        + ": " + error.getMessage());
            }
        }
    }

    /**
     * Compiles a Blockbench source directly from the shared IR. The outliner becomes
     * Bedrock bones; unlike {@link #addCustomItem}, this path never flattens the hierarchy
     * through a Java model first.
     */
    private boolean addBlockbenchItem(@NotNull ItemDefinition definition,
                                      @NotNull ModelDefinition.Blockbench blockbench) {
        Key key = definition.key();
        String shorthand = key.namespace() + "_" + key.value();
        try {
            io.kalo.model.ModelIR model = io.kalo.model.ModelRegistry.load(javaSource,
                    blockbench.source());
            String geometryId = BedrockGeometry.identifierFor(key.namespace(), key.value());
            io.kalo.model.bedrock.BedrockModelCompiler.Result compiled =
                    io.kalo.model.bedrock.BedrockModelCompiler.compile(model, geometryId,
                            blockbench.textures(), key.namespace(), shorthand);
            io.kalo.model.ModelDiagnostics.log(LOGGER,
                    "Bedrock Blockbench model for " + key.asString(), compiled.diagnostics());
            if (!compiled.compiled()) {
                return false;
            }

            // Preflight every source image before writing geometry or publishing a mapping.
            for (io.kalo.model.ModelTextureResolver.ResolvedTexture texture
                    : compiled.textures()) {
                if (texture.embeddedPng() == null
                        && javaSource.file(texturePath(texture.key())) == null) {
                    return false;
                }
            }

            String icon = definition.bedrock().iconOverride() != null
                    ? definition.bedrock().iconOverride() : shorthand;
            Map<String, String> materialPaths = new TreeMap<>();
            Set<Key> written = new HashSet<>();
            String firstTexturePath = null;
            for (io.kalo.model.ModelTextureResolver.ResolvedTexture texture
                    : compiled.textures()) {
                String destination = "textures/items/" + shorthand + "_" + texture.material();
                materialPaths.put(texture.material(), destination);
                if (firstTexturePath == null) {
                    firstTexturePath = destination;
                }
                if (written.add(texture.key())) {
                    if (texture.embeddedPng() != null) {
                        pack.file(destination + ".png", Writable.bytes(texture.embeddedPng()));
                    } else {
                        copyTexture(texture.key(), destination + ".png");
                    }
                }
            }
            if (materialPaths.isEmpty()) {
                return false;
            }
            if (!materialPaths.containsKey("default")) {
                materialPaths.put("default", firstTexturePath);
            }

            // A 3D item uses a rendered isometric preview, not its first texture, for the
            // flat Bedrock inventory icon. The model's own material paths stay separate.
            byte[] renderedIcon = BedrockModelIcon.render(model, compiled.textures(),
                    javaSource);
            if (renderedIcon != null) {
                pack.file("textures/items/" + icon + ".png", Writable.bytes(renderedIcon));
            } else {
                ModelTextureResolver.ResolvedTexture first = compiled.textures().getFirst();
                if (first.embeddedPng() != null) {
                    pack.file("textures/items/" + icon + ".png",
                            Writable.bytes(first.embeddedPng()));
                } else {
                    copyTexture(first.key(), "textures/items/" + icon + ".png");
                }
            }

            JsonObject geometry = compiled.geometry().deepCopy();
            addItemBinding(geometry);
            pack.file("models/entity/" + shorthand + ".geo.json", Json.writable(geometry));
            if (compiled.animations() != null) {
                pack.file("animations/" + shorthand + ".animation.json",
                        Json.writable(compiled.animations()));
            }
            List<String> heldLoops = new ArrayList<>(compiled.loopAnimations());
            writeControllers(shorthand, compiled, heldLoops);
            pack.file("attachables/" + shorthand + ".json", Json.writable(
                    BedrockAttachable.heldItem(key, geometryId,
                            materialPaths.get("default"), materialPaths,
                            compiled.animationIds(), heldLoops)));

            registerItemAtlas(icon);
            registerItem(definition, icon);
            addStateVariants(definition, shorthand);

            if (definition.placement() != null) {
                String placedGeometryId = geometryId + ".placed";
                JsonObject placedGeometry = compiled.geometry().deepCopy();
                placedGeometry.getAsJsonArray("minecraft:geometry").get(0)
                        .getAsJsonObject().getAsJsonObject("description")
                        .addProperty("identifier", placedGeometryId);
                pack.file("models/entity/" + shorthand + "_placed.geo.json",
                        Json.writable(placedGeometry));

                Map<String, String> animations = new TreeMap<>();
                compiled.animationIds().forEach((name, id) -> {
                    if (!name.startsWith("hold_")) {
                        animations.put(name, id);
                    }
                });
                List<String> loops = new ArrayList<>(compiled.loopAnimations());
                loops.removeIf(name -> name.startsWith("hold_"));
                writeControllers(shorthand, compiled, loops);
                String idle = definition.placement().idleAnimation();
                if (idle != null && !idle.isBlank()) {
                    String wanted = idle.toLowerCase(java.util.Locale.ROOT)
                            .replaceAll("[^a-z0-9_]+", "_");
                    if (animations.containsKey(wanted)) {
                        loops = List.of(wanted);
                    } else {
                        LOGGER.warning("Placed model " + definition.key()
                                + " asks for idle animation '" + idle
                                + "', which is not authored; loops remain " + loops);
                    }
                }

                String entityId = "kalo:model_" + sanitize(shorthand);
                JsonObject entity = placedEntity(entityId, placedGeometryId,
                        materialPaths.get("default"), materialPaths, animations, loops);
                pack.file("entity/" + shorthand + ".entity.json", Json.writable(entity));
                placedModels.add(new BedrockPlacedModelRegistration(key, entityId,
                        placedGeometryId, materialPaths.get("default"),
                        animations, loops, definition.placement().scale()));
            }
            return true;
        } catch (Exception error) {
            LOGGER.warning("Cannot convert Bedrock Blockbench item " + definition.key()
                    + ": " + error.getMessage());
            return false;
        }
    }

    /** Bedrock attaches a held model to one root bone via the item-slot binding. */
    private static void addItemBinding(@NotNull JsonObject geometry) {
        JsonArray geometries = geometry.getAsJsonArray("minecraft:geometry");
        if (geometries == null || geometries.isEmpty()) {
            return;
        }
        JsonArray bones = geometries.get(0).getAsJsonObject().getAsJsonArray("bones");
        if (bones == null) {
            return;
        }
        for (JsonElement element : bones) {
            JsonObject bone = element.getAsJsonObject();
            if (!bone.has("parent")) {
                bone.addProperty("binding", "q.item_slot_to_bone_name(context.item_slot)");
                return;
            }
        }
        if (!bones.isEmpty()) {
            bones.get(0).getAsJsonObject().addProperty("binding",
                    "q.item_slot_to_bone_name(context.item_slot)");
        }
    }

    /** {@code assets/<ns>/textures/<value>.png} in the source Java pack. */
    private static @NotNull String texturePath(@NotNull Key key) {
        return "assets/" + key.namespace() + "/textures/" + key.value() + ".png";
    }

    /**
     * Resolve texture aliases from a Java model and Kalo's authored texture overrides.
     * Only one actual texture sheet can be represented without rebuilding the UV atlas.
     */
    static @Nullable Key singleSheet(@NotNull ModelDefinition.Custom custom,
                                             @NotNull JsonObject javaModel) {
        Map<String, String> modelTextures = new TreeMap<>();
        JsonObject textures = javaModel.getAsJsonObject("textures");
        if (textures != null) {
            textures.entrySet().forEach(entry -> {
                if (entry.getValue().isJsonPrimitive()) {
                    modelTextures.put(entry.getKey(), entry.getValue().getAsString());
                }
            });
        }
        Set<Key> used = new HashSet<>();
        JsonArray elements = javaModel.getAsJsonArray("elements");
        if (elements == null || elements.isEmpty()) {
            return null;
        }
        for (JsonElement element : elements) {
            if (!element.isJsonObject()) return null;
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            if (faces == null) continue;
            for (var face : faces.entrySet()) {
                if (!face.getValue().isJsonObject()) return null;
                JsonElement textureValue = face.getValue().getAsJsonObject().get("texture");
                if (textureValue == null) continue;
                Key resolved = resolveTexture(textureValue.getAsString(), custom, modelTextures);
                if (resolved == null) return null;
                used.add(resolved);
                if (used.size() > 1) return null;
            }
        }

        // Models without explicit face references can still use a declared single sheet.
        if (used.isEmpty()) {
            used.addAll(custom.textures().values());
            if (used.isEmpty()) {
                for (String entry : modelTextures.values()) {
                    Key resolved = resolveTexture(entry, custom, modelTextures);
                    if (resolved == null) return null;
                    used.add(resolved);
                    if (used.size() > 1) return null;
                }
            }
        }
        return used.size() == 1 ? used.iterator().next() : null;
    }

    private static @Nullable Key resolveTexture(@NotNull String reference,
                                                 @NotNull ModelDefinition.Custom custom,
                                                 @NotNull Map<String, String> modelTextures) {
        Set<String> visited = new HashSet<>();
        String value = reference;
        while (value.startsWith("#")) {
            String slot = value.substring(1);
            if (!visited.add(slot)) return null;
            Key override = custom.textures().get(slot);
            if (override != null) return override;
            value = modelTextures.get(slot);
            if (value == null) return null;
        }
        // The model's own namespace is the correct default for an unqualified key.
        return Key.key(value.contains(":") ? value : custom.model().namespace() + ":" + value);
    }

    /**
     * Bedrock resolves item icons through a flat atlas keyed by shorthand, not by path, so
     * the texture is copied to a flat location and registered here.
     */
    private void registerItemTexture(@NotNull String icon, @NotNull Key texture) {
        registerItemAtlas(icon);
        copyTexture(texture, "textures/items/" + icon + ".png");
    }

    /** Adds the atlas alias without copying; used when the caller already wrote the PNG. */
    private void registerItemAtlas(@NotNull String icon) {
        JsonArray paths = new JsonArray();
        paths.add("textures/items/" + icon);
        JsonObject entry = new JsonObject();
        entry.add("textures", paths);
        textureData.add(icon, entry);
    }

    /**
     * The item form of a block, so a Bedrock player holds the block rather than a note
     * block.
     *
     * <p>Without this the block's item falls back to its Java base material, and
     * {@code JavaBlockItemCompiler} places every block from a {@code NOTE_BLOCK} stack —
     * so a Bedrock player saw a note block in the hotbar for every custom block, and every
     * block looked identical to every other. Geyser matches on the {@code item_model} that
     * compiler stamps, which is what lets them all share the one vanilla item without
     * colliding.</p>
     *
     * <p>The icon is the block's own face texture drawn flat. A Bedrock client can render a
     * true block preview instead, but that needs the item tied to the custom block's
     * identifier rather than given its own, and a flat icon is the half that is verified.</p>
     */
    private void addBlockItem(@NotNull BlockDefinition definition, @NotNull String shorthand) {
        Key icon = iconTextureFor(definition);
        if (icon == null) {
            return;
        }
        Key key = definition.key();
        // Must stay in step with JavaBlockItemCompiler, which hardcodes NOTE_BLOCK for
        // every block whatever carrier the block itself borrows.
        String javaItem = "minecraft:note_block";
        String bedrockId = key.namespace() + ":" + key.value();
        String iconKey = shorthand + "_item";

        JsonObject mapping = new JsonObject();
        mapping.addProperty("type", "definition");
        mapping.addProperty("model", key.asString());
        mapping.addProperty("bedrock_identifier", bedrockId);
        JsonObject bedrockOptions = new JsonObject();
        bedrockOptions.addProperty("icon", iconKey);
        mapping.add("bedrock_options", bedrockOptions);

        String displayName = plainName(definition.display().name());
        if (displayName != null) {
            mapping.addProperty("display_name", displayName);
        }
        JsonObject components = new JsonObject();
        components.addProperty("minecraft:max_stack_size", 64);
        mapping.add("components", components);

        JsonArray definitions = mappedItems.has(javaItem)
                ? mappedItems.getAsJsonArray(javaItem)
                : new JsonArray();
        definitions.add(mapping);
        mappedItems.add(javaItem, definitions);

        itemRegistrations.add(new BedrockItemRegistration(
                javaItem, bedrockId, key.asString(), iconKey, displayName, 64, null, false));

        registerItemTexture(iconKey, icon);
        byte[] renderedIcon = renderedBlockIcons.remove(shorthand);
        if (renderedIcon != null) {
            pack.file("textures/items/" + iconKey + ".png", Writable.bytes(renderedIcon));
            return;
        }
        byte[] embeddedIcon = embeddedBlockIcons.remove(shorthand);
        if (embeddedIcon != null) {
            // The flat item icon comes from the same first texture; an embedded source is
            // not present in the Java pack's asset tree, so write it from the IR bytes.
            pack.file("textures/items/" + iconKey + ".png", Writable.bytes(embeddedIcon));
        }
    }

    /** The face a block's flat inventory icon is drawn from. */
    private @Nullable Key iconTextureFor(@NotNull BlockDefinition definition) {
        return switch (definition.model()) {
            case BlockModelDefinition.CubeAll cubeAll -> cubeAll.texture();
            case BlockModelDefinition.Cube cube -> cubeFaceTexture(cube.faces(), "up");
            case BlockModelDefinition.Custom custom -> {
                if (custom.textures().isEmpty()) {
                    yield null;
                }
                Map<String, Key> sorted = new TreeMap<>(custom.textures());
                yield sorted.containsKey("all") ? sorted.get("all") : sorted.values().iterator().next();
            }
            case BlockModelDefinition.Blockbench ignored -> {
                BlockbenchBlockAssets assets = blockbenchBlocks.get(definition.key().asString());
                yield assets == null ? null : assets.icon();
            }
        };
    }

    /** Adds emote props: geometry, animations and a registered client entity. */
    public void addEmotes(@NotNull Iterable<? extends io.kalo.content.emote.Emote> emotes,
                          @NotNull java.util.Set<String> skip) {
        for (io.kalo.content.emote.Emote emote : emotes) {
            io.kalo.content.emote.EmoteDefinition definition = emote.definition();
            if (definition.bedrock().enabled() && !skip.contains(definition.key().asString())) {
                Key previousOwner = pack.owner(definition.key());
                try {
                    addRig(definition.key(), emoteModel(definition), "kalo:emote_", "_emote",
                            definition.rig() == io.kalo.content.emote.EmoteDefinition.RigMode.PLAYER);
                } finally {
                    pack.owner(previousOwner);
                }
            }
        }
    }

    /** Adds vehicles: geometry, animations and a registered client entity. */
    public void addVehicles(@NotNull Iterable<? extends io.kalo.content.vehicle.Vehicle> vehicles,
                            @NotNull java.util.Set<String> skip) {
        for (io.kalo.content.vehicle.Vehicle vehicle : vehicles) {
            io.kalo.content.vehicle.VehicleDefinition definition = vehicle.definition();
            if (definition.bedrock().enabled() && !skip.contains(definition.key().asString())) {
                Key previousOwner = pack.owner(definition.key());
                try {
                    addRig(definition.key(), definition.model(), "kalo:vehicle_", "_vehicle",
                            false);
                } finally {
                    pack.owner(previousOwner);
                }
            }
        }
    }

    private static @NotNull ModelDefinition emoteModel(
            @NotNull io.kalo.content.emote.EmoteDefinition definition) {
        if (!(definition.model() instanceof ModelDefinition.Blockbench blockbench)
                || definition.skin() == null) {
            return definition.model();
        }
        Map<String, Key> textures = new TreeMap<>(blockbench.textures());
        textures.putIfAbsent("0", definition.skin());
        return new ModelDefinition.Blockbench(blockbench.source(), textures);
    }

    /** Adds Bedrock client-entity assets for each VFX layer. */
    public void addVfx(@NotNull Iterable<VfxDefinition> definitions) {
        for (VfxDefinition definition : definitions) {
            Key previous = pack.owner(definition.key());
            try {
                rigs.addAll(BedrockVfxCompiler.compile(javaSource, pack, List.of(definition),
                        key -> {
                            Item item = itemIndex.get(key);
                            return item == null ? null : item.definition().model();
                        }));
            } finally {
                pack.owner(previous);
            }
        }
    }

    private void addRig(@NotNull Key key,
                        @NotNull ModelDefinition modelDefinition,
                        @NotNull String identifierPrefix,
                        @NotNull String fileSuffix,
                        boolean playerAttachable) {
        if (!(modelDefinition instanceof ModelDefinition.Blockbench blockbench)) {
            return;
        }
        String shorthand = key.namespace() + "_" + key.value() + fileSuffix;
        try {
            io.kalo.model.ModelIR model = io.kalo.model.ModelRegistry.load(javaSource,
                    blockbench.source());
            String entityId = identifierPrefix + sanitize(shorthand);
            String geometryId = BedrockGeometry.identifierFor(key.namespace(),
                    key.value() + fileSuffix);
            io.kalo.model.bedrock.BedrockModelCompiler.Result compiled =
                    io.kalo.model.bedrock.BedrockModelCompiler.compileEntity(model,
                            geometryId, blockbench.textures(), key.namespace(), shorthand);
            io.kalo.model.ModelDiagnostics.log(LOGGER,
                    "Bedrock rig " + key.asString(), compiled.diagnostics());
            if (!compiled.compiled()) {
                LOGGER.warning("Rig model for " + key.asString()
                        + " did not compile for Bedrock: " + compiled.errorSummary());
                return;
            }
            Map<String, String> textures = new TreeMap<>();
            for (io.kalo.model.ModelTextureResolver.ResolvedTexture texture
                    : compiled.textures()) {
                String destination = "textures/entity/" + shorthand + "_"
                        + texture.material();
                if (texture.embeddedPng() != null) {
                    pack.file(destination + ".png", Writable.bytes(texture.embeddedPng()));
                } else if (javaSource.file(texturePath(texture.key())) != null) {
                    pack.file(destination + ".png", javaSource.file(texturePath(texture.key())));
                } else {
                    LOGGER.warning("Rig texture " + texture.key().asString()
                            + " is missing from the pack");
                    return;
                }
                textures.put(texture.material(), destination);
            }
            if (textures.isEmpty()) {
                return;
            }
            pack.file("models/entity/" + shorthand + ".geo.json",
                    Json.writable(compiled.geometry()));
            Map<String, String> animations = new TreeMap<>();
            if (compiled.animationIds() != null) {
                compiled.animationIds().forEach(animations::put);
            }
            List<String> loops = new ArrayList<>(compiled.loopAnimations());
            writeControllers(shorthand, compiled, loops);
            if (compiled.animations() != null) {
                pack.file("animations/" + shorthand + ".animation.json",
                        Json.writable(compiled.animations()));
            }
            JsonObject entity = placedEntity(entityId, geometryId,
                    textures.values().iterator().next(), textures, animations, loops);
            pack.file("entity/" + shorthand + ".entity.json", Json.writable(entity));
            if (playerAttachable) {
                pack.file("attachables/" + shorthand + ".json", Json.writable(
                        BedrockAttachable.playerEmote(key, geometryId,
                                textures.values().iterator().next(), textures,
                                animations, loops)));
            }
            rigs.add(new BedrockRigRegistration(key, entityId));
        } catch (Exception error) {
            LOGGER.warning("Cannot convert Bedrock rig " + key.asString() + ": "
                    + error.getMessage());
        }
    }

    /**
     * Adds custom mobs: geometry, animations and a client entity registered with Geyser.
     * Mobs without a Blockbench model keep the base mob's vanilla appearance.
     */
    public void addMobs(@NotNull Iterable<? extends io.kalo.content.mob.Mob> mobsToAdd,
                        @NotNull java.util.Set<String> skip) {
        for (io.kalo.content.mob.Mob mob : mobsToAdd) {
            io.kalo.content.mob.MobDefinition definition = mob.definition();
            if (!definition.bedrock().enabled()
                    || skip.contains(definition.key().asString())) {
                continue;
            }
            if (!(definition.model() instanceof ModelDefinition.Blockbench blockbench)) {
                continue;
            }
            Key key = definition.key();
            Key previousOwner = pack.owner(key);
            try {
            String shorthand = key.namespace() + "_" + key.value();
            try {
                io.kalo.model.ModelIR model = io.kalo.model.ModelRegistry.load(javaSource,
                        blockbench.source());
                String entityId = "kalo:mob_" + sanitize(shorthand);
                String geometryId = BedrockGeometry.identifierFor(key.namespace(),
                        key.value() + "_mob");
                io.kalo.model.bedrock.BedrockModelCompiler.Result compiled =
                        io.kalo.model.bedrock.BedrockModelCompiler.compileEntity(model,
                                geometryId, blockbench.textures(), key.namespace(),
                                shorthand + "_mob");
                io.kalo.model.ModelDiagnostics.log(LOGGER,
                        "Bedrock mob " + key.asString(), compiled.diagnostics());
                if (!compiled.compiled()) {
                    LOGGER.warning("Mob model for " + key.asString()
                            + " did not compile for Bedrock: " + compiled.errorSummary());
                    continue;
                }
                Map<String, String> textures = new TreeMap<>();
                boolean missing = false;
                for (io.kalo.model.ModelTextureResolver.ResolvedTexture texture
                        : compiled.textures()) {
                    String destination = "textures/entity/" + shorthand + "_"
                            + texture.material();
                    if (texture.embeddedPng() != null) {
                        pack.file(destination + ".png",
                                Writable.bytes(texture.embeddedPng()));
                    } else if (javaSource.file(texturePath(texture.key())) != null) {
                        pack.file(destination + ".png",
                                javaSource.file(texturePath(texture.key())));
                    } else {
                        missing = true;
                        break;
                    }
                    textures.put(texture.material(), destination);
                }
                if (missing || textures.isEmpty()) {
                    continue;
                }
                pack.file("models/entity/" + shorthand + "_mob.geo.json",
                        Json.writable(compiled.geometry()));
                Map<String, String> animations = new TreeMap<>();
                if (compiled.animationIds() != null) {
                    compiled.animationIds().forEach(animations::put);
                }
                List<String> loops = new ArrayList<>(compiled.loopAnimations());
                writeControllers(shorthand + "_mob", compiled, loops);
                if (compiled.animations() != null) {
                    pack.file("animations/" + shorthand + "_mob.animation.json",
                            Json.writable(compiled.animations()));
                }
                JsonObject entity = placedEntity(entityId, geometryId,
                        textures.values().iterator().next(), textures, animations, loops);
                pack.file("entity/" + shorthand + "_mob.entity.json",
                        Json.writable(entity));
                mobs.add(new BedrockMobRegistration(key, entityId, animations, loops));
            } catch (Exception error) {
                LOGGER.warning("Cannot convert Bedrock mob " + key.asString() + ": "
                        + error.getMessage());
            }
            } finally {
                pack.owner(previousOwner);
            }
        }
    }

    /**
     * Adds armor: the attachable that puts it on the Bedrock player model.
     *
     * <p>Separate from {@link #add} because armor needs more than an icon. Java paints an
     * equipment texture onto the player; Bedrock attaches a model and hides the vanilla
     * layer beneath it. Same result, different mechanism — the icon still goes through
     * {@code add} like any other item.</p>
     */
    public void addArmor(@NotNull Iterable<? extends io.kalo.content.armor.Armor> armors) {
        for (io.kalo.content.armor.Armor armor : armors) {
            io.kalo.content.armor.ArmorDefinition definition = armor.armorDefinition();
            Key previousOwner = pack.owner(definition.key());
            try {

            if (!definition.item().bedrock().enabled()) {
                continue;
            }
            if (definition.armorModel() != null && addWornArmor(definition)) {
                attachables++;
                continue;
            }
            io.kalo.content.armor.ArmorDefinition.EquipmentTexture equipment = definition.equipment();
            if (equipment == null) {
                // Opted out of a custom worn appearance, so the base material's own armor
                // is correct on Bedrock too.
                continue;
            }

            Key key = definition.key();
            Key source = definition.slot().usesLeggingsLayer() && equipment.leggings() != null
                    ? equipment.leggings()
                    : equipment.humanoid();
            String textureName = source.namespace() + "_" + source.value();

            pack.file(BedrockAttachable.attachablePath(key),
                    Json.writable(BedrockAttachable.attachable(definition, textureName)));

            // Java keeps armor sheets under textures/entity/equipment/<layer>/; Bedrock
            // wants one flat textures/models/armor/ with a numbered suffix.
            int layer = BedrockAttachable.textureLayer(definition.slot());
            copyEquipmentTexture(source, definition.slot(),
                    BedrockAttachable.texturePath(textureName, layer) + ".png");

            attachables++;
            } finally {
                pack.owner(previousOwner);
            }
        }
    }

    /**
     * Compiles a Blockbench worn model: geometry + animations + an attachable bound to
     * the vanilla armor bones. The model must be authored in Bedrock entity/wearer space
     * (feet at y=0, X/Z centred), which is why this uses the entity-space compiler.
     */
    private boolean addWornArmor(
            @NotNull io.kalo.content.armor.ArmorDefinition definition) {
        Key key = definition.key();
        io.kalo.content.armor.ArmorDefinition.ArmorModel worn = definition.armorModel();
        String shorthand = key.namespace() + "_" + key.value();
        try {
            io.kalo.model.ModelIR model = io.kalo.model.ModelRegistry.load(javaSource,
                    worn.source());
            String geometryId = BedrockGeometry.identifierFor(key.namespace(),
                    key.value() + "_armor");
            io.kalo.model.bedrock.BedrockModelCompiler.Result compiled =
                    io.kalo.model.bedrock.BedrockModelCompiler.compileEntity(model,
                            geometryId, worn.textures(), key.namespace(), shorthand + "_armor");
            io.kalo.model.ModelDiagnostics.log(LOGGER,
                    "Bedrock worn armor " + key.asString(), compiled.diagnostics());
            if (!compiled.compiled()) {
                LOGGER.warning("Worn armor model for " + key.asString()
                        + " did not compile for Bedrock: " + compiled.errorSummary());
                return false;
            }
            Map<String, String> textures = new TreeMap<>();
            for (io.kalo.model.ModelTextureResolver.ResolvedTexture texture
                    : compiled.textures()) {
                String destination = "textures/models/armor/" + shorthand + "_"
                        + texture.material();
                if (texture.embeddedPng() != null) {
                    pack.file(destination + ".png", Writable.bytes(texture.embeddedPng()));
                } else if (javaSource.file(texturePath(texture.key())) != null) {
                    pack.file(destination + ".png",
                            javaSource.file(texturePath(texture.key())));
                } else {
                    LOGGER.warning("Worn armor texture " + texture.key().asString()
                            + " is missing from the pack");
                    return false;
                }
                textures.put(texture.material(), destination);
            }
            if (textures.isEmpty()) {
                return false;
            }
            Map<String, String> animations = new TreeMap<>();
            if (compiled.animationIds() != null) {
                compiled.animationIds().forEach((name, id) -> {
                    if (!name.startsWith("hold_")) {
                        animations.put(name, id);
                    }
                });
            }
            List<String> loops = new ArrayList<>();
            for (String loop : compiled.loopAnimations()) {
                if (!loop.startsWith("hold_")) {
                    loops.add(loop);
                }
            }
            writeControllers(shorthand + "_armor", compiled, loops);
            pack.file("models/armor/" + shorthand + ".geo.json",
                    Json.writable(compiled.geometry()));
            if (compiled.animations() != null) {
                pack.file("animations/" + shorthand + "_armor.animation.json",
                        Json.writable(compiled.animations()));
            }
            pack.file(BedrockAttachable.attachablePath(key), Json.writable(
                    BedrockAttachable.wornModel(definition, geometryId,
                            textures.values().iterator().next(), textures,
                            animations, loops)));
            return true;
        } catch (Exception error) {
            LOGGER.warning("Cannot convert worn armor model " + key.asString() + ": "
                    + error.getMessage());
            return false;
        }
    }

    /** Armor sheets live under a different root than item textures on the Java side. */
    private void copyEquipmentTexture(@NotNull Key texture,
                                      @NotNull io.kalo.content.armor.ArmorSlot slot,
                                      @NotNull String destination) {
        String layerDir = slot.usesLeggingsLayer() ? "humanoid_leggings" : "humanoid";
        String source = "assets/" + texture.namespace() + "/textures/entity/equipment/"
                + layerDir + "/" + texture.value() + ".png";

        Writable content = javaSource.file(source);
        if (content != null) {
            pack.file(destination, content);
        }
    }

    /**
     * Adds custom blocks: their Bedrock appearance, and the record a Geyser extension
     * needs to register them.
     *
     * <p>Bedrock has real custom blocks rather than borrowed states, so nothing here
     * mirrors the carrier the Java side uses. What does have to cross over is the pairing
     * — which Java block state corresponds to which Bedrock identifier — because that is
     * what Geyser overrides. The resource pack can only supply the look; registration
     * itself happens inside Geyser.</p>
     *
     * @param allocator supplies the carrier and state each block occupies. The carrier is
     *                  part of the answer, not decoration: a block that spilled onto
     *                  tripwire or scaffolding is not a note block, and naming it as one
     *                  would override a Java state that was never placed.
     */
    public void addBlocks(@NotNull Iterable<? extends Block> blocks,
                          @NotNull java.util.function.Function<Key, BlockStateAllocator.Assignment> allocator,
                          @NotNull java.util.Set<String> skip) {
        for (Block block : blocks) {
            BlockDefinition definition = block.definition();
            if (!definition.bedrock().enabled()) {
                continue;
            }
            if (skip.contains(definition.key().asString())) {
                // Java could not place this block, so Bedrock must not either. Registering
                // on one platform and not the other means a Bedrock player sees something
                // the Java player beside them does not.
                skipped++;
                continue;
            }

            Key key = definition.key();
            Key previousOwner = pack.owner(key);
            try {
            // Geyser namespaces every custom block it registers itself, and a
            // custom_mappings file cannot override that, so the identifier is built the
            // one way both registration paths can agree on. The pack's blocks.json is
            // keyed by it, so a mismatch renders the block untextured.
            String shorthand = key.namespace() + "_" + key.value();
            String bedrockId = BedrockBlockRegistration.identifierFor(shorthand);

            if (definition.model() instanceof BlockModelDefinition.Blockbench blockbench) {
                BlockbenchBlockAssets assets = compileBlockbenchBlock(definition, blockbench,
                        shorthand);
                if (assets == null) {
                    skipped++;
                    continue;
                }
                blockbenchBlocks.put(key.asString(), assets);
            }

            JsonElement textures = blockTextures(definition, shorthand);
            if (textures == null) {
                // Custom geometry, which Bedrock needs authored separately.
                skipped++;
                continue;
            }

            JsonObject blockDefinition = new JsonObject();
            blockDefinition.add("textures", textures);
            blockDefinition.addProperty("sound", "stone");
            blockDefinitions.add(bedrockId, blockDefinition);

            JsonObject record = new JsonObject();
            record.addProperty("java_key", key.asString());
            record.addProperty("bedrock_identifier", bedrockId);
            // blocks.json lives in the resource pack; these names live in the block
            // behaviour sent by Geyser. Both halves are required, and the texture names
            // must point at the exact terrain-atlas shorthands generated above.
            Map<String, String> materials = blockMaterialInstances(definition, shorthand);
            JsonObject materialJson = new JsonObject();
            materials.forEach(materialJson::addProperty);
            record.add("material_instances", materialJson);
            JavaBlockMode javaMode = definition.java().mode();
            record.addProperty("java_mode", javaMode.name().toLowerCase(Locale.ROOT));
            BlockStateAllocator.Assignment assignment =
                    javaMode == JavaBlockMode.NATIVE ? allocator.apply(key) : null;
            String javaIdentifier = null;
            if (assignment != null) {
                record.addProperty("java_carrier", assignment.carrier().name());
                record.addProperty("java_carrier_state", assignment.state());
                javaIdentifier = assignment.javaIdentifier();
                record.addProperty("java_identifier", javaIdentifier);
            }
            String displayName = plainName(definition.display().name());
            if (displayName != null) {
                record.addProperty("display_name", displayName);
            }
            float hardness = definition.behaviour().unbreakable()
                    ? Float.MAX_VALUE
                    : definition.behaviour().hardness();
            record.addProperty("hardness", hardness);
            String geometry = switch (definition.model()) {
                case BlockModelDefinition.Custom ignored ->
                        BedrockGeometry.identifierFor(key.namespace(), key.value());
                case BlockModelDefinition.Blockbench ignored ->
                        blockbenchBlocks.get(key.asString()).geometryIdentifier();
                default -> "minecraft:geometry.full_block";
            };
            record.addProperty("geometry", geometry);
            mappedBlocks.add(record);
            blockRegistrations.add(new BedrockBlockRegistration(
                    key.asString(), bedrockId, javaIdentifier, geometry, displayName,
                    hardness, materials));
            addBlockItem(definition, shorthand);
            } finally {
                pack.owner(previousOwner);
            }
        }
    }

    /**
     * Compiles a Blockbench block once. Textures/geometry are written by
     * {@link #blockTextures} after all sources have been preflighted.
     */
    private @Nullable BlockbenchBlockAssets compileBlockbenchBlock(
            @NotNull BlockDefinition definition,
            @NotNull BlockModelDefinition.Blockbench blockbench,
            @NotNull String shorthand) {
        Key key = definition.key();
        try {
            io.kalo.model.ModelIR model = io.kalo.model.ModelRegistry.load(javaSource,
                    blockbench.source());
            String identifier = BedrockGeometry.identifierFor(key.namespace(), key.value());
            io.kalo.model.bedrock.BedrockModelCompiler.Result compiled =
                    io.kalo.model.bedrock.BedrockModelCompiler.compile(model, identifier,
                            blockbench.textures(), key.namespace(), shorthand);
            io.kalo.model.ModelDiagnostics.log(LOGGER,
                    "Bedrock Blockbench block " + key.asString(), compiled.diagnostics());
            if (!compiled.compiled()) {
                return null;
            }
            for (io.kalo.model.ModelTextureResolver.ResolvedTexture texture
                    : compiled.textures()) {
                if (texture.embeddedPng() == null
                        && javaSource.file(texturePath(texture.key())) == null) {
                    return null;
                }
            }
            // Bedrock animation is delivered through a client entity, and the entity is
            // only spawned for display-backed (virtual) blocks. An explicitly native
            // block stays a static block on both platforms. Hold poses are synthesized
            // for every model, so a block is only animated when it has an authored loop.
            Map<String, String> animations = new TreeMap<>();
            List<String> loops = new ArrayList<>();
            if (compiled.animationIds() != null) {
                compiled.animationIds().forEach((name, id) -> {
                    if (!name.startsWith("hold_")) {
                        animations.put(name, id);
                    }
                });
            }
            for (String loop : compiled.loopAnimations()) {
                if (!loop.startsWith("hold_")) {
                    loops.add(loop);
                }
            }
            boolean animated = definition.java().mode() == JavaBlockMode.VIRTUAL
                    && compiled.animations() != null
                    && (!loops.isEmpty() || !compiled.controllerIds().isEmpty());
            Map<String, String> entityTextures = new TreeMap<>();
            if (animated) {
                // Bedrock block geometry is static. The visible mesh becomes a client
                // entity, so its textures live under entity/ rather than the terrain atlas.
                for (io.kalo.model.ModelTextureResolver.ResolvedTexture texture
                        : compiled.textures()) {
                    String destination = "textures/entity/" + shorthand + "_"
                            + texture.material();
                    if (texture.embeddedPng() != null) {
                        pack.file(destination + ".png", Writable.bytes(texture.embeddedPng()));
                    } else {
                        copyTexture(texture.key(), destination + ".png");
                    }
                    entityTextures.put(texture.material(), destination);
                }
                JsonObject blockAnimations = compiled.animations().deepCopy();
                JsonObject table = blockAnimations.getAsJsonObject("animations");
                if (table != null) {
                    for (String id : List.copyOf(table.keySet())) {
                        if (id.endsWith(".hold_first_person")
                                || id.endsWith(".hold_third_person")) {
                            table.remove(id);
                        }
                    }
                }
                pack.file("animations/" + shorthand + "_block.animation.json",
                        Json.writable(blockAnimations));
                writeControllers(shorthand + "_block", compiled, loops);
                String entityId = "kalo:block_" + sanitize(shorthand);
                JsonObject entity = placedEntity(entityId, identifier,
                        entityTextures.values().iterator().next(), entityTextures,
                        animations, loops);
                pack.file("entity/" + shorthand + "_block.entity.json",
                        Json.writable(entity));
                animatedBlocks.add(new BedrockAnimatedBlockRegistration(key, entityId,
                        animations, loops));
            }
            byte[] renderedIcon = BedrockModelIcon.render(model, compiled.textures(),
                    javaSource);
            if (renderedIcon != null) {
                renderedBlockIcons.put(shorthand, renderedIcon);
            }
            Map<String, String> materials = new TreeMap<>();
            Map<String, io.kalo.model.ModelTextureResolver.ResolvedTexture> byMaterial =
                    new LinkedHashMap<>();
            for (io.kalo.model.ModelTextureResolver.ResolvedTexture texture
                    : compiled.textures()) {
                materials.put(texture.material(), shorthand + "_" + texture.material());
                byMaterial.putIfAbsent(texture.material(), texture);
            }
            if (materials.isEmpty()) {
                return null;
            }
            io.kalo.model.ModelTextureResolver.ResolvedTexture icon = compiled.textures().getFirst();
            if (icon.embeddedPng() != null) {
                embeddedBlockIcons.put(shorthand, icon.embeddedPng());
            }
            return new BlockbenchBlockAssets(compiled.geometry(), identifier, materials,
                    byMaterial, icon.key(), animated, Map.copyOf(entityTextures));
        } catch (Exception error) {
            LOGGER.warning("Cannot convert Bedrock Blockbench block " + definition.key()
                    + ": " + error.getMessage());
            return null;
        }
    }

    private record BlockbenchBlockAssets(
            @NotNull JsonObject geometry,
            @NotNull String geometryIdentifier,
            @NotNull Map<String, String> materials,
            @NotNull Map<String, io.kalo.model.ModelTextureResolver.ResolvedTexture> textures,
            @NotNull Key icon,
            boolean animated,
            @NotNull Map<String, String> entityTextures
    ) {
    }

    /**
     * Registers each face texture in the terrain atlas and returns what {@code blocks.json}
     * should point at — a single shorthand for a uniform cube, or a per-face object.
     */
    private @Nullable JsonElement blockTextures(@NotNull BlockDefinition definition, @NotNull String shorthand) {
        switch (definition.model()) {
            case BlockModelDefinition.CubeAll cubeAll -> {
                registerTerrainTexture(shorthand, cubeAll.texture());
                return new com.google.gson.JsonPrimitive(shorthand);
            }
            case BlockModelDefinition.Cube cube -> {
                JsonObject faces = new JsonObject();
                // Java's authoring shape allows an `all` fallback plus `top`/`bottom`
                // aliases. Bedrock's blocks.json does not expand those: `side` means
                // only the four lateral faces, and `particle` is not a face at all.
                // Resolve every rendered face explicitly so top and bottom cannot be
                // left untextured.
                for (String face : java.util.List.of(
                        "down", "up", "north", "south", "west", "east")) {
                    Key texture = cubeFaceTexture(cube.faces(), face);
                    String faceShorthand = shorthand + "_" + face;
                    registerTerrainTexture(faceShorthand, texture);
                    faces.addProperty(bedrockFace(face), faceShorthand);
                }
                return faces;
            }
            case BlockModelDefinition.Custom custom -> {
                // A hand-authored Java model: convert its shape to Bedrock geometry and
                // register whatever textures it declares.
                if (custom.textures().isEmpty()) {
                    // The shape may be convertible, but Geyser cannot build a material
                    // instance without a texture slot. Silently emitting an unregistered
                    // shorthand produces the purple/black missing texture in-game.
                    return null;
                }
                JsonObject geometry = convertGeometry(definition, custom);
                if (geometry == null) {
                    return null;
                }
                Map<String, Key> sorted = new TreeMap<>(custom.textures());
                sorted.forEach((slot, texture) -> registerTerrainTexture(
                        shorthand + "_" + BedrockGeometry.materialName(slot), texture));

                // blocks.json requires a valid fallback even though named geometry faces
                // use the explicit material instances in the Geyser registration.
                String fallbackSlot = sorted.containsKey("all")
                        ? "all"
                        : sorted.keySet().iterator().next();
                return new com.google.gson.JsonPrimitive(
                        shorthand + "_" + BedrockGeometry.materialName(fallbackSlot));
            }
            case BlockModelDefinition.Blockbench ignored -> {
                BlockbenchBlockAssets assets = blockbenchBlocks.get(definition.key().asString());
                if (assets == null) {
                    return null;
                }
                assets.textures().forEach((material, texture) -> {
                    String terrainShorthand = assets.materials().get(material);
                    if (assets.animated()) {
                        // The client entity draws the visible mesh; keeping the block
                        // surface transparent avoids drawing the model twice.
                        registerTerrainTexture(terrainShorthand,
                                BedrockTransparentTexture.png());
                    } else if (texture.embeddedPng() != null) {
                        registerTerrainTexture(terrainShorthand, texture.embeddedPng());
                    } else {
                        registerTerrainTexture(terrainShorthand, texture.key());
                    }
                });
                Key key = definition.key();
                pack.file("models/blocks/" + key.namespace() + "_" + key.value() + ".geo.json",
                        Json.writable(assets.geometry()));
                geometries.addProperty(key.asString(), assets.geometryIdentifier());
                String fallback = assets.materials().values().iterator().next();
                return new com.google.gson.JsonPrimitive(fallback);
            }
        }
    }

    /**
     * Material name -> terrain-atlas shorthand consumed by Geyser's block API.
     *
     * <p>The generated resource pack alone cannot fill this behaviour component. An
     * absent texture makes Geyser fall back to the block identifier, which differs from
     * Kalo's underscore-separated atlas keys.</p>
     */
    private @NotNull Map<String, String> blockMaterialInstances(
            @NotNull BlockDefinition definition, @NotNull String shorthand) {
        Map<String, String> materials = new TreeMap<>();
        switch (definition.model()) {
            case BlockModelDefinition.CubeAll ignored -> materials.put("*", shorthand);
            case BlockModelDefinition.Cube ignored -> {
                for (String face : java.util.List.of(
                        "down", "up", "north", "south", "west", "east")) {
                    materials.put(face, shorthand + "_" + face);
                }
            }
            case BlockModelDefinition.Custom custom -> {
                new TreeMap<>(custom.textures()).forEach((slot, ignored) -> {
                    String material = BedrockGeometry.materialName(slot);
                    materials.put(material, shorthand + "_" + material);
                });
            }
            case BlockModelDefinition.Blockbench ignored -> {
                BlockbenchBlockAssets assets = blockbenchBlocks.get(definition.key().asString());
                if (assets != null) {
                    materials.putAll(assets.materials());
                }
            }
        }
        return Map.copyOf(materials);
    }

    private static @NotNull Key cubeFaceTexture(@NotNull java.util.Map<String, Key> textures,
                                                 @NotNull String face) {
        Key texture = switch (face) {
            case "down" -> textures.getOrDefault("down", textures.get("bottom"));
            case "up" -> textures.getOrDefault("up", textures.get("top"));
            default -> textures.get(face);
        };
        if (texture == null) {
            texture = textures.get("all");
        }
        if (texture == null) {
            // BlockModelDefinition.Cube validates this invariant. Keeping the guard here
            // makes failures from third-party implementations descriptive.
            throw new IllegalArgumentException("cube has no texture for face '" + face + "'");
        }
        return texture;
    }

    /**
     * Converts the block's Java model into Bedrock geometry and writes it into the pack.
     *
     * <p>Reads the model out of the Java pack rather than off disk, because that pack has
     * already gathered every content pack's assets in one place.</p>
     *
     * @return the geometry, or {@code null} if the model has no shape of its own — a
     *         model that only sets a parent inherits one, which cannot be resolved here
     */
    private @Nullable JsonObject convertGeometry(@NotNull BlockDefinition definition,
                                                 @NotNull BlockModelDefinition.Custom custom) {
        Key model = custom.model();
        String source = "assets/" + model.namespace() + "/models/" + model.value() + ".json";
        Writable content = javaSource.file(source);
        if (content == null) {
            return null;
        }

        try {
            JsonObject javaModel = com.google.gson.JsonParser
                    .parseString(new String(content.toByteArray(), StandardCharsets.UTF_8))
                    .getAsJsonObject();

            Key key = definition.key();
            String identifier = BedrockGeometry.identifierFor(key.namespace(), key.value());
            JsonObject geometry = BedrockGeometry.convert(identifier, javaModel);
            if (geometry == null) {
                return null;
            }

            pack.file("models/blocks/" + key.namespace() + "_" + key.value() + ".geo.json",
                    Json.writable(geometry));
            geometries.addProperty(key.asString(), identifier);
            return geometry;
        } catch (Exception e) {
            return null;
        }
    }

    private void registerTerrainTexture(@NotNull String shorthand, @NotNull Key texture) {
        JsonObject entry = new JsonObject();
        entry.addProperty("textures", "textures/blocks/" + shorthand);
        terrainTextureData.add(shorthand, entry);

        copyTexture(texture, "textures/blocks/" + shorthand + ".png");
    }

    private void registerTerrainTexture(@NotNull String shorthand, byte @NotNull [] png) {
        JsonObject entry = new JsonObject();
        entry.addProperty("textures", "textures/blocks/" + shorthand);
        terrainTextureData.add(shorthand, entry);
        pack.file("textures/blocks/" + shorthand + ".png", Writable.bytes(png));
    }

    /** Java names cube faces by direction; Bedrock uses up/down/side plus directions. */
    private static @NotNull String bedrockFace(@NotNull String javaFace) {
        return switch (javaFace) {
            case "top" -> "up";
            case "bottom" -> "down";
            case "all" -> "side";
            default -> javaFace;
        };
    }

    private void writeControllers(@NotNull String stem,
                                  @NotNull io.kalo.model.bedrock.BedrockModelCompiler.Result compiled,
                                  @NotNull List<String> autoAnimate) {
        if (compiled.controllers() != null) {
            pack.file("animation_controllers/" + stem + ".animation_controllers.json",
                    Json.writable(compiled.controllers()));
        }
        autoAnimate.addAll(compiled.controllerIds());
    }

    /** Pulls a texture out of the Java pack, which already gathered every pack's assets. */
    private void copyTexture(@NotNull Key texture, @NotNull String destination) {
        String source = "assets/" + texture.namespace() + "/textures/" + texture.value() + ".png";
        Writable content = javaSource.file(source);
        if (content != null) {
            pack.file(destination, content);
        }
    }

    /** Writes the files that describe the pack as a whole. Call once, after all adds. */
    public @NotNull Result finish() {
        // Translate Java base-pack resources into Bedrock-native paths before atlas
        // serialization. Custom item/block registrations below are still authoritative.
        JavaAssetsBedrockTranslator.Report translation =
                JavaAssetsBedrockTranslator.translate(javaSource, pack, textureData, terrainTextureData);
        if (translation.textures() > 0 || translation.soundFiles() > 0 || translation.locales() > 0) {
            LOGGER.info("Converted Java base assets for Bedrock: " + translation.textures()
                    + " textures (" + translation.vanillaTextures() + " vanilla overrides), "
                    + translation.soundFiles() + " sounds, " + translation.locales() + " language(s)");
        }
        if (translation.unsupportedAssets() > 0) {
            LOGGER.warning(translation.unsupportedAssets()
                    + " Java asset descriptor(s) still need specialized Bedrock conversion"
                    + " or runtime Geyser item/block identity; examples: "
                    + translation.unsupportedExamples());
        }
        JsonObject itemTexture = new JsonObject();
        itemTexture.addProperty("resource_pack_name", "kalo");
        itemTexture.addProperty("texture_name", "atlas.items");
        itemTexture.add("texture_data", textureData);

        pack.file("textures/item_texture.json", Json.writable(itemTexture));
        pack.file("manifest.json", Json.writable(manifest()));
        pack.file(SessionContentManifest.PATH,
                Json.writable(SessionContentManifest.json(pack.owners())));

        if (!blockDefinitions.isEmpty()) {
            JsonObject blocks = new JsonObject();
            blocks.addProperty("format_version", "1.21.0");
            blockDefinitions.entrySet().forEach(entry -> blocks.add(entry.getKey(), entry.getValue()));
            pack.file("blocks.json", Json.writable(blocks));

        }
        if (!terrainTextureData.isEmpty()) {
            JsonObject terrain = new JsonObject();
            terrain.addProperty("resource_pack_name", "kalo");
            terrain.addProperty("texture_name", "atlas.terrain");
            terrain.addProperty("padding", 8);
            terrain.addProperty("num_mip_levels", 4);
            terrain.add("texture_data", terrainTextureData);
            pack.file("textures/terrain_texture.json", Json.writable(terrain));
        }

        JsonObject itemMappings = new JsonObject();
        itemMappings.addProperty("format_version", 2);
        itemMappings.add("items", mappedItems);

        int itemDefinitions = mappedItems.entrySet().stream()
                .mapToInt(entry -> entry.getValue().getAsJsonArray().size())
                .sum();
        return new Result(target, Json.writable(itemMappings), Json.writable(blockMappings()),
                mappedItems.size(), itemDefinitions,
                blockRegistrations.size(), attachables, skipped, generation,
                List.copyOf(blockRegistrations), List.copyOf(itemRegistrations),
                List.copyOf(placedModels), List.copyOf(animatedBlocks),
                List.copyOf(mobs), List.copyOf(rigs));
    }

    /**
     * Geyser's own custom-block mapping file, keyed by the Java state each block overrides.
     *
     * <p>Its own format, not Kalo's: Geyser reads {@code custom_mappings} natively, so a
     * server that runs Geyser in a separate process needs no Kalo artifact inside it at
     * all. Blocks use {@code format_version} 1 while items use 2, which is why these are
     * two files rather than one.</p>
     *
     * <p>Virtual blocks are absent by definition — they have no carrier state to override,
     * because on Java they are an entity wearing a block's appearance.</p>
     */
    private @NotNull JsonObject blockMappings() {
        // Keyed by plain Java block id with states under state_overrides; see
        // GeyserBlockMappings for why the bracketed form is rejected by Geyser.
        return io.kalo.platform.bedrock.GeyserBlockMappings.encode(blockRegistrations);
    }

    /**
     * @param pack        the Bedrock pack, ready to be written as {@code .mcpack}
     * @param itemMappings  Geyser's custom_mappings file for items (format_version 2)
     * @param blockMappings Geyser's custom_mappings file for blocks (format_version 1);
     *                      a separate file because the two formats version separately
     * @param mappedCount  how many vanilla items carry at least one custom definition
     * @param itemCount    how many custom items exist across them — the number a reader
     *                     expects, since a thousand custom items may all sit on PAPER
     * @param blockCount   how many custom blocks got a Bedrock appearance
     * @param armorCount   how many armor pieces render on the Bedrock player model
     * @param skippedCount content Bedrock did not get, whether because it needs geometry
     *                     Kalo cannot produce or because Java could not place it either
     */
    public record Result(@NotNull ResourcePack pack,
                         @NotNull Writable itemMappings, @NotNull Writable blockMappings,
                         int mappedCount, int itemCount, int blockCount, int armorCount,
                         int skippedCount,
                         @NotNull BedrockRegistrationSnapshot.Generation generation,
                         @NotNull List<BedrockBlockRegistration> registrations,
                         @NotNull List<BedrockItemRegistration> itemRegistrations,
                         @NotNull List<BedrockPlacedModelRegistration> placedModels,
                         @NotNull List<BedrockAnimatedBlockRegistration> animatedBlocks,
                         @NotNull List<BedrockMobRegistration> mobs,
                         @NotNull List<BedrockRigRegistration> rigs) {
        public Result {
            registrations = List.copyOf(registrations);
            itemRegistrations = List.copyOf(itemRegistrations);
            placedModels = List.copyOf(placedModels);
            animatedBlocks = List.copyOf(animatedBlocks);
            mobs = List.copyOf(mobs);
            rigs = List.copyOf(rigs);
        }
    }

    /**
     * Largest model dimension, in model units (16 = one block).
     *
     * <p>Only used to keep an outlier from arriving in the hand at twice a block; a model
     * that already fits is left exactly as its author drew it.</p>
     */
    static double modelSpan(@NotNull JsonObject model) {
        com.google.gson.JsonArray elements = model.getAsJsonArray("elements");
        if (elements == null) {
            return 0;
        }
        double span = 0;
        for (com.google.gson.JsonElement element : elements) {
            if (!element.isJsonObject()) continue;
            com.google.gson.JsonObject entry = element.getAsJsonObject();
            com.google.gson.JsonArray from = entry.getAsJsonArray("from");
            com.google.gson.JsonArray to = entry.getAsJsonArray("to");
            if (from == null || to == null || from.size() < 3 || to.size() < 3) continue;
            for (int axis = 0; axis < 3; axis++) {
                span = Math.max(span, Math.abs(to.get(axis).getAsDouble()
                        - from.get(axis).getAsDouble()));
            }
        }
        return span;
    }

    /** A hand-held model larger than this (0.625 block) is scaled down automatically. */
    private static final double MAX_HAND_UNITS = 10.0;

    /**
     * Base scale for a held model, or null when the author's own size already fits.
     *
     * <p>Java renders a held item at half the model's authored size; Bedrock has no such
     * base, so the compiler supplies it. A model authored much larger than a vanilla item
     * would still arrive oversized, so the base is reduced until the longest side fits
     * {@link #MAX_HAND_UNITS}. Nothing is ever enlarged: a small model stays small.</p>
     */
    static @org.jetbrains.annotations.Nullable Double autoHandScale(@NotNull JsonObject model) {
        double span = modelSpan(model);
        if (span <= 0) {
            return null;
        }
        double display = 1.0;
        com.google.gson.JsonObject displaySection = model.getAsJsonObject("display");
        if (displaySection != null && displaySection.has("thirdperson_righthand")) {
            com.google.gson.JsonObject third =
                    displaySection.getAsJsonObject("thirdperson_righthand");
            if (third != null && third.has("scale")) {
                com.google.gson.JsonArray scale = third.getAsJsonArray("scale");
                if (scale != null && scale.size() == 3) {
                    display = Math.max(scale.get(0).getAsDouble(),
                            Math.max(scale.get(1).getAsDouble(), scale.get(2).getAsDouble()));
                }
            }
        }
        double effective = span * display * 0.5;
        if (effective <= MAX_HAND_UNITS) {
            return null;
        }
        return 0.5 * (MAX_HAND_UNITS / effective);
    }

    private static @NotNull String sanitize(@NotNull String value) {
        String cleaned = value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_+|_+$", "");
        return cleaned.isBlank() ? "model" : cleaned;
    }

    static @NotNull JsonObject placedEntity(@NotNull String identifier,
                                                    @NotNull String geometry,
                                                    @NotNull String texture,
                                                    @NotNull Map<String, String> materialPaths,
                                                    @NotNull Map<String, String> animations,
                                                    @NotNull List<String> loops) {
        JsonObject materials = new JsonObject();
        materials.addProperty("default", "entity_alphatest");
        JsonObject textures = new JsonObject();
        textures.addProperty("default", texture);
        materialPaths.forEach((material, path) -> {
            if (material.equals("default") || material.equals("enchanted")) {
                return;
            }
            materials.addProperty(material, "entity_alphatest");
            textures.addProperty(material, path);
        });
        JsonObject geometryRef = new JsonObject();
        geometryRef.addProperty("default", geometry);

        JsonObject description = new JsonObject();
        description.addProperty("identifier", identifier);
        description.add("materials", materials);
        description.add("textures", textures);
        description.add("geometry", geometryRef);
        if (!animations.isEmpty()) {
            JsonObject animationRefs = new JsonObject();
            animations.forEach(animationRefs::addProperty);
            description.add("animations", animationRefs);
        }
        if (!loops.isEmpty()) {
            JsonArray animate = new JsonArray();
            loops.forEach(animate::add);
            JsonObject scripts = new JsonObject();
            scripts.add("animate", animate);
            description.add("scripts", scripts);
        }
        JsonArray controllers = new JsonArray();
        controllers.add("controller.render.default");
        description.add("render_controllers", controllers);

        JsonObject body = new JsonObject();
        body.add("description", description);
        JsonObject entity = new JsonObject();
        entity.addProperty("format_version", "1.10.0");
        entity.add("minecraft:client_entity", body);
        return entity;
    }

    private static @Nullable String plainName(@NotNull ItemDefinition definition) {
        return plainName(definition.display().name());
    }

    private static @Nullable String plainName(
            @Nullable net.kyori.adventure.text.Component component) {
        return component == null
                ? null
                : PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static @NotNull JsonObject manifest() {
        JsonObject header = new JsonObject();
        header.addProperty("name", "Kalo Bedrock Content");
        header.addProperty("description", "Generated by Kalo");
        header.addProperty("uuid", stableUuid("kalo-bedrock-header"));
        header.add("version", version());
        // Oldest Bedrock version this pack's format is valid for.
        header.add("min_engine_version", engineVersion());

        JsonObject module = new JsonObject();
        module.addProperty("type", "resources");
        module.addProperty("description", "Kalo generated resources");
        module.addProperty("uuid", stableUuid("kalo-bedrock-module"));
        module.add("version", version());

        JsonArray modules = new JsonArray();
        modules.add(module);

        JsonObject manifest = new JsonObject();
        manifest.addProperty("format_version", 2);
        manifest.add("header", header);
        manifest.add("modules", modules);
        return manifest;
    }

    /** Bedrock manifests take versions as [major, minor, patch], not as a string. */
    private static @NotNull JsonArray version() {
        JsonArray version = new JsonArray();
        version.add(1);
        version.add(0);
        version.add(0);
        return version;
    }

    private static @NotNull JsonArray engineVersion() {
        JsonArray version = new JsonArray();
        version.add(1);
        version.add(21);
        version.add(0);
        return version;
    }

    /**
     * Derived from a fixed name so the pack keeps the same identity across regenerations
     * — a changing uuid makes Bedrock treat it as a different pack every time.
     */
    private static @NotNull String stableUuid(@NotNull String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * Delegating pack that records which content key owns each file. The owning key is set
     * around one content's compile pass; files written outside such a pass (manifests,
     * atlases, translated base assets) stay ownerless and are always shared.
     */
    private static final class TrackedPack implements ResourcePack {
        private final ResourcePack delegate;
        private final Map<String, String> owners = new TreeMap<>();
        private @Nullable Key owner;

        private TrackedPack(@NotNull ResourcePack delegate) {
            this.delegate = delegate;
        }

        /** Sets the current owner and returns the previous one for try/finally restore. */
        private @Nullable Key owner(@Nullable Key next) {
            Key previous = owner;
            owner = next;
            return previous;
        }

        private @NotNull Map<String, String> owners() {
            return Map.copyOf(owners);
        }

        @Override
        public void file(@NotNull String path, @NotNull Writable content) {
            String normalized = normalize(path);
            delegate.file(normalized, content);
            if (owner == null) {
                owners.remove(normalized);
            } else {
                owners.put(normalized, owner.asString());
            }
        }

        @Override
        public @Nullable Writable file(@NotNull String path) {
            return delegate.file(path);
        }

        @Override
        public boolean removeFile(@NotNull String path) {
            String normalized = normalize(path);
            owners.remove(normalized);
            return delegate.removeFile(normalized);
        }

        @Override
        public @NotNull Map<String, Writable> files() {
            return delegate.files();
        }

        @Override
        public @NotNull PackMeta meta() {
            return delegate.meta();
        }

        @Override
        public void meta(@NotNull PackMeta meta) {
            delegate.meta(meta);
        }

        private static @NotNull String normalize(@NotNull String path) {
            String normalized = path.replace('\\', '/');
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            return normalized;
        }
    }
}

package io.kalo.integration;

import io.kalo.content.item.Item;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.manager.RegistryManager;
import io.kalo.converter.GuiCustomSkullScanner;
import io.kalo.platform.bedrock.BedrockAnimatedBlockRegistration;
import io.kalo.platform.bedrock.BedrockBlockRegistration;
import io.kalo.platform.bedrock.BedrockItemRegistration;
import io.kalo.platform.bedrock.BedrockMobRegistration;
import io.kalo.platform.bedrock.BedrockPlacedModelRegistration;
import io.kalo.platform.bedrock.BedrockRegistrationSnapshot;
import io.kalo.platform.bedrock.BedrockRigRegistration;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.component.BoxComponent;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.component.GeometryComponent;
import org.geysermc.geyser.api.block.custom.component.MaterialInstance;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.SessionLoadResourcePacksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomSkullsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.component.java.JavaItemDataComponents;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Registers Kalo's blocks with Geyser from inside the server process.
 *
 * <p>Most servers run Geyser as a plugin rather than standalone, which means it shares
 * this JVM — and then a separate extension jar is pure ceremony. The extension existed
 * only because the two were assumed to be different processes: it made the server owner
 * install a second artifact and hand-copy {@code bedrock-mappings.json} between them, and
 * every regeneration meant copying it again or serving stale blocks.</p>
 *
 * <p>{@code EventRegistrar.of} is Geyser's own hook for exactly this: an owner for event
 * subscriptions that is not an extension. Nothing here needs the extension to exist.</p>
 *
 * <p>The standalone extension remains for servers that genuinely run Geyser in another
 * process, which this cannot reach.</p>
 */
public final class GeyserBridge {

    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(GeyserBridge.class.getName());

    private static final java.util.concurrent.atomic.AtomicReference<CachedPack> CACHED_PACK =
            new java.util.concurrent.atomic.AtomicReference<>();

    private GeyserBridge() {
    }

    /**
     * Subscribes to Geyser's block definition event when Geyser is in this JVM.
     *
     * <p>Called through {@link GeyserIntegration}, which does the is-it-present check —
     * this class names Geyser types in its own signatures, so loading it at all requires
     * Geyser on the classpath.</p>
     */
    static @NotNull Object register(@NotNull Object owner) {
        EventRegistrar registrar = EventRegistrar.of(owner);

        // Geyser-Spigot enables its Bukkit plugin first, but deliberately defers
        // GeyserImpl.start() (and these definition events) to ServerLoadEvent. Paper
        // enables Kalo and executes this subscription before that event is fired.
        GeyserApi.api().eventBus().subscribe(registrar, GeyserDefineCustomBlocksEvent.class,
                GeyserBridge::onDefineCustomBlocks);
        GeyserApi.api().eventBus().subscribe(registrar, GeyserDefineCustomItemsEvent.class,
                GeyserBridge::onDefineCustomItems);
        GeyserApi.api().eventBus().subscribe(registrar, GeyserDefineCustomSkullsEvent.class,
                GeyserBridge::onDefineCustomSkulls);
        GeyserApi.api().eventBus().subscribe(registrar, GeyserDefineEntitiesEvent.class,
                GeyserBridge::onDefineEntities);
        GeyserApi.api().eventBus().subscribe(registrar, GeyserDefineResourcePacksEvent.class,
                GeyserBridge::onDefineResourcePacks);
        // Initial generation finishes before this bridge is installed. The per-session
        // hook remains a defensive fallback for a Geyser reload or a pack that was
        // temporarily unavailable during its resource-pack lifecycle event.
        GeyserApi.api().eventBus().subscribe(registrar, SessionLoadResourcePacksEvent.class,
                GeyserBridge::onSessionLoadResourcePacks);

        Plugins.logger().info("Registering Kalo items, blocks and resource pack with Geyser directly"
                + " — no extension needed");
        return registrar;
    }

    /** Unsubscribes on Kalo disable so Geyser cannot call into torn-down registries. */
    static void unregister(@NotNull Object registered) {
        GeyserApi.api().eventBus().unregisterAll((EventRegistrar) registered);
    }

    /**
     * Reads straight from the live registries rather than from a mapping file.
     *
     * <p>This is the real win over the extension: the two halves cannot drift, because
     * there is no file in between to go stale.</p>
     */
    private static void onDefineCustomBlocks(@NotNull GeyserDefineCustomBlocksEvent event) {
        if (!bedrockOutputEnabled()) {
            return;
        }

        var snapshot = BedrockRegistrationSnapshot.await(Duration.ofSeconds(30));
        if (snapshot.isEmpty()) {
            Plugins.logger().warning("Kalo's Bedrock compiler did not finish before Geyser froze its"
                    + " custom-block palette; no incomplete blocks were registered");
            return;
        }

        int registered = 0;
        int virtual = 0;
        for (BedrockBlockRegistration block : snapshot.get().blocks()) {
            try {
                if (block.javaIdentifier() == null) {
                    // A virtual block is an ItemDisplay entity, not a block state, so there
                    // is no Java state for Geyser to override. Expected, not a failure —
                    // warning per block turned a virtual-heavy pack's startup into a wall
                    // of noise about nothing being wrong.
                    virtual++;
                    continue;
                }
                CustomBlockData data = build(block);

                event.register(data);
                event.registerOverride(block.javaIdentifier(), data.defaultBlockState());
                registered++;
            } catch (Exception e) {
                // One block failing must not cost the rest their registration.
                Plugins.logger().log(Level.WARNING,
                        "Could not register " + block.javaKey() + " with Geyser", e);
            }
        }

        Plugins.logger().info("Registered and mapped " + registered + " block(s) with Geyser natively"
                + (virtual > 0 ? "; " + virtual + " virtual block(s) render as entities instead" : ""));
    }

    /**
     * Registers the client-entity identifiers placed models are spawned as. Geyser asks
     * once, at startup; the pack supplies geometry/animation/entity JSON.
     */
    private static void onDefineEntities(@NotNull GeyserDefineEntitiesEvent event) {
        if (!bedrockOutputEnabled()) {
            return;
        }
        var snapshot = BedrockRegistrationSnapshot.await(java.time.Duration.ofSeconds(30));
        if (snapshot.isEmpty()) {
            Plugins.logger().warning("Kalo's Bedrock compiler did not finish before Geyser froze"
                    + " its custom-entity registry; placed models will not render on Bedrock");
            return;
        }
        int registered = registerPlacedEntities(event, snapshot.get().placedModels());
        int animated = registerAnimatedBlockEntities(event, snapshot.get().animatedBlocks());
        int mobs = registerMobs(event, snapshot.get().mobs());
        int rigs = registerRigs(event, snapshot.get().rigs());
        if (registered > 0) {
            Plugins.logger().info("Registered " + registered
                    + " placed-model client entity slot(s) with Geyser");
        }
        if (animated > 0) {
            Plugins.logger().info("Registered " + animated
                    + " animated-block client entity slot(s) with Geyser");
        }
        if (mobs > 0) {
            Plugins.logger().info("Registered " + mobs
                    + " custom-mob client entity slot(s) with Geyser");
        }
        if (rigs > 0) {
            Plugins.logger().info("Registered " + rigs
                    + " vehicle/emote client entity slot(s) with Geyser");
        }
    }

    /** Package-visible pure registration step, exercised without a live Geyser. */
    static int registerPlacedEntities(@NotNull GeyserDefineEntitiesEvent event,
                                      @NotNull java.util.List<BedrockPlacedModelRegistration> placed) {
        return registerPlacedEntities(event, placed,
                identifier -> org.geysermc.geyser.api.entity.custom.CustomEntityDefinition
                        .of(Identifier.of(identifier)));
    }

    static int registerPlacedEntities(@NotNull GeyserDefineEntitiesEvent event,
                                      @NotNull java.util.List<BedrockPlacedModelRegistration> placed,
                                      @NotNull java.util.function.Function<String,
                                              org.geysermc.geyser.api.entity.custom.CustomEntityDefinition> factory) {
        return registerIdentifiers(event,
                placed.stream().map(BedrockPlacedModelRegistration::entityIdentifier).toList(),
                factory);
    }

    /** Package-visible pure registration step for animated-block client entities. */
    static int registerAnimatedBlockEntities(@NotNull GeyserDefineEntitiesEvent event,
                                             @NotNull java.util.List<BedrockAnimatedBlockRegistration> blocks) {
        return registerAnimatedBlockEntities(event, blocks,
                identifier -> org.geysermc.geyser.api.entity.custom.CustomEntityDefinition
                        .of(Identifier.of(identifier)));
    }

    static int registerAnimatedBlockEntities(@NotNull GeyserDefineEntitiesEvent event,
                                             @NotNull java.util.List<BedrockAnimatedBlockRegistration> blocks,
                                             @NotNull java.util.function.Function<String,
                                                     org.geysermc.geyser.api.entity.custom.CustomEntityDefinition> factory) {
        return registerIdentifiers(event,
                blocks.stream().map(BedrockAnimatedBlockRegistration::entityIdentifier).toList(),
                factory);
    }

    /** Package-visible pure registration step for custom-mob client entities. */
    static int registerMobs(@NotNull GeyserDefineEntitiesEvent event,
                            @NotNull java.util.List<BedrockMobRegistration> mobs) {
        return registerMobs(event, mobs,
                identifier -> org.geysermc.geyser.api.entity.custom.CustomEntityDefinition
                        .of(Identifier.of(identifier)));
    }

    static int registerMobs(@NotNull GeyserDefineEntitiesEvent event,
                            @NotNull java.util.List<BedrockMobRegistration> mobs,
                            @NotNull java.util.function.Function<String,
                                    org.geysermc.geyser.api.entity.custom.CustomEntityDefinition> factory) {
        return registerIdentifiers(event,
                mobs.stream().map(BedrockMobRegistration::entityIdentifier).toList(),
                factory);
    }

    static int registerRigs(@NotNull GeyserDefineEntitiesEvent event,
                            @NotNull java.util.List<BedrockRigRegistration> rigs) {
        return registerRigs(event, rigs,
                identifier -> org.geysermc.geyser.api.entity.custom.CustomEntityDefinition
                        .of(Identifier.of(identifier)));
    }

    static int registerRigs(@NotNull GeyserDefineEntitiesEvent event,
                            @NotNull java.util.List<BedrockRigRegistration> rigs,
                            @NotNull java.util.function.Function<String,
                                    org.geysermc.geyser.api.entity.custom.CustomEntityDefinition> factory) {
        return registerIdentifiers(event,
                rigs.stream().map(BedrockRigRegistration::entityIdentifier).toList(), factory);
    }

    private static int registerIdentifiers(
            @NotNull GeyserDefineEntitiesEvent event,
            @NotNull java.util.List<String> identifiers,
            @NotNull java.util.function.Function<String,
                    org.geysermc.geyser.api.entity.custom.CustomEntityDefinition> factory) {
        int registered = 0;
        for (String identifier : identifiers) {
            try {
                event.register(factory.apply(identifier));
                registered++;
            } catch (Exception | LinkageError error) {
                LOGGER.warning("Could not register Bedrock client entity "
                        + identifier + ": " + error.getMessage());
            }
        }
        return registered;
    }

    /**
     * Register menu custom heads before Geyser generates its Bedrock skull pack.
     * Files are scanned on every Geyser startup/reload, so changing DeluxeMenus
     * never requires hand-editing a second Bedrock-only mapping file.
     */
    private static void onDefineCustomSkulls(@NotNull GeyserDefineCustomSkullsEvent event) {
        if (!Plugins.plugin().getConfig().getBoolean("bedrock.auto-register-gui-heads", true)) {
            return;
        }
        Path pluginsDirectory = Plugins.plugin().getDataFolder().toPath().getParent();
        if (pluginsDirectory == null) return;
        Path menus = pluginsDirectory.resolve("DeluxeMenus").resolve("gui_menus");
        try {
            var scan = GuiCustomSkullScanner.discover(menus);
            int registered = 0;
            for (String skinHash : scan.skinHashes()) {
                try {
                    event.register(skinHash, GeyserDefineCustomSkullsEvent.SkullTextureType.SKIN_HASH);
                    registered++;
                } catch (Exception ex) {
                    Plugins.logger().log(Level.WARNING,
                            "Could not register Java GUI skull " + skinHash + " with Geyser", ex);
                }
            }
            Plugins.logger().info("Auto-registered " + registered + " DeluxeMenus custom skull(s) with Geyser"
                    + " from " + scan.filesScanned() + " Java menu file(s)"
                    + (scan.invalidProfiles() > 0
                        ? "; rejected " + scan.invalidProfiles() + " invalid profile(s)" : ""));
        } catch (Exception ex) {
            // A misconfigured menu must never disable Geyser or prevent login.
            Plugins.logger().log(Level.WARNING, "Could not scan DeluxeMenus GUI textures", ex);
        }
    }

    /**
     * Registers exactly the item definitions the compiler emitted.
     *
     * <p>This used to walk the live registries and re-derive every field — base material,
     * icon, components — duplicating the decision the compiler had already made for the
     * mapping file. Two copies of one decision drift, and one of them had already gone
     * missing entirely: block items were never registered at all here, so a Bedrock player
     * saw a note block in the hotbar for every custom block.</p>
     */
    private static void onDefineCustomItems(@NotNull GeyserDefineCustomItemsEvent event) {
        if (!bedrockOutputEnabled()) {
            return;
        }
        var snapshot = BedrockRegistrationSnapshot.await(Duration.ofSeconds(30));
        if (snapshot.isEmpty()) {
            Plugins.logger().warning("Kalo's Bedrock compiler did not finish before Geyser froze its"
                    + " custom-item registry; no items were registered");
            return;
        }

        int registered = 0;
        for (BedrockItemRegistration item : snapshot.get().items()) {
            try {
                if (item.legacyCustomModelData() != null) {
                    var options = org.geysermc.geyser.api.item.custom.CustomItemOptions.builder()
                            .customModelData(item.legacyCustomModelData()).build();
                    var builder = org.geysermc.geyser.api.item.custom.CustomItemData.builder()
                            .name(item.bedrockIdentifier().replace(':', '_'))
                            .icon(item.icon())
                            .customItemOptions(options);
                    if (item.displayName() != null) builder.displayName(item.displayName());
                    if (event.register(item.javaItem(), builder.build())) registered++;
                    continue;
                }
                CustomItemDefinition.Builder custom = CustomItemDefinition.builder(
                                Identifier.of(item.bedrockIdentifier()), Identifier.of(item.model()))
                        .bedrockOptions(CustomItemBedrockOptions.builder().icon(item.icon()))
                        .component(JavaItemDataComponents.MAX_STACK_SIZE, item.maxStackSize());

                if (item.displayName() != null) {
                    custom.displayName(item.displayName());
                }
                if (item.maxDamage() != null) {
                    custom.component(JavaItemDataComponents.MAX_DAMAGE, item.maxDamage());
                }
                if (item.enchantmentGlint()) {
                    custom.component(JavaItemDataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                }
                // These are exactly the predicate objects that the standalone JSON
                // exporter received from the compiler. No second interpretation.
                for (var predicate : item.predicates()) {
                    custom.predicate(asGeyserPredicate(predicate));
                }
                if (item.predicates().size() > 1) {
                    custom.predicateStrategy(org.geysermc.geyser.api.predicate.PredicateStrategy.AND);
                }

                event.register(Identifier.of(item.javaItem()), custom.build());
                registered++;
            } catch (Exception e) {
                Plugins.logger().log(Level.WARNING,
                        "Could not register " + item.bedrockIdentifier() + " with Geyser", e);
            }
        }

        Plugins.logger().info("Registered " + registered + " item(s) with Geyser natively");
    }

    private static org.geysermc.geyser.api.predicate.MinecraftPredicate<
            ? super org.geysermc.geyser.api.predicate.context.item.ItemPredicateContext>
            asGeyserPredicate(io.kalo.converter.ExternalModelPredicate predicate) {
        var result = switch (predicate.kind()) {
            case CONDITION -> switch (predicate.property()) {
                case "broken" -> org.geysermc.geyser.api.predicate.item.ItemConditionPredicate.BROKEN;
                case "damaged" -> org.geysermc.geyser.api.predicate.item.ItemConditionPredicate.DAMAGED;
                case "fishing_rod_cast" ->
                        org.geysermc.geyser.api.predicate.item.ItemConditionPredicate.FISHING_ROD_CAST;
                case "custom_model_data" ->
                        org.geysermc.geyser.api.predicate.item.ItemConditionPredicate.customModelData(predicate.index());
                case "has_component" ->
                        org.geysermc.geyser.api.predicate.item.ItemConditionPredicate.hasComponent(
                                Identifier.of(predicate.value()));
                default -> throw new IllegalArgumentException("Unsupported condition " + predicate.property());
            };
            case RANGE -> switch (predicate.property()) {
                case "custom_model_data" ->
                        org.geysermc.geyser.api.predicate.item.ItemRangeDispatchPredicate.customModelData(
                                predicate.index(), predicate.threshold().floatValue());
                case "count" -> predicate.normalize()
                        ? org.geysermc.geyser.api.predicate.item.ItemRangeDispatchPredicate.normalizedCount(
                                predicate.threshold())
                        : org.geysermc.geyser.api.predicate.item.ItemRangeDispatchPredicate.count(
                                (int) Math.ceil(predicate.threshold()));
                case "damage" -> predicate.normalize()
                        ? org.geysermc.geyser.api.predicate.item.ItemRangeDispatchPredicate.normalizedDamage(
                                predicate.threshold())
                        : org.geysermc.geyser.api.predicate.item.ItemRangeDispatchPredicate.damage(
                                (int) Math.ceil(predicate.threshold()));
                case "bundle_fullness" ->
                        org.geysermc.geyser.api.predicate.item.ItemRangeDispatchPredicate.bundleFullness(
                                predicate.threshold());
                default -> throw new IllegalArgumentException("Unsupported range " + predicate.property());
            };
            case MATCH -> switch (predicate.property()) {
                case "custom_model_data" ->
                        org.geysermc.geyser.api.predicate.item.ItemMatchPredicate.customModelData(
                                predicate.index(), predicate.value());
                case "context_dimension" ->
                        org.geysermc.geyser.api.predicate.MatchPredicate.dimension(
                                Identifier.of(predicate.value()));
                case "charge_type" ->
                        org.geysermc.geyser.api.predicate.item.ItemMatchPredicate.chargeType(
                                org.geysermc.geyser.api.predicate.context.item.ChargedProjectile.ChargeType.valueOf(
                                        predicate.value().toUpperCase(java.util.Locale.ROOT)));
                case "trim_material" ->
                        org.geysermc.geyser.api.predicate.item.ItemMatchPredicate.trimMaterial(
                                Identifier.of(predicate.value()));
                default -> throw new IllegalArgumentException("Unsupported match " + predicate.property());
            };
        };
        // Every branch accepts ItemPredicateContext; dimension is a predicate
        // over its superclass MinecraftPredicateContext.
        @SuppressWarnings("unchecked")
        org.geysermc.geyser.api.predicate.MinecraftPredicate<
                ? super org.geysermc.geyser.api.predicate.context.item.ItemPredicateContext> typed =
                (org.geysermc.geyser.api.predicate.MinecraftPredicate<
                        ? super org.geysermc.geyser.api.predicate.context.item.ItemPredicateContext>)
                        (org.geysermc.geyser.api.predicate.MinecraftPredicate<?>) result;
        return predicate.expected() ? typed : typed.negate();
    }

    private static @NotNull CustomBlockData build(@NotNull BedrockBlockRegistration block) {
        CustomBlockComponents.Builder components = CustomBlockComponents.builder()
                .displayName(block.displayName() != null ? block.displayName() : block.bedrockName())
                // Java's carrier is a full note block. Keeping these boxes full-sized
                // preserves server collision and targeting even for decorative geometry.
                .selectionBox(BoxComponent.fullBox())
                .collisionBoxes(BoxComponent.fullBox())
                .geometry(GeometryComponent.builder().identifier(block.geometry()).build())
                .destructibleByMining(block.hardness());

        block.materialInstances().forEach((material, texture) ->
                addMaterial(components, material, texture));

        // CustomBlockData, not NonVanillaCustomBlockData: Geyser namespaces this itself as
        // geyser_custom, which is the only namespace a custom_mappings file can produce.
        // Registering under a Kalo-chosen namespace here would make the identifier depend
        // on which path registered the block, while the pack's blocks.json is keyed by one.
        // NonVanilla is for blocks with no Java counterpart anyway; Kalo overrides a real
        // carrier state, so it never needed it.
        return CustomBlockData.builder()
                .name(block.bedrockName())
                // Obtained through Kalo's own items, so a second copy in the Bedrock
                // creative menu would only be confusing.
                .includedInCreativeInventory(false)
                .components(components.build())
                .build();
    }

    private static void addMaterial(@NotNull CustomBlockComponents.Builder components,
                                    @NotNull String name, @NotNull String texture) {
        components.materialInstance(name, MaterialInstance.builder()
                .texture(texture)
                .renderMethod("opaque")
                .faceDimming(true)
                .ambientOcclusion(true)
                .build());
    }

    private static void onDefineResourcePacks(@NotNull GeyserDefineResourcePacksEvent event) {
        if (!bedrockOutputEnabled()) {
            return;
        }
        if (perPlayerPacks()) {
            // The per-session copy is registered on SessionLoadResourcePacksEvent
            // instead. Registering the global archive here as well would hand a client
            // two copies of the same pack UUID; that event is the one with the XUID.
            Plugins.logger().info("Kalo Bedrock packs are staged per session");
            return;
        }
        ResourcePack pack = generatedPack();
        if (pack == null) {
            // A later pack is attached when a Bedrock player connects.
            return;
        }
        if (containsPack(event.resourcePacks(), pack)) {
            return;
        }
        try {
            event.register(pack);
            Plugins.logger().info("Registered generated.mcpack with Geyser (per-player staging disabled)");
        } catch (Exception e) {
            Plugins.logger().log(Level.WARNING, "Could not register generated.mcpack with Geyser", e);
        }
    }

    /** Whether each Bedrock session receives its own staged copy of generated.mcpack. */
    static boolean perPlayerPacks() {
        try {
            return Plugins.plugin().getConfig().getBoolean("bedrock-packs.per-player", true);
        } catch (Throwable unavailable) {
            return true;
        }
    }

    private static void onSessionLoadResourcePacks(@NotNull SessionLoadResourcePacksEvent event) {
        if (!bedrockOutputEnabled()) {
            return;
        }
        Path source = generatedPackPath();
        if (source == null) {
            Plugins.logger().info("Kalo's generated.mcpack is not ready yet; it will be attached when a"
                    + " Bedrock player connects");
            return;
        }

        String xuid = event.connection().xuid();
        if (perPlayerPacks() && xuid != null && !xuid.isBlank()) {
            try {
                Path dataFolder = Constants.dataFolder().toPath();
                java.util.Map<String, byte[]> overlays = new java.util.TreeMap<>(
                        io.kalo.platform.bedrock.BedrockSkinOverlays.forXuid(xuid));
                java.util.UUID playerId = event.connection().javaUuid();
                if (playerId != null) {
                    overlays.putAll(io.kalo.platform.bedrock.EmoteSkinOverlays
                            .forPlayer(dataFolder, playerId));
                }
                io.kalo.platform.bedrock.SessionContentPolicy policy =
                        io.kalo.platform.bedrock.SessionContentPolicy.fromPluginConfig();
                java.util.function.Predicate<String> permissions = node -> false;
                if (playerId != null) {
                    org.bukkit.entity.Player player =
                            org.bukkit.Bukkit.getPlayer(playerId);
                    if (player != null) {
                        permissions = player::hasPermission;
                    }
                }
                Path staged = io.kalo.platform.bedrock.BedrockPackStager.stage(
                        dataFolder, source, xuid, overlays, policy, permissions);
                event.register(ResourcePack.create(PackCodec.path(staged)),
                        new org.geysermc.geyser.api.pack.option.ResourcePackOption<?>[0]);
                io.kalo.platform.bedrock.BedrockPackStager.markServed(xuid,
                        io.kalo.platform.bedrock.BedrockPackStager.sessionDigest(
                                source, overlays,
                                policy.fingerprint(xuid, permissions)));
                return;
            } catch (Exception stagedError) {
                Plugins.logger().log(Level.WARNING,
                        "Could not stage the per-session Bedrock pack for " + xuid
                                + "; falling back to the global pack", stagedError);
            }
        }

        ResourcePack pack = generatedPack();
        if (pack == null || containsPack(event.resourcePacks(), pack)) {
            return;
        }
        try {
            event.register(pack,
                    new org.geysermc.geyser.api.pack.option.ResourcePackOption<?>[0]);
        } catch (Exception e) {
            Plugins.logger().log(Level.WARNING,
                    "Could not attach generated.mcpack to a Bedrock connection", e);
        }
    }

    private static @Nullable Path generatedPackPath() {
        Path path = new java.io.File(Constants.dataFolder(), "generated.mcpack").toPath();
        return Files.isRegularFile(path) ? path : null;
    }

    private static boolean containsPack(@NotNull List<ResourcePack> packs,
                                        @NotNull ResourcePack candidate) {
        return packs.stream().anyMatch(pack -> pack.uuid().equals(candidate.uuid()));
    }

    /**
     * Reads the manifest through Geyser's supported path codec, once per generated pack.
     *
     * <p>This is called on every Bedrock connection as well as at startup. Re-reading and
     * re-parsing the archive per joining player is real work at a thousand items, so the
     * result is held until the file changes — identity, not age, decides: a regenerated
     * pack has a new size or timestamp and invalidates the cache on its own.</p>
     */
    private static @Nullable ResourcePack generatedPack() {
        Path path = new java.io.File(Constants.dataFolder(), "generated.mcpack").toPath();

        long size;
        long modified;
        try {
            if (!Files.isRegularFile(path)) {
                CACHED_PACK.set(null);
                return null;
            }
            size = Files.size(path);
            modified = Files.getLastModifiedTime(path).toMillis();
        } catch (java.io.IOException e) {
            Plugins.logger().log(Level.WARNING, "Could not stat " + path, e);
            return null;
        }

        CachedPack cached = CACHED_PACK.get();
        if (cached != null && cached.size() == size && cached.modified() == modified) {
            return cached.pack();
        }

        try {
            ResourcePack pack = ResourcePack.create(PackCodec.path(path));
            CACHED_PACK.set(new CachedPack(pack, size, modified));
            return pack;
        } catch (Exception e) {
            Plugins.logger().log(Level.WARNING, "Could not open " + path + " as a Bedrock resource pack", e);
            return null;
        }
    }

    private record CachedPack(@NotNull ResourcePack pack, long size, long modified) {
    }

    /** One reader for the {@code bedrock} setting, shared with the pack generator. */
    private static boolean bedrockOutputEnabled() {
        return io.kalo.manager.ResourcePackManagerImpl.bedrockWanted();
    }

}

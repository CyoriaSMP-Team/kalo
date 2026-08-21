package io.kalo.platform.bedrock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.content.feature.Feature;
import io.kalo.content.feature.FeatureEventBus;
import io.kalo.content.feature.FeatureEventBusImpl;
import io.kalo.content.item.ImmutableItemStack;
import io.kalo.content.item.Item;
import io.kalo.content.item.definition.BedrockOptions;
import io.kalo.content.item.definition.DisplayProperties;
import io.kalo.content.item.definition.ItemBehaviour;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.pack.PackFormats;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockPackCompilerTest {

    private static ResourcePack javaPackWith(String texturePath) {
        ResourcePack java = new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "java"));
        java.file("assets/minecraft/blockstates/note_block.json", Writable.string("{}"));
        java.file(texturePath, Writable.bytes(new byte[]{1, 2, 3}));
        return java;
    }

    private static ItemDefinition sprite(String name) {
        Key key = Key.key("testpack", name);
        return ItemDefinition.builder(key)
                .display(new DisplayProperties(Component.text("Ruby Sword"), List.of(), false))
                .model(new ModelDefinition.Sprite(Key.key("testpack", "item/" + name)))
                .build();
    }

    private static io.kalo.content.block.definition.BlockDefinition cubeAll(String name) {
        Key key = Key.key("testpack", name);
        return io.kalo.content.block.definition.BlockDefinition.builder(key)
                .model(new io.kalo.content.block.definition.BlockModelDefinition.CubeAll(
                        Key.key("testpack", "block/" + name)))
                .build();
    }

    private static BedrockPackCompiler.Result compile(ResourcePack java, List<Item> items) {
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));
        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.add(items);
        return compiler.finish();
    }

    @Test
    void bedrockPackDoesNotCarryTheJavaAssetTree() throws IOException {
        // A Bedrock pack has its own layout and ignores assets/ entirely; shipping it
        // was tens of kilobytes of dead weight in every download.
        ResourcePack java = javaPackWith("assets/testpack/textures/item/ruby_sword.png");

        BedrockPackCompiler.Result result = compile(java, List.of(new StubItem(sprite("ruby_sword"))));

        assertTrue(result.pack().files().keySet().stream().noneMatch(p -> p.startsWith("assets/")),
                "bedrock pack should contain no assets/ entries, got: " + result.pack().files().keySet());
        assertNotNull(result.pack().file("manifest.json"));
        assertNotNull(result.pack().file("textures/item_texture.json"));
    }

    @Test
    void separateAddsAccumulateIntoOneMapping() throws IOException {
        // Items and armor are separate content types compiled in separate passes. Writing
        // the mapping file per pass meant whichever ran last erased the other's items.
        ResourcePack java = javaPackWith("assets/testpack/textures/item/ruby_sword.png");
        java.file("assets/testpack/textures/item/ruby_helmet.png", Writable.bytes(new byte[]{4}));

        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));
        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.add(List.of(new StubItem(sprite("ruby_sword"))));
        compiler.add(List.of(new StubItem(sprite("ruby_helmet"))));

        JsonObject mappings = json(compiler.finish().mappings());
        JsonObject items = mappings.getAsJsonObject("items");

        // Both map onto PAPER by default, so they share one vanilla item entry.
        int total = items.entrySet().stream().mapToInt(e -> e.getValue().getAsJsonArray().size()).sum();
        assertEquals(2, total, "both passes' items should survive: " + items);
    }

    @Test
    void spriteTextureIsCopiedIntoBedrocksFlatItemAtlas() throws IOException {
        ResourcePack java = javaPackWith("assets/testpack/textures/item/ruby_sword.png");

        BedrockPackCompiler.Result result = compile(java, List.of(new StubItem(sprite("ruby_sword"))));

        // Bedrock resolves icons by atlas shorthand, not by path.
        assertNotNull(result.pack().file("textures/items/testpack_ruby_sword.png"));

        JsonObject atlas = json(result.pack().file("textures/item_texture.json"));
        assertTrue(atlas.getAsJsonObject("texture_data").has("testpack_ruby_sword"));
    }

    @Test
    void displayNameIsRenderedTextNotTheKey() throws IOException {
        // Geyser shows this verbatim; the key would appear literally in the inventory.
        ResourcePack java = javaPackWith("assets/testpack/textures/item/ruby_sword.png");

        JsonObject mappings = json(compile(java, List.of(new StubItem(sprite("ruby_sword")))).mappings());
        JsonObject entry = mappings.getAsJsonObject("items")
                .getAsJsonArray("minecraft:paper").get(0).getAsJsonObject();

        assertEquals("Ruby Sword", entry.get("display_name").getAsString());
        assertEquals("testpack:ruby_sword", entry.get("bedrock_identifier").getAsString());
    }

    @Test
    void iconUsesGeysersV2BedrockOptionsSection() throws IOException {
        ResourcePack java = javaPackWith("assets/testpack/textures/item/ruby_sword.png");

        JsonObject entry = json(compile(java, List.of(new StubItem(sprite("ruby_sword")))).mappings())
                .getAsJsonObject("items")
                .getAsJsonArray("minecraft:paper").get(0).getAsJsonObject();

        // A top-level icon is ignored by Geyser's v2 reader. Its fallback would be
        // `testpack.ruby_sword`, which does not name the generated atlas entry.
        assertFalse(entry.has("icon"));
        assertEquals("testpack_ruby_sword",
                entry.getAsJsonObject("bedrock_options").get("icon").getAsString());
    }

    @Test
    void durabilityUsesTheJavaMaxDamageComponentGeyserMatches() throws IOException {
        ResourcePack java = javaPackWith("assets/testpack/textures/item/hammer.png");
        ItemDefinition durable = ItemDefinition.builder(Key.key("testpack", "hammer"))
                .model(new ModelDefinition.Sprite(Key.key("testpack", "item/hammer")))
                .behaviour(new ItemBehaviour(1, 250, false))
                .build();

        JsonObject components = json(compile(java, List.of(new StubItem(durable))).mappings())
                .getAsJsonObject("items")
                .getAsJsonArray("minecraft:paper").get(0).getAsJsonObject()
                .getAsJsonObject("components");

        assertEquals(250, components.get("minecraft:max_damage").getAsInt());
        assertFalse(components.has("minecraft:durability"),
                "that is a Bedrock output component, not a Java component Geyser can match");
    }

    @Test
    void enchantmentGlintUsesTheJavaComponentGeyserMatches() throws IOException {
        ResourcePack java = javaPackWith("assets/testpack/textures/item/glinting.png");
        ItemDefinition glinting = ItemDefinition.builder(Key.key("testpack", "glinting"))
                .display(new DisplayProperties(null, List.of(), true))
                .model(new ModelDefinition.Sprite(Key.key("testpack", "item/glinting")))
                .build();

        JsonObject components = json(compile(java, List.of(new StubItem(glinting))).mappings())
                .getAsJsonObject("items")
                .getAsJsonArray("minecraft:paper").get(0).getAsJsonObject()
                .getAsJsonObject("components");

        assertTrue(components.get("minecraft:enchantment_glint_override").getAsBoolean());
    }

    @Test
    void vanillaModelledItemsNeedNoBedrockEntry() throws IOException {
        ResourcePack java = javaPackWith("assets/testpack/textures/item/x.png");
        ItemDefinition vanilla = ItemDefinition.builder(Key.key("testpack", "plain_apple"))
                .model(new ModelDefinition.Vanilla(Key.key("minecraft", "apple")))
                .build();

        BedrockPackCompiler.Result result = compile(java, List.of(new StubItem(vanilla)));

        assertEquals(0, result.itemCount(), "Bedrock already draws vanilla items correctly");
        assertEquals(0, result.skippedCount(), "and this is not a gap, so it is not a skip");
    }

    @Test
    void customModelsAreReportedAsSkippedRatherThanSilentlyDropped() throws IOException {
        // A pack author whose item appears on Java but not Bedrock deserves to know why.
        ResourcePack java = javaPackWith("assets/testpack/textures/item/x.png");
        ItemDefinition custom = ItemDefinition.builder(Key.key("testpack", "fancy"))
                .model(new ModelDefinition.Custom(Key.key("testpack", "item/fancy"), Map.of()))
                .build();

        BedrockPackCompiler.Result result = compile(java, List.of(new StubItem(custom)));

        assertEquals(0, result.itemCount());
        assertEquals(1, result.skippedCount());
    }

    @Test
    void bedrockCanBeDisabledPerItem() throws IOException {
        ResourcePack java = javaPackWith("assets/testpack/textures/item/ruby_sword.png");
        ItemDefinition off = ItemDefinition.builder(Key.key("testpack", "java_only"))
                .model(new ModelDefinition.Sprite(Key.key("testpack", "item/ruby_sword")))
                .bedrock(new BedrockOptions(false, null))
                .build();

        assertEquals(0, compile(java, List.of(new StubItem(off))).itemCount());
    }

    @Test
    void manifestUsesArrayVersionsAndAStableUuid() throws IOException {
        ResourcePack java = javaPackWith("assets/testpack/textures/item/ruby_sword.png");

        JsonObject first = json(compile(java, List.of(new StubItem(sprite("ruby_sword")))).pack().file("manifest.json"));
        JsonObject second = json(compile(java, List.of(new StubItem(sprite("ruby_sword")))).pack().file("manifest.json"));

        JsonObject header = first.getAsJsonObject("header");
        // Bedrock rejects a string version here.
        assertTrue(header.get("version").isJsonArray());
        assertEquals(3, header.getAsJsonArray("version").size());
        assertTrue(header.has("min_engine_version"));

        // A changing uuid makes Bedrock treat it as a brand new pack every regeneration.
        assertEquals(header.get("uuid").getAsString(),
                second.getAsJsonObject("header").get("uuid").getAsString());
    }

    @Test
    void cubeBlocksGetABedrockAppearanceAndATerrainAtlasEntry() throws IOException {
        ResourcePack java = javaPackWith("assets/testpack/textures/block/ruby_block.png");
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));

        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.addBlocks(List.of(new StubBlock(cubeAll("ruby_block"))), key -> 7, Set.of());
        BedrockPackCompiler.Result result = compiler.finish();

        assertEquals(1, result.blockCount());

        // Bedrock has real custom blocks, declared in blocks.json at the pack root.
        JsonObject blocks = json(result.pack().file("blocks.json"));
        assertEquals("testpack_ruby_block",
                blocks.getAsJsonObject("testpack:ruby_block").get("textures").getAsString());

        // Block faces resolve through the terrain atlas, separate from the item atlas.
        JsonObject terrain = json(result.pack().file("textures/terrain_texture.json"));
        assertEquals("textures/blocks/testpack_ruby_block",
                terrain.getAsJsonObject("texture_data")
                        .getAsJsonObject("testpack_ruby_block").get("textures").getAsString());
        assertNotNull(result.pack().file("textures/blocks/testpack_ruby_block.png"));

        JsonObject materials = json(result.mappings()).getAsJsonArray("kalo:blocks")
                .get(0).getAsJsonObject().getAsJsonObject("material_instances");
        assertEquals(Map.of("*", "testpack_ruby_block"),
                materials.asMap().entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().getAsString())));
    }

    @Test
    void cubeAllFallbackExpandsToSixBedrockFacesAndIgnoresParticle() throws IOException {
        ResourcePack java = javaPackWith("assets/testpack/textures/block/all.png");
        java.file("assets/testpack/textures/block/top.png", Writable.bytes(new byte[]{2}));
        java.file("assets/testpack/textures/block/particle.png", Writable.bytes(new byte[]{3}));

        var definition = io.kalo.content.block.definition.BlockDefinition
                .builder(Key.key("testpack", "faced_cube"))
                .model(new io.kalo.content.block.definition.BlockModelDefinition.Cube(Map.of(
                        "all", Key.key("testpack", "block/all"),
                        "top", Key.key("testpack", "block/top"),
                        "particle", Key.key("testpack", "block/particle"))))
                .build();
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));
        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.addBlocks(List.of(new StubBlock(definition)), key -> 9, Set.of());

        BedrockPackCompiler.Result result = compiler.finish();
        JsonObject textures = json(result.pack().file("blocks.json"))
                .getAsJsonObject("testpack:faced_cube").getAsJsonObject("textures");

        assertEquals(Set.of("down", "up", "north", "south", "west", "east"),
                textures.keySet());
        assertEquals("testpack_faced_cube_up", textures.get("up").getAsString());
        assertEquals("testpack_faced_cube_down", textures.get("down").getAsString());

        JsonObject atlas = json(result.pack().file("textures/terrain_texture.json"))
                .getAsJsonObject("texture_data");
        assertEquals("textures/blocks/testpack_faced_cube_up",
                atlas.getAsJsonObject("testpack_faced_cube_up").get("textures").getAsString());
        assertNotNull(result.pack().file("textures/blocks/testpack_faced_cube_north.png"),
                "the all fallback must be copied for every unspecified rendered face");
        assertFalse(atlas.has("testpack_faced_cube_particle"),
                "Java's particle texture is an inventory-particle fallback, not a Bedrock cube face");

        JsonObject materials = json(result.mappings()).getAsJsonArray("kalo:blocks")
                .get(0).getAsJsonObject().getAsJsonObject("material_instances");
        assertEquals(Set.of("down", "up", "north", "south", "west", "east"),
                materials.keySet());
        assertEquals("testpack_faced_cube_up", materials.get("up").getAsString());
    }

    @Test
    void blockRecordCarriesTheJavaStateForTheGeyserExtension() throws IOException {
        // Geyser registers custom blocks at runtime, not through the mapping file, so the
        // Java carrier state has to reach the extension somehow.
        ResourcePack java = javaPackWith("assets/testpack/textures/block/ruby_block.png");
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));

        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.addBlocks(List.of(new StubBlock(cubeAll("ruby_block"))), key -> 7, Set.of());

        JsonObject mappings = json(compiler.finish().mappings());
        JsonObject record = mappings.getAsJsonArray("kalo:blocks").get(0).getAsJsonObject();

        assertEquals("testpack:ruby_block", record.get("java_key").getAsString());
        assertEquals("testpack:ruby_block", record.get("bedrock_identifier").getAsString());
        assertEquals("native", record.get("java_mode").getAsString());
        assertEquals(7, record.get("java_carrier_state").getAsInt());
        assertEquals("minecraft:note_block[instrument=harp,note=3,powered=true]",
                record.get("java_identifier").getAsString());
        assertEquals("minecraft:geometry.full_block", record.get("geometry").getAsString());
        assertEquals(1.5f, record.get("hardness").getAsFloat());
    }

    @Test
    void virtualBlockRecordHasNoFiniteJavaCarrierState() throws IOException {
        ResourcePack java = javaPackWith("assets/testpack/textures/block/unlimited.png");
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));

        io.kalo.content.block.definition.BlockDefinition definition =
                io.kalo.content.block.definition.BlockDefinition.builder(Key.key("testpack", "unlimited"))
                        .model(new io.kalo.content.block.definition.BlockModelDefinition.CubeAll(
                                Key.key("testpack", "block/unlimited")))
                        .java(io.kalo.content.block.definition.JavaBlockOptions.virtual())
                        .build();
        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.addBlocks(List.of(new StubBlock(definition)), key -> 99, Set.of());

        JsonObject record = json(compiler.finish().mappings())
                .getAsJsonArray("kalo:blocks").get(0).getAsJsonObject();
        assertEquals("virtual", record.get("java_mode").getAsString());
        assertFalse(record.has("java_carrier_state"));
    }

    @Test
    void aCustomBlockModelIsConvertedToBedrockGeometry() throws IOException {
        ResourcePack java = javaPackWith("assets/testpack/textures/block/chair.png");
        java.file("assets/testpack/models/block/chair.json", Writable.string(
                "{\"elements\":[{\"from\":[4,0,4],\"to\":[12,10,12],\"faces\":{}}]}"));

        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));
        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.addBlocks(List.of(new StubBlock(
                io.kalo.content.block.definition.BlockDefinition.builder(Key.key("testpack", "chair"))
                        .model(new io.kalo.content.block.definition.BlockModelDefinition.Custom(
                                Key.key("testpack", "block/chair"),
                                Map.of("all", Key.key("testpack", "block/chair"))))
                        .build())), key -> 4, Set.of());
        BedrockPackCompiler.Result result = compiler.finish();

        assertEquals(1, result.blockCount(), "a custom model should no longer be skipped");
        assertNotNull(result.pack().file("models/blocks/testpack_chair.geo.json"));

        // The extension needs to know which geometry to apply to which block.
        JsonObject mappings = json(result.mappings());
        assertEquals("geometry.kalo.testpack_chair",
                mappings.getAsJsonObject("kalo:geometries").get("testpack:chair").getAsString());
        JsonObject record = mappings.getAsJsonArray("kalo:blocks").get(0).getAsJsonObject();
        assertEquals("testpack_chair_all",
                record.getAsJsonObject("material_instances").get("all").getAsString());
        assertEquals("testpack_chair_all", json(result.pack().file("blocks.json"))
                .getAsJsonObject("testpack:chair").get("textures").getAsString());
        assertNotNull(result.pack().file("textures/blocks/testpack_chair_all.png"));
    }

    @Test
    void aModelThatOnlyInheritsAParentIsStillSkipped() throws IOException {
        // Nothing to convert: the shape lives in the parent, which the pack cannot resolve.
        ResourcePack java = javaPackWith("assets/testpack/textures/block/plain.png");
        java.file("assets/testpack/models/block/plain.json", Writable.string(
                "{\"parent\":\"minecraft:block/cube_all\"}"));

        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));
        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.addBlocks(List.of(new StubBlock(
                io.kalo.content.block.definition.BlockDefinition.builder(Key.key("testpack", "plain"))
                        .model(new io.kalo.content.block.definition.BlockModelDefinition.Custom(
                                Key.key("testpack", "block/plain"), Map.of()))
                        .build())), key -> 5, Set.of());

        assertEquals(1, compiler.finish().skippedCount());
    }

    @Test
    void nativeRegistrationSnapshotContainsOnlyBlocksTheCompilerActuallyEmitted()
            throws IOException {
        ResourcePack java = javaPackWith("assets/testpack/textures/block/good.png");
        java.file("assets/testpack/models/block/parent_only.json", Writable.string(
                "{\"parent\":\"minecraft:block/cube_all\"}"));

        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));
        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.addBlocks(List.of(
                new StubBlock(cubeAll("good")),
                new StubBlock(io.kalo.content.block.definition.BlockDefinition
                        .builder(Key.key("testpack", "bad"))
                        .model(new io.kalo.content.block.definition.BlockModelDefinition.Custom(
                                Key.key("testpack", "block/parent_only"),
                                Map.of("all", Key.key("testpack", "block/good"))))
                        .build())), key -> key.value().equals("good") ? 1 : 2, Set.of());

        BedrockPackCompiler.Result result = compiler.finish();
        // Runtime publication is deliberately separate: the manager performs it only
        // after the .mcpack and mapping file have both been written successfully.
        BedrockRegistrationSnapshot.publishSuccess(result.generation(), result.registrations());

        List<BedrockBlockRegistration> registrations = BedrockRegistrationSnapshot
                .await(Duration.ZERO).orElseThrow();
        assertEquals(List.of("testpack:good"),
                registrations.stream().map(BedrockBlockRegistration::javaKey).toList());
    }

    @Test
    void aBlockJavaCouldNotPlaceIsSkippedOnBedrockToo() throws IOException {
        // Registering on one platform and not the other means a Bedrock player sees a
        // block the Java player beside them does not — the opposite of the point.
        ResourcePack java = javaPackWith("assets/testpack/textures/block/ruby_block.png");
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));

        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.addBlocks(List.of(new StubBlock(cubeAll("ruby_block"))), key -> 1,
                Set.of("testpack:ruby_block"));
        BedrockPackCompiler.Result result = compiler.finish();

        assertEquals(0, result.blockCount());
        assertEquals(1, result.skippedCount());
    }

    @Test
    void armorGetsAnAttachableSoItRendersOnTheBedrockPlayer() throws IOException {
        // Without this Bedrock draws the base material's armor — a "custom helmet" that
        // looks like plain netherite on everyone who plays from Bedrock.
        ResourcePack java = javaPackWith("assets/testpack/textures/entity/equipment/humanoid/ruby.png");
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));

        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.addArmor(List.of(new StubArmor("ruby_helmet", io.kalo.content.armor.ArmorSlot.HEAD)));
        BedrockPackCompiler.Result result = compiler.finish();

        assertEquals(1, result.armorCount());

        JsonObject attachable = json(result.pack().file("attachables/testpack_ruby_helmet.json"))
                .getAsJsonObject("minecraft:attachable").getAsJsonObject("description");

        assertEquals("testpack:ruby_helmet", attachable.get("identifier").getAsString());
        assertEquals("geometry.player_armor.helmet",
                attachable.getAsJsonObject("geometry").get("default").getAsString());
        // Hides the vanilla layer, or Bedrock draws both at once.
        assertTrue(attachable.getAsJsonObject("scripts").get("parent_setup").getAsString()
                .contains("helmet_layer_visible"), attachable.toString());

        // Java keeps armor sheets under entity/equipment; Bedrock wants a flat directory.
        assertNotNull(result.pack().file("textures/models/armor/testpack_ruby_1.png"));
    }

    @Test
    void leggingsUseBedrocksSecondArmorSheet() throws IOException {
        // The same split Java makes between its two layers, arrived at independently.
        ResourcePack java = javaPackWith(
                "assets/testpack/textures/entity/equipment/humanoid_leggings/ruby.png");
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));

        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.addArmor(List.of(new StubArmor("ruby_leggings", io.kalo.content.armor.ArmorSlot.LEGS)));
        BedrockPackCompiler.Result result = compiler.finish();

        assertNotNull(result.pack().file("textures/models/armor/testpack_ruby_2.png"));
        JsonObject description = json(result.pack().file("attachables/testpack_ruby_leggings.json"))
                .getAsJsonObject("minecraft:attachable").getAsJsonObject("description");
        assertEquals("textures/models/armor/testpack_ruby_2",
                description.getAsJsonObject("textures").get("default").getAsString());
    }

    @Test
    void armorThatOptedOutOfACustomLookGetsNoAttachable() throws IOException {
        // The base material's own armor is the correct appearance there.
        ResourcePack java = javaPackWith("assets/testpack/textures/item/x.png");
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));

        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.addArmor(List.of(new StubArmor("plain_helmet", io.kalo.content.armor.ArmorSlot.HEAD, null)));

        assertEquals(0, compiler.finish().armorCount());
    }

    private record StubArmor(io.kalo.content.armor.ArmorDefinition armorDefinition)
            implements io.kalo.content.armor.Armor {

        StubArmor(String name, io.kalo.content.armor.ArmorSlot slot) {
            this(name, slot, new io.kalo.content.armor.ArmorDefinition.EquipmentTexture(
                    Key.key("testpack", "ruby"), Key.key("testpack", "ruby")));
        }

        StubArmor(String name, io.kalo.content.armor.ArmorSlot slot,
                  io.kalo.content.armor.ArmorDefinition.EquipmentTexture equipment) {
            this(new io.kalo.content.armor.ArmorDefinition(
                    ItemDefinition.builder(Key.key("testpack", name)).build(), slot, equipment));
        }

        @Override
        public @NotNull ItemDefinition definition() {
            return armorDefinition.item();
        }

        @Override
        public @NotNull ImmutableItemStack itemStack() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSimilar(@NotNull org.bukkit.inventory.ItemStack itemStack) {
            return false;
        }

        @Override
        public @NotNull Collection<Feature> features() {
            return List.of();
        }

        @Override
        public @NotNull FeatureEventBus featureEventBus() {
            return new FeatureEventBusImpl();
        }

        @Override
        public @NotNull Key key() {
            return armorDefinition.key();
        }

        @Override
        public @NotNull String translationKey() {
            return armorDefinition.translationKey();
        }

        @Override
        public Item asItem() {
            return this;
        }
    }

    @Test
    void aPackWithNoBlocksEmitsNoBlockFiles() throws IOException {
        ResourcePack java = javaPackWith("assets/testpack/textures/item/ruby_sword.png");

        BedrockPackCompiler.Result result = compile(java, List.of(new StubItem(sprite("ruby_sword"))));

        assertFalse(result.pack().files().containsKey("blocks.json"));
        assertFalse(result.pack().files().containsKey("textures/terrain_texture.json"));
    }

    private record StubBlock(io.kalo.content.block.definition.BlockDefinition definition)
            implements io.kalo.content.block.Block {
        @Override
        public @NotNull io.kalo.content.block.definition.BlockDefinition definition() {
            return definition;
        }

        @Override
        public @NotNull ImmutableItemStack itemStack() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull Collection<Feature> features() {
            return List.of();
        }

        @Override
        public @NotNull FeatureEventBus featureEventBus() {
            return new FeatureEventBusImpl();
        }

        @Override
        public @NotNull Key key() {
            return definition.key();
        }

        @Override
        public @NotNull String translationKey() {
            return definition.translationKey();
        }
    }

    private static JsonObject json(Writable writable) throws IOException {
        assertNotNull(writable);
        return JsonParser.parseString(new String(writable.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private record StubItem(ItemDefinition definition) implements Item {
        @Override
        public @NotNull ItemDefinition definition() {
            return definition;
        }

        @Override
        public @NotNull ImmutableItemStack itemStack() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSimilar(@NotNull org.bukkit.inventory.ItemStack itemStack) {
            return false;
        }

        @Override
        public @NotNull Collection<Feature> features() {
            return List.of();
        }

        @Override
        public @NotNull FeatureEventBus featureEventBus() {
            return new FeatureEventBusImpl();
        }

        @Override
        public @NotNull Key key() {
            return definition.key();
        }

        @Override
        public @NotNull String translationKey() {
            return definition.translationKey();
        }

        @Override
        public Item asItem() {
            return this;
        }
    }
}

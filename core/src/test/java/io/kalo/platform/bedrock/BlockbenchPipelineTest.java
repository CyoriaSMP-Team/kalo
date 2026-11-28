package io.kalo.platform.bedrock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.content.block.Block;
import io.kalo.content.block.definition.BlockCarrier;
import io.kalo.content.block.definition.BlockDefinition;
import io.kalo.content.block.definition.BlockModelDefinition;
import io.kalo.content.feature.Feature;
import io.kalo.content.feature.FeatureEventBus;
import io.kalo.content.feature.FeatureEventBusImpl;
import io.kalo.content.item.ImmutableItemStack;
import io.kalo.content.item.Item;
import io.kalo.content.armor.ArmorDefinition;
import io.kalo.content.armor.ArmorSlot;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.content.mob.Mob;
import io.kalo.content.mob.MobDefinition;
import io.kalo.content.emote.EmoteDefinition;
import io.kalo.content.vehicle.VehicleDefinition;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.pack.PackFormats;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import io.kalo.platform.java.BlockStateAllocator;
import io.kalo.platform.java.JavaBlockCompiler;
import io.kalo.platform.java.JavaPackCompiler;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end: one {@code .bbmodel} source, one shared IR, both platform outputs.
 */
class BlockbenchPipelineTest {

    private static final Key ITEM_SOURCE = Key.key("testpack",
            "models/item/simple_cube.bbmodel");
    private static final Key BLOCK_SOURCE = Key.key("testpack",
            "models/block/rigged_arm.bbmodel");

    private static byte[] fixture(String name) throws IOException {
        try (var stream = BlockbenchPipelineTest.class.getResourceAsStream(
                "/blockbench/" + name)) {
            assertNotNull(stream);
            return stream.readAllBytes();
        }
    }

    private static ResourcePack sourcePack() throws IOException {
        ResourcePack pack = new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "java"));
        pack.file("assets/testpack/models/item/simple_cube.bbmodel",
                Writable.bytes(fixture("simple_cube.bbmodel")));
        pack.file("assets/testpack/models/block/rigged_arm.bbmodel",
                Writable.bytes(fixture("rigged_arm.bbmodel")));
        pack.file("assets/testpack/models/block/rigged_arm_interact.bbmodel",
                Writable.bytes(fixture("rigged_arm_interact.bbmodel")));
        pack.file("assets/testpack/models/block/rigged_arm_controller.bbmodel",
                Writable.bytes(fixture("rigged_arm_controller.bbmodel")));
        pack.file("assets/testpack/textures/item/ruby.png", Writable.bytes(
                solidPng(16, 16, 0xFFFF0000)));
        pack.file("assets/testpack/textures/item/steel.png", Writable.bytes(
                solidPng(16, 16, 0xFF0000FF)));
        return pack;
    }

    private static byte[] solidPng(int width, int height, int argb) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, argb);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    @Test
    void oneBlockbenchSourceBuildsBothAJavaModelAndBedrockGeometry() throws IOException {
        ResourcePack java = sourcePack();
        ItemDefinition definition = ItemDefinition.builder(Key.key("testpack", "ruby_hammer"))
                .model(new ModelDefinition.Blockbench(ITEM_SOURCE, Map.of()))
                .build();

        JavaPackCompiler.compileItems(java, List.of(new StubItem(definition)));

        JsonObject itemDefinition = json(java.file("assets/testpack/items/ruby_hammer.json"));
        assertEquals("testpack:item/ruby_hammer",
                itemDefinition.getAsJsonObject("model").get("model").getAsString());
        JsonObject javaModel = json(java.file("assets/testpack/models/item/ruby_hammer.json"));
        assertEquals(1, javaModel.getAsJsonArray("elements").size());
        assertEquals("#t0", javaModel.getAsJsonArray("elements").get(0).getAsJsonObject()
                .getAsJsonObject("faces").getAsJsonObject("north")
                .get("texture").getAsString());

        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));
        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.add(List.of(new StubItem(definition)));
        BedrockPackCompiler.Result result = compiler.finish();

        assertEquals(1, result.itemCount(), result.skippedCount() + " skipped");
        assertEquals(0, result.skippedCount());
        JsonObject geometry = json(result.pack().file(
                "models/entity/testpack_ruby_hammer.geo.json"));
        assertEquals(golden("simple_cube.geo.json"),
                string(result.pack().file("models/entity/testpack_ruby_hammer.geo.json")),
                "Bedrock geometry is a golden, byte-stable artifact");
        JsonObject bone = geometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones").get(0).getAsJsonObject();
        assertEquals("q.item_slot_to_bone_name(context.item_slot)",
                bone.get("binding").getAsString());

        JsonObject attachable = json(result.pack().file(
                "attachables/testpack_ruby_hammer.json"))
                .getAsJsonObject("minecraft:attachable").getAsJsonObject("description");
        assertEquals("geometry.kalo.testpack_ruby_hammer",
                attachable.getAsJsonObject("geometry").get("default").getAsString());
        assertEquals("textures/items/testpack_ruby_hammer_t0",
                attachable.getAsJsonObject("textures").get("default").getAsString());
        assertNotNull(result.pack().file("textures/items/testpack_ruby_hammer.png"),
                "the inventory icon is a rendered preview");
        assertNotNull(result.pack().file("textures/items/testpack_ruby_hammer_t0.png"),
                "the model's material texture stays separate from the icon");

        JsonObject mapping = json(result.itemMappings()).getAsJsonObject("items")
                .getAsJsonArray("minecraft:paper").get(0).getAsJsonObject();
        assertEquals("testpack:ruby_hammer", mapping.get("bedrock_identifier").getAsString());

        // The Bedrock pack must not leak the Blockbench project file.
        assertTrue(result.pack().files().keySet().stream()
                .noneMatch(path -> path.endsWith(".bbmodel")));
    }

    @Test
    void theBedrockPackManifestMapsGeneratedAssetsToTheirContent() throws IOException {
        ResourcePack java = sourcePack();
        ItemDefinition definition = ItemDefinition.builder(Key.key("testpack", "ruby_hammer"))
                .model(new ModelDefinition.Blockbench(ITEM_SOURCE, Map.of()))
                .build();
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));
        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);

        compiler.add(List.of(new StubItem(definition)));
        BedrockPackCompiler.Result result = compiler.finish();

        JsonObject entries = json(result.pack().file(SessionContentManifest.PATH))
                .getAsJsonObject("entries");
        assertEquals("testpack:ruby_hammer", entries
                .get("models/entity/testpack_ruby_hammer.geo.json").getAsString());
        assertFalse(entries.has("manifest.json"),
                "shared pack files must not claim a content owner");
    }

    @Test
    void aBlockbenchBlockKeepsItsBonesInBedrockAndCompilesOnJava() throws IOException {
        ResourcePack java = sourcePack();
        BlockDefinition definition = BlockDefinition.builder(Key.key("testpack", "rig"))
                .model(new BlockModelDefinition.Blockbench(BLOCK_SOURCE, Map.of()))
                .build();

        BlockStateAllocator allocator = new BlockStateAllocator(BlockCarrier.NOTE_BLOCK);
        JavaBlockCompiler.compileBlocks(java, List.of(new StubBlock(definition)), allocator);

        JsonObject javaModel = json(java.file("assets/testpack/models/block/rig.json"));
        assertEquals(2, javaModel.getAsJsonArray("elements").size());

        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));
        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.addBlocks(List.of(new StubBlock(definition)),
                key -> new BlockStateAllocator.Assignment(BlockCarrier.NOTE_BLOCK, 3), Set.of());
        BedrockPackCompiler.Result result = compiler.finish();

        assertEquals(1, result.blockCount());
        JsonObject geometry = json(result.pack().file("models/blocks/testpack_rig.geo.json"));
        assertEquals(2, geometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones").size());

        JsonObject record = json(result.blockMappings()).getAsJsonObject("blocks")
                .entrySet().stream().findFirst().orElseThrow().getValue().getAsJsonObject();
        assertEquals("geometry.kalo.testpack_rig", record.get("geometry").getAsString());
        assertEquals("t0", record.getAsJsonObject("material_instances").keySet().iterator().next());
        assertNotNull(result.pack().file("textures/blocks/testpack_rig_t0.png"));
        JsonObject entries = json(result.pack().file(SessionContentManifest.PATH))
                .getAsJsonObject("entries");
        assertEquals("testpack:rig",
                entries.get("models/blocks/testpack_rig.geo.json").getAsString());
        assertEquals("testpack:rig",
                entries.get("textures/blocks/testpack_rig_t0.png").getAsString());
    }

    @Test
    void aMissingBlockbenchSourceIsReportedAndCreatesNoBrokenMapping() throws IOException {
        ResourcePack java = sourcePack();
        ItemDefinition definition = ItemDefinition.builder(Key.key("testpack", "missing"))
                .model(new ModelDefinition.Blockbench(
                        Key.key("testpack", "models/item/nope.bbmodel"), Map.of()))
                .build();

        JavaPackCompiler.compileItems(java, List.of(new StubItem(definition)));
        assertFalse(java.files().containsKey("assets/testpack/items/missing.json"));

        BedrockPackCompiler.Result result = compileBedrock(java, List.of(new StubItem(definition)));
        assertEquals(0, result.itemCount());
        assertEquals(1, result.skippedCount());
        assertTrue(result.itemRegistrations().isEmpty());
    }

    @Test
    void repeatedCompilationIsByteDeterministic() throws IOException {
        ResourcePack java = sourcePack();
        ItemDefinition definition = ItemDefinition.builder(Key.key("testpack", "stable"))
                .model(new ModelDefinition.Blockbench(ITEM_SOURCE, Map.of()))
                .build();

        BedrockPackCompiler.Result first = compileBedrock(java, List.of(new StubItem(definition)));
        BedrockPackCompiler.Result second = compileBedrock(java, List.of(new StubItem(definition)));

        assertEquals(first.pack().file("models/entity/testpack_stable.geo.json").toByteArray()
                        .length,
                second.pack().file("models/entity/testpack_stable.geo.json").toByteArray()
                        .length);
        assertEquals(string(first.pack().file("models/entity/testpack_stable.geo.json")),
                string(second.pack().file("models/entity/testpack_stable.geo.json")));
        assertEquals(string(first.pack().file("attachables/testpack_stable.json")),
                string(second.pack().file("attachables/testpack_stable.json")));
    }

    @Test
    void generatedPacksCanBeInspectedAsArchives() throws IOException {
        ResourcePack java = sourcePack();
        ItemDefinition item = ItemDefinition.builder(Key.key("testpack", "ruby_hammer"))
                .model(new ModelDefinition.Blockbench(ITEM_SOURCE, Map.of()))
                .build();
        ItemDefinition animated = ItemDefinition.builder(Key.key("testpack", "animated_rig"))
                .model(new ModelDefinition.Blockbench(BLOCK_SOURCE, Map.of()))
                .build();
        ItemDefinition placedState = ItemDefinition.builder(Key.key("testpack", "placed_rig_on"))
                .model(new ModelDefinition.Blockbench(BLOCK_SOURCE, Map.of()))
                .build();
        ItemDefinition placed = ItemDefinition.builder(Key.key("testpack", "placed_rig"))
                .model(new ModelDefinition.Blockbench(BLOCK_SOURCE, Map.of()))
                .placement(new io.kalo.content.item.definition.PlacementDefinition(
                        1.0, 1.0, 1.0, null, 0, "swing", "swing",
                        new io.kalo.content.item.definition.PlacementDefinition.Storage(3, "Rig"),
                        List.of(new io.kalo.content.item.definition.PlacementDefinition.State(
                                2.0, 15, true, placedState.key(), null))))
                .build();
        ItemDefinition interactive = ItemDefinition.builder(Key.key("testpack", "interactive_rig"))
                .model(new ModelDefinition.Blockbench(
                        Key.key("testpack", "models/block/rigged_arm_interact.bbmodel"),
                        Map.of()))
                .placement(new io.kalo.content.item.definition.PlacementDefinition(
                        1.0, 1.0, 1.0, null, 0, "swing", "Open"))
                .build();
        MobDefinition mobDefinition = MobDefinition.builder(Key.key("testpack", "golem"))
                .base(org.bukkit.entity.EntityType.ZOMBIE)
                .health(60)
                .model(new ModelDefinition.Blockbench(BLOCK_SOURCE, Map.of()))
                .build();
        StubMob mob = new StubMob(mobDefinition);
        ArmorDefinition armorDefinition = new ArmorDefinition(
                ItemDefinition.builder(Key.key("testpack", "ruby_helmet")).build(),
                ArmorSlot.HEAD, null,
                new ArmorDefinition.ArmorModel(BLOCK_SOURCE, Map.of()));
        StubArmor armor = new StubArmor(armorDefinition);
        EmoteDefinition emoteDefinition = new EmoteDefinition(
                Key.key("testpack", "wave"), net.kyori.adventure.text.Component.text("Wave"),
                new ModelDefinition.Blockbench(BLOCK_SOURCE, Map.of()),
                2.0, true, 1.0, 0.0, null,
                io.kalo.content.item.definition.BedrockOptions.defaults());
        StubEmote emote = new StubEmote(emoteDefinition);
        VehicleDefinition vehicleDefinition = new VehicleDefinition(
                Key.key("testpack", "speeder"),
                net.kyori.adventure.text.Component.text("Speeder"),
                org.bukkit.entity.EntityType.ARMOR_STAND,
                new ModelDefinition.Blockbench(BLOCK_SOURCE, Map.of()),
                0.4, 9, 1.0, 0.0, 3,
                io.kalo.content.item.definition.BedrockOptions.defaults());
        StubVehicle vehicle = new StubVehicle(vehicleDefinition);
        BlockDefinition block = BlockDefinition.builder(Key.key("testpack", "rig"))
                .model(new BlockModelDefinition.Blockbench(BLOCK_SOURCE, Map.of()))
                // Animated Blockbench blocks are coerced to virtual at load time so
                // Java can play them through per-bone display entities.
                .java(io.kalo.content.block.definition.JavaBlockOptions.virtual())
                .build();

        JavaPackCompiler.compileItems(java, List.of(new StubItem(item),
                new StubItem(animated), new StubItem(placed),
                new StubItem(placedState), new StubItem(interactive)));
        JavaBlockCompiler.compileBlocks(java, List.of(new StubBlock(block)),
                new BlockStateAllocator(BlockCarrier.NOTE_BLOCK));
        io.kalo.platform.java.JavaMobCompiler.compile(java, List.of(mob));
        io.kalo.platform.java.JavaArmorCompiler.compileArmor(java, List.of(armor));
        io.kalo.platform.java.JavaWornModelCompiler.compile(java, List.of(armor));
        io.kalo.platform.java.JavaRigCompiler.compileEmotes(java, List.of(emote));
        io.kalo.platform.java.JavaRigCompiler.compileVehicles(java, List.of(vehicle));

        BedrockPackCompiler combined = new BedrockPackCompiler(java,
                new ResourcePackImpl(PackMeta.of(0, "bedrock")));
        combined.add(List.of(new StubItem(item), new StubItem(animated),
                new StubItem(placed), new StubItem(placedState),
                new StubItem(interactive)));
        combined.addBlocks(List.of(new StubBlock(block)),
                key -> new BlockStateAllocator.Assignment(BlockCarrier.NOTE_BLOCK, 2), Set.of());
        combined.addMobs(List.of(mob), Set.of());
        combined.addArmor(List.of(armor));
        combined.addEmotes(List.of(emote), Set.of());
        combined.addVehicles(List.of(vehicle), Set.of());
        BedrockPackCompiler.Result result = combined.finish();

        File directory = Path.of("build", "reports", "blockbench-pipeline").toFile();
        io.kalo.model.ModelRegistry.removeSources(java);
        io.kalo.pack.ZipPackWriter.write(new File(directory, "java-pack.zip"), java);
        BedrockPackWriter.write(new File(directory, "bedrock-pack.mcpack"),
                result.pack().files());

        assertTrue(new File(directory, "java-pack.zip").length() > 0);
        assertTrue(new File(directory, "bedrock-pack.mcpack").length() > 0);
        assertNotNull(result.pack().file(
                "animations/testpack_animated_rig.animation.json"));
        assertNotNull(result.pack().file("entity/testpack_placed_rig.entity.json"));
        assertNotNull(result.pack().file(
                "models/entity/testpack_placed_rig_placed.geo.json"));
        assertNotNull(result.pack().file(
                "entity/testpack_placed_rig_state1.entity.json"));
        assertEquals(6, result.itemCount(),
                "five Blockbench items plus the block's own item");
        assertEquals(1, result.blockCount());
        assertEquals(3, result.placedModels().size());

        JsonObject baked = json(result.pack().file(
                        "animations/testpack_rig_block.animation.json"))
                .getAsJsonObject("animations")
                .getAsJsonObject("animation.kalo.testpack_rig.swing")
                .getAsJsonObject("bones").getAsJsonObject("arm")
                .getAsJsonObject("rotation");
        assertTrue(baked.size() >= 8,
                "the generated Bedrock pack must contain baked Bezier keys: " + baked);

        Path sessionData = Path.of("build", "reports", "blockbench-pipeline",
                "session-filter");
        Files.createDirectories(sessionData);
        Path emoteOnly = BedrockPackStager.stage(sessionData,
                new File(directory, "bedrock-pack.mcpack").toPath(), "emote-only",
                Map.of(), SessionContentPolicy.of(
                        Map.of("emote-only", Set.of("testpack:wave"))));
        try (ZipFile zip = new ZipFile(emoteOnly.toFile())) {
            assertNotNull(zip.getEntry("models/entity/testpack_wave_emote.geo.json"));
            assertNotNull(zip.getEntry("textures/entity/testpack_wave_emote_t0.png"));
            assertNull(zip.getEntry("models/blocks/testpack_rig.geo.json"),
                    "a block outside the session allowlist must not be staged");
            assertNull(zip.getEntry("models/entity/testpack_golem_mob.geo.json"),
                    "a mob outside the session allowlist must not be staged");
            assertNotNull(zip.getEntry("manifest.json"),
                    "shared pack files remain");
        }

        // Java: one model per bone, plus the published rig the world runtime reads.
        var rig = io.kalo.platform.java.BlockbenchRigRegistry.get(block.key());
        assertNotNull(rig, "the animated block must publish a Java display rig");
        assertFalse(rig.bones().isEmpty());
        String armBone = rig.bones().stream()
                .filter(bone -> bone.boneName().equals("arm"))
                .findFirst().orElseThrow().boneId();
        double javaQuarter = rig.allAnimations().stream()
                .filter(clip -> clip.name().equals("swing"))
                .findFirst().orElseThrow()
                .sample(0.25).bone(armBone).rotation().z();
        assertEquals(baked.getAsJsonArray("0.25").get(2).getAsDouble(), javaQuarter, 1e-6,
                "Java sampling and the baked Bedrock key must agree");

        JsonObject sway = json(result.pack().file(
                        "animations/testpack_rig_block.animation.json"))
                .getAsJsonObject("animations")
                .getAsJsonObject("animation.kalo.testpack_rig.sway")
                .getAsJsonObject("bones").getAsJsonObject("arm")
                .getAsJsonObject("rotation");
        assertEquals("catmullrom",
                sway.getAsJsonObject("0.5").get("lerp_mode").getAsString(),
                "the generated Bedrock pack must carry the Catmull-Rom mode");
        double javaSway = rig.allAnimations().stream()
                .filter(clip -> clip.name().equals("sway"))
                .findFirst().orElseThrow()
                .sample(0.75).bone(armBone).rotation().z();
        assertEquals(50.625, javaSway, 1e-9,
                "Java Catmull-Rom must use the generated neighbouring keys");
        for (var bone : rig.bones()) {
            assertNotNull(java.file("assets/" + bone.modelKey().namespace() + "/models/"
                    + bone.modelKey().value() + ".json"), bone.modelKey().asString());
        }

        // Bedrock: the animated block is drawn by a client entity, so its block
        // surface becomes transparent and the entity carries the geometry/animation.
        assertEquals(1, result.animatedBlocks().size());
        assertEquals("kalo:block_testpack_rig",
                result.animatedBlocks().getFirst().entityIdentifier());
        JsonObject blockEntity = json(result.pack().file(
                "entity/testpack_rig_block.entity.json"))
                .getAsJsonObject("minecraft:client_entity")
                .getAsJsonObject("description");
        assertEquals("kalo:block_testpack_rig",
                blockEntity.get("identifier").getAsString());
        assertEquals("geometry.kalo.testpack_rig",
                blockEntity.getAsJsonObject("geometry").get("default").getAsString());
        assertTrue(blockEntity.getAsJsonObject("scripts").getAsJsonArray("animate")
                .toString().contains("swing"));
        assertNotNull(result.pack().file(
                "animations/testpack_rig_block.animation.json"));

        // Mob and 3D worn armor assets in the same generated archives.
        assertNotNull(java.file("assets/testpack/models/mob/golem.json"));
        assertNotNull(java.file("assets/testpack/models/armor/ruby_helmet.json"));
        assertNotNull(result.pack().file(
                "models/entity/testpack_golem_mob.geo.json"));
        assertNotNull(result.pack().file(
                "entity/testpack_golem_mob.entity.json"));
        assertNotNull(result.pack().file(
                "models/armor/testpack_ruby_helmet.geo.json"));
        assertNotNull(result.pack().file(
                "attachables/testpack_ruby_helmet.json"));
        assertEquals(1, result.mobs().size());
        assertEquals(2, result.rigs().size());
        assertEquals(3, vehicleDefinition.seats().size(),
                "one vehicle registers one rig regardless of seat count");
        assertEquals(-0.7, vehicleDefinition.seats().get(0).x(), 1e-9);
        assertEquals(0.0, vehicleDefinition.seats().get(1).x(), 1e-9);
        assertEquals(0.7, vehicleDefinition.seats().get(2).x(), 1e-9);
        assertTrue(result.rigs().stream().anyMatch(registration ->
                registration.entityIdentifier().equals("kalo:emote_testpack_wave_emote")));
        assertTrue(result.rigs().stream().anyMatch(registration ->
                registration.entityIdentifier().equals("kalo:vehicle_testpack_speeder_vehicle")));
        assertNotNull(java.file("assets/testpack/models/emote/wave.json"));
        assertNotNull(java.file("assets/testpack/models/vehicle/speeder.json"));
        assertNotNull(java.file("assets/testpack/models/item/placed_rig_bone_2.json"));
        assertNotNull(java.file("assets/testpack/models/item/interactive_rig_bone_2.json"));
        assertNotNull(io.kalo.platform.java.BlockbenchRigRegistry.getPlaced(placed.key()));
        JsonObject interactiveEntity = json(result.pack().file(
                "entity/testpack_interactive_rig.entity.json"))
                .getAsJsonObject("minecraft:client_entity")
                .getAsJsonObject("description");
        assertEquals("animation.kalo.testpack_interactive_rig.open",
                interactiveEntity.getAsJsonObject("animations").get("open").getAsString());
        assertTrue(interactiveEntity.getAsJsonObject("scripts").getAsJsonArray("animate")
                .toString().contains("swing"));
        assertFalse(interactiveEntity.getAsJsonObject("scripts").getAsJsonArray("animate")
                .toString().contains("open"));
        assertNotNull(result.pack().file(
                "entity/testpack_wave_emote.entity.json"));
        assertNotNull(result.pack().file(
                "entity/testpack_speeder_vehicle.entity.json"));

        byte[] surface = result.pack().file("textures/blocks/testpack_rig_t0.png")
                .toByteArray();
        BufferedImage hidden = ImageIO.read(new java.io.ByteArrayInputStream(surface));
        assertEquals(1, hidden.getWidth());
        assertEquals(1, hidden.getHeight());
        assertEquals(0, hidden.getRGB(0, 0) >>> 24,
                "the block surface must be fully transparent under the entity");
    }

    @Test
    void anItemJavaCannotCompileIsSkippedOnBedrockToo() throws IOException {
        ResourcePack java = sourcePack();
        java.file("assets/testpack/models/item/rotated.bbmodel", Writable.string("""
                {"meta":{"format_version":"5.0","model_format":"java_block"},
                 "groups":[{"uuid":"g","name":"arm","origin":[8,8,8],"rotation":[0,30,0]}],
                 "outliner":[{"uuid":"g","children":["e"]}],
                 "elements":[{"uuid":"e","from":[0,0,0],"to":[4,4,4],
                              "faces":{"north":{"uv":[0,0,4,4],"texture":0}}}],
                 "textures":[{"id":"0","name":"ruby",
                              "relative_path":"textures/item/ruby.png"}]}
                """));
        ItemDefinition definition = ItemDefinition.builder(Key.key("testpack", "rotated_item"))
                .model(new ModelDefinition.Blockbench(
                        Key.key("testpack", "models/item/rotated.bbmodel"), Map.of()))
                .build();

        java.util.Set<String> failed = JavaPackCompiler.compileItems(java,
                List.of(new StubItem(definition)));
        assertTrue(failed.contains("testpack:rotated_item"));
        assertFalse(java.files().containsKey("assets/testpack/items/rotated_item.json"));

        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));
        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.add(List.of(new StubItem(definition)), failed);
        BedrockPackCompiler.Result result = compiler.finish();
        assertEquals(0, result.itemCount());
        assertEquals(1, result.skippedCount());
    }

    @Test
    void aBlockbenchItemAnimationIsWrittenAndWiredToItsAttachable() throws IOException {
        ResourcePack java = sourcePack();
        ItemDefinition definition = ItemDefinition.builder(Key.key("testpack", "animated_rig"))
                .model(new ModelDefinition.Blockbench(BLOCK_SOURCE, Map.of()))
                .build();

        JavaPackCompiler.compileItems(java, List.of(new StubItem(definition)));
        BedrockPackCompiler.Result result = compileBedrock(java, List.of(new StubItem(definition)));

        assertEquals(1, result.itemCount());
        JsonObject animations = json(result.pack().file(
                "animations/testpack_animated_rig.animation.json"));
        assertTrue(animations.getAsJsonObject("animations")
                .has("animation.kalo.testpack_animated_rig.swing"));

        JsonObject description = json(result.pack().file(
                "attachables/testpack_animated_rig.json"))
                .getAsJsonObject("minecraft:attachable").getAsJsonObject("description");
        assertEquals("animation.kalo.testpack_animated_rig.swing",
                description.getAsJsonObject("animations").get("swing").getAsString());
        assertTrue(description.getAsJsonObject("scripts").getAsJsonArray("animate")
                .toString().contains("\"swing\""));
    }

    @Test
    void aPlacedInteractionAnimationIsEmittedButNotAutoStarted() throws IOException {
        ResourcePack java = sourcePack();
        Key source = Key.key("testpack", "models/block/rigged_arm_interact.bbmodel");
        ItemDefinition definition = ItemDefinition.builder(Key.key("testpack", "interactive_rig"))
                .model(new ModelDefinition.Blockbench(source, Map.of()))
                .placement(new io.kalo.content.item.definition.PlacementDefinition(
                        1.0, 1.0, 1.0, null, 0, "swing", "Open"))
                .build();

        JavaPackCompiler.compileItems(java, List.of(new StubItem(definition)));
        var rig = io.kalo.platform.java.BlockbenchRigRegistry.getPlaced(definition.key());
        assertNotNull(rig);
        assertTrue(rig.animations().stream().anyMatch(clip -> clip.name().equals("swing")));
        assertFalse(rig.animations().stream().anyMatch(clip -> clip.name().equals("Open")));
        assertTrue(rig.allAnimations().stream().anyMatch(clip -> clip.name().equals("Open")));

        BedrockPackCompiler.Result result = compileBedrock(java,
                List.of(new StubItem(definition)));
        JsonObject entity = json(result.pack().file(
                "entity/testpack_interactive_rig.entity.json"))
                .getAsJsonObject("minecraft:client_entity")
                .getAsJsonObject("description");
        assertTrue(entity.getAsJsonObject("animations").has("open"));
        assertEquals("animation.kalo.testpack_interactive_rig.open",
                entity.getAsJsonObject("animations").get("open").getAsString());
        assertTrue(entity.getAsJsonObject("scripts").getAsJsonArray("animate")
                .toString().contains("swing"));
        assertFalse(entity.getAsJsonObject("scripts").getAsJsonArray("animate")
                .toString().contains("open"));
        assertEquals("animation.kalo.testpack_interactive_rig.open",
                result.placedModels().getFirst().animations().get("open"));
    }

    @Test
    void anAnimatedBlockKeepsItsInteractionClipOnTheBedrockRegistration() throws IOException {
        ResourcePack java = sourcePack();
        Key source = Key.key("testpack", "models/block/rigged_arm_interact.bbmodel");
        BlockDefinition definition = BlockDefinition.builder(Key.key("testpack", "fan"))
                .model(new BlockModelDefinition.Blockbench(source, Map.of()))
                .java(io.kalo.content.block.definition.JavaBlockOptions.virtual())
                .animation(new io.kalo.content.block.definition.BlockAnimation(
                        "swing", "Open"))
                .build();

        JavaBlockCompiler.compileBlocks(java, List.of(new StubBlock(definition)),
                new BlockStateAllocator(BlockCarrier.NOTE_BLOCK));
        BedrockPackCompiler compiler = new BedrockPackCompiler(java,
                new ResourcePackImpl(PackMeta.of(0, "bedrock")));
        compiler.addBlocks(List.of(new StubBlock(definition)),
                key -> new BlockStateAllocator.Assignment(BlockCarrier.NOTE_BLOCK, 4), Set.of());
        BedrockPackCompiler.Result result = compiler.finish();

        var registration = result.animatedBlocks().getFirst();
        assertEquals("animation.kalo.testpack_fan.open",
                registration.animations().get("open"));
        assertTrue(registration.loops().contains("swing"));
        JsonObject entity = json(result.pack().file("entity/testpack_fan_block.entity.json"))
                .getAsJsonObject("minecraft:client_entity")
                .getAsJsonObject("description");
        assertTrue(entity.getAsJsonObject("animations").has("open"));
        assertTrue(entity.getAsJsonObject("scripts").getAsJsonArray("animate")
                .toString().contains("swing"));
        assertFalse(entity.getAsJsonObject("scripts").getAsJsonArray("animate")
                .toString().contains("open"));
    }

    @Test
    void aBlockbenchControllerCompilesAndIsAutoStartedOnBedrock() throws IOException {
        ResourcePack java = sourcePack();
        BlockDefinition definition = BlockDefinition.builder(Key.key("testpack", "controller_rig"))
                .model(new BlockModelDefinition.Blockbench(
                        Key.key("testpack", "models/block/rigged_arm_controller.bbmodel"),
                        Map.of()))
                .java(io.kalo.content.block.definition.JavaBlockOptions.virtual())
                .animation(new io.kalo.content.block.definition.BlockAnimation(
                        "swing", "Open"))
                .build();

        JavaBlockCompiler.compileBlocks(java, List.of(new StubBlock(definition)),
                new BlockStateAllocator(BlockCarrier.NOTE_BLOCK));
        var rig = io.kalo.platform.java.BlockbenchRigRegistry.get(definition.key());
        assertNotNull(rig);
        assertEquals(1, rig.controllers().size());

        BedrockPackCompiler compiler = new BedrockPackCompiler(java,
                new ResourcePackImpl(PackMeta.of(0, "bedrock")));
        compiler.addBlocks(List.of(new StubBlock(definition)),
                key -> new BlockStateAllocator.Assignment(BlockCarrier.NOTE_BLOCK, 5), Set.of());
        BedrockPackCompiler.Result result = compiler.finish();

        assertNotNull(result.pack().file(
                "animation_controllers/testpack_controller_rig_block.animation_controllers.json"));
        JsonObject controllers = json(result.pack().file(
                "animation_controllers/testpack_controller_rig_block.animation_controllers.json"))
                .getAsJsonObject("animation_controllers");
        assertTrue(controllers.has("controller.animation.test"));
        assertEquals("default", controllers.getAsJsonObject("controller.animation.test")
                .get("initial_state").getAsString());
        var open = controllers.getAsJsonObject("controller.animation.test")
                .getAsJsonObject("states").getAsJsonObject("open");
        assertEquals("variable.speed_curve",
                open.getAsJsonArray("animations").get(0).getAsJsonObject()
                        .get("open").getAsString());
        assertEquals("query.modified_move_speed",
                open.getAsJsonObject("variables").getAsJsonObject("speed_curve")
                        .get("input").getAsString());

        JsonObject entity = json(result.pack().file(
                "entity/testpack_controller_rig_block.entity.json"))
                .getAsJsonObject("minecraft:client_entity")
                .getAsJsonObject("description");
        assertTrue(entity.getAsJsonObject("scripts").getAsJsonArray("animate")
                .toString().contains("controller.animation.test"));
        assertEquals(1, result.animatedBlocks().size());
        assertTrue(result.animatedBlocks().getFirst().loops()
                .contains("controller.animation.test"));
    }

    @Test
    void aBlockbenchBlockWithNoAuthoredLoopStaysStatic() throws IOException {
        ResourcePack java = sourcePack();
        BlockDefinition block = BlockDefinition.builder(Key.key("testpack", "static_rig"))
                .model(new BlockModelDefinition.Blockbench(ITEM_SOURCE, Map.of()))
                .java(io.kalo.content.block.definition.JavaBlockOptions.virtual())
                .build();

        JavaBlockCompiler.compileBlocks(java, List.of(new StubBlock(block)),
                new BlockStateAllocator(BlockCarrier.NOTE_BLOCK));
        BedrockPackCompiler compiler = new BedrockPackCompiler(java,
                new ResourcePackImpl(PackMeta.of(0, "bedrock")));
        compiler.addBlocks(List.of(new StubBlock(block)),
                key -> new BlockStateAllocator.Assignment(BlockCarrier.NOTE_BLOCK, 3), Set.of());
        BedrockPackCompiler.Result result = compiler.finish();

        assertTrue(result.animatedBlocks().isEmpty(),
                "hold poses must not turn every Blockbench block into an animated entity");
        assertNotNull(result.pack().file("textures/blocks/testpack_static_rig_t0.png"));
        BufferedImage surface = ImageIO.read(new java.io.ByteArrayInputStream(
                result.pack().file("textures/blocks/testpack_static_rig_t0.png")
                        .toByteArray()));
        assertTrue(surface.getWidth() > 1,
                "a static block keeps its real surface instead of the transparent placeholder");
    }

    @Test
    void aPlacedModelGetsAClientEntityGeometryAnimationAndRegistration() throws IOException {
        ResourcePack java = sourcePack();
        ItemDefinition definition = ItemDefinition.builder(Key.key("testpack", "placed_rig"))
                .model(new ModelDefinition.Blockbench(BLOCK_SOURCE, Map.of()))
                .placement(new io.kalo.content.item.definition.PlacementDefinition(
                        1.5, 2.0, 2.0, null, 10, "swing", "swing"))
                .build();

        JavaPackCompiler.compileItems(java, List.of(new StubItem(definition)));
        assertNotNull(java.file("assets/testpack/models/item/placed_rig_bone_2.json"));
        var placedRig = io.kalo.platform.java.BlockbenchRigRegistry.getPlaced(
                definition.key());
        assertNotNull(placedRig, "the placed item must publish a per-bone Java rig");
        assertFalse(placedRig.bones().isEmpty());
        assertFalse(placedRig.animations().isEmpty());

        BedrockPackCompiler.Result result = compileBedrock(java, List.of(new StubItem(definition)));

        assertNotNull(result.pack().file(
                "models/entity/testpack_placed_rig_placed.geo.json"));
        JsonObject entity = json(result.pack().file(
                "entity/testpack_placed_rig.entity.json"))
                .getAsJsonObject("minecraft:client_entity")
                .getAsJsonObject("description");
        assertEquals("kalo:model_testpack_placed_rig",
                entity.get("identifier").getAsString());
        assertEquals("geometry.kalo.testpack_placed_rig.placed",
                entity.getAsJsonObject("geometry").get("default").getAsString());
        assertEquals("animation.kalo.testpack_placed_rig.swing",
                entity.getAsJsonObject("animations").get("swing").getAsString());
        assertTrue(entity.getAsJsonObject("scripts").getAsJsonArray("animate")
                .toString().contains("\"swing\""));

        assertEquals(1, result.placedModels().size());
        assertEquals("kalo:model_testpack_placed_rig",
                result.placedModels().getFirst().entityIdentifier());
        assertEquals(1.5, result.placedModels().getFirst().scale(), 1e-9);
        assertEquals("animation.kalo.testpack_placed_rig.swing",
                result.placedModels().getFirst().animations().get("swing"));
        assertTrue(result.placedModels().getFirst().loops().contains("swing"));
        assertTrue(placedRig.allAnimations().stream()
                .anyMatch(clip -> clip.name().equals("swing")),
                "the Java rig must keep one-shots available for interaction playback");
    }

    @Test
    void aPlacedModelStateCompilesItsOwnClientEntity() throws IOException {
        ResourcePack java = sourcePack();
        ItemDefinition stateModel = ItemDefinition.builder(Key.key("testpack", "lamp_on"))
                .model(new ModelDefinition.Blockbench(BLOCK_SOURCE, Map.of()))
                .build();
        ItemDefinition base = ItemDefinition.builder(Key.key("testpack", "lamp"))
                .model(new ModelDefinition.Blockbench(BLOCK_SOURCE, Map.of()))
                .placement(new io.kalo.content.item.definition.PlacementDefinition(
                        1.0, 1.0, 1.0, null, 0, "swing", "swing",
                        new io.kalo.content.item.definition.PlacementDefinition.Storage(3, "Lamp"),
                        List.of(new io.kalo.content.item.definition.PlacementDefinition.State(
                                2.0, 15, true, stateModel.key(), null))))
                .build();

        JavaPackCompiler.compileItems(java, List.of(new StubItem(base),
                new StubItem(stateModel)));
        BedrockPackCompiler combined = new BedrockPackCompiler(java,
                new ResourcePackImpl(PackMeta.of(0, "bedrock")));
        combined.add(List.of(new StubItem(base), new StubItem(stateModel)));
        BedrockPackCompiler.Result result = combined.finish();

        JsonObject entity = json(result.pack().file(
                "entity/testpack_lamp_state1.entity.json"))
                .getAsJsonObject("minecraft:client_entity")
                .getAsJsonObject("description");
        assertEquals("kalo:model_testpack_lamp_state1",
                entity.get("identifier").getAsString());
        assertEquals(2, result.placedModels().size(),
                "base model and state model both need entity registrations");
        assertTrue(result.placedModels().stream().anyMatch(model ->
                model.javaKey().equals(Key.key("testpack", "lamp_on"))
                        && model.entityIdentifier().equals("kalo:model_testpack_lamp_state1")));
    }

    private static BedrockPackCompiler.Result compileBedrock(ResourcePack java, List<Item> items) {
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));
        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.add(items);
        return compiler.finish();
    }

    private static JsonObject json(Writable writable) throws IOException {
        assertNotNull(writable);
        return JsonParser.parseString(string(writable)).getAsJsonObject();
    }

    private static String string(Writable writable) throws IOException {
        assertNotNull(writable);
        return new String(writable.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String golden(String name) throws IOException {
        try (var stream = BlockbenchPipelineTest.class.getResourceAsStream("/golden/" + name)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
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

    @Test
    void aBlockbenchMobCompilesBothPlatformsAndRegistersItsEntity() throws IOException {
        ResourcePack java = sourcePack();
        MobDefinition definition = MobDefinition.builder(Key.key("testpack", "golem"))
                .base(org.bukkit.entity.EntityType.ZOMBIE)
                .health(60)
                .model(new ModelDefinition.Blockbench(
                        Key.key("testpack", "models/block/rigged_arm_interact.bbmodel"),
                        Map.of()))
                .animation(new io.kalo.content.animation.RigAnimation("swing", "Open"))
                .build();
        StubMob mob = new StubMob(definition);

        io.kalo.platform.java.JavaMobCompiler.compile(java, List.of(mob));
        assertNotNull(java.file("assets/testpack/models/mob/golem.json"));
        assertNotNull(java.file("assets/testpack/models/mob/golem_bone_2.json"));
        assertNotNull(io.kalo.platform.java.MobModelRegistry.get(mob.key()));
        var mobRig = io.kalo.platform.java.BlockbenchRigRegistry.getMob(mob.key());
        assertNotNull(mobRig, "the mob must publish a per-bone Java rig");
        assertFalse(mobRig.bones().isEmpty());
        assertFalse(mobRig.animations().isEmpty());
        assertTrue(mobRig.bones().stream().anyMatch(bone -> bone.parentId() != null),
                "the registry must keep parent ids for Java motion propagation");
        assertTrue(mobRig.allAnimations().stream()
                .anyMatch(clip -> clip.name().equals("Open")));

        BedrockPackCompiler compiler = new BedrockPackCompiler(java,
                new ResourcePackImpl(PackMeta.of(0, "bedrock")));
        compiler.addMobs(List.of(mob), Set.of());
        BedrockPackCompiler.Result result = compiler.finish();

        assertNotNull(result.pack().file(
                "models/entity/testpack_golem_mob.geo.json"));
        assertNotNull(result.pack().file(
                "animations/testpack_golem_mob.animation.json"));
        assertNotNull(result.pack().file(
                "textures/entity/testpack_golem_t0.png"));
        JsonObject entity = json(result.pack().file(
                "entity/testpack_golem_mob.entity.json"))
                .getAsJsonObject("minecraft:client_entity")
                .getAsJsonObject("description");
        assertEquals("kalo:mob_testpack_golem", entity.get("identifier").getAsString());
        assertEquals("geometry.kalo.testpack_golem_mob",
                entity.getAsJsonObject("geometry").get("default").getAsString());
        assertEquals("animation.kalo.testpack_golem_mob.swing",
                entity.getAsJsonObject("animations").get("swing").getAsString());
        assertEquals("animation.kalo.testpack_golem_mob.open",
                entity.getAsJsonObject("animations").get("open").getAsString());
        assertFalse(entity.getAsJsonObject("scripts").getAsJsonArray("animate")
                .toString().contains("open"));
        assertEquals("animation.kalo.testpack_golem_mob.open",
                result.mobs().getFirst().animations().get("open"));
        assertEquals(1, result.mobs().size());
        assertEquals("kalo:mob_testpack_golem",
                result.mobs().getFirst().entityIdentifier());
    }

    @Test
    void aPlayerBodyEmoteCompilesPerBoneAssetsAndABakedSkin() throws IOException {
        ResourcePack java = sourcePack();
        EmoteDefinition definition = new EmoteDefinition(
                Key.key("testpack", "body_wave"),
                net.kyori.adventure.text.Component.text("Wave"),
                new ModelDefinition.Blockbench(
                        Key.key("testpack", "models/block/rigged_arm_interact.bbmodel"),
                        Map.of("0", Key.key("testpack", "item/steel"))),
                0, true, 1.0, 0.0, null,
                io.kalo.content.item.definition.BedrockOptions.defaults(),
                EmoteDefinition.RigMode.PLAYER, Key.key("testpack", "item/steel"));
        StubEmote emote = new StubEmote(definition);

        io.kalo.platform.java.JavaRigCompiler.compileEmotes(java, List.of(emote));
        assertNotNull(java.file("assets/testpack/models/emote/body_wave_bone_2.json"));
        assertNotNull(java.file(
                "assets/testpack/textures/emote/body_wave_skin.png"),
                "body emotes need a unique texture path for per-player overlays");
        assertTrue(string(java.file(
                        "assets/testpack/models/emote/body_wave_bone_2.json"))
                        .contains("testpack:emote/body_wave_skin"),
                "bone models must reference the overlay texture path");
        var rig = io.kalo.platform.java.BlockbenchRigRegistry.getEmote(definition.key());
        assertNotNull(rig, "a player-body emote needs a per-bone Java rig");
        assertFalse(rig.bones().isEmpty());
        assertTrue(rig.allAnimations().stream()
                .anyMatch(clip -> clip.name().equals("Open")));

        BedrockPackCompiler compiler = new BedrockPackCompiler(java,
                new ResourcePackImpl(PackMeta.of(0, "bedrock")));
        compiler.addEmotes(List.of(emote), Set.of());
        BedrockPackCompiler.Result result = compiler.finish();

        assertNotNull(result.pack().file(
                "models/entity/testpack_body_wave_emote.geo.json"));
        assertNotNull(result.pack().file(
                "entity/testpack_body_wave_emote.entity.json"));
        assertNotNull(result.pack().file(
                "attachables/testpack_body_wave_emote.json"));
        assertNotNull(result.pack().file(
                "textures/entity/testpack_body_wave_emote_t0.png"));
        JsonObject entries = json(result.pack().file(SessionContentManifest.PATH))
                .getAsJsonObject("entries");
        assertEquals("testpack:body_wave",
                entries.get("entity/testpack_body_wave_emote.entity.json").getAsString());
        assertEquals("testpack:body_wave",
                entries.get("textures/entity/testpack_body_wave_emote_t0.png").getAsString());
        JsonObject attachable = json(result.pack().file(
                "attachables/testpack_body_wave_emote.json"))
                .getAsJsonObject("minecraft:attachable")
                .getAsJsonObject("description");
        assertEquals("testpack:body_wave_emote", attachable.get("identifier").getAsString());
        assertTrue(attachable.getAsJsonObject("animations").has("open"));
        assertTrue(attachable.getAsJsonObject("scripts").getAsJsonArray("animate")
                .toString().contains("swing"));
    }

    @Test
    void aBlockbenchWornArmorCompilesAModelAndAnAttachable() throws IOException {
        ResourcePack java = sourcePack();
        ItemDefinition item = ItemDefinition.builder(Key.key("testpack", "ruby_helmet"))
                .build();
        ArmorDefinition definition = new ArmorDefinition(item, ArmorSlot.HEAD, null,
                new ArmorDefinition.ArmorModel(
                        Key.key("testpack", "models/block/rigged_arm_interact.bbmodel"),
                        Map.of(),
                        new io.kalo.content.animation.RigAnimation("swing", "Open")));
        StubArmor armor = new StubArmor(definition);

        io.kalo.platform.java.JavaArmorCompiler.compileArmor(java, List.of(armor));
        io.kalo.platform.java.JavaWornModelCompiler.compile(java, List.of(armor));
        assertNotNull(java.file("assets/testpack/models/armor/ruby_helmet.json"));
        assertNotNull(java.file("assets/testpack/models/armor/ruby_helmet_bone_2.json"));
        var wornRig = io.kalo.platform.java.BlockbenchRigRegistry.getWorn(
                definition.key());
        assertNotNull(wornRig);
        assertTrue(wornRig.bones().stream().anyMatch(bone -> bone.parentId() != null));
        assertNotNull(java.file(
                "assets/kalo/textures/entity/equipment/humanoid/armor_invisible.png"));
        JsonObject equipment = json(java.file("assets/testpack/equipment/ruby_helmet.json"));
        assertEquals("kalo:armor_invisible",
                equipment.getAsJsonObject("layers").getAsJsonArray("humanoid")
                        .get(0).getAsJsonObject().get("texture").getAsString());

        BedrockPackCompiler compiler = new BedrockPackCompiler(java,
                new ResourcePackImpl(PackMeta.of(0, "bedrock")));
        compiler.addArmor(List.of(armor));
        BedrockPackCompiler.Result result = compiler.finish();

        assertNotNull(result.pack().file(
                "models/armor/testpack_ruby_helmet.geo.json"));
        assertNotNull(result.pack().file(
                "animations/testpack_ruby_helmet_armor.animation.json"));
        assertNotNull(result.pack().file(
                "textures/models/armor/testpack_ruby_helmet_t0.png"));
        JsonObject description = json(result.pack().file(
                "attachables/testpack_ruby_helmet.json"))
                .getAsJsonObject("minecraft:attachable")
                .getAsJsonObject("description");
        assertEquals("geometry.kalo.testpack_ruby_helmet_armor",
                description.getAsJsonObject("geometry").get("default").getAsString());
        assertTrue(description.getAsJsonObject("scripts")
                .get("parent_setup").getAsString().contains("helmet_layer_visible"));
        assertTrue(description.getAsJsonObject("scripts").getAsJsonArray("animate")
                .toString().contains("swing"));
        assertEquals("animation.kalo.testpack_ruby_helmet_armor.open",
                description.getAsJsonObject("animations").get("open").getAsString());
        assertFalse(description.getAsJsonObject("scripts").getAsJsonArray("animate")
                .toString().contains("open"));
    }

    private record StubArmor(ArmorDefinition armorDefinition) implements io.kalo.content.armor.Armor {
        @Override
        public @NotNull ArmorDefinition armorDefinition() {
            return armorDefinition;
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
        public io.kalo.content.item.Item asItem() {
            return this;
        }
    }

    private record StubEmote(EmoteDefinition definition)
            implements io.kalo.content.emote.Emote {
        @Override public @NotNull Collection<Feature> features() { return List.of(); }
        @Override public @NotNull FeatureEventBus featureEventBus() { return new FeatureEventBusImpl(); }
        @Override public @NotNull Key key() { return definition.key(); }
        @Override public @NotNull String translationKey() { return definition.translationKey(); }
    }

    private record StubVehicle(VehicleDefinition definition)
            implements io.kalo.content.vehicle.Vehicle {
        @Override public @NotNull Collection<Feature> features() { return List.of(); }
        @Override public @NotNull FeatureEventBus featureEventBus() { return new FeatureEventBusImpl(); }
        @Override public @NotNull Key key() { return definition.key(); }
        @Override public @NotNull String translationKey() { return definition.translationKey(); }
    }

    private record StubMob(MobDefinition definition) implements Mob {
        @Override
        public @NotNull MobDefinition definition() {
            return definition;
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

    private record StubBlock(BlockDefinition definition) implements Block {
        @Override
        public @NotNull BlockDefinition definition() {
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
}

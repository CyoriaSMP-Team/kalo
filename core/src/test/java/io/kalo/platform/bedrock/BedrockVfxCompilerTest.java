package io.kalo.platform.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.content.vfx.definition.VfxDefinition;
import io.kalo.content.vfx.definition.VfxRecipe;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import io.kalo.platform.java.VfxSpritePainter;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockVfxCompilerTest {

    @AfterEach
    void clearRegistry() {
        BedrockVfxRegistry.clear();
    }

    @Test
    void aVfxLayerBecomesATexturedClientEntity() throws Exception {
        BedrockVfxRegistry.clear();
        VfxDefinition definition = new VfxDefinition(Key.key("testpack", "frost"),
                "Frost", new VfxRecipe(20, null, List.of(
                new VfxRecipe.Layer("glow", 0, 20, 4, VfxRecipe.Vec3.ZERO,
                        VfxRecipe.Shape.SPHERE, 1, 0.1, 0.2, 0.5, 10, 0, 0xFFFFFF)),
                List.of()), null);
        ResourcePack java = new ResourcePackImpl(PackMeta.of(0, "java"));
        java.file(VfxSpritePainter.texturePath(definition, 0), Writable.bytes(png()));
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));

        List<BedrockRigRegistration> registrations = BedrockVfxCompiler.compile(
                java, bedrock, List.of(definition));

        assertEquals(1, registrations.size());
        String entityId = BedrockVfxRegistry.entityFor(definition.key(), 0);
        assertNotNull(entityId);
        assertTrue(entityId.startsWith("kalo:vfx_"));
        String stem = "testpack_frost_vfx_0";
        JsonObject entity = JsonParser.parseString(new String(
                bedrock.file("entity/" + stem + ".entity.json").toByteArray(),
                StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject description = entity.getAsJsonObject("minecraft:client_entity")
                .getAsJsonObject("description");
        assertEquals(entityId, description.get("identifier").getAsString());
        assertEquals("geometry.kalo.vfx_" + stem,
                description.getAsJsonObject("geometry").get("default").getAsString());
        assertEquals("textures/entity/vfx/" + stem,
                description.getAsJsonObject("textures").get("default").getAsString());

        JsonObject geometry = JsonParser.parseString(new String(
                bedrock.file("models/entity/" + stem + ".geo.json").toByteArray(),
                StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject shape = geometry.getAsJsonArray("minecraft:geometry").get(0)
                .getAsJsonObject();
        assertEquals(32, shape.getAsJsonObject("description")
                .get("texture_width").getAsInt());
        JsonObject cube = shape.getAsJsonArray("bones").get(0).getAsJsonObject()
                .getAsJsonArray("cubes").get(0).getAsJsonObject();
        assertEquals(8, cube.getAsJsonArray("size").get(0).getAsInt());
        assertNotNull(bedrock.file("textures/entity/vfx/" + stem + ".png"));

        Path artifacts = Path.of("build", "reports", "vfx", "bedrock");
        Files.createDirectories(artifacts);
        Files.writeString(artifacts.resolve(stem + ".entity.json"), entity.toString());
        Files.writeString(artifacts.resolve(stem + ".geo.json"), geometry.toString());
    }

    @Test
    void aCameraLayerBecomesItsOwnClientEntity() throws Exception {
        BedrockVfxRegistry.clear();
        VfxDefinition definition = new VfxDefinition(Key.key("testpack", "frost"),
                "Frost", new VfxRecipe(20, null, List.of(),
                List.of(new VfxRecipe.Camera(new VfxRecipe.Layer("spark",
                        0, 20, 3, VfxRecipe.Vec3.ZERO, VfxRecipe.Shape.RING,
                        0.5, 0, 0, 0.4, 10, 0, 0x00FF00), 2, 0.1)),
                List.of(), List.of(), List.of()), null);
        ResourcePack java = new ResourcePackImpl(PackMeta.of(0, "java"));
        java.file(VfxSpritePainter.texturePath(definition, -1), Writable.bytes(png()));
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));

        List<BedrockRigRegistration> registrations = BedrockVfxCompiler.compile(
                java, bedrock, List.of(definition));

        assertEquals(1, registrations.size());
        String entityId = BedrockVfxRegistry.entityFor(definition.key(), -1);
        assertNotNull(entityId);
        assertTrue(entityId.equals("kalo:vfx_testpack_frost_vfx_camera_0"));
        String stem = "testpack_frost_vfx_camera_0";
        JsonObject entity = JsonParser.parseString(new String(
                bedrock.file("entity/" + stem + ".entity.json").toByteArray(),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(entityId, entity.getAsJsonObject("minecraft:client_entity")
                .getAsJsonObject("description").get("identifier").getAsString());
        JsonObject geometry = JsonParser.parseString(new String(
                bedrock.file("models/entity/" + stem + ".geo.json").toByteArray(),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(6.4, geometry.getAsJsonArray("minecraft:geometry").get(0)
                .getAsJsonObject().getAsJsonArray("bones").get(0).getAsJsonObject()
                .getAsJsonArray("cubes").get(0).getAsJsonObject()
                .getAsJsonArray("size").get(0).getAsDouble(), 1e-9);
        assertNotNull(bedrock.file("textures/entity/vfx/" + stem + ".png"));

        Path artifacts = Path.of("build", "reports", "vfx", "bedrock");
        Files.createDirectories(artifacts);
        Files.writeString(artifacts.resolve(stem + ".entity.json"), entity.toString());
        Files.writeString(artifacts.resolve(stem + ".geo.json"), geometry.toString());
    }

    @Test
    void aSpriteModelLayerBecomesAClientEntity() throws Exception {
        Key ruby = Key.key("testpack", "ruby");
        Key texture = Key.key("testpack:item/ruby");
        VfxDefinition definition = modelDefinition(ruby, "frost_model_sprite");
        ResourcePack java = new ResourcePackImpl(PackMeta.of(0, "java"));
        java.file("assets/testpack/textures/item/ruby.png", Writable.bytes(png()));
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));

        List<BedrockRigRegistration> registrations = BedrockVfxCompiler.compile(
                java, bedrock, List.of(definition),
                key -> key.equals(ruby) ? new ModelDefinition.Sprite(texture) : null);

        assertEquals(1, registrations.size());
        String entityId = BedrockVfxRegistry.modelFor(definition.key(), 0);
        assertNotNull(entityId);
        String stem = "testpack_frost_model_sprite_vfx_model_0";
        assertModelEntity(bedrock, stem, entityId);
        JsonObject geometry = modelGeometry(bedrock, stem);
        assertEquals(16, geometry.getAsJsonArray("minecraft:geometry").get(0)
                .getAsJsonObject().getAsJsonArray("bones").get(0).getAsJsonObject()
                .getAsJsonArray("cubes").get(0).getAsJsonObject()
                .getAsJsonArray("size").get(0).getAsDouble(), 1e-9);
        assertNotNull(bedrock.file("textures/entity/vfx/" + stem + ".png"));
        writeArtifacts(stem, bedrock);
    }

    @Test
    void aCustomJavaModelLayerBecomesAClientEntity() throws Exception {
        Key ruby = Key.key("testpack", "ruby");
        Key model = Key.key("testpack:item/ruby");
        VfxDefinition definition = modelDefinition(ruby, "frost_model_custom");
        ResourcePack java = new ResourcePackImpl(PackMeta.of(0, "java"));
        java.file("assets/testpack/models/item/ruby.json", Writable.string("""
                {"textures":{"0":"testpack:item/ruby"},
                 "elements":[{"from":[0,0,0],"to":[16,16,16],
                   "faces":{"north":{"uv":[0,0,16,16],"texture":"#0"},
                            "south":{"uv":[0,0,16,16],"texture":"#0"}}}]}
                """));
        java.file("assets/testpack/textures/item/ruby.png", Writable.bytes(png()));
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));

        List<BedrockRigRegistration> registrations = BedrockVfxCompiler.compile(
                java, bedrock, List.of(definition),
                key -> key.equals(ruby)
                        ? new ModelDefinition.Custom(model, Map.of()) : null);

        assertEquals(1, registrations.size());
        String entityId = BedrockVfxRegistry.modelFor(definition.key(), 0);
        assertNotNull(entityId);
        String stem = "testpack_frost_model_custom_vfx_model_0";
        assertModelEntity(bedrock, stem, entityId);
        JsonObject geometry = modelGeometry(bedrock, stem);
        assertEquals(1, geometry.getAsJsonArray("minecraft:geometry").get(0)
                .getAsJsonObject().getAsJsonArray("bones").get(0).getAsJsonObject()
                .getAsJsonArray("cubes").size());
        assertNotNull(bedrock.file("textures/entity/vfx/" + stem + ".png"));
        writeArtifacts(stem, bedrock);
    }

    @Test
    void aBlockbenchModelLayerBecomesAClientEntity() throws Exception {
        Key ruby = Key.key("testpack", "ruby");
        Key source = Key.key("testpack:models/item/thing.bbmodel");
        VfxDefinition definition = modelDefinition(ruby, "frost_model_bbmodel");
        ResourcePack java = new ResourcePackImpl(PackMeta.of(0, "java"));
        try (InputStream stream = getClass().getResourceAsStream(
                "/blockbench/simple_cube.bbmodel")) {
            assertNotNull(stream);
            java.file("assets/testpack/models/item/thing.bbmodel",
                    Writable.bytes(stream.readAllBytes()));
        }
        java.file("assets/testpack/textures/item/ruby.png", Writable.bytes(png()));
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));

        List<BedrockRigRegistration> registrations = BedrockVfxCompiler.compile(
                java, bedrock, List.of(definition),
                key -> key.equals(ruby)
                        ? new ModelDefinition.Blockbench(source,
                                Map.of("0", Key.key("testpack:item/ruby"))) : null);

        assertEquals(1, registrations.size());
        String entityId = BedrockVfxRegistry.modelFor(definition.key(), 0);
        assertNotNull(entityId);
        String stem = "testpack_frost_model_bbmodel_vfx_model_0";
        assertModelEntity(bedrock, stem, entityId);
        assertNotNull(bedrock.file("models/entity/" + stem + ".geo.json"));
        assertTrue(bedrock.files().keySet().stream()
                .anyMatch(path -> path.startsWith("textures/entity/vfx/" + stem)
                        && path.endsWith(".png")), bedrock.files().keySet().toString());
        writeArtifacts(stem, bedrock);
    }

    @Test
    void modelRollIsBakedIntoTheBedrockGeometry() throws Exception {
        Key ruby = Key.key("testpack", "ruby");
        VfxDefinition definition = modelDefinition(ruby, "frost_model_roll", 30);
        ResourcePack java = new ResourcePackImpl(PackMeta.of(0, "java"));
        java.file("assets/testpack/textures/item/ruby.png", Writable.bytes(png()));
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));

        List<BedrockRigRegistration> registrations = BedrockVfxCompiler.compile(
                java, bedrock, List.of(definition),
                key -> key.equals(ruby)
                        ? new ModelDefinition.Sprite(Key.key("testpack:item/ruby")) : null);

        assertEquals(1, registrations.size());
        String stem = "testpack_frost_model_roll_vfx_model_0";
        JsonObject geometry = modelGeometry(bedrock, stem);
        JsonArray bones = geometry.getAsJsonArray("minecraft:geometry").get(0)
                .getAsJsonObject().getAsJsonArray("bones");
        JsonObject wrapper = null;
        for (JsonElement element : bones) {
            JsonObject bone = element.getAsJsonObject();
            if (bone.get("name").getAsString().equals("kalo_vfx_roll")) {
                wrapper = bone;
            }
        }
        assertNotNull(wrapper);
        JsonArray rotation = wrapper.getAsJsonArray("rotation");
        assertEquals(0, rotation.get(0).getAsDouble(), 1e-9);
        assertEquals(0, rotation.get(1).getAsDouble(), 1e-9);
        assertEquals(30, rotation.get(2).getAsDouble(), 1e-9);
        assertTrue(bones.get(1).getAsJsonObject().get("parent").getAsString()
                .equals("kalo_vfx_roll"));
        writeArtifacts(stem, bedrock);
    }

    @Test
    void aVanillaModelWithoutAPackModelIsSkippedGracefully() throws Exception {
        Key stone = Key.key("minecraft", "stone");
        VfxDefinition definition = modelDefinition(stone, "frost_model_vanilla");
        ResourcePack java = new ResourcePackImpl(PackMeta.of(0, "java"));
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));

        List<BedrockRigRegistration> registrations = BedrockVfxCompiler.compile(
                java, bedrock, List.of(definition),
                key -> key.equals(stone)
                        ? new ModelDefinition.Vanilla(stone) : null);

        assertTrue(registrations.isEmpty());
        assertNull(BedrockVfxRegistry.modelFor(definition.key(), 0));
    }

    private static VfxDefinition modelDefinition(Key model, String name) {
        return modelDefinition(model, name, 0);
    }

    private static VfxDefinition modelDefinition(Key model, String name, double roll) {
        return new VfxDefinition(Key.key("testpack", name), name,
                new VfxRecipe(20, null, List.of(), List.of(),
                        List.of(new VfxRecipe.Model(model, 0, 20, VfxRecipe.Vec3.ZERO,
                                1.5, 0, 0, roll, 20)),
                        List.of(), List.of()), null);
    }

    private static void assertModelEntity(ResourcePack bedrock, String stem, String entityId)
            throws Exception {
        JsonObject entity = JsonParser.parseString(new String(
                bedrock.file("entity/" + stem + ".entity.json").toByteArray(),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(entityId, entity.getAsJsonObject("minecraft:client_entity")
                .getAsJsonObject("description").get("identifier").getAsString());
        assertNotNull(bedrock.file("models/entity/" + stem + ".geo.json"));
    }

    private static JsonObject modelGeometry(ResourcePack bedrock, String stem) throws Exception {
        return JsonParser.parseString(new String(
                bedrock.file("models/entity/" + stem + ".geo.json").toByteArray(),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void writeArtifacts(String stem, ResourcePack bedrock) throws Exception {
        Path artifacts = Path.of("build", "reports", "vfx", "bedrock");
        Files.createDirectories(artifacts);
        Files.writeString(artifacts.resolve(stem + ".entity.json"),
                new String(bedrock.file("entity/" + stem + ".entity.json").toByteArray(),
                        StandardCharsets.UTF_8));
        Files.writeString(artifacts.resolve(stem + ".geo.json"),
                new String(bedrock.file("models/entity/" + stem + ".geo.json").toByteArray(),
                        StandardCharsets.UTF_8));
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(16, 16, 0xFFFFFFFF);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}

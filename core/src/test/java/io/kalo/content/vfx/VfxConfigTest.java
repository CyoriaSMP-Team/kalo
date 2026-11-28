package io.kalo.content.vfx;

import io.kalo.content.PackContext;
import io.kalo.content.vfx.definition.VfxDefinition;
import io.kalo.content.vfx.definition.VfxPresets;
import io.kalo.content.vfx.definition.VfxRecipe;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxConfigTest {

    @Test
    void aMotesRecipeParsesShapesSpeedsOffsetsAndDiagnostics() {
        YamlConfiguration root = new YamlConfiguration();
        ConfigurationSection recipe = root.createSection("recipe");
        recipe.set("duration", 30);
        recipe.set("loop", List.of(5, 20));
        Map<String, Object> motes = new LinkedHashMap<>();
        motes.put("type", "motes");
        motes.put("kind", "streak");
        motes.put("start", 0);
        motes.put("end", 20);
        motes.put("burst", 40);
        motes.put("at", List.of(0, 1, 0));
        motes.put("shape", "sphere");
        motes.put("speed", List.of(0.3, 0.6));
        motes.put("size", 0.4);
        motes.put("life", 12);
        motes.put("gravity", 0.01);
        motes.put("color", "#3FBF4A");
        recipe.set("layers", List.of(motes, Map.of("type", "beam", "burst", 10)));

        VfxRecipe parsed = VfxRecipe.parse(recipe);

        assertEquals(30, parsed.duration());
        assertEquals(5, parsed.loop().start());
        assertEquals(20, parsed.loop().end());
        assertEquals(1, parsed.layers().size());
        VfxRecipe.Layer layer = parsed.layers().get(0);
        assertEquals("streak", layer.kind());
        assertEquals(40, layer.burst());
        assertEquals(1, layer.at().y());
        assertEquals(VfxRecipe.Shape.SPHERE, layer.shape());
        assertEquals(0.3, layer.speedMin());
        assertEquals(0.6, layer.speedMax());
        assertEquals(12, layer.lifetime());
        assertEquals(0x3FBF4A, layer.color());
        assertTrue(parsed.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.contains("beam")), parsed.diagnostics().toString());
    }

    @Test
    void cameraAndModelLayersParseFromYamlToo() {
        YamlConfiguration root = new YamlConfiguration();
        ConfigurationSection recipe = root.createSection("recipe");
        recipe.set("duration", 30);
        Map<String, Object> camera = new LinkedHashMap<>();
        camera.put("type", "camera");
        camera.put("kind", "spark");
        camera.put("burst", 6);
        camera.put("at", List.of(0, 0.5, 0));
        camera.put("distance", 2);
        camera.put("shake", 0.2);
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("type", "model");
        model.put("model", "testpack:ruby");
        model.put("at", List.of(0, 1, 0));
        model.put("scale", 1.5);
        model.put("spin", 20);
        recipe.set("layers", List.of(camera, model));

        VfxRecipe parsed = VfxRecipe.parse(recipe);

        assertEquals(1, parsed.cameras().size());
        assertEquals(1, parsed.models().size());
        assertEquals("spark", parsed.cameras().get(0).layer().kind());
        assertEquals(0.5, parsed.cameras().get(0).layer().at().y(), 1e-9);
        assertEquals(2, parsed.cameras().get(0).distance(), 1e-9);
        assertEquals(Key.key("testpack:ruby"), parsed.models().get(0).model());
        assertEquals(1.5, parsed.models().get(0).scale(), 1e-9);
        assertEquals(20, parsed.models().get(0).spin(), 1e-9);
    }

    @Test
    void soundEventsParseFromTheRecipeTimeline() {
        YamlConfiguration root = new YamlConfiguration();
        ConfigurationSection recipe = root.createSection("recipe");
        recipe.set("duration", 40);
        recipe.set("sounds", List.of(
                Map.of("tick", 5, "sound", "entity.generic.explode",
                        "volume", 0.5, "pitch", 1.5),
                Map.of("time", 20, "id", "block.note_block.chime")));
        recipe.set("layers", List.of(Map.of(
                "type", "motes", "burst", 2, "shape", "sphere")));

        VfxRecipe parsed = VfxRecipe.parse(recipe);

        assertEquals(2, parsed.sounds().size());
        assertEquals(5, parsed.sounds().get(0).tick());
        assertEquals("entity.generic.explode", parsed.sounds().get(0).name());
        assertEquals(0.5, parsed.sounds().get(0).volume(), 1e-9);
        assertEquals(1.5, parsed.sounds().get(0).pitch(), 1e-9);
        assertEquals(20, parsed.sounds().get(1).tick());
        assertEquals("block.note_block.chime", parsed.sounds().get(1).name());
    }

    @Test
    void outOfRangeNumbersAreClampedAndUnknownShapesBecomeSpheres() {
        YamlConfiguration root = new YamlConfiguration();
        ConfigurationSection recipe = root.createSection("recipe");
        recipe.set("duration", 5000);
        recipe.set("layers", List.of(Map.of(
                "type", "motes", "burst", 900, "shape", "kaleidoscope",
                "color", "not-a-color", "radius", 500)));

        VfxRecipe parsed = VfxRecipe.parse(recipe);

        assertEquals(VfxRecipe.MAX_DURATION, parsed.duration());
        assertEquals(VfxRecipe.MAX_SPRITES, parsed.layers().get(0).burst());
        assertEquals(VfxRecipe.Shape.SPHERE, parsed.layers().get(0).shape());
        assertEquals(0xFFFFFF, parsed.layers().get(0).color());
        assertEquals(32, parsed.layers().get(0).radius());
        assertEquals(3, parsed.diagnostics().size(), parsed.diagnostics().toString());
    }

    @Test
    void allTenStudioPresetNamesHaveRecipes() {
        assertEquals(10, VfxPresets.NAMES.size());
        for (String name : VfxPresets.NAMES) {
            VfxRecipe recipe = VfxPresets.recipe(name).orElseThrow();
            assertTrue(recipe.duration() > 0, name);
            assertTrue(!recipe.layers().isEmpty(), name);
            assertTrue(recipe.layers().stream().mapToInt(VfxRecipe.Layer::burst).sum()
                    <= VfxRecipe.MAX_SPRITES, name);
        }
    }

    @Test
    void aPresetOrJsonFileOrInlineRecipeLoads(@TempDir Path folder) throws Exception {
        Path vfxFolder = folder.resolve("vfx");
        Files.createDirectories(vfxFolder);
        Files.writeString(vfxFolder.resolve("burst.json"), """
                {"duration": 12, "layers": [
                  {"type": "motes", "kind": "spark", "burst": 8, "shape": "ring"}
                ]}
                """);
        PackContext pack = new PackContext("testpack", folder.toFile());

        YamlConfiguration preset = new YamlConfiguration();
        preset.set("preset", "frost_nova");
        VfxDefinition presetDefinition = VfxType.parse(pack,
                Key.key("testpack", "frost"), preset);
        assertEquals("Frost Nova", presetDefinition.name());
        assertEquals("frost_nova", presetDefinition.preset());

        YamlConfiguration file = new YamlConfiguration();
        file.set("file", "testpack/vfx/burst.json");
        VfxDefinition fileDefinition = VfxType.parse(pack,
                Key.key("testpack", "burst"), file);
        assertEquals("burst", fileDefinition.name());
        assertEquals(12, fileDefinition.recipe().duration());

        YamlConfiguration inline = new YamlConfiguration();
        ConfigurationSection inlineRecipe = inline.createSection("recipe");
        inlineRecipe.set("duration", 8);
        inlineRecipe.set("layers", List.of(Map.of(
                "type", "motes", "burst", 4, "shape", "line")));
        VfxDefinition inlineDefinition = VfxType.parse(pack,
                Key.key("testpack", "spark"), inline);
        assertEquals(8, inlineDefinition.recipe().duration());

        YamlConfiguration missing = new YamlConfiguration();
        assertThrows(IllegalArgumentException.class,
                () -> VfxType.parse(pack, Key.key("testpack", "missing"), missing));
        YamlConfiguration unknown = new YamlConfiguration();
        unknown.set("preset", "no_such_preset");
        assertThrows(IllegalArgumentException.class,
                () -> VfxType.parse(pack, Key.key("testpack", "unknown"), unknown));
    }
}

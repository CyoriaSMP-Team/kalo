package io.kalo.content.liquid;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.content.liquid.definition.LiquidDefinition;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquidBiomePackTest {

    private static LiquidDefinition acid() {
        return new LiquidDefinition(Key.key("testpack", "acid"), "Acid",
                LiquidDefinition.Base.WATER, 0x3FBF4A, "POISON", 1, 1.0, false);
    }

    private static LiquidDefinition plain() {
        return new LiquidDefinition(Key.key("testpack", "plain"), "Plain",
                LiquidDefinition.Base.WATER, null, null, 0, 0, false);
    }

    @Test
    void coloredLiquidsWriteBiomesAndUncoloredOnesDoNot(@TempDir Path world)
            throws Exception {
        boolean changed = LiquidBiomePack.install(world, List.of(acid(), plain()));
        assertTrue(changed);

        Path biome = world.resolve(
                "datapacks/kalo-liquids/data/testpack/worldgen/biome/liquid/acid.json");
        assertTrue(Files.isRegularFile(biome));
        assertFalse(Files.exists(world.resolve(
                "datapacks/kalo-liquids/data/testpack/worldgen/biome/liquid/plain.json")));
        assertTrue(Files.isRegularFile(
                world.resolve("datapacks/kalo-liquids/pack.mcmeta")));

        JsonObject json = JsonParser.parseString(Files.readString(biome)).getAsJsonObject();
        assertEquals(0x3FBF4A,
                json.getAsJsonObject("effects").get("water_color").getAsInt());
        assertTrue(json.has("carvers"));
        assertTrue(json.has("features"));
        assertTrue(json.has("spawn_costs"));
        assertTrue(json.has("spawners"));
        assertTrue(json.getAsJsonObject("spawners").has("monster"));
        assertEquals(Key.key("testpack", "liquid/acid"),
                LiquidBiomePack.biomeKey(acid()));
        assertEquals("data/testpack/worldgen/biome/liquid/acid.json",
                LiquidBiomePack.biomePath(acid()));

        assertFalse(LiquidBiomePack.install(world, List.of(acid(), plain())),
                "unchanged install must not request a restart");

        assertTrue(LiquidBiomePack.install(world, List.of(plain())));
        assertFalse(Files.exists(biome), "removed liquid must remove its biome file");

        Path artifacts = Path.of("build", "reports", "liquid");
        Files.createDirectories(artifacts);
        Files.writeString(artifacts.resolve("acid-biome.json"), json.toString());
    }
}

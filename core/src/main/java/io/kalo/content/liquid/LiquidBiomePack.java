package io.kalo.content.liquid;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.kalo.content.liquid.definition.LiquidDefinition;
import io.kalo.pack.Json;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the Java world data pack that registers one tinted biome per colored liquid.
 *
 * <p>Biomes are registered when the world loads, so a colour needs a restart. The runtime
 * applies a volume's biome after the id exists; until then the liquid rules still work and
 * the water keeps its vanilla colour.</p>
 */
public final class LiquidBiomePack {

    public static final String DIRECTORY_NAME = "kalo-liquids";
    private static final String META_PATH = "pack.mcmeta";

    private LiquidBiomePack() {
    }

    public static @NotNull Key biomeKey(@NotNull LiquidDefinition definition) {
        return Key.key(definition.key().namespace(),
                "liquid/" + definition.key().value());
    }

    public static @NotNull String biomePath(@NotNull LiquidDefinition definition) {
        return "data/" + definition.key().namespace() + "/worldgen/biome/liquid/"
                + definition.key().value() + ".json";
    }

    public static @NotNull Map<String, byte[]> render(
            @NotNull List<LiquidDefinition> definitions) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(META_PATH, bytes(metadata()));
        for (LiquidDefinition definition : definitions) {
            if (definition.color() != null) {
                files.put(biomePath(definition), bytes(biome(definition)));
            }
        }
        return files;
    }

    /**
     * @return whether a world restart is needed to register changed biomes
     */
    public static boolean install(@NotNull Path worldFolder,
                                  @NotNull List<LiquidDefinition> definitions)
            throws IOException {
        Path container = worldFolder.resolve("datapacks").resolve(DIRECTORY_NAME);
        Path root = container.toAbsolutePath().normalize();
        Map<String, byte[]> rendered = render(definitions);
        boolean exists = Files.isDirectory(root);
        if (!exists && rendered.size() == 1) {
            return false;
        }

        Files.createDirectories(root);
        boolean changed = false;
        for (Map.Entry<String, byte[]> entry : rendered.entrySet()) {
            Path target = root.resolve(entry.getKey()).normalize();
            if (!target.startsWith(root)) {
                throw new IOException("Unsafe liquid data-pack path: " + entry.getKey());
            }
            if (Files.isRegularFile(target)
                    && java.util.Arrays.equals(Files.readAllBytes(target), entry.getValue())) {
                continue;
            }
            Files.createDirectories(target.getParent());
            writeAtomically(target, entry.getValue());
            changed = true;
        }

        List<Path> stale = new ArrayList<>();
        Path data = root.resolve("data");
        if (Files.isDirectory(data)) {
            try (DirectoryStream<Path> namespaces = Files.newDirectoryStream(data)) {
                for (Path namespace : namespaces) {
                    Path biomes = namespace.resolve("worldgen/biome/liquid");
                    if (!Files.isDirectory(biomes)) {
                        continue;
                    }
                    try (DirectoryStream<Path> files = Files.newDirectoryStream(biomes, "*.json")) {
                        for (Path file : files) {
                            String relative = root.relativize(file.normalize())
                                    .toString().replace('\\', '/');
                            if (!rendered.containsKey(relative)) {
                                stale.add(file);
                            }
                        }
                    }
                }
            }
        }
        for (Path file : stale) {
            Files.deleteIfExists(file);
            changed = true;
        }
        return changed;
    }

    private static @NotNull JsonObject biome(@NotNull LiquidDefinition definition) {
        int color = definition.color() == null ? 0x3F76E4 : definition.color();
        JsonObject effects = new JsonObject();
        effects.addProperty("water_color", color);
        effects.addProperty("water_fog_color", color);
        effects.addProperty("sky_color", 7907327);
        effects.addProperty("fog_color", 12638463);

        JsonObject root = new JsonObject();
        root.addProperty("temperature", 0.8);
        root.addProperty("downfall", 0.4);
        root.addProperty("has_precipitation", true);
        root.add("effects", effects);
        // The biome codec requires these keys even when they are empty. Omitting them
        // makes 1.21.6+ reject the whole data pack and the server refuses to start.
        root.add("carvers", new JsonArray());
        root.add("features", new JsonArray());
        root.add("spawn_costs", new JsonObject());
        JsonObject spawners = new JsonObject();
        for (String category : List.of("ambient", "axolotls", "creature", "misc",
                "monster", "underground_water_creature", "water_ambient",
                "water_creature")) {
            spawners.add(category, new JsonArray());
        }
        root.add("spawners", spawners);
        return root;
    }

    private static JsonObject metadata() {
        JsonObject root = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Kalo liquid colour biomes");
        pack.addProperty("pack_format", 80);
        JsonObject supported = new JsonObject();
        supported.addProperty("min_inclusive", 80);
        supported.addProperty("max_inclusive", 81);
        pack.add("supported_formats", supported);
        JsonArray minimum = new JsonArray();
        minimum.add(80);
        minimum.add(0);
        pack.add("min_format", minimum);
        JsonArray maximum = new JsonArray();
        maximum.add(107);
        maximum.add(1);
        pack.add("max_format", maximum);
        root.add("pack", pack);
        return root;
    }

    private static void writeAtomically(Path target, byte[] value) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), ".kalo-", ".tmp");
        try {
            Files.write(temp, value);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static byte[] bytes(JsonObject object) {
        return (Json.write(object) + "\n").getBytes(StandardCharsets.UTF_8);
    }
}

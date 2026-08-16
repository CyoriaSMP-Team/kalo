package io.kalo.pack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * Checks a generated pack for references that go nowhere.
 *
 * <p>A texture path with a typo produces no error anywhere: the pack builds, the server
 * starts, and the first sign of trouble is a player looking at a magenta-and-black cube.
 * Everything else in Kalo reports what it cannot do rather than failing silently, and
 * asset references were the last place that was not true.</p>
 *
 * <p>Reports rather than refuses. A pack that is 95% right should still load, with the
 * other 5% named.</p>
 */
public final class PackValidator {
    private static final Logger LOGGER = Logger.getLogger(PackValidator.class.getName());

    private PackValidator() {
    }

    /**
     * @return every problem found, empty when the pack is self-consistent
     */
    public static @NotNull @Unmodifiable List<String> validate(@NotNull ResourcePack pack) {
        Map<String, Writable> files = pack.files();
        List<String> problems = new ArrayList<>();

        // Sorted so the same broken pack reports the same way every run.
        for (String path : new TreeSet<>(files.keySet())) {
            if (!path.endsWith(".json")) {
                continue;
            }
            try {
                String content = new String(files.get(path).toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                checkReferences(path, content, files, problems);
            } catch (Exception e) {
                problems.add(path + " could not be read: " + e.getMessage());
            }
        }

        return List.copyOf(problems);
    }

    /**
     * Follows texture and model references out of a JSON asset.
     *
     * <p>Looks for reference-shaped values by key name at any depth rather than parsing
     * each asset kind into a typed model. The formats change every Minecraft release; what
     * does not change is that a {@code namespace:path} reference has to resolve to a file
     * in the pack.</p>
     */
    private static void checkReferences(@NotNull String path,
                                        @NotNull String content,
                                        @NotNull Map<String, Writable> files,
                                        @NotNull List<String> problems) {
        if (path.contains("/models/")) {
            for (String texture : valuesOf(content, "layer0", "all", "up", "down",
                    "north", "south", "east", "west", "side", "particle")) {
                requireTexture(path, texture, files, problems);
            }
            String parent = firstValue(content, "parent");
            if (parent != null && !parent.startsWith("minecraft:")) {
                requireModel(path, parent, files, problems);
            }
        }

        if (path.contains("/items/") || path.contains("/blockstates/")) {
            for (String model : valuesOf(content, "model")) {
                if (!model.startsWith("minecraft:")) {
                    requireModel(path, model, files, problems);
                }
            }
        }

        if (path.contains("/equipment/")) {
            for (String texture : valuesOf(content, "texture")) {
                if (!texture.startsWith("minecraft:")) {
                    // Equipment layers live under a different root than item textures.
                    String[] parts = split(texture);
                    boolean found = files.containsKey(
                            "assets/" + parts[0] + "/textures/entity/equipment/humanoid/" + parts[1] + ".png")
                            || files.containsKey("assets/" + parts[0]
                            + "/textures/entity/equipment/humanoid_leggings/" + parts[1] + ".png");
                    if (!found) {
                        problems.add(path + " references equipment texture '" + texture
                                + "', which is not in the pack");
                    }
                }
            }
        }
    }

    private static void requireTexture(@NotNull String source,
                                       @NotNull String texture,
                                       @NotNull Map<String, Writable> files,
                                       @NotNull List<String> problems) {
        if (texture.startsWith("minecraft:")) {
            // Vanilla textures are in the client, not in this pack.
            return;
        }
        String[] parts = split(texture);
        String expected = "assets/" + parts[0] + "/textures/" + parts[1] + ".png";
        if (!files.containsKey(expected)) {
            problems.add(source + " references texture '" + texture + "' (expected " + expected + ")");
        }
    }

    private static void requireModel(@NotNull String source,
                                     @NotNull String model,
                                     @NotNull Map<String, Writable> files,
                                     @NotNull List<String> problems) {
        String[] parts = split(model);
        String expected = "assets/" + parts[0] + "/models/" + parts[1] + ".json";
        if (!files.containsKey(expected)) {
            problems.add(source + " references model '" + model + "' (expected " + expected + ")");
        }
    }

    /** {@code ns:path}, defaulting the namespace to minecraft the way the game does. */
    private static String[] split(@NotNull String key) {
        int separator = key.indexOf(':');
        return separator < 0
                ? new String[]{"minecraft", key}
                : new String[]{key.substring(0, separator), key.substring(separator + 1)};
    }

    /**
     * Collects the string values of every occurrence of {@code key}, at any depth.
     *
     * <p>Parsed properly rather than scanned as text. A first attempt matched
     * {@code "model":} in the raw JSON and, on an item definition whose {@code model} is
     * an <em>object</em>, happily grabbed the next quoted string it found — which was the
     * word {@code type}. Walking the tree costs nothing here and cannot mistake a key for
     * a value.</p>
     */
    private static @NotNull Set<String> valuesOf(@NotNull String json, @NotNull String... keys) {
        Set<String> wanted = Set.of(keys);
        Set<String> values = new TreeSet<>();
        collect(com.google.gson.JsonParser.parseString(json), wanted, values);
        return values;
    }

    private static void collect(@NotNull com.google.gson.JsonElement element,
                                @NotNull Set<String> keys,
                                @NotNull Set<String> into) {
        if (element.isJsonObject()) {
            for (var entry : element.getAsJsonObject().entrySet()) {
                if (keys.contains(entry.getKey()) && entry.getValue().isJsonPrimitive()) {
                    into.add(entry.getValue().getAsString());
                }
                collect(entry.getValue(), keys, into);
            }
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collect(child, keys, into));
        }
    }

    private static String firstValue(@NotNull String json, @NotNull String key) {
        Set<String> values = valuesOf(json, key);
        return values.isEmpty() ? null : values.iterator().next();
    }

    /** Logs each problem; called after generation so the list appears with the pack. */
    public static void report(@NotNull List<String> problems) {
        if (problems.isEmpty()) {
            return;
        }
        LOGGER.warning("The generated pack has " + problems.size()
                + " broken reference(s) — these will render as missing textures:");
        problems.forEach(problem -> LOGGER.warning("  " + problem));
    }
}

package io.kalo.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Folds an existing resource pack into the generated one.
 *
 * <p>Most servers already ship a pack — a font, a HUD, retextured vanilla — and until now
 * Kalo's output simply replaced it, which forced a choice between custom content and
 * everything already built. Merging removes that choice.</p>
 *
 * <p>Kalo's own generated files win on a collision, because they are the half that has to
 * agree with the server: an item definition that disagrees with the {@code item_model}
 * component renders as missing texture. The two exceptions are the files that are
 * genuinely additive.</p>
 */
public final class PackMerger {
    private static final Logger LOGGER = Logger.getLogger(PackMerger.class.getName());

    /**
     * Files where both packs' contents belong in the result.
     *
     * <p>A language file is a flat map of keys, and a block state file is a map of
     * variants — taking one whole and discarding the other would drop translations or
     * make blocks render as missing texture, when both could have coexisted.</p>
     */
    private static final String LANG_SUFFIX = "/lang/en_us.json";
    private static final String BLOCKSTATES_INFIX = "/blockstates/";

    private PackMerger() {
    }

    /**
     * Merges {@code base} into {@code pack}.
     *
     * @return how many files came from the base pack
     */
    public static int merge(@NotNull ResourcePack pack, @NotNull File base) {
        if (!base.isFile()) {
            LOGGER.warning("Base pack " + base + " does not exist; generating without it");
            return 0;
        }

        int merged = 0;
        try (ZipFile zip = new ZipFile(base)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                try {
                    if (mergeEntry(pack, zip, entry)) {
                        merged++;
                    }
                } catch (IllegalArgumentException e) {
                    // Never copy traversal-like or malformed entry names into the pack
                    // that is sent on to clients.
                    LOGGER.warning("Ignoring unsafe entry '" + entry.getName()
                            + "' in base pack " + base.getName());
                }
            }
        } catch (IOException e) {
            // A broken base pack must not cost the server its custom content.
            LOGGER.log(Level.WARNING, "Could not read the base pack " + base + "; generating without it", e);
            return 0;
        }

        LOGGER.info("Merged " + merged + " file(s) from " + base.getName());
        return merged;
    }

    private static boolean mergeEntry(@NotNull ResourcePack pack,
                                      @NotNull ZipFile zip,
                                      @NotNull ZipEntry entry) throws IOException {
        String path = entry.getName();

        // pack.mcmeta is generated from PackMeta and describes the merged result, not
        // whichever pack happened to supply it.
        if (path.equals("pack.mcmeta")) {
            return false;
        }

        byte[] content;
        try (var input = zip.getInputStream(entry)) {
            content = input.readAllBytes();
        }

        Writable existing = pack.file(path);
        if (existing == null) {
            pack.file(path, Writable.bytes(content));
            return true;
        }

        if (path.endsWith(LANG_SUFFIX) || path.contains(BLOCKSTATES_INFIX)) {
            return mergeJsonObjects(pack, path, content, existing);
        }

        // Everything else: Kalo's generated file wins, because it is the half that has to
        // match what the server sends clients.
        return false;
    }

    /**
     * Merges two flat JSON objects, keeping Kalo's entry on a key collision.
     *
     * <p>For a block state file that means a base pack cannot silently take over a state
     * Kalo has allocated to a custom block; for a language file it means Kalo's own
     * content keeps its name while every other translation survives.</p>
     */
    private static boolean mergeJsonObjects(@NotNull ResourcePack pack,
                                            @NotNull String path,
                                            byte @NotNull [] baseContent,
                                            @NotNull Writable generated) {
        try {
            JsonObject baseJson = JsonParser.parseString(new String(baseContent, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonObject generatedJson = JsonParser.parseString(
                    new String(generated.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();

            JsonObject merged = deepMerge(baseJson, generatedJson);
            pack.file(path, Json.writable(merged));
            return true;
        } catch (Exception e) {
            // Not JSON, or not the shape expected — keep the generated file, which is the
            // one the server depends on.
            LOGGER.log(Level.FINE, "Could not merge " + path + "; keeping the generated version", e);
            return false;
        }
    }

    /** {@code winner}'s members take precedence; nested objects merge rather than replace. */
    private static @NotNull JsonObject deepMerge(@NotNull JsonObject base, @NotNull JsonObject winner) {
        JsonObject result = base.deepCopy();
        for (var entry : winner.entrySet()) {
            JsonElement existing = result.get(entry.getKey());
            if (existing != null && existing.isJsonObject() && entry.getValue().isJsonObject()) {
                result.add(entry.getKey(), deepMerge(existing.getAsJsonObject(), entry.getValue().getAsJsonObject()));
            } else {
                result.add(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}

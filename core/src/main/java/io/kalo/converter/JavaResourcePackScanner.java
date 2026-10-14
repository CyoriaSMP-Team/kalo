package io.kalo.converter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Read-only inspection of an existing Java Edition resource pack.
 *
 * <p>This scanner deliberately does not register new Kalo items, mutate world blocks, or
 * guess a Geyser mapping from an asset name. A resource pack describes rendering; it
 * does not tell us which ItemStack data components a server actually sends. Those
 * associations must come from the server or an explicitly supplied companion snapshot.
 *
 * <p>The inspector is the first input stage for a hybrid conversion pipeline. It does
 * not claim to perform conversion or deployment itself.
 */
public final class JavaResourcePackScanner {
    private static final int MAX_ENTRIES = 50_000;
    private static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    private static final long MAX_ARCHIVE_BYTES = 512L * 1024 * 1024;

    private JavaResourcePackScanner() {
    }

    public enum ItemModelKind {
        PLAIN, CONDITION, RANGE_DISPATCH, SELECT, COMPOSITE, LEGACY_OVERRIDES, OTHER
    }

    public record ItemModel(@NotNull String id, @NotNull String path,
                            @NotNull ItemModelKind kind) {
    }

    public record ScanReport(
            @NotNull List<ItemModel> itemModels,
            @NotNull List<String> blockStateCandidates,
            @NotNull List<String> texturePaths,
            @NotNull List<String> soundDefinitions,
            @NotNull List<String> languageFiles,
            @NotNull List<String> warnings) {
        public ScanReport {
            itemModels = List.copyOf(itemModels);
            blockStateCandidates = List.copyOf(blockStateCandidates);
            texturePaths = List.copyOf(texturePaths);
            soundDefinitions = List.copyOf(soundDefinitions);
            languageFiles = List.copyOf(languageFiles);
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * Index assets without decompressing images or writing anything to disk.
     * JSON size, archive size and entry count are bounded before untrusted content
     * can be parsed. Returned block states are candidates, not confirmed custom blocks.
     */
    public static @NotNull ScanReport scan(@NotNull Path archive) throws IOException {
        if (java.nio.file.Files.size(archive) > MAX_ARCHIVE_BYTES) {
            throw new IOException("Pack ZIP exceeds the " + MAX_ARCHIVE_BYTES + "-byte inspection limit");
        }

        List<ItemModel> items = new ArrayList<>();
        List<String> blockStates = new ArrayList<>();
        List<String> textures = new ArrayList<>();
        List<String> sounds = new ArrayList<>();
        List<String> languages = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int inspected = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++inspected > MAX_ENTRIES) {
                    throw new IOException("Pack ZIP exceeds the " + MAX_ENTRIES + "-entry inspection limit");
                }
                String path = entry.getName();
                if (unsafePath(path)) {
                    throw new IOException("Unsafe ZIP entry path: " + path);
                }
                if (entry.isDirectory() || !path.startsWith("assets/")) {
                    continue;
                }
                if (!seen.add(path)) {
                    warnings.add("Duplicate pack asset: " + path);
                    continue;
                }

                String[] parts = path.split("/", 4);
                if (parts.length < 4 || !validNamespace(parts[1])) {
                    warnings.add("Ignoring malformed asset path: " + path);
                    continue;
                }
                String namespace = parts[1];
                String section = parts[2];
                String name = parts[3];

                if (section.equals("items") && name.endsWith(".json")) {
                    JsonObject object = readObject(zip, entry, warnings);
                    if (object != null) {
                        items.add(new ItemModel(namespace + ":" + withoutJson(name), path,
                                modernItemKind(object)));
                    }
                } else if (section.equals("models") && name.startsWith("item/")
                        && name.endsWith(".json")) {
                    JsonObject object = readObject(zip, entry, warnings);
                    if (object != null && object.has("overrides")
                            && object.get("overrides").isJsonArray()) {
                        items.add(new ItemModel(namespace + ":" + withoutJson(name.substring(5)),
                                path, ItemModelKind.LEGACY_OVERRIDES));
                    }
                } else if (section.equals("blockstates") && name.endsWith(".json")) {
                    // Pack overrides do not prove a block is a custom block. A real
                    // mapping also needs the Java state selected at runtime.
                    blockStates.add(path);
                } else if (section.equals("textures") && name.endsWith(".png")) {
                    textures.add(path);
                } else if (section.equals("lang") && name.endsWith(".json")) {
                    languages.add(path);
                } else if (section.equals("sounds.json") && name.isEmpty()) {
                    // Reserved for unusual paths; regular sounds.json is handled below.
                    sounds.add(path);
                } else if (path.equals("assets/" + namespace + "/sounds.json")) {
                    sounds.add(path);
                }
            }
        }
        // ZipFile iterates central-directory order. Keep output stable even when
        // two archives contain the same assets in different order.
        items.sort(java.util.Comparator.comparing(ItemModel::path));
        blockStates.sort(String::compareTo);
        textures.sort(String::compareTo);
        sounds.sort(String::compareTo);
        languages.sort(String::compareTo);
        return new ScanReport(items, blockStates, textures, sounds, languages, warnings);
    }

    private static JsonObject readObject(ZipFile zip, ZipEntry entry,
                                         List<String> warnings) throws IOException {
        if (entry.getSize() > MAX_JSON_BYTES) {
            throw new IOException("JSON asset too large: " + entry.getName());
        }
        try (InputStream stream = zip.getInputStream(entry)) {
            byte[] bytes = stream.readNBytes(MAX_JSON_BYTES + 1);
            if (bytes.length > MAX_JSON_BYTES) {
                throw new IOException("JSON asset too large: " + entry.getName());
            }
            JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                warnings.add("JSON asset is not an object: " + entry.getName());
                return null;
            }
            return root.getAsJsonObject();
        } catch (com.google.gson.JsonParseException | IllegalStateException exception) {
            warnings.add("Malformed JSON in " + entry.getName() + ": " + exception.getMessage());
            return null;
        }
    }

    private static ItemModelKind modernItemKind(JsonObject json) {
        JsonElement model = json.get("model");
        if (model == null || !model.isJsonObject()) {
            return ItemModelKind.OTHER;
        }
        JsonElement type = model.getAsJsonObject().get("type");
        if (type == null || !type.isJsonPrimitive() || !type.getAsJsonPrimitive().isString()) {
            return ItemModelKind.OTHER;
        }
        return switch (type.getAsString()) {
            case "minecraft:model", "model" -> ItemModelKind.PLAIN;
            case "minecraft:condition", "condition" -> ItemModelKind.CONDITION;
            case "minecraft:range_dispatch", "range_dispatch" -> ItemModelKind.RANGE_DISPATCH;
            case "minecraft:select", "select" -> ItemModelKind.SELECT;
            case "minecraft:composite", "composite" -> ItemModelKind.COMPOSITE;
            default -> ItemModelKind.OTHER;
        };
    }

    private static boolean validNamespace(String namespace) {
        return namespace.matches("[a-z0-9_.-]+");
    }

    private static String withoutJson(String name) {
        return name.substring(0, name.length() - ".json".length());
    }

    private static boolean unsafePath(String path) {
        if (path.startsWith("/") || path.indexOf('\\') >= 0) {
            return true;
        }
        for (String part : path.split("/")) {
            if (part.equals("..") || part.equals(".")) {
                return true;
            }
        }
        return false;
    }
}

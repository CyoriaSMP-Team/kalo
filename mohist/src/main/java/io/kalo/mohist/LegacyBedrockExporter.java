package io.kalo.mohist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Independent Java 17 legacy CustomModelData -> Bedrock texture/mapping exporter.
 * Only translates vanilla Bukkit carrier items and flat generated/handheld sprites.
 * Does not claim to implement Forge custom item registry/protocol translations.
 */
public final class LegacyBedrockExporter {
    private static final int MAX_MODEL_JSON = 512 * 1024;
    private static final int MAX_TEXTURE = 8 * 1024 * 1024;
    private static final int MAX_ITEMS = 512;
    private static final long MAX_TEXTURE_TOTAL = 64L * 1024 * 1024;
    private static final byte[] PNG_HEADER = {(byte)137,80,78,71,13,10,26,10};
    private static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LegacyBedrockExporter() {}

    public record Observed(String material, int customModelData) {
        public Observed {
            if (material == null || !material.matches("[a-z0-9_]{1,90}")
                    || customModelData < 0) throw new IllegalArgumentException("Invalid Java CMD identity");
        }
        public String key() { return "minecraft:" + material; }
    }

    public record Result(int converted, List<String> skipped, JsonObject mapping,
                         Map<String, byte[]> packEntries) {
        public Result {
            skipped = List.copyOf(skipped);
            packEntries = Map.copyOf(packEntries);
        }
    }

    public static Result convert(List<Path> sources, List<Observed> observations) throws IOException {
        if (observations.size() > MAX_ITEMS) throw new IOException("Too many observed items");
        List<ZipFile> open = new ArrayList<>();
        try {
            for (Path path : sources) open.add(new ZipFile(path.toFile()));
            JsonObject mapped = new JsonObject();
            mapped.addProperty("format_version", 2);
            JsonObject items = new JsonObject();
            mapped.add("items", items);
            var textures = new JsonObject();
            var files = new HashMap<String, byte[]>();
            var skipped = new ArrayList<String>();
            Set<String> completed = new HashSet<>();
            long totalTextures = 0;

            for (Observed item : observations.stream().sorted(Comparator.comparing(Observed::key)
                    .thenComparingInt(Observed::customModelData)).toList()) {
                if (!completed.add(item.key()+":"+item.customModelData())) continue;
                try {
                    String materialModel = "assets/minecraft/models/item/" + item.material() + ".json";
                    JsonObject carrier = json(open, materialModel);
                    if (carrier == null) {
                        skipped.add(item.key()+" CMD "+item.customModelData()+": missing vanilla carrier model");
                        continue;
                    }
                    String target = selectOverride(carrier, item.customModelData());
                    if (target == null) {
                        skipped.add(item.key()+" CMD "+item.customModelData()+": no supported override");
                        continue;
                    }
                    String modelPath = assetPath(target, "models/", ".json");
                    JsonObject model = json(open, modelPath);
                    if (model == null) {
                        skipped.add(item.key()+" CMD "+item.customModelData()+": missing model");
                        continue;
                    }
                    String suffix = item.material()+"_"+item.customModelData();
                    String icon = "kalo_mohist_"+suffix;
                    String bedrockId = "kalo_mohist:"+suffix;
                    boolean hasCubes = model.has("elements") && model.get("elements").isJsonArray()
                            && !model.getAsJsonArray("elements").isEmpty();
                    io.kalo.mohist.content.StaticItemAdapter.Output cuboid = hasCubes
                            ? io.kalo.mohist.content.StaticItemAdapter.adapt(model, bedrockId,icon) : null;
                    if ((hasCubes && cuboid == null) || (!hasCubes && !isFlat(model))) {
                        skipped.add(item.key()+" CMD "+item.customModelData()+": unsupported 3D model");
                        continue;
                    }
                    String layer = cuboid != null ? cuboid.texture() : layer0(model);
                    if(layer!=null && !layer.contains(":") && target.contains(":")) {
                        layer=target.substring(0,target.indexOf(":"))+":"+layer;
                    }
                    if (layer == null) {
                        skipped.add(item.key()+" CMD "+item.customModelData()+": unresolved texture layer0");
                        continue;
                    }
                    String texturePath = assetPath(layer, "textures/", ".png");
                    // A spritesheet animation without its Bedrock flipbook would
                    // display incorrectly. Defer animation conversion explicitly.
                    if (asset(open, texturePath+".mcmeta",MAX_MODEL_JSON) != null) {
                        skipped.add(item.key()+" CMD "+item.customModelData()+": animated texture not converted");
                        continue;
                    }
                    byte[] image = asset(open, texturePath, MAX_TEXTURE);
                    if (image == null || !isPng(image)) {
                        skipped.add(item.key()+" CMD "+item.customModelData()+": missing or invalid PNG");
                        continue;
                    }
                    if (totalTextures > MAX_TEXTURE_TOTAL - image.length) {
                        skipped.add(item.key()+" CMD "+item.customModelData()+": total texture limit");
                        continue;
                    }
                    String path = "textures/items/"+icon+".png";
                    if(cuboid != null) {
                        files.put("models/entity/"+icon+".geo.json", jsonBytes(cuboid.geometry()));
                        files.put("attachables/"+icon+".json", jsonBytes(cuboid.attachable()));
                    }
                    files.put(path, image);
                    totalTextures += image.length;

                    JsonObject data = new JsonObject();
                    data.addProperty("textures","textures/items/"+icon);
                    textures.add(icon, data);
                    JsonArray carrierItems = items.has(item.key())
                            ? items.getAsJsonArray(item.key()) : new JsonArray();
                    JsonObject mapping = new JsonObject();
                    mapping.addProperty("type", "legacy");
                    mapping.addProperty("bedrock_identifier", bedrockId);
                    mapping.addProperty("custom_model_data", item.customModelData());
                    JsonObject options = new JsonObject();
                    options.addProperty("icon", icon);
                    mapping.add("bedrock_options", options);
                    carrierItems.add(mapping);
                    items.add(item.key(), carrierItems);
                } catch (IllegalArgumentException invalid) {
                    skipped.add(item.key()+" CMD "+item.customModelData()+": "+invalid.getMessage());
                }
            }

            int converted = completed.size() - skipped.size();
            if (converted > 0) {
                JsonObject atlas = new JsonObject();
                atlas.addProperty("resource_pack_name","Kalo Mohist Compatibility");
                atlas.addProperty("texture_name","atlas.items");
                atlas.add("texture_data", textures);
                files.put("textures/item_texture.json", jsonBytes(atlas));
                files.put("manifest.json", jsonBytes(manifest()));
            }
            return new Result(converted, skipped, mapped, files);
        } finally {
            for (ZipFile zip : open) try { zip.close(); } catch (IOException ignored) { }
        }
    }

    private static String selectOverride(JsonObject carrier, int data) {
        JsonArray overrides = carrier.getAsJsonArray("overrides");
        if (overrides == null || overrides.size() > 1024) return null;
        String selected = null;
        for (JsonElement item : overrides) {
            if (!item.isJsonObject()) return null;
            JsonObject entry = item.getAsJsonObject();
            JsonObject test = entry.getAsJsonObject("predicate");
            // A legacy Java model can also depend on damage/angle/pulling.
            // Skip the whole carrier rather than exporting a misleading icon.
            if (test == null || test.size() != 1 || !test.has("custom_model_data")) return null;
            if (!entry.has("model") || !entry.get("model").isJsonPrimitive()) return null;
            double threshold = test.get("custom_model_data").getAsDouble();
            if (!Double.isFinite(threshold)) return null;
            // Java 1.20.1 applies overrides in list order: last match wins.
            if (data >= threshold) selected = entry.get("model").getAsString();
        }
        return selected;
    }

    private static boolean isFlat(JsonObject model) {
        if (!model.has("parent")) return false;
        String parent = model.get("parent").getAsString();
        return parent.equals("minecraft:item/generated") || parent.equals("item/generated")
                || parent.equals("minecraft:item/handheld") || parent.equals("item/handheld");
    }

    private static String layer0(JsonObject model) {
        JsonObject textures = model.getAsJsonObject("textures");
        if (textures == null || !textures.has("layer0")) return null;
        String layer = textures.get("layer0").getAsString();
        return layer.startsWith("#") ? null : layer;
    }

    private static String assetPath(String key, String folder, String suffix) {
        String namespace = "minecraft", name = key;
        int colon = key.indexOf(':');
        if (colon >= 0) {
            namespace = key.substring(0,colon);
            name = key.substring(colon+1);
        }
        if (!namespace.matches("[a-z0-9_.-]+") || !name.matches("[a-z0-9_./-]+")
                || name.contains("..") || name.startsWith("/") || name.contains("//")) {
            throw new IllegalArgumentException("Unsafe asset identifier "+key);
        }
        return "assets/"+namespace+"/"+folder+name+suffix;
    }

    private static JsonObject json(List<ZipFile> sources, String path) throws IOException {
        byte[] bytes = asset(sources, path, MAX_MODEL_JSON);
        if (bytes == null) return null;
        try {
            JsonElement json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            return json.isJsonObject() ? json.getAsJsonObject() : null;
        } catch (RuntimeException bad) { return null; }
    }

    private static byte[] asset(List<ZipFile> sources, String path, int cap) throws IOException {
        byte[] result = null;
        // The first source is the operator-selected server resource pack;
        // lower-priority mod jars can only supply assets not in that pack.
        for (int i=0;i<sources.size();i++) {
            ZipFile zip = sources.get(i);
            ZipEntry entry = zip.getEntry(path);
            if (entry == null) continue;
            if (entry.getSize() < 0 || entry.getSize() > cap)
                throw new IOException("Oversize ZIP entry "+path);
            try (var in = zip.getInputStream(entry)) {
                byte[] bytes = in.readNBytes(cap+1);
                if (bytes.length > cap) throw new IOException("ZIP entry expansion "+path);
                if (result != null && i > 0) {
                    // Mod jar collisions are ambiguous. Do not prefer a random jar.
                    throw new IOException("Duplicate asset path across archives: "+path);
                }
                result = bytes;
            }
            if (i == 0) return result; // Explicit pack overrides mod assets
        }
        return result;
    }

    private static boolean isPng(byte[] image) {
        if (image.length < 24) return false;
        for (int i=0;i<PNG_HEADER.length;i++)
            if (image[i]!=PNG_HEADER[i]) return false;
        return true;
    }

    private static JsonObject manifest() {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format_version",2);
        JsonObject header = new JsonObject();
        header.addProperty("name","Kalo Mohist - Legacy 2D");
        header.addProperty("description","Experimental vanilla CMD assets for Geyser proxy");
        header.addProperty("uuid","031c81aa-7a4f-54ec-922c-8257dcd87bf3");
        header.add("version",versionArray());
        header.add("min_engine_version",intArray(1,20,0));
        manifest.add("header",header);
        JsonObject module = new JsonObject();
        module.addProperty("type","resources");
        module.addProperty("uuid","debb451d-25f9-5c1c-93cd-d59a9cc6e71b");
        module.add("version",versionArray());
        JsonArray modules = new JsonArray();
        modules.add(module);
        manifest.add("modules",modules);
        return manifest;
    }
    private static JsonArray versionArray() {return intArray(1,0,0);}
    private static JsonArray intArray(int a,int b,int c) {
        JsonArray v=new JsonArray();v.add(a);v.add(b);v.add(c);return v;
    }
    private static byte[] jsonBytes(JsonObject object) {
        return GSON.toJson(object).getBytes(StandardCharsets.UTF_8);
    }

    public static void write(Path folder, Result output) throws IOException {
        Files.createDirectories(folder);
        // A zero-conversion result MUST NOT overwrite a previously good export.
        if (output.converted() == 0) throw new IOException("No supported models; old export left unchanged");
        Path mcpack = folder.resolve("kalo-mohist-generated.mcpack");
        Path mapping = folder.resolve("kalo-mohist-items.json");
        Path stagedPack = Files.createTempFile(folder,".kalo-mohist-", ".mcpack");
        Path stagedMapping = Files.createTempFile(folder,".kalo-mapping-", ".json");
        try {
            try (ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(stagedPack))) {
                for (var entry : output.packEntries().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey()).toList()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            Files.write(stagedMapping, jsonBytes(output.mapping()));
            replace(stagedPack, mcpack);
            replace(stagedMapping, mapping);
        } finally {
            Files.deleteIfExists(stagedPack);
            Files.deleteIfExists(stagedMapping);
        }
    }
    private static void replace(Path from, Path to) throws IOException {
        if (Files.isSymbolicLink(to)) throw new IOException("Refusing symlink output "+to);
        try {
            Files.move(from,to,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

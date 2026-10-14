package io.kalo.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only legacy Java CustomModelData override selection, with persisted item observations. */
public final class ExternalLegacyItems {
    private static final int MAX_ENTRIES = 2048;
    private static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    private ExternalLegacyItems() {}

    public record Binding(@NotNull Material material, int customModelData, int maxStackSize) {
        public Binding {
            if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR
                    || customModelData < 0 || maxStackSize < 1 || maxStackSize > 99) {
                throw new IllegalArgumentException("Invalid legacy Java item binding");
            }
        }
        public String id() { return material.name() + ":" + customModelData; }
    }

    public static synchronized List<Binding> load(Path path) throws IOException {
        if (!Files.exists(path)) return List.of();
        if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid legacy bindings file");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.load(path.toFile()); }
        catch (InvalidConfigurationException bad) { throw new IOException("Invalid legacy YAML",bad); }
        if (yaml.contains("items") && !yaml.isList("items")) throw new IOException("Expected a list of legacy items");
        List<Map<?, ?>> rows = yaml.getMapList("items");
        if (rows.size() > MAX_ENTRIES) throw new IOException("Legacy item mapping limit reached");
        Map<String,Binding> result = new LinkedHashMap<>();
        for (Map<?, ?> row : rows) {
            try {
                Material material = Material.matchMaterial(String.valueOf(row.get("material")));
                if (material == null) throw new IllegalArgumentException("Invalid material");
                Binding b = new Binding(material,
                        Integer.parseInt(String.valueOf(row.get("custom-model-data"))),
                        Integer.parseInt(String.valueOf(row.get("max-stack"))));
                if (result.putIfAbsent(b.id(),b) != null) throw new IllegalArgumentException("Duplicate legacy key");
            } catch (RuntimeException invalid) { throw new IOException("Invalid legacy item: " + row,invalid); }
        }
        return List.copyOf(result.values());
    }

    public static synchronized void remember(Path path,Binding binding) throws IOException {
        Map<String,Binding> items = new LinkedHashMap<>();
        for (Binding b : load(path)) items.put(b.id(),b);
        if (!items.containsKey(binding.id()) && items.size() >= MAX_ENTRIES) throw new IOException("Too many mappings");
        items.put(binding.id(),binding);
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String,Object>> rows = new ArrayList<>();
        items.values().stream().sorted(Comparator.comparing(Binding::id)).forEach(b ->
                rows.add(Map.of("material",b.material().name(),
                        "custom-model-data",b.customModelData(),"max-stack",b.maxStackSize())));
        yaml.set("items",rows);
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(),".legacy-items-", ".tmp");
        try {
            Files.writeString(temp,yaml.saveToString(),StandardCharsets.UTF_8);
            try { Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }

    public record Model(@NotNull ItemDefinition definition,int customModelData) {}

    /**
     * Java legacy overrides are applied in list order, with the last matching
     * predicate winning. Reject other predicate types rather than guessing.
     */
    public static @Nullable Model resolve(ResourcePack pack,Binding binding) {
        try {
            String itemPath="assets/minecraft/models/item/"
                    + binding.material().name().toLowerCase(java.util.Locale.ROOT)+".json";
            Writable writable=pack.file(itemPath);
            if(writable==null)return null;
            byte[] bytes=writable.toByteArray();
            if(bytes.length>MAX_JSON_BYTES)return null;
            JsonObject root=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonArray overrides=root.getAsJsonArray("overrides");
            if(overrides==null||overrides.size()>256)return null;
            String source=null;
            for (JsonElement element:overrides) {
                if(!element.isJsonObject())return null;
                JsonObject entry=element.getAsJsonObject();
                JsonObject predicates=entry.getAsJsonObject("predicate");
                if(predicates==null||predicates.size()!=1||!predicates.has("custom_model_data"))return null;
                double threshold=predicates.get("custom_model_data").getAsDouble();
                if(!Double.isFinite(threshold))return null;
                if(threshold<=binding.customModelData()) {
                    source=entry.get("model").getAsString();
                }
            }
            if(source==null)return null;
            Key sourceModel=Key.key(source);
            String id=java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(binding.id().getBytes(StandardCharsets.UTF_8)),0,12);
            Key synthetic=Key.key("kalo_legacy",id);
            ItemDefinition definition=ExternalItemModelResolver.resolveModel(pack,
                    new ExternalItemBindings.Binding(synthetic,binding.material(),binding.maxStackSize()),
                    synthetic,sourceModel);
            return definition==null?null:new Model(definition,binding.customModelData());
        } catch (Exception unsupported) {return null;}
    }
}

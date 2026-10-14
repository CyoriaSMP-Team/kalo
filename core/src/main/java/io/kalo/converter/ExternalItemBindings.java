package io.kalo.converter;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persisted, operator-observed Java item identities. Never guesses which vanilla
 * material carries an item model merely because an asset exists in a resource pack.
 */
public final class ExternalItemBindings {
    private static final int MAX_ITEMS = 2048;

    private ExternalItemBindings() {}

    public record Binding(@NotNull Key itemModel, @NotNull Material material,
                          int maxStackSize) {
        public Binding {
            if (material == Material.AIR || material == Material.CAVE_AIR
                    || material == Material.VOID_AIR) {
                throw new IllegalArgumentException("Expected a Java item material");
            }
            if (maxStackSize < 1 || maxStackSize > 99) {
                throw new IllegalArgumentException("Stack size must be 1..99");
            }
        }
    }

    public static synchronized @NotNull List<Binding> load(@NotNull Path path) throws IOException {
        if (!Files.exists(path)) return List.of();
        if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("External mapping store must be a regular file: " + path);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(path.toFile());
        } catch (org.bukkit.configuration.InvalidConfigurationException invalid) {
            throw new IOException("Malformed external bindings YAML", invalid);
        }
        if (yaml.contains("items") && !yaml.isList("items")) {
            throw new IOException("The items key must be a YAML list");
        }
        List<Map<?, ?>> rows = yaml.getMapList("items");
        if (rows.size() > MAX_ITEMS) throw new IOException("Too many external mappings");
        Map<String, Binding> loaded = new LinkedHashMap<>();
        for (Map<?, ?> row : rows) {
            try {
                String model = String.valueOf(row.get("item-model"));
                String javaMaterial = String.valueOf(row.get("material"));
                String sizeText = String.valueOf(row.get("max-stack"));
                Key key = Key.key(model);
                Material material = Material.matchMaterial(javaMaterial);
                if (material == null) throw new IllegalArgumentException("Unknown material " + javaMaterial);
                Binding binding = new Binding(key, material, Integer.parseInt(sizeText));
                if (loaded.putIfAbsent(key.asString(), binding) != null) {
                    throw new IllegalArgumentException("Duplicate item model " + model);
                }
            } catch (RuntimeException invalid) {
                throw new IOException("Invalid external item binding in " + path + ": " + row, invalid);
            }
        }
        return List.copyOf(loaded.values());
    }

    /**
     * Writes only after the held item has been observed on its owning entity thread.
     * A repeated model is updated instead of duplicating Geyser identifiers.
     */
    public static synchronized void remember(@NotNull Path path, @NotNull Binding binding)
            throws IOException {
        Map<String, Binding> items = new LinkedHashMap<>();
        for (Binding previous : load(path)) items.put(previous.itemModel().asString(), previous);
        if (!items.containsKey(binding.itemModel().asString()) && items.size() >= MAX_ITEMS) {
            throw new IOException("External mapping limit reached");
        }
        items.put(binding.itemModel().asString(), binding);
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> rows = new ArrayList<>();
        items.values().stream().sorted(Comparator.comparing(b -> b.itemModel().asString()))
                .forEach(b -> rows.add(Map.of(
                        "item-model", b.itemModel().asString(),
                        "material", b.material().name(),
                        "max-stack", b.maxStackSize())));
        yaml.set("items", rows);
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".external-items-", ".tmp");
        try {
            Files.writeString(temporary, yaml.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

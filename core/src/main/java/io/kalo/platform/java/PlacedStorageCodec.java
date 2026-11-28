package io.kalo.platform.java;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Durable, self-contained encoding for a placed-model container.
 *
 * <p>The contents live on the hitbox entity's persistent data rather than in a sidecar
 * file, so moving/copying the chunk moves the container with it. The payload is YAML,
 * base64-wrapped because persistent data is a string field.</p>
 */
final class PlacedStorageCodec {

    private PlacedStorageCodec() {
    }

    static @NotNull String encode(@NotNull ItemStack[] contents) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack != null && !stack.getType().isAir()) {
                yaml.set("slot." + slot, stack);
            }
        }
        return Base64.getEncoder().encodeToString(
                yaml.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    /** @return a slot-aligned array; missing/removed slots are null */
    static @Nullable ItemStack[] decode(@Nullable String encoded, int size) {
        if (encoded == null) {
            return null;
        }
        if (encoded.isBlank()) {
            return new ItemStack[Math.max(0, size)];
        }
        try {
            String yamlText = new String(Base64.getDecoder().decode(encoded),
                    StandardCharsets.UTF_8);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(yamlText);
            ItemStack[] contents = new ItemStack[Math.max(0, size)];
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack stack = yaml.getItemStack("slot." + slot);
                if (stack != null && !stack.getType().isAir()) {
                    contents[slot] = stack;
                }
            }
            return contents;
        } catch (IllegalArgumentException | InvalidConfigurationException malformed) {
            return null;
        }
    }
}

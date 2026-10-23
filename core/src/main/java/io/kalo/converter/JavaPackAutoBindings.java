package io.kalo.converter;

import io.kalo.pack.ResourcePack;
import org.bukkit.Material;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bind only resource-pack item selectors whose Java carrier identity is unambiguous.
 *
 * The path assets/minecraft/items/<material>.json names the actual vanilla item that
 * Java uses. For arbitrary custom namespaces, a pack does NOT reveal which ItemStack
 * carries the item_model component, so those still need observed external bindings.
 * Never guess an identity from a texture filename or a model key.
 */
public final class JavaPackAutoBindings {
    private static final String PREFIX = "assets/minecraft/items/";
    private static final int MAX_MATCHES = 256;

    private JavaPackAutoBindings() {}

    public static @NotNull List<ExternalItemBindings.Binding> discover(@NotNull ResourcePack pack) {
        List<ExternalItemBindings.Binding> result = new ArrayList<>();
        for (String path : pack.files().keySet().stream().sorted().toList()) {
            if (!path.startsWith(PREFIX) || !path.endsWith(".json")) continue;
            String name = path.substring(PREFIX.length(), path.length() - 5);
            if (name.contains("/") || !name.matches("[a-z0-9_]+")) continue;
            Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
            if (material == null || !material.isItem() || material.isAir()) continue;
            // Bukkit's material identity is the authoritative carrier, not the
            // model's namespace or any guessed CustomModelData value.
            result.add(new ExternalItemBindings.Binding(Key.key("minecraft", name),
                    material, Math.max(1, material.getMaxStackSize())));
            if (result.size() >= MAX_MATCHES) break;
        }
        return List.copyOf(result);
    }
}

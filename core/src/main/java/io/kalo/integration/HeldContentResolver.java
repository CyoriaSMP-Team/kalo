package io.kalo.integration;

import io.kalo.content.Content;
import io.kalo.content.block.Block;
import io.kalo.content.item.Item;
import io.kalo.content.item.ItemImpl;
import io.kalo.manager.RegistryManager;
import io.kalo.platform.java.JavaBlockItemCompiler;
import io.kalo.registry.Registry;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Shared lookup for every built-in content kind that has a held item form. */
final class HeldContentResolver {

    private HeldContentResolver() {
    }

    static @Nullable ResolvedContent resolve(
            @NotNull ItemStack stack,
            @NotNull RegistryManager.GlobalRegistries registries
    ) {
        return resolveIds(
                ItemImpl.idOf(stack), JavaBlockItemCompiler.idOf(stack),
                registries.item(), registries.armor(), registries.block(), registries.furniture());
    }

    /**
     * Kept independent of Bukkit item metadata so the cross-registry resolution contract
     * can be exercised without a running Minecraft server.
     */
    static @Nullable ResolvedContent resolveIds(
            @Nullable String itemId,
            @Nullable String blockId,
            @NotNull Registry<? extends Content> items,
            @NotNull Registry<? extends Content> armor,
            @NotNull Registry<? extends Content> blocks,
            @NotNull Registry<? extends Content> furniture
    ) {
        ResolvedContent itemForm = resolveId(itemId, items, armor);
        return itemForm != null ? itemForm : resolveId(blockId, blocks, furniture);
    }

    static @NotNull String displayName(@NotNull Content content) {
        var name = switch (content) {
            case Item item -> item.definition().display().name();
            case Block block -> block.definition().display().name();
            default -> null;
        };
        return name != null
                ? PlainTextComponentSerializer.plainText().serialize(name)
                : content.key().value();
    }

    @SafeVarargs
    private static @Nullable ResolvedContent resolveId(
            @Nullable String id,
            @NotNull Registry<? extends Content>... registries
    ) {
        if (id == null) {
            return null;
        }

        final Key key;
        try {
            key = Key.key(id);
        } catch (RuntimeException ignored) {
            // Persistent data is player-controlled through commands and item editors;
            // a malformed value must not break an entire PlaceholderAPI expansion pass.
            return null;
        }

        for (Registry<? extends Content> registry : registries) {
            Content content = registry.get(key).orElse(null);
            if (content != null) {
                return new ResolvedContent(id, content);
            }
        }
        return null;
    }

    record ResolvedContent(@NotNull String id, @NotNull Content content) {
    }
}

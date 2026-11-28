package io.kalo.compat;

import io.kalo.Kalo;
import io.kalo.content.block.Block;
import io.kalo.registry.Registries;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Block and furniture side of the shims. Kalo furniture is a static, block-backed piece, not an
 * entity, so only what maps onto a block is offered here: identify, place and remove. Anything that
 * needs a furniture entity has nothing to return and is left to the shims to omit.
 */
public final class KaloBlocks {
    private KaloBlocks() {
    }

    private static Registries registries() {
        return Kalo.plugin().registryManager().registries();
    }

    /** Kalo block keys ("namespace:value"), excluding furniture. */
    public static @NotNull List<String> blockKeys() {
        List<String> keys = new ArrayList<>();
        registries().block().entries().forEach(entry -> keys.add(entry.left().asString()));
        return keys;
    }

    /** Kalo furniture keys ("namespace:value"). */
    public static @NotNull List<String> furnitureKeys() {
        List<String> keys = new ArrayList<>();
        registries().furniture().entries().forEach(entry -> keys.add(entry.left().asString()));
        return keys;
    }

    /** The exact key a block or furniture id refers to, or null. Uses the shared id rule. */
    public static @Nullable String resolveKey(@NotNull String id) {
        List<String> all = new ArrayList<>(blockKeys());
        all.addAll(furnitureKeys());
        return CompatIds.resolve(id, all);
    }

    /** The Kalo content (block or furniture) for an id, or null. */
    public static @Nullable Block contentFor(@NotNull String id) {
        String key = resolveKey(id);
        if (key == null) {
            return null;
        }
        Key parsed = Key.key(key);
        return registries().furniture().get(parsed).map(f -> (Block) f)
                .or(() -> registries().block().get(parsed)).orElse(null);
    }

    /** What an id names among blocks and furniture: BLOCK, FURNITURE, or null if neither. */
    public static @Nullable KaloItemLookup.Kind kindOfId(@NotNull String id) {
        String key = resolveKey(id);
        if (key == null) {
            return null;
        }
        KaloItemLookup.Kind kind = KaloItemLookup.kindOf(key);
        return kind == KaloItemLookup.Kind.ITEM ? null : kind;
    }

    /** What an item stack is, among blocks and furniture; null for plain items and non-Kalo stacks. */
    public static @Nullable KaloItemLookup.Kind kindOfStack(@Nullable ItemStack stack) {
        String key = KaloItemLookup.keyOf(stack);
        if (key == null) {
            return null;
        }
        KaloItemLookup.Kind kind = KaloItemLookup.kindOf(key);
        return kind == KaloItemLookup.Kind.ITEM ? null : kind;
    }

    /** The Kalo key of the block or furniture drawn at a world block, or null. */
    public static @Nullable String keyAt(@NotNull org.bukkit.block.Block world) {
        Block content = Kalo.plugin().blockWorld().blockAt(world);
        return content == null ? null : content.key().asString();
    }

    /** What is drawn at a world block: BLOCK, FURNITURE, or null if it is not Kalo content. */
    public static @Nullable KaloItemLookup.Kind kindAt(@NotNull org.bukkit.block.Block world) {
        String key = keyAt(world);
        return key == null ? null : KaloItemLookup.kindOf(key);
    }

    /** Places the block or furniture for an id at a location. False if unknown or not placeable. */
    public static boolean place(@NotNull String id, @NotNull Location location) {
        Block content = contentFor(id);
        return content != null && Kalo.plugin().blockWorld().place(content, location.getBlock());
    }

    /** Removes Kalo content at a location. */
    public static boolean remove(@NotNull Location location, boolean dropItem) {
        return Kalo.plugin().blockWorld().remove(location.getBlock(), dropItem);
    }

    /** The vanilla state a native block is drawn with, or null. */
    public static @Nullable BlockData blockData(@NotNull String id) {
        Block content = contentFor(id);
        return content == null ? null : Kalo.plugin().blockWorld().blockData(content);
    }
}

package io.kalo.platform.java;

import net.kyori.adventure.key.Key;
import io.kalo.Kalo;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared item-display plumbing for vehicle and emote props. */
final class RigDisplay {

    private RigDisplay() {
    }

    static @NotNull ItemStack stack(@NotNull Key modelKey) {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, modelKey);
        return stack;
    }

    /**
     * Client-visible range for a prop display, scaled by the runtime budget.
     *
     * <p>Props were spawned with a fixed 64-block range, so a server under pressure kept
     * sending every prop to every nearby client. The floor keeps a prop visible to the
     * player standing next to it.</p>
     */
    static float viewRange(float multiplier) {
        return Math.max(16f, Math.min(64f, 64f * multiplier));
    }

    private static float budgetedViewRange() {
        try {
            return viewRange((float) Kalo.plugin().performance().snapshot()
                    .budget().renderDistanceScale());
        } catch (RuntimeException notReady) {
            // Enabling or disabling: spawn at the full range rather than fail a spawn.
            return 64f;
        }
    }

    static @NotNull ItemDisplay spawn(@NotNull Location location, @NotNull Key modelKey,
                                      double yOffset, double scale,
                                      @NotNull org.bukkit.NamespacedKey ownerKey,
                                      @NotNull String ownerId) {
        return location.getWorld().spawn(location, ItemDisplay.class, spawned -> {
            spawned.setItemStack(stack(modelKey));
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setSilent(true);
            spawned.setViewRange(budgetedViewRange());
            spawned.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING,
                    ownerId);
            apply(spawned, location, yOffset, scale);
        });
    }

    static void follow(@NotNull ItemDisplay display, @NotNull Location location,
                       double yOffset, double scale) {
        display.teleport(location);
        apply(display, location, yOffset, scale);
    }

    private static void apply(@NotNull ItemDisplay display, @NotNull Location location,
                              double yOffset, double scale) {
        display.setRotation(location.getYaw(), 0f);
        // Item models render around (8,8,8); entity-space models put their feet/origin
        // at y=0, so half a block lifts the prop onto the carrier instead of under it.
        float y = (float) (yOffset + 0.5);
        float s = (float) scale;
        display.setTransformation(new Transformation(new Vector3f(0f, y, 0f),
                new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
    }
}

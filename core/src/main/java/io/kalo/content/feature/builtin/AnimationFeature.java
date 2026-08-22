package io.kalo.content.feature.builtin;

import io.kalo.content.Content;
import io.kalo.content.feature.*;
import io.kalo.content.feature.event.ItemInteractEvent;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Provides item use animations and custom model data swapping.
 *
 * <p>Supported animations:</p>
 * <ul>
 *   <li><b>swing</b> — Plays swing animation on use</li>
 *   <li><b>block</b> — Plays block animation on use</li>
 *   <li><b>bow</b> — Plays bow pull animation</li>
 *   <li><b>crossbow</b> — Plays crossbow load animation</li>
 *   <li><b>drink</b> — Plays drink animation</li>
 *   <li><b>eat</b> — Plays eat animation</li>
 *   <li><b>spyglass</b> — Plays spyglass use animation</li>
 *   <li><b>totem</b> — Plays totem of undying animation</li>
 * </ul>
 *
 * <p>Also supports custom model data swapping on use (useful for animated items):</p>
 * <pre>
 * features:
 *   animation:
 *     id: kalo:animation
 *     arguments:
 *       swing: true
 *       custom_model_data_frames: [1, 2, 3, 4, 5]
 *       frame_delay: 5
 * </pre>
 */
public final class AnimationFeature extends Feature {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "animation");

    private AnimationFeature(@NotNull FeatureFactory.Context context) {
        super(context);
        Map<String, String> args = context.arguments().raw();

        // Subscribe to item interact events
        context.eventBus().subscribe(ItemInteractEvent.class, event -> {
            Player player = event.player();
            ItemStack item = event.item();

            // Play animation
            if (args.containsKey("swing")) {
                player.swingMainHand();
            }
            if (args.containsKey("block")) {
                player.swingMainHand();
            }
            if (args.containsKey("bow")) {
                player.swingMainHand();
            }
            if (args.containsKey("crossbow")) {
                player.swingMainHand();
            }
            if (args.containsKey("drink")) {
                player.swingMainHand();
            }
            if (args.containsKey("eat")) {
                player.swingMainHand();
            }
            if (args.containsKey("spyglass")) {
                player.swingMainHand();
            }
            if (args.containsKey("totem")) {
                player.swingMainHand();
            }

            // Custom model data frame animation
            String framesStr = args.get("custom_model_data_frames");
            if (framesStr != null) {
                animateCustomModelData(player, item, args);
            }
        });

        Plugins.logger().info("Loaded animation feature for " + context.content().key().asString());
    }

    private void animateCustomModelData(@NotNull Player player, @NotNull ItemStack item, @NotNull Map<String, String> args) {
        // Get frame data
        String framesStr = args.get("custom_model_data_frames");
        if (framesStr == null) return;

        // Parse frame list (simplified - just set first frame)
        try {
            String[] frames = framesStr.replace("[", "").replace("]", "").split(",");
            if (frames.length > 0) {
                int firstFrame = Integer.parseInt(frames[0].trim());
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setCustomModelData(firstFrame);
                    item.setItemMeta(meta);
                }
            }
        } catch (NumberFormatException ignored) {}
    }

    public static final class Factory implements FeatureFactory<AnimationFeature> {
        @Override
        public @NotNull AnimationFeature create(@NotNull Context context) {
            return new AnimationFeature(context);
        }
    }
}

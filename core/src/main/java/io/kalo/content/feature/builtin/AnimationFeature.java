package io.kalo.content.feature.builtin;

import io.kalo.content.feature.Feature;
import io.kalo.content.feature.FeatureFactory;
import io.kalo.content.feature.event.ItemInteractEvent;
import io.kalo.content.item.ItemImpl;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Server-authoritative finite custom-model-data sequences for the held Kalo item.
 *
 * <p>This is not a generic animation converter: Java and Bedrock still need authored
 * frame variants and their own client-side animation assets. Unsupported animation
 * names are reported rather than faking them with an unrelated hand swing.</p>
 */
public final class AnimationFeature extends Feature {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "animation");
    private final Map<UUID, ScheduledTask> running = new ConcurrentHashMap<>();

    private AnimationFeature(@NotNull FeatureFactory.Context context) {
        super(context);
        Map<String, String> args = context.arguments().raw();
        AnimationFrames frames = null;
        if (args.containsKey("custom_model_data_frames")) {
            try {
                frames = AnimationFrames.parse(args.get("custom_model_data_frames"),
                        args.getOrDefault("frame_delay", "5"));
            } catch (IllegalArgumentException exception) {
                Plugins.logger().log(Level.WARNING,
                        "Invalid animation frames for " + context.content().key(), exception);
            }
        }

        for (String unsupported : new String[]{"block", "bow", "crossbow", "drink",
                "eat", "spyglass", "totem"}) {
            if (Boolean.parseBoolean(args.getOrDefault(unsupported, "false"))) {
                Plugins.logger().warning("Animation '" + unsupported + "' is not yet implemented on "
                        + context.content().key() + " (no false swing fallback)");
            }
        }

        AnimationFrames sequence = frames;
        String itemKey = context.content().key().asString();
        context.eventBus().subscribe(ItemInteractEvent.class, event -> {
            Player player = event.player();
            if (Boolean.parseBoolean(args.getOrDefault("swing", "false"))) {
                player.swingMainHand();
            }
            if (sequence != null) {
                animate(player, itemKey, sequence);
            }
        });
    }

    private void animate(@NotNull Player player, @NotNull String itemKey,
                         @NotNull AnimationFrames sequence) {
        UUID playerId = player.getUniqueId();
        ScheduledTask previous = running.remove(playerId);
        if (previous != null) previous.cancel();

        AtomicInteger index = new AtomicInteger();
        ScheduledTask task = player.getScheduler().runAtFixedRate(Plugins.plugin(), scheduled -> {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!Objects.equals(itemKey, ItemImpl.idOf(hand))) {
                scheduled.cancel();
                running.remove(playerId, scheduled);
                return;
            }

            int frame = index.getAndIncrement();
            if (frame >= sequence.frames().size()) {
                scheduled.cancel();
                running.remove(playerId, scheduled);
                return;
            }

            ItemMeta meta = hand.getItemMeta();
            if (meta == null) {
                scheduled.cancel();
                running.remove(playerId, scheduled);
                return;
            }
            meta.setCustomModelData(sequence.frames().get(frame));
            hand.setItemMeta(meta);
            player.getInventory().setItemInMainHand(hand);
        }, () -> running.remove(playerId), 1L, sequence.delayTicks());

        // The player may already have been retired from the scheduler.
        if (task != null) running.put(playerId, task);
    }

    public static final class Factory implements FeatureFactory<AnimationFeature> {
        @Override public @NotNull AnimationFeature create(@NotNull Context context) {
            return new AnimationFeature(context);
        }
    }
}

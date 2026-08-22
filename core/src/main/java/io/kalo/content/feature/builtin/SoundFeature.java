package io.kalo.content.feature.builtin;

import io.kalo.content.Content;
import io.kalo.content.feature.*;
import io.kalo.content.feature.event.ItemInteractEvent;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Provides custom sound effects.
 *
 * <p>Supported features:</p>
 * <ul>
 *   <li><b>use_sound</b> — Sound on item use</li>
 *   <li><b>hit_sound</b> — Sound on hit</li>
 *   <li><b>ambient_sound</b> — Ambient sound</li>
 * </ul>
 *
 * <p>Example YAML config:</p>
 * <pre>
 * features:
 *   sound:
 *     id: kalo:sound
 *     arguments:
 *       use_sound: ENTITY_PLAYER_LEVELUP
 *       use_volume: 1.0
 *       use_pitch: 1.0
 * </pre>
 */
public final class SoundFeature extends Feature {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "sound");

    private SoundFeature(@NotNull FeatureFactory.Context context) {
        super(context);
        Map<String, String> args = context.arguments().raw();

        // Handle item use sounds
        context.eventBus().subscribe(ItemInteractEvent.class, event -> {
            Player player = event.player();

            String useSound = args.get("use_sound");
            if (useSound != null) {
                try {
                    Sound sound = Sound.valueOf(useSound.toUpperCase());
                    float volume = Float.parseFloat(args.getOrDefault("use_volume", "1.0"));
                    float pitch = Float.parseFloat(args.getOrDefault("use_pitch", "1.0"));
                    player.playSound(player.getLocation(), sound, volume, pitch);
                } catch (IllegalArgumentException ignored) {}
            }
        });

        Plugins.logger().info("Loaded sound feature for " + context.content().key().asString());
    }

    public static final class Factory implements FeatureFactory<SoundFeature> {
        @Override
        public @NotNull SoundFeature create(@NotNull Context context) {
            return new SoundFeature(context);
        }
    }
}

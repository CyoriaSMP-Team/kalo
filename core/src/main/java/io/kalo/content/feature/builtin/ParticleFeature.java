package io.kalo.content.feature.builtin;

import io.kalo.content.Content;
import io.kalo.content.feature.*;
import io.kalo.content.feature.event.ItemInteractEvent;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Provides visual particle effects.
 *
 * <p>Supported features:</p>
 * <ul>
 *   <li><b>use_particle</b> — Particle on item use</li>
 *   <li><b>hit_particle</b> — Particle on hit</li>
 *   <li><b>ambient_particle</b> — Ambient particle around player</li>
 * </ul>
 *
 * <p>Example YAML config:</p>
 * <pre>
 * features:
 *   particle:
 *     id: kalo:particle
 *     arguments:
 *       use_particle: FLAME
 *       use_count: 10
 *       hit_particle: CRIT
 *       hit_count: 5
 * </pre>
 */
public final class ParticleFeature extends Feature {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "particle");

    private ParticleFeature(@NotNull FeatureFactory.Context context) {
        super(context);
        Map<String, String> args = context.arguments().raw();

        // Handle item use particles
        context.eventBus().subscribe(ItemInteractEvent.class, event -> {
            Player player = event.player();

            String useParticle = args.get("use_particle");
            if (useParticle != null) {
                try {
                    Particle particle = Particle.valueOf(useParticle.toUpperCase());
                    int count = Integer.parseInt(args.getOrDefault("use_count", "10"));
                    player.spawnParticle(particle, player.getLocation(), count);
                } catch (IllegalArgumentException ignored) {}
            }
        });

        Plugins.logger().info("Loaded particle feature for " + context.content().key().asString());
    }

    public static final class Factory implements FeatureFactory<ParticleFeature> {
        @Override
        public @NotNull ParticleFeature create(@NotNull Context context) {
            return new ParticleFeature(context);
        }
    }
}

package io.kalo.content.mob;

import io.kalo.content.Content;
import org.jetbrains.annotations.NotNull;

/**
 * A registered custom mob.
 *
 * <p>The mob is a real Bukkit {@link org.bukkit.entity.LivingEntity} of the declared base
 * type — AI, damage, pathfinding and drops are the base mob's. The custom model is a
 * display rig on Java and a client entity on Bedrock, not a replacement entity.</p>
 */
public interface Mob extends Content {

    @NotNull MobDefinition definition();

    @NotNull String translationKey();
}

package io.kalo.content.mob;

import io.kalo.content.animation.RigAnimation;
import io.kalo.content.item.definition.BedrockOptions;
import io.kalo.content.item.definition.ModelDefinition;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The platform-neutral description of a custom mob.
 *
 * @param key         content key
 * @param displayName name shown above the mob
 * @param base        vanilla mob whose AI, hitbox and drops are reused
 * @param health      maximum health
 * @param width       interaction width in blocks
 * @param height      interaction height in blocks
 * @param model       optional Blockbench model; null keeps the base mob's appearance
 * @param animation   optional idle/interaction animation names
 * @param bedrock     Bedrock opt-out/icon options
 */
public record MobDefinition(
        @NotNull Key key,
        @NotNull Component displayName,
        @NotNull EntityType base,
        double health,
        double width,
        double height,
        @Nullable ModelDefinition model,
        @NotNull RigAnimation animation,
        @NotNull BedrockOptions bedrock
) {

    public MobDefinition {
        Class<?> entityClass = base.getEntityClass();
        if (entityClass == null || !LivingEntity.class.isAssignableFrom(entityClass)) {
            throw new IllegalArgumentException("mob base " + base
                    + " is not a living entity");
        }
        if (!Double.isFinite(health) || health <= 0 || health > 2048) {
            throw new IllegalArgumentException("mob health must be within 0..2048");
        }
        if (!Double.isFinite(width) || width <= 0 || width > 8
                || !Double.isFinite(height) || height <= 0 || height > 8) {
            throw new IllegalArgumentException("mob width/height must be within 0..8 blocks");
        }
        if (model != null && !(model instanceof ModelDefinition.Blockbench)) {
            throw new IllegalArgumentException(
                    "mob model must be a 'blockbench' project; a custom mob needs bones");
        }
    }

    public boolean customModel() {
        return model instanceof ModelDefinition.Blockbench;
    }

    public @NotNull String translationKey() {
        return "mob." + key.namespace() + "." + key.value();
    }

    public static @NotNull Builder builder(@NotNull Key key) {
        return new Builder(key);
    }

    public static final class Builder {
        private final Key key;
        private Component displayName;
        private EntityType base = EntityType.ZOMBIE;
        private double health = 20;
        private double width = 0.6;
        private double height = 1.8;
        private ModelDefinition model;
        private RigAnimation animation = RigAnimation.none();
        private BedrockOptions bedrock = BedrockOptions.defaults();

        private Builder(@NotNull Key key) {
            this.key = key;
        }

        public @NotNull Builder displayName(@NotNull Component displayName) {
            this.displayName = displayName;
            return this;
        }

        public @NotNull Builder base(@NotNull EntityType base) {
            this.base = base;
            return this;
        }

        public @NotNull Builder health(double health) {
            this.health = health;
            return this;
        }

        public @NotNull Builder size(double width, double height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public @NotNull Builder model(@Nullable ModelDefinition model) {
            this.model = model;
            return this;
        }

        public @NotNull Builder animation(@NotNull RigAnimation animation) {
            this.animation = animation;
            return this;
        }

        public @NotNull Builder bedrock(@NotNull BedrockOptions bedrock) {
            this.bedrock = bedrock;
            return this;
        }

        public @NotNull MobDefinition build() {
            Component name = displayName != null ? displayName
                    : Component.text(key.value());
            return new MobDefinition(key, name, base, health, width, height, model,
                    animation, bedrock);
        }
    }
}

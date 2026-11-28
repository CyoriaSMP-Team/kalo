package io.kalo.content.emote;

import io.kalo.content.item.definition.BedrockOptions;
import io.kalo.content.item.definition.ModelDefinition;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A playable emote prop.
 *
 * <p>A prop emote renders an authored model around the player: a wave flag, a dance aura,
 * a held sign. A player-body emote ({@code rig: player}) hides the vanilla body and drives
 * one display per bone instead. Either way the model follows the player for its duration
 * on Java, with a matching client entity on Bedrock.</p>
 *
 * @param key         content key
 * @param displayName name shown in command feedback
 * @param model       Blockbench prop model
 * @param duration    seconds to play; 0 means until the player sneaks
 * @param loop        whether the animation loops for the duration
 * @param scale       size multiplier
 * @param yOffset     vertical offset from the player's feet
 * @param permission  optional permission required to play it
 * @param bedrock     Bedrock opt-out/icon options
 * @param rig         prop around the player or a player-body rig
 * @param skin        optional player-skin texture baked for the body rig
 * @param performers  cast slots; the first is the lead
 */
public record EmoteDefinition(
        @NotNull Key key,
        @NotNull Component displayName,
        @NotNull ModelDefinition model,
        double duration,
        boolean loop,
        double scale,
        double yOffset,
        @Nullable String permission,
        @NotNull BedrockOptions bedrock,
        @NotNull RigMode rig,
        @Nullable net.kyori.adventure.key.Key skin,
        @NotNull java.util.List<Performer> performers
) {

    /** Backward-compatible constructor for a prop emote without an explicit skin. */
    public EmoteDefinition(@NotNull Key key, @NotNull Component displayName,
                           @NotNull ModelDefinition model, double duration, boolean loop,
                           double scale, double yOffset, @Nullable String permission,
                           @NotNull BedrockOptions bedrock) {
        this(key, displayName, model, duration, loop, scale, yOffset, permission,
                bedrock, RigMode.PROP, null, defaultPerformers());
    }

    /** Backward-compatible constructor for a player rig without an explicit cast. */
    public EmoteDefinition(@NotNull Key key, @NotNull Component displayName,
                           @NotNull ModelDefinition model, double duration, boolean loop,
                           double scale, double yOffset, @Nullable String permission,
                           @NotNull BedrockOptions bedrock, @NotNull RigMode rig,
                           @Nullable net.kyori.adventure.key.Key skin) {
        this(key, displayName, model, duration, loop, scale, yOffset, permission,
                bedrock, rig, skin, defaultPerformers());
    }

    /** A cast slot: name plus an offset in the lead's facing frame. */
    public record Performer(@NotNull String name, double x, double y, double z) {
        public Performer {
            if (!Double.isFinite(x) || Math.abs(x) > 8
                    || !Double.isFinite(y) || Math.abs(y) > 8
                    || !Double.isFinite(z) || Math.abs(z) > 8) {
                throw new IllegalArgumentException(
                        "emote performer offsets must be within -8..8");
            }
            if (name.isBlank()) {
                throw new IllegalArgumentException("emote performer name must not be blank");
            }
        }
    }

    public static @NotNull java.util.List<Performer> defaultPerformers() {
        return java.util.List.of(new Performer("self", 0, 0, 0));
    }

    public enum RigMode {
        PROP,
        PLAYER;

        public static @NotNull RigMode parse(@NotNull String value) {
            return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "prop" -> PROP;
                case "player", "body" -> PLAYER;
                default -> throw new IllegalArgumentException(
                        "emote rig must be prop or player, got '" + value + "'");
            };
        }
    }

    public EmoteDefinition {
        if (!(model instanceof ModelDefinition.Blockbench)) {
            throw new IllegalArgumentException(
                    "emote model must be a 'blockbench' project");
        }
        if (!Double.isFinite(duration) || duration < 0 || duration > 600) {
            throw new IllegalArgumentException("emote duration must be within 0..600 seconds");
        }
        if (!Double.isFinite(scale) || scale < 0.125 || scale > 8) {
            throw new IllegalArgumentException("emote scale must be within 0.125..8");
        }
        if (!Double.isFinite(yOffset) || yOffset < -4 || yOffset > 4) {
            throw new IllegalArgumentException("emote y-offset must be within -4..4");
        }
        if (permission != null && permission.isBlank()) {
            permission = null;
        }
        performers = java.util.List.copyOf(performers);
        if (performers.isEmpty() || performers.size() > 8) {
            throw new IllegalArgumentException("emote performers must be within 1..8");
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Performer performer : performers) {
            if (!names.add(performer.name().toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "duplicate emote performer '" + performer.name() + "'");
            }
        }
    }

    public @NotNull String translationKey() {
        return "emote." + key.namespace() + "." + key.value();
    }
}

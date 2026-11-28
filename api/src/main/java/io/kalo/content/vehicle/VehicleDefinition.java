package io.kalo.content.vehicle;

import io.kalo.content.item.definition.BedrockOptions;
import io.kalo.content.item.definition.ModelDefinition;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * A rideable vehicle.
 *
 * <p>The base is a real invisible mob/armour stand with the driver as its passenger; the
 * authored model is a display on Java and a client entity on Bedrock. Movement follows the
 * driver's held keys.</p>
 *
 * @param key        content key
 * @param displayName name used by commands
 * @param base       invisible carrier entity (an armour stand by default)
 * @param model      authored Blockbench model, entity space
 * @param speed      blocks per tick at full throttle
 * @param turnSpeed  degrees per tick
 * @param scale      size multiplier
 * @param yOffset    display offset above the carrier
 * @param seats      seat offsets relative to the carrier; seat zero is the driver
 * @param physics    medium, buoyancy/flight and collision behaviour
 * @param bedrock    Bedrock opt-out/icon options
 */
public record VehicleDefinition(
        @NotNull Key key,
        @NotNull Component displayName,
        @NotNull EntityType base,
        @NotNull ModelDefinition model,
        double speed,
        double turnSpeed,
        double scale,
        double yOffset,
        @NotNull java.util.List<Seat> seats,
        @NotNull Physics physics,
        @NotNull BedrockOptions bedrock
) {

    /** Backward-compatible constructor for a simple row of {@code seatCount} seats. */
    public VehicleDefinition(@NotNull Key key, @NotNull Component displayName,
                             @NotNull EntityType base, @NotNull ModelDefinition model,
                             double speed, double turnSpeed, double scale, double yOffset,
                             int seatCount, @NotNull BedrockOptions bedrock) {
        this(key, displayName, base, model, speed, turnSpeed, scale, yOffset,
                defaultSeats(seatCount), Physics.defaults(), bedrock);
    }

    /**
     * How the carrier moves through its medium.
     *
     * @param medium      land, water or air
     * @param buoyancy    upward velocity while in water, blocks/tick
     * @param gravity     downward velocity when not held up, blocks/tick
     * @param climbSpeed  upward velocity for an air vehicle while jump is held
     * @param collision   stop horizontal motion at a solid block
     */
    public record Physics(
            @NotNull VehicleMedium medium,
            double buoyancy,
            double gravity,
            double climbSpeed,
            boolean collision
    ) {
        public Physics {
            if (!Double.isFinite(buoyancy) || buoyancy < 0 || buoyancy > 1) {
                throw new IllegalArgumentException("vehicle buoyancy must be within 0..1");
            }
            if (!Double.isFinite(gravity) || gravity < 0 || gravity > 1) {
                throw new IllegalArgumentException("vehicle gravity must be within 0..1");
            }
            if (!Double.isFinite(climbSpeed) || climbSpeed < 0 || climbSpeed > 1) {
                throw new IllegalArgumentException("vehicle climb-speed must be within 0..1");
            }
        }

        public static @NotNull Physics defaults() {
            return new Physics(VehicleMedium.LAND, 0.06, 0.02, 0.25, true);
        }
    }

    /**
     * One rideable position, in the carrier's local frame: X is the wearer's right,
     * Y is up, Z is forward. The runtime rotates it by the carrier's yaw.
     */
    public record Seat(double x, double y, double z) {
        public Seat {
            if (!Double.isFinite(x) || Math.abs(x) > 8
                    || !Double.isFinite(y) || Math.abs(y) > 8
                    || !Double.isFinite(z) || Math.abs(z) > 8) {
                throw new IllegalArgumentException(
                        "vehicle seat offsets must be within -8..8");
            }
        }
    }

    /** A centred row of seats, 0.7 blocks apart, the first one the driver. */
    public static @NotNull java.util.List<Seat> defaultSeats(int count) {
        if (count < 1 || count > 8) {
            throw new IllegalArgumentException("vehicle seats must be within 1..8");
        }
        java.util.List<Seat> result = new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double x = (index - (count - 1) / 2.0) * 0.7;
            result.add(new Seat(x, 0.2, 0.0));
        }
        return java.util.List.copyOf(result);
    }

    public VehicleDefinition {
        Class<?> entityClass = base.getEntityClass();
        if (entityClass == null || !LivingEntity.class.isAssignableFrom(entityClass)
                || !base.isSpawnable() || base == EntityType.PLAYER) {
            throw new IllegalArgumentException(
                    "vehicle base " + base + " is not a spawnable living entity");
        }
        if (!(model instanceof ModelDefinition.Blockbench)) {
            throw new IllegalArgumentException(
                    "vehicle model must be a 'blockbench' project");
        }
        if (!Double.isFinite(speed) || speed <= 0 || speed > 4) {
            throw new IllegalArgumentException("vehicle speed must be within 0..4 blocks/tick");
        }
        if (!Double.isFinite(turnSpeed) || turnSpeed <= 0 || turnSpeed > 90) {
            throw new IllegalArgumentException("vehicle turn-speed must be within 0..90 deg/tick");
        }
        if (!Double.isFinite(scale) || scale < 0.125 || scale > 8) {
            throw new IllegalArgumentException("vehicle scale must be within 0.125..8");
        }
        if (!Double.isFinite(yOffset) || yOffset < -4 || yOffset > 4) {
            throw new IllegalArgumentException("vehicle y-offset must be within -4..4");
        }
        seats = java.util.List.copyOf(seats);
        if (seats.isEmpty() || seats.size() > 8) {
            throw new IllegalArgumentException("vehicle seats must be within 1..8");
        }
    }

    public @NotNull String translationKey() {
        return "vehicle." + key.namespace() + "." + key.value();
    }
}

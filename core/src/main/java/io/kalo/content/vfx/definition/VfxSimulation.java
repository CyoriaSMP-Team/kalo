package io.kalo.content.vfx.definition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic sprite simulation for one cast.
 *
 * <p>It owns no Bukkit types so the shape, lifetime and loop-independent behaviour can be
 * tested without a server. World-space motes are positioned from the cast origin; camera
 * motes are positioned from the caster's eye and look direction. {@code VfxRuntime} turns
 * each {@link Sprite} into an {@code ItemDisplay}.</p>
 */
public final class VfxSimulation {

    /**
     * The caster's eye position and view angles for camera-space layers.
     *
     * <p>Yaw follows Minecraft's convention (0 is south, 90 is west) and pitch is positive
     * when looking down.</p>
     */
    public record CameraBasis(double x, double y, double z, double yaw, double pitch) {
    }

    public record Sprite(int moteId, int layerIndex, double x, double y, double z,
                         double size) {
    }

    public record Mote(int id, int layerIndex, int spawnTick, int lifetime,
                       double offsetX, double offsetY, double offsetZ,
                       double velocityX, double velocityY, double velocityZ,
                       double size, double gravity, boolean camera, double shake) {
    }

    private final VfxRecipe recipe;
    private final VfxRecipe.Vec3 origin;
    private final double yaw;
    private final List<Mote> motes;

    public VfxSimulation(@NotNull VfxRecipe recipe, @NotNull VfxRecipe.Vec3 origin,
                         double yaw, long seed) {
        this.recipe = recipe;
        this.origin = origin;
        this.yaw = yaw;
        this.motes = build(recipe, seed);
    }

    public @NotNull VfxRecipe recipe() {
        return recipe;
    }

    public @NotNull List<Mote> motes() {
        return motes;
    }

    public int durationTicks() {
        return recipe.duration();
    }

    public int maxSprites() {
        return motes.size();
    }

    public @NotNull List<Sprite> frame(int tick) {
        return frame(tick, 0, 0, Integer.MAX_VALUE, origin, yaw, 1.0, null);
    }

    /**
     * One frame of a loop cycle. Motes spawned outside the section are excluded, and the
     * cycle is part of the sprite id so a new loop cycle spawns fresh displays instead of
     * teleporting the previous cycle's sprites back to the start.
     */
    public @NotNull List<Sprite> frame(int tick, long cycle, int fromSpawn, int toSpawn) {
        return frame(tick, cycle, fromSpawn, toSpawn, origin, yaw, 1.0, null);
    }

    /**
     * Frame with a cast-time origin, turn and scale. {@code follow} casts recompute the
     * origin/yaw every tick without rebuilding the mote distribution.
     */
    public @NotNull List<Sprite> frame(int tick, long cycle, int fromSpawn, int toSpawn,
                                       @NotNull VfxRecipe.Vec3 frameOrigin, double frameYaw,
                                       double scale) {
        return frame(tick, cycle, fromSpawn, toSpawn, frameOrigin, frameYaw, scale, null);
    }

    /**
     * Frame including the caster's eye basis for camera layers. Camera motes are skipped
     * when {@code camera} is null, which keeps the pure world-space overloads unchanged.
     */
    public @NotNull List<Sprite> frame(int tick, long cycle, int fromSpawn, int toSpawn,
                                       @NotNull VfxRecipe.Vec3 frameOrigin, double frameYaw,
                                       double scale, @Nullable CameraBasis camera) {
        if (tick < 0) {
            return List.of();
        }
        int stride = motes.size() + 1;
        double radians = Math.toRadians(frameYaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        CameraAxes axes = camera == null ? null : new CameraAxes(camera);
        List<Sprite> sprites = new ArrayList<>();
        for (Mote mote : motes) {
            if (mote.spawnTick() < fromSpawn || mote.spawnTick() >= toSpawn) {
                continue;
            }
            int age = tick - mote.spawnTick();
            if (age < 0 || age >= mote.lifetime()) {
                continue;
            }
            if (mote.camera()) {
                if (axes == null) {
                    continue;
                }
                double offsetX = mote.offsetX() * scale;
                double offsetY = mote.offsetY() * scale;
                double offsetZ = mote.offsetZ() * scale;
                double velocityX = mote.velocityX() * scale;
                double velocityY = mote.velocityY() * scale;
                double velocityZ = mote.velocityZ() * scale;
                double shakeX = mote.shake() * Math.sin((tick + mote.id() * 3.1) * 2.1);
                double shakeY = mote.shake() * Math.cos((tick + mote.id() * 5.7) * 1.7);
                double x = offsetX + velocityX * age + shakeX;
                double y = offsetY + velocityY * age
                        + 0.5 * mote.gravity() * scale * age * age + shakeY;
                double z = offsetZ + velocityZ * age;
                sprites.add(new Sprite((int) (mote.id() + cycle * (long) stride),
                        mote.layerIndex(),
                        axes.x + axes.rightX * x + axes.upX * y + axes.forwardX * z,
                        axes.y + axes.rightY * x + axes.upY * y + axes.forwardY * z,
                        axes.z + axes.rightZ * x + axes.upZ * y + axes.forwardZ * z,
                        mote.size() * scale));
                continue;
            }
            double offsetX = mote.offsetX() * scale;
            double offsetY = mote.offsetY() * scale;
            double offsetZ = mote.offsetZ() * scale;
            double velocityX = mote.velocityX() * scale;
            double velocityY = mote.velocityY() * scale;
            double velocityZ = mote.velocityZ() * scale;
            double x = frameOrigin.x() + rotateX(offsetX, offsetZ, cos, sin)
                    + rotateX(velocityX, velocityZ, cos, sin) * age;
            double y = frameOrigin.y() + offsetY + velocityY * age
                    + 0.5 * mote.gravity() * scale * age * age;
            double z = frameOrigin.z() + rotateZ(offsetX, offsetZ, cos, sin)
                    + rotateZ(velocityX, velocityZ, cos, sin) * age;
            sprites.add(new Sprite((int) (mote.id() + cycle * (long) stride),
                    mote.layerIndex(), x, y, z, mote.size() * scale));
        }
        return List.copyOf(sprites);
    }

    private static double rotateX(double x, double z, double cos, double sin) {
        return x * cos - z * sin;
    }

    private static double rotateZ(double x, double z, double cos, double sin) {
        return x * sin + z * cos;
    }

    private static @NotNull List<Mote> build(@NotNull VfxRecipe recipe, long seed) {
        Random random = new Random(seed);
        List<Mote> motes = new ArrayList<>();
        int id = 0;
        for (int layerIndex = 0; layerIndex < recipe.layers().size(); layerIndex++) {
            VfxRecipe.Layer layer = recipe.layers().get(layerIndex);
            int window = Math.max(1, layer.end() - layer.start());
            for (int index = 0; index < layer.burst(); index++) {
                Sample sample = sample(layer.shape(), layer.radius(), random);
                double speed = layer.speedMin()
                        + random.nextDouble() * (layer.speedMax() - layer.speedMin());
                int spawnTick = layer.start() + random.nextInt(window);
                motes.add(new Mote(id++, layerIndex, spawnTick, layer.lifetime(),
                        layer.at().x() + sample.offsetX(),
                        layer.at().y() + sample.offsetY(),
                        layer.at().z() + sample.offsetZ(),
                        sample.directionX() * speed,
                        sample.directionY() * speed,
                        sample.directionZ() * speed,
                        layer.size(), layer.gravity(), false, 0));
            }
        }
        for (int cameraIndex = 0; cameraIndex < recipe.cameras().size(); cameraIndex++) {
            VfxRecipe.Camera camera = recipe.cameras().get(cameraIndex);
            VfxRecipe.Layer layer = camera.layer();
            int window = Math.max(1, layer.end() - layer.start());
            for (int index = 0; index < layer.burst(); index++) {
                Sample sample = sample(layer.shape(), layer.radius(), random);
                double speed = layer.speedMin()
                        + random.nextDouble() * (layer.speedMax() - layer.speedMin());
                int spawnTick = layer.start() + random.nextInt(window);
                motes.add(new Mote(id++, VfxDefinition.cameraLayerId(cameraIndex),
                        spawnTick, layer.lifetime(),
                        layer.at().x() + sample.offsetX(),
                        layer.at().y() + sample.offsetY(),
                        layer.at().z() + camera.distance() + sample.offsetZ(),
                        sample.directionX() * speed,
                        sample.directionY() * speed,
                        sample.directionZ() * speed,
                        layer.size(), layer.gravity(), true, camera.shake()));
            }
        }
        return List.copyOf(motes);
    }

    /** Precomputed orthonormal camera axes for one frame. */
    private static final class CameraAxes {
        private final double x;
        private final double y;
        private final double z;
        private final double forwardX;
        private final double forwardY;
        private final double forwardZ;
        private final double rightX;
        private final double rightY;
        private final double rightZ;
        private final double upX;
        private final double upY;
        private final double upZ;

        private CameraAxes(@NotNull CameraBasis camera) {
            this.x = camera.x();
            this.y = camera.y();
            this.z = camera.z();
            double yawRadians = Math.toRadians(camera.yaw());
            double pitchRadians = Math.toRadians(camera.pitch());
            double cosPitch = Math.cos(pitchRadians);
            forwardX = -Math.sin(yawRadians) * cosPitch;
            forwardY = -Math.sin(pitchRadians);
            forwardZ = Math.cos(yawRadians) * cosPitch;
            rightX = -Math.cos(yawRadians);
            rightY = 0;
            rightZ = -Math.sin(yawRadians);
            upX = rightY * forwardZ - rightZ * forwardY;
            upY = rightZ * forwardX - rightX * forwardZ;
            upZ = rightX * forwardY - rightY * forwardX;
        }
    }

    private record Sample(double offsetX, double offsetY, double offsetZ,
                          double directionX, double directionY, double directionZ) {
    }

    private static @NotNull Sample sample(@NotNull VfxRecipe.Shape shape, double radius,
                                          @NotNull Random random) {
        double angle = random.nextDouble() * Math.PI * 2;
        double u = random.nextDouble();
        double x;
        double y;
        double z;
        switch (shape) {
            case SPHERE -> {
                z = random.nextDouble() * 2 - 1;
                double ring = Math.sqrt(Math.max(0, 1 - z * z));
                double distance = Math.cbrt(random.nextDouble()) * radius;
                x = Math.cos(angle) * ring * distance;
                y = Math.sin(angle) * ring * distance;
                z *= distance;
            }
            case RING -> {
                x = Math.cos(angle) * radius;
                y = (random.nextDouble() - 0.5) * radius * 0.08;
                z = Math.sin(angle) * radius;
            }
            case DISC -> {
                double distance = Math.sqrt(u) * radius;
                x = Math.cos(angle) * distance;
                y = 0;
                z = Math.sin(angle) * distance;
            }
            case LINE -> {
                x = (random.nextDouble() * 2 - 1) * radius;
                y = (random.nextDouble() - 0.5) * radius * 0.08;
                z = (random.nextDouble() - 0.5) * radius * 0.08;
            }
            case CONE -> {
                double distance = Math.sqrt(u) * radius;
                x = Math.cos(angle) * distance;
                y = -radius + random.nextDouble() * radius * 2;
                z = Math.sin(angle) * distance;
            }
            default -> throw new IllegalStateException("unhandled VFX shape " + shape);
        }
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1e-6) {
            return new Sample(x, y, z, 0, 1, 0);
        }
        return new Sample(x, y, z, x / length, y / length, z / length);
    }
}

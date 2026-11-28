package io.kalo.content.vfx.definition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Maps playback time to recipe time for one shot and loop casts.
 *
 * <p>A looping recipe with a loop section plays its build-up once, repeats the section,
 * and plays the section after it as a fade when stopped. A recipe without a loop section
 * repeats the whole timeline and cuts immediately when stopped.</p>
 */
public final class VfxPlayback {

    public record Frame(int recipeTick, long cycle, int fromSpawn, int toSpawn) {
    }

    private final VfxRecipe recipe;
    private final boolean looping;
    private final long cycleLimit;
    private final double speed;
    private final long loopTicks;
    private double position;
    private long lastCycle;
    private long playbackTicks;
    private boolean stopping;

    public VfxPlayback(@NotNull VfxRecipe recipe) {
        this(recipe, false, 0, 1.0);
    }

    public VfxPlayback(@NotNull VfxRecipe recipe, boolean looping, long cycleLimit) {
        this(recipe, looping, cycleLimit, 1.0);
    }

    public VfxPlayback(@NotNull VfxRecipe recipe, boolean looping, long cycleLimit,
                       double speed) {
        this(recipe, looping, cycleLimit, speed, 0);
    }

    public VfxPlayback(@NotNull VfxRecipe recipe, boolean looping, long cycleLimit,
                       double speed, long loopTicks) {
        if (speed < 0.1 || speed > 10) {
            throw new IllegalArgumentException("VFX playback speed must be within 0.1..10");
        }
        if (loopTicks < 0) {
            throw new IllegalArgumentException("VFX loop time must be >= 0");
        }
        this.recipe = recipe;
        this.looping = looping;
        this.cycleLimit = Math.max(0, cycleLimit);
        this.speed = speed;
        this.loopTicks = loopTicks;
    }

    public boolean looping() {
        return looping;
    }

    /** @return the next frame, or null when the cast has fully finished */
    public @Nullable Frame next() {
        if (looping && !stopping && loopTicks > 0 && playbackTicks >= loopTicks) {
            VfxRecipe.Loop loop = recipe.loop();
            stopping = true;
            if (loop == null || loop.end() >= recipe.duration()) {
                return null;
            }
            position = loop.end();
        }
        if (!looping) {
            if (position >= recipe.duration()) {
                return null;
            }
            int tick = (int) Math.floor(position);
            position += speed;
            playbackTicks++;
            return new Frame(tick, 0, 0, recipe.duration());
        }

        VfxRecipe.Loop loop = recipe.loop();
        if (stopping) {
            int fadeStart = loop == null ? recipe.duration() : loop.end();
            if (position < fadeStart) {
                position = fadeStart;
            }
            if (position >= recipe.duration()) {
                return null;
            }
            int tick = (int) Math.floor(position);
            position += speed;
            playbackTicks++;
            return new Frame(tick, lastCycle + 1, fadeStart, recipe.duration());
        }

        if (loop != null) {
            if (position < loop.start()) {
                int tick = (int) Math.floor(position);
                position += speed;
                playbackTicks++;
                lastCycle = 0;
                return new Frame(tick, 0, 0, loop.start());
            }
            long length = loop.end() - loop.start();
            double offset = position - loop.start();
            long cycleIndex = (long) Math.floor(offset / length);
            if (cycleLimit > 0 && cycleIndex >= cycleLimit) {
                stopping = true;
                position = loop.end();
                return next();
            }
            long cycle = cycleIndex + 1;
            int tick = loop.start() + (int) Math.floor(offset % length);
            position += speed;
            playbackTicks++;
            lastCycle = cycle;
            return new Frame(tick, cycle, loop.start(), loop.end());
        }

        long cycle = (long) Math.floor(position / recipe.duration());
        if (cycleLimit > 0 && cycle >= cycleLimit) {
            stopping = true;
            return null;
        }
        int tick = (int) Math.floor(position % recipe.duration());
        position += speed;
        playbackTicks++;
        lastCycle = cycle;
        return new Frame(tick, cycle, 0, recipe.duration());
    }

    /**
     * Requests a graceful stop.
     *
     * @return whether a fade remains; false means the caller should remove the cast now
     */
    public boolean requestStop() {
        if (!looping) {
            return false;
        }
        if (stopping) {
            return true;
        }
        VfxRecipe.Loop loop = recipe.loop();
        if (loop == null || loop.end() >= recipe.duration()) {
            return false;
        }
        stopping = true;
        position = loop.end();
        return true;
    }
}

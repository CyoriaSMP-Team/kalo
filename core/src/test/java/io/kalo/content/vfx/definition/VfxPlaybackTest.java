package io.kalo.content.vfx.definition;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxPlaybackTest {

    private static VfxRecipe recipe(int duration, VfxRecipe.Loop loop) {
        return new VfxRecipe(duration, loop, List.of(
                new VfxRecipe.Layer("glow", 0, duration, 4, VfxRecipe.Vec3.ZERO,
                        VfxRecipe.Shape.SPHERE, 1, 0.1, 0.2, 0.3, 10, 0,
                        0xFFFFFF)), List.of());
    }

    @Test
    void oneShotPlaysEveryTickOnce() {
        VfxPlayback playback = new VfxPlayback(recipe(5, null));
        for (int tick = 0; tick < 5; tick++) {
            VfxPlayback.Frame frame = playback.next();
            assertEquals(tick, frame.recipeTick());
            assertEquals(0, frame.cycle());
        }
        assertNull(playback.next());
        assertFalse(playback.requestStop());
    }

    @Test
    void aLoopSectionRepeatsAndAFadeRunsAfterStop() {
        VfxPlayback playback = new VfxPlayback(recipe(20, new VfxRecipe.Loop(5, 10)),
                true, 0);

        for (int tick = 0; tick < 5; tick++) {
            VfxPlayback.Frame frame = playback.next();
            assertEquals(tick, frame.recipeTick());
            assertEquals(0, frame.cycle());
        }
        for (int cycle = 1; cycle <= 2; cycle++) {
            for (int tick = 5; tick < 10; tick++) {
                VfxPlayback.Frame frame = playback.next();
                assertEquals(tick, frame.recipeTick());
                assertEquals(cycle, frame.cycle());
                assertEquals(5, frame.fromSpawn());
                assertEquals(10, frame.toSpawn());
            }
        }

        assertTrue(playback.requestStop());
        for (int tick = 10; tick < 20; tick++) {
            VfxPlayback.Frame frame = playback.next();
            assertEquals(tick, frame.recipeTick());
            assertEquals(3, frame.cycle());
            assertEquals(10, frame.fromSpawn());
        }
        assertNull(playback.next());
    }

    @Test
    void aCycleLimitStopsAfterTheRequestedLoops() {
        VfxPlayback playback = new VfxPlayback(recipe(20, new VfxRecipe.Loop(5, 10)),
                true, 2);
        int frames = 0;
        while (playback.next() != null) {
            frames++;
            if (frames > 100) {
                throw new AssertionError("loop did not terminate");
            }
        }
        assertEquals(5 + 10 + 10, frames,
                "build-up, two loop cycles, then the fade section");
    }

    @Test
    void aTimeLimitedLoopStopsAtTheLimitAndRunsItsFade() {
        VfxPlayback playback = new VfxPlayback(recipe(20, new VfxRecipe.Loop(5, 10)),
                true, 0, 1.0, 15);
        int frames = 0;
        while (playback.next() != null) {
            frames++;
            if (frames > 100) {
                throw new AssertionError("time-limited loop did not terminate");
            }
        }
        assertEquals(25, frames, "build-up + 15 playback ticks + 10 fade ticks");
    }

    @Test
    void speedControlsHowManyRecipeTicksAdvancePerFrame() {
        VfxPlayback slow = new VfxPlayback(recipe(3, null), false, 0, 0.5);
        assertEquals(0, slow.next().recipeTick());
        assertEquals(0, slow.next().recipeTick());
        assertEquals(1, slow.next().recipeTick());
        assertEquals(1, slow.next().recipeTick());

        VfxPlayback fast = new VfxPlayback(recipe(5, null), false, 0, 2.0);
        assertEquals(0, fast.next().recipeTick());
        assertEquals(2, fast.next().recipeTick());
        assertEquals(4, fast.next().recipeTick());
        assertNull(fast.next());
    }

    @Test
    void aRecipeWithoutALoopSectionRepeatsTheWholeTimelineAndCuts() {
        VfxPlayback playback = new VfxPlayback(recipe(3, null), true, 0);
        assertEquals(0, playback.next().recipeTick());
        assertEquals(1, playback.next().recipeTick());
        assertEquals(2, playback.next().recipeTick());
        VfxPlayback.Frame restarted = playback.next();
        assertEquals(0, restarted.recipeTick());
        assertEquals(1, restarted.cycle());
        assertFalse(playback.requestStop());
    }
}

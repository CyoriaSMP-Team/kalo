package io.kalo.content.vfx.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxCastOptionsTest {

    @Test
    void loopSoundVolumeTagAndEndOptionsParse() {
        VfxCastOptions options = VfxCastOptions.parse(
                "loop:3x quiet volume:0.5 tag:arena end:cut");

        assertTrue(options.loop());
        assertEquals(3, options.cycles());
        assertTrue(options.quiet());
        assertEquals(0.5, options.volume(), 1e-9);
        assertEquals("arena", options.tag());
        assertFalse(options.finishOnStop());
        assertEquals("arena", options.tagOr("fallback"));
    }

    @Test
    void movementScaleDelaySeedAndAudienceOptionsParse() {
        VfxCastOptions options = VfxCastOptions.parse(
                "speed:2 scale:1.5 follow offset:1,-2,3 turn:45 delay:1.5s seed:42 only:caster");

        assertEquals(2.0, options.speed(), 1e-9);
        assertEquals(1.5, options.scale(), 1e-9);
        assertTrue(options.follow());
        assertEquals(1.0, options.offset().x(), 1e-9);
        assertEquals(-2.0, options.offset().y(), 1e-9);
        assertEquals(3.0, options.offset().z(), 1e-9);
        assertEquals(45.0, options.turn(), 1e-9);
        assertEquals(30, options.delayTicks());
        assertEquals(42L, options.seed());
        assertTrue(options.onlyCaster());
    }

    @Test
    void loopTimeAndTrailingConditionsParse() {
        VfxCastOptions timed = VfxCastOptions.parse(
                "loop:30s check:5t until:{health} < 6");
        assertTrue(timed.loop());
        assertEquals(600, timed.loopTicks());
        assertEquals(5, timed.checkTicks());
        assertTrue(timed.until() != null);
        assertTrue(timed.until().evaluate(new VfxCondition.Context() {
            @Override
            public String value(String name) {
                return name.equals("health") ? "5" : "";
            }

            @Override
            public boolean permission(String node) {
                return false;
            }
        }));

        VfxCastOptions whileCast = VfxCastOptions.parse(
                "loop:5m while:{state} == \"running\"");
        assertEquals(6000, whileCast.loopTicks());
        assertTrue(whileCast.whileCondition() != null);
    }

    @Test
    void defaultsAndInvalidOptions() {
        assertFalse(VfxCastOptions.ONCE.loop());
        assertEquals("fallback", VfxCastOptions.ONCE.tagOr("fallback"));
        assertThrows(IllegalArgumentException.class,
                () -> VfxCastOptions.parse("loop:nope"));
        assertThrows(IllegalArgumentException.class,
                () -> VfxCastOptions.parse("volume:9"));
        assertThrows(IllegalArgumentException.class,
                () -> VfxCastOptions.parse("nonsense"));
        assertThrows(IllegalArgumentException.class,
                () -> VfxCastOptions.parse("speed:0"));
        assertThrows(IllegalArgumentException.class,
                () -> VfxCastOptions.parse("offset:1,2"));
    }
}

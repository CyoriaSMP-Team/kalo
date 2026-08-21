package io.kalo.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Only two pack_format numbers have been read out of a real client jar. The rule for
 * everything else is to guess low within the same family, not high: a client refuses a
 * pack_format above its own outright, while an older one loads with a warning.
 */
class PackFormatsTest {

    @Test
    void theVerifiedNumbersAreTheOnesFromVersionJson() {
        assertEquals(46, PackFormats.MC_1_21_4);
        assertEquals(88, PackFormats.MC_26_2);
        assertEquals(PackFormats.MC_26_2, PackFormats.CURRENT);
    }

    /**
     * resolve() reads the running server, and there is none here — the compilers have to
     * stay runnable outside one, so it must answer rather than throw.
     */
    @Test
    void resolvingWithoutAServerFallsBackInsteadOfThrowing() {
        assertEquals(PackFormats.CURRENT, PackFormats.resolve());
    }
}

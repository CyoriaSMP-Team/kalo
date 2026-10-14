package io.kalo.pack;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PackFormatsTest {
    @Test void usesMojangVerifiedResourceFormats() {
        Map<String, Integer> verified = Map.ofEntries(
                Map.entry("1.21.4", 46),
                Map.entry("1.21.5", 55),
                Map.entry("1.21.6", 63),
                Map.entry("1.21.7", 64),
                Map.entry("1.21.8", 64),
                Map.entry("1.21.9", 69),
                Map.entry("1.21.10", 69),
                Map.entry("1.21.11", 75),
                Map.entry("26.1", 84),
                Map.entry("26.2", 88)
        );
        verified.forEach((version, format) ->
                assertEquals(format, PackFormats.resolveForVersion(version), version));
    }

    @Test void compilersStillWorkOutsideARealServer() {
        assertEquals(PackFormats.CURRENT, PackFormats.resolve());
        assertEquals(46, PackFormats.MC_1_21_4);
        assertEquals(75, PackFormats.MC_1_21_11);
        assertEquals(88, PackFormats.MC_26_2);
    }

    @Test void handlesServerSuffixWithoutTreatingPatchAsVerified() {
        assertEquals(55, PackFormats.resolveForVersion("1.21.5-R0.1"));
        assertEquals(88, PackFormats.resolveForVersion("26.2-Paper"));
        assertEquals(88, PackFormats.resolveForVersion("26.3"));
    }
}

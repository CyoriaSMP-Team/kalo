package io.kalo.content.emote;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmoteCastTest {

    @Test
    void castNamesSplitOnCommasAndWhitespace() {
        assertEquals(List.of("Alex", "Steve"), EmoteCast.tokens("Alex, Steve"));
        assertEquals(List.of("Alex", "Steve"), EmoteCast.tokens(" Alex  Steve "));
        assertTrue(EmoteCast.tokens(null).isEmpty());
    }

    @Test
    void theRequiredCastSizeIsEnforced() {
        assertNull(EmoteCast.validate(1, List.of("Alex"), "Steve"));
        assertTrue(EmoteCast.validate(1, List.of(), "Steve")
                .contains("needs 1"));
        assertTrue(EmoteCast.validate(1, List.of("Alex", "Herobrine"), "Steve")
                .contains("needs 1"));
    }

    @Test
    void duplicateAndLeadNamesAreRejected() {
        assertTrue(EmoteCast.validate(1, List.of("Steve"), "Steve")
                .contains("lead"));
        assertTrue(EmoteCast.validate(2, List.of("Alex", "alex"), "Steve")
                .contains("twice"));
    }
}

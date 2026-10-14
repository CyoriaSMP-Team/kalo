package io.kalo.content.feature.builtin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class AnimationFramesTest {
    @Test void parsesListAndDelay() {
        AnimationFrames result = AnimationFrames.parse("[1, 5, 12]", "4");
        assertEquals(List.of(1, 5, 12), result.frames());
        assertEquals(4, result.delayTicks());
    }
    @Test void rejectsInvalidOrUnboundedSequences() {
        assertThrows(IllegalArgumentException.class, () -> AnimationFrames.parse("", "5"));
        assertThrows(IllegalArgumentException.class, () -> AnimationFrames.parse("[1,,3]", "5"));
        assertThrows(IllegalArgumentException.class, () -> AnimationFrames.parse("[1,2]", "0"));
        assertThrows(IllegalArgumentException.class, () -> AnimationFrames.parse("[1,-2]", "5"));
        assertThrows(IllegalArgumentException.class, () -> AnimationFrames.parse("[1]", "500"));
    }
}

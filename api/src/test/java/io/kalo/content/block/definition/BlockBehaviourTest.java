package io.kalo.content.block.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlockBehaviourTest {

    @Test
    void hardnessMustBeFinite() {
        assertThrows(IllegalArgumentException.class,
                () -> new BlockBehaviour(Float.NaN, true));
        assertThrows(IllegalArgumentException.class,
                () -> new BlockBehaviour(Float.POSITIVE_INFINITY, true));
        assertThrows(IllegalArgumentException.class,
                () -> new BlockBehaviour(Float.NEGATIVE_INFINITY, true));
        assertDoesNotThrow(() -> new BlockBehaviour(-1f, true));
        assertDoesNotThrow(() -> new BlockBehaviour(0f, false));
    }
}

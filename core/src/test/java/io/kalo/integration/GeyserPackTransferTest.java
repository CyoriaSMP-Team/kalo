package io.kalo.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reconnect is the only way an already-playing Bedrock session picks up a replaced
 * pack, so the decision has to be exact: never bounce a session that already holds the
 * current revision, and never skip one that holds different bytes.
 */
class GeyserPackTransferTest {

    @Test
    void aSessionWithDifferentBytesIsReconnected() {
        assertTrue(GeyserBedrockSupport.needsReconnect("old", "new", true));
        assertTrue(GeyserBedrockSupport.needsReconnect(null, "new", true),
                "a session that predates staging holds a stale pack as well");
    }

    @Test
    void aSessionAlreadyServingTheCurrentRevisionIsLeftAlone() {
        assertFalse(GeyserBedrockSupport.needsReconnect("new", "new", true));
    }

    @Test
    void theTransferSwitchDisablesReconnectsWithoutDisablingStaging() {
        assertFalse(GeyserBedrockSupport.needsReconnect("old", "new", false));
        assertFalse(GeyserBedrockSupport.needsReconnect(null, "new", false));
    }
}

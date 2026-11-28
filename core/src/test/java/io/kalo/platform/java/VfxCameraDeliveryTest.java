package io.kalo.platform.java;

import io.kalo.content.vfx.definition.VfxCameraFrame;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VfxCameraDeliveryTest {

    @Test
    void theSeamReceivesThePlannedPoseAndClear() {
        RecordingTransport transport = new RecordingTransport();
        UUID playerId = UUID.randomUUID();
        VfxCameraFrame frame = new VfxCameraFrame(1, 2, 3, 45, 10, 55);

        VfxCameraDelivery.apply(transport, playerId, frame);

        assertEquals(playerId, transport.applied);
        assertEquals(frame, transport.frame);
        assertEquals(UUID.class, transport.applied.getClass());

        VfxCameraDelivery.clear(transport, playerId);

        assertEquals(playerId, transport.cleared);
    }

    @Test
    void theNoOpTransportAndFactoryAreHarmless() {
        assertFalse(VfxCameraTransport.NONE.available());
        assertDoesNotThrow(() -> VfxCameraTransport.NONE.apply(UUID.randomUUID(),
                new VfxCameraFrame(0, 0, 0, 0, 0, 70)));
        assertDoesNotThrow(() -> VfxCameraTransport.NONE.clear(UUID.randomUUID()));
        VfxCameraTransport created = VfxCameraTransports.create();
        assertNotNull(created);
        assertFalse(created.available(),
                "without server internals the factory must return the no-op transport");
    }

    private static final class RecordingTransport implements VfxCameraTransport {
        private UUID applied;
        private UUID cleared;
        private VfxCameraFrame frame;

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public void apply(@NotNull UUID playerId, @NotNull VfxCameraFrame frame) {
            this.applied = playerId;
            this.frame = frame;
        }

        @Override
        public void clear(@NotNull UUID playerId) {
            this.cleared = playerId;
        }
    }
}

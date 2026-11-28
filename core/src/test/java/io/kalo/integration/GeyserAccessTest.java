package io.kalo.integration;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the proxy deployment shape: Floodgate present, Geyser absent.
 *
 * <p>The first shim called {@code GeyserApi.api()} directly, so every inventory open on a
 * proxy-backed server threw {@code NoClassDefFoundError}. That error is not an exception,
 * so callers could not catch it. These cases fail if the class ever links Geyser again.</p>
 */
class GeyserAccessTest {

    @Test
    void missingLoaderIsNotBedrock() {
        assertFalse(GeyserAccess.isBedrock(null, UUID.randomUUID()));
    }

    @Test
    void loaderWithoutGeyserIsNotBedrock() {
        assertFalse(GeyserAccess.isBedrock(
                GeyserAccessTest.class.getClassLoader(), UUID.randomUUID()));
    }
}

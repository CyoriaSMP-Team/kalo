package io.kalo.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BedrockFormsIntegrationTest {

    @Test
    void javaOnlyServersDoNotSelectAFormProvider() {
        assertNull(BedrockFormsIntegration.provider(false, false));
    }

    @Test
    void floodgateIsPreferredWhenBothArePresent() {
        assertEquals("floodgate", BedrockFormsIntegration.provider(true, true));
        assertEquals("floodgate", BedrockFormsIntegration.provider(true, false));
    }

    @Test
    void aGeyserBackendCanStillProvideForms() {
        assertEquals("Geyser-Spigot", BedrockFormsIntegration.provider(false, true));
    }
}

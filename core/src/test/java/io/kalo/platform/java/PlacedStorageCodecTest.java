package io.kalo.platform.java;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlacedStorageCodecTest {

    @Test
    void anEmptyContainerStillProducesAValidPayload() {
        String encoded = PlacedStorageCodec.encode(new ItemStack[27]);
        ItemStack[] decoded = PlacedStorageCodec.decode(encoded, 27);
        assertNotNull(decoded);
        assertEquals(27, decoded.length);
    }

    @Test
    void malformedPayloadFailsClosedToAnEmptyContainer() {
        assertNull(PlacedStorageCodec.decode("not base64", 27));
        assertNull(PlacedStorageCodec.decode(null, 27));
        assertNotNull(PlacedStorageCodec.decode("", 27));
    }
}

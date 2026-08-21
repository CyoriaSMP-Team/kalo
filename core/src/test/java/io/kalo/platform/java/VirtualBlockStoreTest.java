package io.kalo.platform.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualBlockStoreTest {

    @Test
    void coordinatesRoundTripIncludingNegativeHeight() {
        long packed = VirtualBlockStore.localPosition(-17, -64, 31);
        assertEquals(15, VirtualBlockStore.localX(packed));
        assertEquals(-64, VirtualBlockStore.localY(packed));
        assertEquals(15, VirtualBlockStore.localZ(packed));
    }

    @Test
    void aChunkViewOnlyContainsThatChunk() {
        VirtualBlockStore store = new VirtualBlockStore();
        UUID world = UUID.randomUUID();
        store.put(world, 0, 70, 0, "test:ruby");
        store.put(world, 15, 71, 15, "test:ruby");
        store.put(world, 16, 72, 0, "test:sapphire");

        List<VirtualBlockStore.Entry> first = store.entries(world, 0, 0);
        assertEquals(2, first.size());
        assertEquals(List.of(
                new VirtualBlockStore.Entry(0, 70, 0, "test:ruby"),
                new VirtualBlockStore.Entry(15, 71, 15, "test:ruby")), first);
        assertEquals(1, store.entries(world, 1, 0).size());
    }

    @Test
    void persistenceRoundTripsPaletteData(@TempDir Path temp) throws Exception {
        UUID world = UUID.randomUUID();
        Path file = temp.resolve("virtual-blocks.kvb");

        VirtualBlockStore written = new VirtualBlockStore();
        written.load(file);
        for (int i = 0; i < 2_000; i++) {
            written.put(world, i & 31, -64 + (i % 384), i >> 5,
                    i % 3 == 0 ? "bench:ruby" : "bench:stone");
        }
        written.flush();
        written.close();

        VirtualBlockStore read = new VirtualBlockStore();
        read.load(file);
        assertEquals(2_000, read.size());
        for (int i = 0; i < 2_000; i += 113) {
            assertEquals(i % 3 == 0 ? "bench:ruby" : "bench:stone",
                    read.get(world, i & 31, -64 + (i % 384), i >> 5));
        }
        read.close();
    }

    @Test
    void removingTheLastBlockDropsTheChunk() {
        VirtualBlockStore store = new VirtualBlockStore();
        UUID world = UUID.randomUUID();
        store.put(world, 100, 80, 100, "test:block");
        assertEquals(1, store.chunkCount());
        assertEquals("test:block", store.remove(world, 100, 80, 100));
        assertNull(store.get(world, 100, 80, 100));
        assertEquals(0, store.chunkCount());
    }

    @Test
    void oneHundredThousandPlacementsHaveNoArtificialContentCeiling() {
        VirtualBlockStore store = new VirtualBlockStore();
        UUID world = UUID.randomUUID();
        int count = 100_000;
        for (int i = 0; i < count; i++) {
            int x = i & 511;
            int z = i >> 9;
            store.put(world, x, 64 + (i & 7), z, "bench:type_" + (i & 31));
        }

        assertEquals(count, store.size());
        assertEquals("bench:type_0", store.get(world, 0, 64, 0));
        assertTrue(store.chunkCount() > 1);
    }
}

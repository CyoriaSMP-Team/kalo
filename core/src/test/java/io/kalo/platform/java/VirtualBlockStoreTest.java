package io.kalo.platform.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

    /**
     * The debounced writer and a shutdown flush must not overlap.
     *
     * <p>flush() was synchronized and writeSnapshot was not, so the two shared no monitor:
     * the writer could snapshot, a player place a block, flush write the newer state, and
     * then the writer's older snapshot land on top of it. Both moves are atomic, so nothing
     * ends up corrupt — the last blocks placed before shutdown just disappear.</p>
     *
     * <p>Asserted as mutual exclusion rather than by racing the two writers: the window is
     * a few milliseconds wide, so a timing-based test passes with the bug present and
     * claims coverage it does not have.</p>
     */
    @Test
    void snapshotWritesAreMutuallyExclusive(@TempDir Path temp) throws Exception {
        UUID world = UUID.randomUUID();
        Path file = temp.resolve("virtual-blocks.kvb");

        VirtualBlockStore store = new VirtualBlockStore();
        store.load(file);
        store.put(world, 0, 64, 0, "bench:stone");

        CountDownLatch entered = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread writer;

        synchronized (store) {
            writer = new Thread(() -> {
                entered.countDown();
                try {
                    store.writeSnapshot(file);
                } catch (Throwable t) {
                    failure.set(t);
                }
            });
            writer.start();

            assertTrue(entered.await(2, TimeUnit.SECONDS), "writer thread never started");
            writer.join(250);
            assertTrue(writer.isAlive(),
                    "writeSnapshot ran while another writer held the store monitor");
        }

        writer.join(2_000);
        assertNull(failure.get());
        store.close();

        VirtualBlockStore reloaded = new VirtualBlockStore();
        reloaded.load(file);
        assertEquals("bench:stone", reloaded.get(world, 0, 64, 0));
        reloaded.close();
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

package io.kalo.benchmark;

import io.kalo.platform.java.VirtualBlockStore;
import io.kalo.registry.DirectScalableRegistry;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Macro benchmark for catching architectural regressions, not a nanosecond microbenchmark.
 * Run with {@code ./gradlew :core:performanceBenchmark}.
 */
@Tag("benchmark")
class PerformanceBenchmarkTest {

    @Test
    void benchmarkCoreHotPaths() throws Exception {
        StringBuilder report = new StringBuilder("Kalo performance macro benchmark\n");

        int registryEntries = 250_000;
        DirectScalableRegistry<Integer> registry = new DirectScalableRegistry<>();
        long start = System.nanoTime();
        for (int i = 0; i < registryEntries; i++) {
            registry.register(Key.key("bench", "content_" + i), i);
        }
        registry.lock();
        long registryBuild = System.nanoTime() - start;

        int lookupCount = 2_000_000;
        long checksum = 0;
        start = System.nanoTime();
        for (int i = 0; i < lookupCount; i++) {
            int wanted = (i * 31) % registryEntries;
            int runtimeId = registry.runtimeId(Key.key("bench", "content_" + wanted));
            checksum += registry.getByRuntimeId(runtimeId).orElseThrow();
        }
        long registryLookup = System.nanoTime() - start;

        int placements = 250_000;
        VirtualBlockStore store = new VirtualBlockStore();
        UUID world = UUID.randomUUID();
        start = System.nanoTime();
        for (int i = 0; i < placements; i++) {
            store.put(world, i & 1023, 64 + (i & 15), i >> 10,
                    "bench:block_" + (i & 63));
        }
        long virtualWrite = System.nanoTime() - start;

        start = System.nanoTime();
        long blockChecksum = 0;
        for (int i = 0; i < lookupCount; i++) {
            int wanted = (i * 17) % placements;
            String id = store.get(world, wanted & 1023, 64 + (wanted & 15), wanted >> 10);
            if (id != null) {
                blockChecksum += id.length();
            }
        }
        long virtualLookup = System.nanoTime() - start;

        assertEquals(placements, store.size());
        store.close();

        append(report, "registry-build", registryEntries, registryBuild);
        append(report, "registry-lookup", lookupCount, registryLookup);
        append(report, "virtual-place", placements, virtualWrite);
        append(report, "virtual-lookup", lookupCount, virtualLookup);
        report.append("checksums=").append(checksum).append(',').append(blockChecksum).append('\n');

        Path output = Path.of(System.getProperty(
                "kalo.benchmark.output", "build/reports/benchmarks/kalo-performance.txt"));
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, report.toString(), StandardCharsets.UTF_8);
        System.out.println(report);
    }

    private static void append(StringBuilder report, String name, int operations, long nanos) {
        double seconds = nanos / 1_000_000_000.0;
        double ops = operations / Math.max(seconds, 0.000_001);
        report.append(String.format(Locale.ROOT,
                "%s: %,d ops in %.3fs = %,.0f ops/s%n", name, operations, seconds, ops));
    }
}

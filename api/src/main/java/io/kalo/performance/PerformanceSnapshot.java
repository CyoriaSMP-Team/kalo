package io.kalo.performance;

/** One lock-free view of Kalo's runtime health sampler. */
public record PerformanceSnapshot(
        double tps,
        long usedHeapBytes,
        long maxHeapBytes,
        double heapUsage,
        Pressure pressure,
        RuntimeBudget budget,
        long sampledAtNanos
) {
    public static PerformanceSnapshot initial() {
        return new PerformanceSnapshot(20.0, 0L, 1L, 0.0,
                Pressure.NORMAL, RuntimeBudget.forPressure(Pressure.NORMAL), System.nanoTime());
    }
}

package io.kalo.performance;

import io.kalo.manager.Context;
import io.kalo.manager.Managerial;
import io.kalo.manager.Reloadable;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight adaptive runtime sampler.
 *
 * <p>Pressure escalates immediately but only recovers after several consecutive healthy
 * samples. That hysteresis prevents render/cache/background policies from oscillating
 * every second when a server is sitting on a threshold.</p>
 */
public final class PerformanceManager implements PerformanceService, Managerial, Reloadable {
    private static final Logger LOGGER = Logger.getLogger(PerformanceManager.class.getName());

    private final CopyOnWriteArrayList<Consumer<PerformanceSnapshot>> listeners =
            new CopyOnWriteArrayList<>();

    private volatile PerformanceSnapshot snapshot = PerformanceSnapshot.initial();
    private volatile Settings settings = Settings.defaults();
    private volatile ScheduledTask samplerTask;
    private int recoverySamples;

    @Override
    public void preload(@NotNull Context context) {
        FileConfiguration config = context.plugin().getConfig();
        settings = new Settings(
                config.getBoolean("performance.adaptive.enabled", true),
                positive(config.getDouble("performance.adaptive.tps-warning", 18.5), 18.5),
                positive(config.getDouble("performance.adaptive.tps-critical", 16.0), 16.0),
                ratio(config.getDouble("performance.adaptive.heap-warning", 0.80), 0.80),
                ratio(config.getDouble("performance.adaptive.heap-critical", 0.90), 0.90),
                Math.max(1, config.getInt("performance.adaptive.recovery-samples", 5)));
        recoverySamples = 0;
    }

    @Override
    public void start(@NotNull Context context) {
        ScheduledTask previous = samplerTask;
        if (previous != null) {
            previous.cancel();
        }
        sampleRuntime();
        samplerTask = Bukkit.getAsyncScheduler().runAtFixedRate(
                context.plugin(), ignored -> sampleRuntime(), 1L, 1L, TimeUnit.SECONDS);
    }

    @Override
    public void end(@NotNull Context context) {
        ScheduledTask task = samplerTask;
        samplerTask = null;
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public @NotNull PerformanceSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public boolean adaptiveEnabled() {
        return settings.enabled();
    }

    @Override
    public void addPressureListener(@NotNull Consumer<PerformanceSnapshot> listener) {
        listeners.addIfAbsent(listener);
    }

    @Override
    public void removePressureListener(@NotNull Consumer<PerformanceSnapshot> listener) {
        listeners.remove(listener);
    }

    private void sampleRuntime() {
        double tps = 20.0;
        try {
            double[] values = Bukkit.getTPS();
            if (values.length > 0 && Double.isFinite(values[0])) {
                tps = values[0];
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.FINE, "Could not sample Paper TPS", e);
        }

        Runtime runtime = Runtime.getRuntime();
        long max = Math.max(1L, runtime.maxMemory());
        long used = Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
        double heap = Math.min(1.0, (double) used / (double) max);
        updateSample(tps, used, max, heap);
    }

    /** Package-private deterministic entry point used by the regression tests. */
    void updateSample(double tps, long usedHeap, long maxHeap, double heapUsage) {
        Settings currentSettings = settings;
        PerformanceSnapshot previous = snapshot;
        Pressure desired = currentSettings.enabled()
                ? classify(tps, heapUsage, currentSettings)
                : Pressure.NORMAL;
        Pressure next = previous.pressure();

        if (desired.ordinal() > next.ordinal()) {
            // Under pressure, react on the first sample.
            next = desired;
            recoverySamples = 0;
        } else if (desired.ordinal() < next.ordinal()) {
            // Recovery is intentionally sticky so one lucky tick cannot re-enable all
            // optional work immediately after a stall.
            recoverySamples++;
            if (recoverySamples >= currentSettings.recoverySamples()) {
                next = desired;
                recoverySamples = 0;
            }
        } else {
            recoverySamples = 0;
        }

        PerformanceSnapshot updated = new PerformanceSnapshot(
                tps,
                usedHeap,
                Math.max(1L, maxHeap),
                heapUsage,
                next,
                RuntimeBudget.forPressure(next),
                System.nanoTime());
        snapshot = updated;

        if (next != previous.pressure()) {
            Level level = next == Pressure.NORMAL ? Level.INFO : Level.WARNING;
            LOGGER.log(level, "Kalo runtime pressure: " + previous.pressure() + " -> " + next
                    + " (TPS=" + String.format(java.util.Locale.ROOT, "%.2f", tps)
                    + ", heap=" + String.format(java.util.Locale.ROOT, "%.1f%%", heapUsage * 100.0) + ")");
            for (Consumer<PerformanceSnapshot> listener : listeners) {
                try {
                    listener.accept(updated);
                } catch (RuntimeException e) {
                    LOGGER.log(Level.WARNING, "Performance listener failed", e);
                }
            }
        }
    }

    static @NotNull Pressure classify(double tps, double heapUsage, @NotNull Settings settings) {
        if (tps <= settings.tpsCritical() || heapUsage >= settings.heapCritical()) {
            return Pressure.CRITICAL;
        }
        if (tps <= settings.tpsWarning() || heapUsage >= settings.heapWarning()) {
            return Pressure.ELEVATED;
        }
        return Pressure.NORMAL;
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    private static double ratio(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 && value < 1.0 ? value : fallback;
    }

    record Settings(boolean enabled,
                    double tpsWarning,
                    double tpsCritical,
                    double heapWarning,
                    double heapCritical,
                    int recoverySamples) {
        static @NotNull Settings defaults() {
            return new Settings(true, 18.5, 16.0, 0.80, 0.90, 5);
        }
    }
}

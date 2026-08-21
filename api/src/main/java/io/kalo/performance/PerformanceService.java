package io.kalo.performance;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Read-only runtime pressure service for Kalo and add-ons.
 *
 * <p>Listeners are invoked when the pressure level changes, not on every sample. Add-ons
 * can use the same signal as Kalo instead of inventing another independent TPS monitor.</p>
 */
public interface PerformanceService {
    @NotNull PerformanceSnapshot snapshot();

    boolean adaptiveEnabled();

    void addPressureListener(@NotNull Consumer<PerformanceSnapshot> listener);

    void removePressureListener(@NotNull Consumer<PerformanceSnapshot> listener);
}

package io.kalo.performance;

/**
 * Relative budgets derived from current server pressure.
 *
 * @param backgroundWorkScale 1.0 at full speed; lower values mean non-critical work should back off
 * @param renderDistanceScale multiplier for client-side custom-content render distance
 * @param cacheRetentionScale multiplier for optional/cold cache retention
 */
public record RuntimeBudget(
        double backgroundWorkScale,
        double renderDistanceScale,
        double cacheRetentionScale
) {
    public static RuntimeBudget forPressure(Pressure pressure) {
        return switch (pressure) {
            case NORMAL -> new RuntimeBudget(1.0, 1.0, 1.0);
            case ELEVATED -> new RuntimeBudget(0.55, 0.75, 0.60);
            case CRITICAL -> new RuntimeBudget(0.20, 0.50, 0.25);
        };
    }
}

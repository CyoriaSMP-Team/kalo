package io.kalo.performance;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerformanceManagerTest {

    @Test
    void classificationUsesEitherTpsOrHeapPressure() {
        PerformanceManager.Settings settings = PerformanceManager.Settings.defaults();
        assertEquals(Pressure.NORMAL, PerformanceManager.classify(20.0, 0.50, settings));
        assertEquals(Pressure.ELEVATED, PerformanceManager.classify(18.0, 0.50, settings));
        assertEquals(Pressure.ELEVATED, PerformanceManager.classify(20.0, 0.82, settings));
        assertEquals(Pressure.CRITICAL, PerformanceManager.classify(15.0, 0.50, settings));
        assertEquals(Pressure.CRITICAL, PerformanceManager.classify(20.0, 0.95, settings));
    }

    @Test
    void pressureEscalatesImmediatelyButRecoveryHasHysteresis() {
        PerformanceManager manager = new PerformanceManager();
        AtomicInteger transitions = new AtomicInteger();
        manager.addPressureListener(ignored -> transitions.incrementAndGet());

        manager.updateSample(15.0, 1, 10, 0.10);
        assertEquals(Pressure.CRITICAL, manager.snapshot().pressure());
        assertEquals(1, transitions.get());

        for (int i = 0; i < 4; i++) {
            manager.updateSample(20.0, 1, 10, 0.10);
            assertEquals(Pressure.CRITICAL, manager.snapshot().pressure());
        }
        manager.updateSample(20.0, 1, 10, 0.10);
        assertEquals(Pressure.NORMAL, manager.snapshot().pressure());
        assertEquals(2, transitions.get());
    }

    @Test
    void budgetShrinksAsPressureRises() {
        RuntimeBudget normal = RuntimeBudget.forPressure(Pressure.NORMAL);
        RuntimeBudget elevated = RuntimeBudget.forPressure(Pressure.ELEVATED);
        RuntimeBudget critical = RuntimeBudget.forPressure(Pressure.CRITICAL);

        org.junit.jupiter.api.Assertions.assertTrue(normal.backgroundWorkScale() > elevated.backgroundWorkScale());
        org.junit.jupiter.api.Assertions.assertTrue(elevated.backgroundWorkScale() > critical.backgroundWorkScale());
        org.junit.jupiter.api.Assertions.assertTrue(normal.renderDistanceScale() > critical.renderDistanceScale());
        org.junit.jupiter.api.Assertions.assertTrue(normal.cacheRetentionScale() > critical.cacheRetentionScale());
    }
}

package io.kalo.content.feature;

import io.kalo.content.feature.event.FeatureEvent;
import io.kalo.content.feature.event.FeatureEventSubscriber;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-content event bus.
 *
 * <p>Concurrent throughout: resource pack generation dispatches on a background thread
 * while features may still be subscribing during content construction.</p>
 */
public final class FeatureEventBusImpl implements FeatureEventBus {
    private final Map<Class<? extends FeatureEvent>, List<FeatureEventSubscriber<?>>> subscribers =
            new ConcurrentHashMap<>();

    @Override
    public <T extends FeatureEvent> void subscribe(@NotNull Class<T> clazz, @NotNull FeatureEventSubscriber<T> subscriber) {
        subscribers.computeIfAbsent(clazz, ignored -> new CopyOnWriteArrayList<>()).add(subscriber);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void call(@NotNull FeatureEvent event) {
        // Matched by assignability rather than exact class, so subscribing to a supertype
        // works. Exact-class lookup silently dropped those subscriptions.
        for (Map.Entry<Class<? extends FeatureEvent>, List<FeatureEventSubscriber<?>>> entry : subscribers.entrySet()) {
            if (!entry.getKey().isInstance(event)) {
                continue;
            }
            for (FeatureEventSubscriber<?> subscriber : entry.getValue()) {
                ((FeatureEventSubscriber<FeatureEvent>) subscriber).onCalled(event);
            }
        }
    }
}

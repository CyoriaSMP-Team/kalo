package io.kalo.manager;

import io.kalo.content.Content;
import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.feature.Feature;
import io.kalo.content.feature.FeatureEventBus;
import io.kalo.content.feature.FeatureEventBusImpl;
import io.kalo.content.feature.event.ResourcePackGenerationEvent;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.registry.Registries;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackManagerImplTest {

    @Test
    void resourcePackFeatureEventReachesAddonAndNonItemContent() {
        RegistryManagerImpl manager = new RegistryManagerImpl();
        StubContent blockLikeContent = new StubContent(Key.key("addon", "machine"));
        AtomicInteger calls = new AtomicInteger();
        blockLikeContent.bus.subscribe(ResourcePackGenerationEvent.class, ignored -> calls.incrementAndGet());
        manager.registries().types().register(Key.key("addon", "machine"), new StubType(blockLikeContent));

        ResourcePack pack = new ResourcePackImpl(PackMeta.of(1, "test"));
        ResourcePackManagerImpl.dispatchFeatureEvents(pack, manager.registries());

        assertEquals(1, calls.get());
    }

    @Test
    void generationQueueNeverRunsTwoWritersAtOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            ResourcePackManagerImpl.GenerationQueue queue =
                    new ResourcePackManagerImpl.GenerationQueue(executor);
            queue.start();

            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            List<Integer> order = java.util.Collections.synchronizedList(new ArrayList<>());

            CompletableFuture<Void> first = queue.submit(() -> {
                order.add(1);
                firstStarted.countDown();
                await(releaseFirst);
                order.add(2);
            });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

            CompletableFuture<Void> second = queue.submit(() -> order.add(3));
            assertThrows(TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS),
                    "the second writer must remain queued while the first owns the destination");

            releaseFirst.countDown();
            CompletableFuture.allOf(first, second).get(2, TimeUnit.SECONDS);
            assertEquals(List.of(1, 2, 3), order);

            queue.stopAndWait();
            assertThrows(ExecutionException.class,
                    () -> queue.submit(() -> { }).get(2, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedGenerationDoesNotPoisonTheNextRequest() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ResourcePackManagerImpl.GenerationQueue queue =
                    new ResourcePackManagerImpl.GenerationQueue(executor);
            queue.start();

            CompletableFuture<Void> failed = queue.submit(() -> {
                throw new IllegalStateException("broken input");
            });
            assertThrows(ExecutionException.class, () -> failed.get(2, TimeUnit.SECONDS));

            AtomicInteger completed = new AtomicInteger();
            queue.submit(completed::incrementAndGet).get(2, TimeUnit.SECONDS);
            assertEquals(1, completed.get());
            queue.stopAndWait();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void stopAndWaitIsABarrierForAnInFlightWriter() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch release = new CountDownLatch(1);
        try {
            ResourcePackManagerImpl.GenerationQueue queue =
                    new ResourcePackManagerImpl.GenerationQueue(executor);
            queue.start();

            CountDownLatch started = new CountDownLatch(1);
            queue.submit(() -> {
                started.countDown();
                await(release);
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));

            CompletableFuture<Void> stopped = CompletableFuture.runAsync(queue::stopAndWait);
            assertThrows(TimeoutException.class, () -> stopped.get(100, TimeUnit.MILLISECONDS),
                    "reload must not clear registries while the old writer is still reading them");

            release.countDown();
            stopped.get(2, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class,
                    () -> queue.submit(() -> { }).get(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static final class StubContent implements Content {
        private final Key key;
        private final FeatureEventBusImpl bus = new FeatureEventBusImpl();

        private StubContent(Key key) {
            this.key = key;
        }

        @Override
        public Key key() {
            return key;
        }

        @Override
        public Collection<Feature> features() {
            return List.of();
        }

        @Override
        public FeatureEventBus featureEventBus() {
            return bus;
        }
    }

    private record StubType(StubContent content) implements ContentType<Content> {
        @Override
        public String id() {
            return "machine";
        }

        @Override
        public Class<Content> clazz() {
            return Content.class;
        }

        @Override
        public boolean load(PackContext pack, Registries registries, ConfigurationSection config) {
            return true;
        }

        @Override
        public Iterable<Content> contents(Registries registries) {
            return List.of(content);
        }
    }
}

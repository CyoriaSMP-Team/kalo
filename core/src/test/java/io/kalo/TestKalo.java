package io.kalo;

import io.kalo.manager.ContentManager;
import io.kalo.manager.RegistryManager;
import io.kalo.manager.RegistryManagerImpl;
import io.kalo.manager.ResourcePackManager;
import io.kalo.performance.PerformanceService;
import io.kalo.performance.PerformanceSnapshot;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Minimal singleton fixture for core tests that need the global registries. */
public final class TestKalo {

    private TestKalo() {
    }

    public static RegistryManagerImpl install() {
        RegistryManagerImpl registries = new RegistryManagerImpl();
        ContentManager contents = ignored -> Optional.empty();
        ResourcePackManager resourcePacks = () -> CompletableFuture.completedFuture(null);

        Kalo.registerPlugin(new KaloPlugin() {
            @Override
            public RegistryManager registryManager() {
                return registries;
            }

            @Override
            public ContentManager contentManager() {
                return contents;
            }

            @Override
            public ResourcePackManager resourcePackManager() {
                return resourcePacks;
            }

            @Override
            public PerformanceService performance() {
                return new PerformanceService() {
                    @Override public PerformanceSnapshot snapshot() { return PerformanceSnapshot.initial(); }
                    @Override public boolean adaptiveEnabled() { return false; }
                    @Override public void addPressureListener(java.util.function.Consumer<PerformanceSnapshot> listener) {}
                    @Override public void removePressureListener(java.util.function.Consumer<PerformanceSnapshot> listener) {}
                };
            }

            @Override
            public void reload() {
            }
        });
        return registries;
    }

    public static void uninstall() {
        Kalo.unregisterPlugin();
    }
}

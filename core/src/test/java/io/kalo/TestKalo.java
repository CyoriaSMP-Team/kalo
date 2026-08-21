package io.kalo;

import io.kalo.manager.ContentManager;
import io.kalo.manager.RegistryManager;
import io.kalo.manager.RegistryManagerImpl;
import io.kalo.manager.ResourcePackManager;

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
            public void reload() {
            }
        });
        return registries;
    }

    public static void uninstall() {
        Kalo.unregisterPlugin();
    }
}

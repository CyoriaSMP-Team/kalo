package io.kalo;

import io.kalo.manager.ContentManager;
import io.kalo.manager.RegistryManager;
import io.kalo.manager.ResourcePackManager;
import io.kalo.performance.PerformanceService;
import org.jetbrains.annotations.NotNull;

public interface KaloPlugin {
    @NotNull RegistryManager registryManager();

    @NotNull ContentManager contentManager();

    @NotNull ResourcePackManager resourcePackManager();

    /** Runtime pressure/health sampler shared by Kalo and performance-aware add-ons. */
    @NotNull PerformanceService performance();

    void reload();
}

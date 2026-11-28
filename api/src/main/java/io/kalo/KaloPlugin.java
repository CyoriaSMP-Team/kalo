package io.kalo;

import io.kalo.items.ItemIdentity;
import io.kalo.manager.ContentManager;
import io.kalo.manager.RegistryManager;
import io.kalo.manager.ResourcePackManager;
import io.kalo.performance.PerformanceService;
import org.jetbrains.annotations.NotNull;

public interface KaloPlugin {
    @NotNull RegistryManager registryManager();

    @NotNull ContentManager contentManager();

    /** Stable item identity contract shared by Kalo and every consumer plugin. */
    @NotNull ItemIdentity items();
    /** Places, removes and identifies blocks and furniture in the world from code. */
    @NotNull io.kalo.content.block.BlockWorld blockWorld();

    @NotNull ResourcePackManager resourcePackManager();

    /** Runtime pressure/health sampler shared by Kalo and performance-aware add-ons. */
    @NotNull PerformanceService performance();

    void reload();
}

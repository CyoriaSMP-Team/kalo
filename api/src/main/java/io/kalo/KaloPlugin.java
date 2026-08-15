package io.kalo;

import io.kalo.manager.ContentManager;
import io.kalo.manager.RegistryManager;
import io.kalo.manager.ResourcePackManager;
import org.jetbrains.annotations.NotNull;

public interface KaloPlugin {
    @NotNull RegistryManager registryManager();

    @NotNull ContentManager contentManager();

    @NotNull ResourcePackManager resourcePackManager();

    void reload();
}

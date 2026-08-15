package io.kalo.content;

import io.kalo.registry.Registries;
import net.kyori.adventure.key.Keyed;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public interface ContentsPack extends Keyed {
    @NotNull String id();

    @NotNull String version();

    @NotNull String author();

    @NotNull File packFolder();

    @NotNull Registries registries();
}

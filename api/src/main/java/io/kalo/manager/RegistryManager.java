package io.kalo.manager;

import io.kalo.Kalo;
import io.kalo.content.ContentType;
import io.kalo.content.ContentsPack;
import io.kalo.content.feature.FeatureFactory;
import io.kalo.registry.DirectWritableRegistry;
import io.kalo.registry.Registries;
import io.kalo.registry.Registry;
import org.jetbrains.annotations.NotNull;

public interface RegistryManager {
    @NotNull GlobalRegistries registries();

    interface GlobalRegistries extends Registries {
        @NotNull Registry<ContentType<?>> types();

        @NotNull DirectWritableRegistry<FeatureFactory<?>> features();

        @NotNull DirectWritableRegistry<ContentsPack> contentsPacks();

        void mergeAll(@NotNull Registries registries);


        static @NotNull GlobalRegistries registries() {
            return Kalo.plugin().registryManager().registries();
        }
    }
}

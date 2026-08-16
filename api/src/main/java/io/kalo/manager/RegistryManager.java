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

        /**
         * Content types, including any an add-on registers.
         *
         * <p>Writable so third parties can add their own kind of content. Register during
         * {@link io.kalo.event.RegistryInitializeEvent} at default priority: Kalo loads
         * content packs at {@code HIGHEST}, which runs last, so a type registered at
         * normal priority is in place before any pack is read.</p>
         *
         * <pre>
         * &#64;EventHandler
         * public void onRegistryInitialize(RegistryInitializeEvent event) {
         *     event.getRegistries().types().register(MyType.KEY, new MyType());
         * }
         * </pre>
         */
        @NotNull DirectWritableRegistry<ContentType<?>> types();

        @NotNull DirectWritableRegistry<FeatureFactory<?>> features();

        @NotNull DirectWritableRegistry<ContentsPack> contentsPacks();

        void mergeAll(@NotNull Registries registries);


        static @NotNull GlobalRegistries registries() {
            return Kalo.plugin().registryManager().registries();
        }
    }
}

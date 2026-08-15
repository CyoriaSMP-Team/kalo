package io.kalo.content;

import io.kalo.pack.ResourcePack;
import io.kalo.registry.Registries;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

/**
 * Handles one kind of content end to end: parsing it out of pack YAML into the registry,
 * and contributing whatever it needs to the generated resource pack.
 */
public interface ContentType<T extends Content> {

    @NotNull String id();

    @NotNull Class<T> clazz();

    /**
     * Parses a single content definition and registers it.
     *
     * @param pack       the owning pack — supplies the namespace for the content key and
     *                   the base directory for asset lookups
     * @param registries the pack's registries to register into
     * @param config     the content's configuration section
     * @return whether loading succeeded
     */
    boolean load(@NotNull PackContext pack, @NotNull Registries registries, @NotNull ConfigurationSection config);

    /**
     * Everything of this type currently registered.
     *
     * <p>Each type owns its own registry, so only the type knows which one to read. This
     * is what lets pack generation walk every content type without hardcoding a list.</p>
     */
    @NotNull Iterable<T> contents(@NotNull Registries registries);

    /**
     * Writes this type's assets into the resource pack being generated.
     *
     * <p>Called once per generation with every registered piece of content of this type.
     * The default is a no-op for types with no client-side representation.</p>
     */
    default void compilePack(@NotNull ResourcePack resourcePack, @NotNull Iterable<T> contents) {
    }
}

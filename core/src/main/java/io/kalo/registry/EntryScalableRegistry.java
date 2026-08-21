package io.kalo.registry;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class EntryScalableRegistry<T, E extends EntryWritableRegistry.RegistryEntry<T>>
        extends ScalableRegistry<T> implements EntryWritableRegistry<T, E> {

    private final Supplier<E> entrySupplier;

    public EntryScalableRegistry(@NotNull Supplier<E> entrySupplier) {
        this.entrySupplier = entrySupplier;
    }

    @Override
    public synchronized @NotNull T register(@NotNull Key key, @NotNull Consumer<E> entry) {
        checkWritable();
        if (map.containsKey(key.asString())) {
            throw new IllegalStateException("The registry already contains '" + key.asString() + "'");
        }

        E registryEntry = entrySupplier.get();
        entry.accept(registryEntry);
        T value = registryEntry.toValue();

        map.put(key.asString(), value);
        markDirty();
        return value;
    }
}

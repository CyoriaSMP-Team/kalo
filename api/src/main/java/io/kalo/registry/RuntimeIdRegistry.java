package io.kalo.registry;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * A registry that also exposes compact process-local integer ids.
 *
 * <p>Runtime ids are deliberately not persisted. They are rebuilt deterministically from
 * sorted content keys whenever the registry changes, which makes hot-path lookups cheap
 * without turning a server's saved data into an implementation detail of one Kalo build.</p>
 */
public interface RuntimeIdRegistry<T> extends Registry<T> {
    int NO_RUNTIME_ID = -1;

    /**
     * Returns the compact id for {@code key}, or {@link #NO_RUNTIME_ID} when it is absent.
     */
    int runtimeId(@NotNull Key key);

    /** Looks up a value without parsing or hashing a namespaced key on the hot path. */
    @NotNull Optional<T> getByRuntimeId(int runtimeId);

    /** Monotonically increases whenever the registry's contents change. */
    long revision();
}

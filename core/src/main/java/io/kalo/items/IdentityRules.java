package io.kalo.items;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Pure identity classification for a carried item.
 *
 * <p>An item can carry two identity markers: the Kalo item id written by the Java
 * compiler and a vanilla {@code item_model} component written by any other content
 * plugin. The Kalo marker always wins, because it is the only one that carries
 * behaviour. A model is accepted only when the server can rebuild it, which keeps the
 * invariant {@code create(idOf(stack))} true for every id this returns.</p>
 */
final class IdentityRules {

    private IdentityRules() {
    }

    static @NotNull Optional<Key> classify(@Nullable String markerId, @Nullable Key itemModel,
                                           @NotNull Predicate<Key> resolvable) {
        Optional<Key> marked = parse(markerId);
        if (marked.isPresent() && resolvable.test(marked.get())) {
            return marked;
        }
        if (itemModel != null && resolvable.test(itemModel)) {
            return Optional.of(itemModel);
        }
        return Optional.empty();
    }

    static @NotNull Optional<Key> parse(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Key.key(raw));
        } catch (RuntimeException malformed) {
            // The id marker lives in a persistent data container, which commands and
            // external tools can edit. Malformed data is not content.
            return Optional.empty();
        }
    }
}

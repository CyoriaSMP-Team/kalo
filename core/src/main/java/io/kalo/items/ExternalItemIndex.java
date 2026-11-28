package io.kalo.items;

import io.kalo.converter.ExternalItemBindings;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Operator-learned external item bindings, indexed by their {@code item_model} key.
 *
 * <p>These are items another content plugin wrote into a real server-visible stack. The
 * server can rebuild them because a binding records which vanilla material carries the
 * model. Nothing here is guessed from a resource-pack asset; the store is written only
 * after an operator has actually held the item.</p>
 */
public final class ExternalItemIndex {

    private final Map<Key, ExternalItemBindings.Binding> bindings = new LinkedHashMap<>();

    public ExternalItemIndex(@NotNull List<ExternalItemBindings.Binding> initial) {
        for (ExternalItemBindings.Binding binding : initial) {
            bindings.put(binding.itemModel(), binding);
        }
    }

    public static @NotNull ExternalItemIndex load(@NotNull Path file) throws IOException {
        return new ExternalItemIndex(ExternalItemBindings.load(file));
    }

    public static @NotNull ExternalItemIndex empty() {
        return new ExternalItemIndex(List.of());
    }

    public synchronized void add(@NotNull ExternalItemBindings.Binding binding) {
        bindings.put(binding.itemModel(), binding);
    }

    public synchronized @NotNull Optional<ExternalItemBindings.Binding> get(@NotNull Key key) {
        return Optional.ofNullable(bindings.get(key));
    }

    public synchronized boolean contains(@NotNull Key key) {
        return bindings.containsKey(key);
    }

    public synchronized int size() {
        return bindings.size();
    }
}

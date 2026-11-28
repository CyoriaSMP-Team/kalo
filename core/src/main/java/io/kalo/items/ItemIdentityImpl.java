package io.kalo.items;

import io.kalo.content.item.Item;
import io.kalo.manager.RegistryManager;
import io.kalo.platform.java.JavaItemCompiler;
import io.kalo.utils.Plugins;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Reads the two identity markers a stack can carry and resolves them against the live
 * registries.
 *
 * <p>The Kalo marker lives in the persistent data container and is written by
 * {@link JavaItemCompiler}. The fallback marker is the vanilla {@code item_model}
 * component, which other content plugins use; it is only accepted once an operator has
 * observed the stack and the binding was persisted, so {@link #create(Key)} can rebuild
 * it.</p>
 */
public final class ItemIdentityImpl implements ItemIdentity {

    private final ExternalItemIndex external;

    public ItemIdentityImpl(@NotNull Path externalBindings) {
        ExternalItemIndex loaded = ExternalItemIndex.empty();
        try {
            loaded = ExternalItemIndex.load(externalBindings);
        } catch (IOException invalid) {
            Plugins.logger().log(Level.WARNING,
                    "Could not read " + externalBindings
                            + "; external item ids stay unresolved until the file is fixed",
                    invalid);
        }
        this.external = loaded;
    }

    @Override
    public @NotNull Optional<Key> idOf(@NotNull ItemStack stack) {
        if (stack.getType().isAir()) {
            return Optional.empty();
        }
        String marker = stack.getPersistentDataContainer()
                .get(JavaItemCompiler.ITEM_ID_KEY, PersistentDataType.STRING);
        Key model = stack.hasData(DataComponentTypes.ITEM_MODEL)
                ? stack.getData(DataComponentTypes.ITEM_MODEL)
                : null;
        return IdentityRules.classify(marker, model, this::resolvable);
    }

    @Override
    public @NotNull Optional<ItemStack> create(@NotNull Key key) {
        RegistryManager.GlobalRegistries registries = RegistryManager.GlobalRegistries.registries();
        Item item = registries.item().get(key).orElse(null);
        if (item == null) {
            item = registries.armor().get(key).orElse(null);
        }
        if (item != null) {
            return Optional.of(item.itemStack().get());
        }
        return external.get(key).map(binding -> {
            ItemStack stack = new ItemStack(binding.material());
            stack.setData(DataComponentTypes.ITEM_MODEL, binding.itemModel());
            return stack;
        });
    }

    /** Number of learned external bindings currently rebuildable. */
    public int externalCount() {
        return external.size();
    }

    /**
     * Adds a binding the server just observed, so it resolves immediately instead of
     * after the next restart. Called from the async writer once the file is persisted.
     */
    public void learn(@NotNull io.kalo.converter.ExternalItemBindings.Binding binding) {
        external.add(binding);
    }

    private boolean resolvable(@NotNull Key key) {
        RegistryManager.GlobalRegistries registries = RegistryManager.GlobalRegistries.registries();
        return registries.item().get(key).isPresent()
                || registries.armor().get(key).isPresent()
                || external.contains(key);
    }
}

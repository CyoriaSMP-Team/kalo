package io.kalo.converter;

import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Optional low-overhead discovery of real server-visible ItemStacks.
 * Observation happens only on the owning player scheduler. Persisting runs
 * on a dedicated single-thread executor, never on the main/global tick.
 *
 * New mappings become available at Geyser's NEXT startup; this class never
 * mutates a frozen custom-item palette while players are connected.
 */
public final class ExternalItemObserver implements Listener, AutoCloseable {
    private final Plugin plugin;
    private final Path file;
    private final Path legacyFile;
    private final Set<String> seenLegacy = ConcurrentHashMap.newKeySet();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon(true).name("kalo-external-item-writer", 0).factory());
    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastScan = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public ExternalItemObserver(@NotNull Plugin plugin) throws java.io.IOException {
        this.plugin = plugin;
        this.file = Constants.dataFolder().toPath().resolve("external-items.yml");
        this.legacyFile = Constants.dataFolder().toPath().resolve("legacy-items.yml");
        for (ExternalItemBindings.Binding binding : ExternalItemBindings.load(file)) {
            seen.add(binding.itemModel().asString());
        }
        for (ExternalLegacyItems.Binding binding : ExternalLegacyItems.load(legacyFile)) {
            seenLegacy.add(binding.id());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleScan(event.getPlayer(), 20L);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        scheduleScan(event.getPlayer(), 2L);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            // The upper inventory may contain custom icons never moved into the
            // player's own inventory. Inspect the server-visible GUI on its event
            // thread before the menu disappears.
            rememberObserved(event.getInventory().getContents());
            scheduleScan(player, 2L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastScan.remove(event.getPlayer().getUniqueId());
    }

    private void scheduleScan(Player player, long delayTicks) {
        if (closed.get()) return;
        player.getScheduler().execute(plugin, () -> inspectInventory(player), null, delayTicks);
    }

    private void inspectInventory(Player player) {
        if (closed.get() || !player.isOnline()) return;
        long now = System.nanoTime();
        java.util.concurrent.atomic.AtomicBoolean allowed = new java.util.concurrent.atomic.AtomicBoolean();
        lastScan.compute(player.getUniqueId(), (id, previous) -> {
            if (previous == null || now - previous >= TimeUnit.MILLISECONDS.toNanos(500)) {
                allowed.set(true);
                return now;
            }
            return previous;
        });
        if (!allowed.get()) return;

        // Read from the player's region. Take only immutable IDs, not live
        // inventory references, across the asynchronous writer boundary.
        rememberObserved(player.getInventory().getContents());
    }

    private void rememberObserved(ItemStack[] items) {
        if (closed.get()) return;
        Map<String, ExternalItemBindings.Binding> newBindings = new java.util.LinkedHashMap<>();
        Map<String, ExternalLegacyItems.Binding> legacyBindings = new java.util.LinkedHashMap<>();
        for (ItemStack item : items) {
            ExternalItemBindings.Binding binding = observed(item);
            if (binding != null && !seen.contains(binding.itemModel().asString())) {
                newBindings.putIfAbsent(binding.itemModel().asString(), binding);
            } else if (binding == null) {
                ExternalLegacyItems.Binding legacy = observedLegacy(item);
                if (legacy != null && !seenLegacy.contains(legacy.id())) {
                    legacyBindings.putIfAbsent(legacy.id(), legacy);
                }
            }
        }
        for (ExternalItemBindings.Binding binding : newBindings.values()) {
            if (!seen.add(binding.itemModel().asString())) continue;
            try {
                writer.execute(() -> {
                    try {
                        ExternalItemBindings.remember(file, binding);
                    } catch (Exception failure) {
                        seen.remove(binding.itemModel().asString());
                        Plugins.logger().log(Level.WARNING,
                                "Could not persist learned external model " + binding.itemModel(), failure);
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException closing) {
                seen.remove(binding.itemModel().asString());
            }
        }

        for (ExternalLegacyItems.Binding binding : legacyBindings.values()) {
            if (!seenLegacy.add(binding.id())) continue;
            try {
                writer.execute(() -> {
                    try {
                        ExternalLegacyItems.remember(legacyFile, binding);
                    } catch (Exception failure) {
                        seenLegacy.remove(binding.id());
                        Plugins.logger().log(Level.WARNING,
                                "Could not save legacy CustomModelData " + binding.id(), failure);
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException closing) {
                seenLegacy.remove(binding.id());
            }
        }
    }

    private static ExternalLegacyItems.Binding observedLegacy(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.hasItemModel() || !meta.hasCustomModelData()) return null;
        try {
            return new ExternalLegacyItems.Binding(
                    item.getType(), meta.getCustomModelData(), Math.min(99, item.getMaxStackSize()));
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static ExternalItemBindings.Binding observed(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasItemModel()) return null;
        NamespacedKey model = meta.getItemModel();
        if (model == null) return null;
        try {
            return new ExternalItemBindings.Binding(
                    Key.key(model.getNamespace(), model.getKey()),
                    item.getType(), Math.min(99, item.getMaxStackSize()));
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        org.bukkit.event.HandlerList.unregisterAll(this);
        writer.shutdown();
        try {
            if (!writer.awaitTermination(3, TimeUnit.SECONDS)) {
                Plugins.logger().warning("External item observer did not finish saving within 3 seconds");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        lastScan.clear();
    }
}

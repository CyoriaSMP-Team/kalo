package io.kalo.platform.java;

import io.kalo.content.liquid.LiquidBucketRegistry;
import io.kalo.content.liquid.LiquidStore;
import io.kalo.content.liquid.LiquidType;
import io.kalo.content.liquid.definition.LiquidDefinition;
import io.kalo.content.item.Item;
import io.kalo.manager.RegistryManager;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Runtime for marked liquids: bucket placement, saved boxes, biome colour and contact rules.
 *
 * <p>The fluid itself is vanilla water or lava; only the saved box makes it a custom liquid.
 * Contact rules are checked once per second rather than on every move event.</p>
 */
public final class LiquidRuntime implements Listener {

    private static final Map<UUID, Block> CORNERS = new ConcurrentHashMap<>();

    private static volatile LiquidType type;
    private static volatile LiquidStore store;
    private static volatile io.papermc.paper.threadedregions.scheduler.ScheduledTask task;

    public LiquidRuntime() {
    }

    public static synchronized void initialize(@NotNull LiquidType liquidType,
                                               @NotNull Path dataFolder) {
        type = liquidType;
        Path file = dataFolder.resolve("liquids.json");
        store = load(file);
        scheduleBiomeApplication();
        if (task == null) {
            try {
                task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                        Plugins.plugin(), ignored -> tick(), 20L, 20L);
            } catch (RuntimeException unavailable) {
                Plugins.logger().log(Level.WARNING,
                        "Could not start liquid contact task", unavailable);
            }
        }
    }

    public static synchronized void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        save();
        store = null;
        type = null;
        CORNERS.clear();
    }

    public static boolean corner(@NotNull Player player) {
        CORNERS.put(player.getUniqueId(), player.getLocation().getBlock());
        return true;
    }

    public static boolean fill(@NotNull Player player,
                               @NotNull LiquidDefinition definition) {
        Block corner = CORNERS.get(player.getUniqueId());
        LiquidStore current = store;
        if (corner == null || current == null
                || !corner.getWorld().equals(player.getWorld())) {
            return false;
        }
        Block now = player.getLocation().getBlock();
        String originalBiome = now.getWorld().getBiome(now.getX(), now.getZ())
                .key().asString();
        LiquidStore.Volume volume = current.addVolume(player.getWorld().getName(),
                definition.key(), corner.getX(), corner.getY(), corner.getZ(),
                now.getX(), now.getY(), now.getZ(), originalBiome);
        save();
        applyBiome(volume, definition);
        return true;
    }

    public static boolean clear(@NotNull Player player) {
        LiquidStore current = store;
        if (current == null) {
            return false;
        }
        Location location = player.getLocation();
        LiquidStore.Volume volume = current.containing(player.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (volume == null) {
            return false;
        }
        restoreBiome(volume);
        boolean removed = current.remove(volume);
        if (removed) {
            save();
        }
        return removed;
    }

    public static @NotNull Map<UUID, Block> corners() {
        return Map.copyOf(CORNERS);
    }

    /** Pure lookup used by the contact task and its tests. */
    static @Nullable LiquidDefinition resolve(@NotNull LiquidStore store,
                                              @NotNull LiquidType type,
                                              @NotNull String world,
                                              int x, int y, int z) {
        LiquidStore.Volume volume = store.containing(world, x, y, z);
        return volume == null ? null : type.liquids().get(volume.liquid());
    }

    /**
     * Pure trim used by sponge absorption and its tests.
     *
     * <p>A sponge event lists the cells about to become air; removing their bounding
     * runs from the saved boxes keeps the marked volume from covering dry space.</p>
     */
    static @NotNull List<LiquidStore.Volume> absorb(@NotNull LiquidStore store,
                                                    @NotNull String world,
                                                    @NotNull List<LiquidStore.BlockPos> blocks) {
        return store.removeBlocks(world, blocks);
    }

    /**
     * Pure trim for one deliberately removed bank/fluid cell.
     *
     * @return the removed cell, or null when no marked volume contained it
     */
    static @Nullable LiquidStore.Volume bankRemoved(@NotNull LiquidStore store,
                                                    @NotNull String world,
                                                    int x, int y, int z) {
        return store.removeCell(world, x, y, z);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpongeAbsorb(@NotNull SpongeAbsorbEvent event) {
        LiquidStore currentStore = store;
        if (currentStore == null || currentStore.size() == 0) {
            return;
        }
        Block sponge = event.getBlock();
        List<LiquidStore.BlockPos> blocks = new ArrayList<>();
        blocks.add(new LiquidStore.BlockPos(sponge.getX(), sponge.getY(), sponge.getZ()));
        for (BlockState state : event.getBlocks()) {
            blocks.add(new LiquidStore.BlockPos(state.getX(), state.getY(), state.getZ()));
        }
        List<LiquidStore.Volume> removed = absorbSafely(currentStore,
                sponge.getWorld().getName(), blocks);
        if (!removed.isEmpty()) {
            save();
            restoreRemoved(removed, true);
        }
    }

    /**
     * A deliberate placement inside a marked box (including filling a pool) means that
     * cell is no longer the liquid. The bank around a pool is outside the box, so
     * breaking it still lets flow expand the volume through {@link #onFlow}.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        LiquidStore currentStore = store;
        if (currentStore == null || currentStore.size() == 0) {
            return;
        }
        Block placed = event.getBlockPlaced();
        if (!shouldTrim(placed.getType())) {
            return;
        }
        LiquidStore.Volume removed = bankRemovedSafely(currentStore,
                placed.getWorld().getName(), placed.getX(), placed.getY(), placed.getZ());
        if (removed != null) {
            save();
            restoreRemoved(List.of(removed), false);
        }
    }

    /** A deliberately broken block inside the box is no longer part of the liquid. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        Block broken = event.getBlock();
        if (!shouldTrim(broken.getType())) {
            return;
        }
        LiquidStore currentStore = store;
        if (currentStore == null || currentStore.size() == 0) {
            return;
        }
        LiquidStore.Volume removed = bankRemovedSafely(currentStore,
                broken.getWorld().getName(), broken.getX(), broken.getY(), broken.getZ());
        if (removed != null) {
            save();
            restoreRemoved(List.of(removed), false);
        }
    }

    /**
     * Physics updates (a neighbouring bank breaking, a fluid receding, a piston moving)
     * can leave a non-fluid cell inside a saved box. Trim it instead of applying contact
     * rules to air or stone.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(@NotNull BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (!shouldTrim(block.getType())) {
            return;
        }
        LiquidStore currentStore = store;
        if (currentStore == null || currentStore.size() == 0) {
            return;
        }
        LiquidStore.Volume removed = bankRemovedSafely(currentStore,
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (removed != null) {
            save();
            restoreRemoved(List.of(removed), false);
        }
    }

    private static @NotNull List<LiquidStore.Volume> absorbSafely(
            @NotNull LiquidStore store, @NotNull String world,
            @NotNull List<LiquidStore.BlockPos> blocks) {
        try {
            return absorb(store, world, blocks);
        } catch (RuntimeException tooMany) {
            Plugins.logger().log(Level.WARNING,
                    "Could not trim liquid volume after sponge absorption", tooMany);
            return List.of();
        }
    }

    private static @Nullable LiquidStore.Volume bankRemovedSafely(
            @NotNull LiquidStore store, @NotNull String world, int x, int y, int z) {
        try {
            return bankRemoved(store, world, x, y, z);
        } catch (RuntimeException tooMany) {
            Plugins.logger().log(Level.WARNING,
                    "Could not trim liquid volume after a block change", tooMany);
            return null;
        }
    }

    private static void restoreRemoved(@NotNull List<LiquidStore.Volume> removed,
                                       boolean reapplyAll) {
        for (LiquidStore.Volume volume : removed) {
            restoreBiome(volume);
        }
        if (reapplyAll) {
            applyBiomes();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucket(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null) {
            return;
        }
        ItemStack held = event.getItem();
        if (held == null || held.getType().isAir()) {
            return;
        }
        Key itemKey = itemKey(held);
        if (held.getType() == Material.BUCKET && itemKey == null) {
            pickup(event);
            return;
        }
        Key liquidKey = LiquidBucketRegistry.liquid(itemKey);
        LiquidType currentType = type;
        LiquidStore currentStore = store;
        if (itemKey == null || liquidKey == null || currentType == null
                || currentStore == null) {
            return;
        }
        if (!ContentEntitlements.allows(event.getPlayer(), itemKey)
                || !ContentEntitlements.allows(event.getPlayer(), liquidKey)) {
            return;
        }
        LiquidDefinition definition = currentType.liquids().get(liquidKey);
        Block clicked = event.getClickedBlock();
        if (definition == null || clicked == null) {
            return;
        }
        Block target = clicked.getRelative(event.getBlockFace());
        if (!target.getType().isAir()
                && target.getType() != Material.WATER
                && target.getType() != Material.LAVA) {
            return;
        }
        target.setType(definition.base().material());
        String originalBiome = target.getWorld().getBiome(target.getX(), target.getZ())
                .key().asString();
        LiquidStore.Volume volume = currentStore.addBlock(target.getWorld().getName(),
                definition.key(), target.getX(), target.getY(), target.getZ(),
                originalBiome);
        save();
        applyBiome(volume, definition);
        consumeBucket(event.getPlayer(), event.getHand());
        event.setCancelled(true);
    }

    /**
     * Empty-bucket pickup: remove one marked source block, trim the volume, and hand back
     * the registered custom bucket for that liquid.
     */
    private static void pickup(@NotNull PlayerInteractEvent event) {
        LiquidType currentType = type;
        LiquidStore currentStore = store;
        Block clicked = event.getClickedBlock();
        if (currentType == null || currentStore == null || clicked == null
                || !isFluid(clicked.getType())) {
            return;
        }
        LiquidStore.Volume volume = currentStore.containing(
                clicked.getWorld().getName(), clicked.getX(), clicked.getY(), clicked.getZ());
        if (volume == null) {
            return;
        }
        LiquidDefinition definition = currentType.liquids().get(volume.liquid());
        Key bucketKey = LiquidBucketRegistry.item(volume.liquid());
        if (definition == null || bucketKey == null
                || definition.base().material() != clicked.getType()) {
            return;
        }
        Item bucket = RegistryManager.GlobalRegistries.registries().item()
                .get(bucketKey).orElse(null);
        if (bucket == null) {
            return;
        }
        if (!ContentEntitlements.allows(event.getPlayer(), bucketKey)
                || !ContentEntitlements.allows(event.getPlayer(), volume.liquid())) {
            return;
        }
        LiquidStore.Volume removed = currentStore.removeBlock(
                clicked.getWorld().getName(), clicked.getX(), clicked.getY(), clicked.getZ());
        if (removed == null) {
            return;
        }
        clicked.setType(Material.AIR, true);
        save();
        restoreBiome(removed);
        applyBiomes();
        giveBucket(event.getPlayer(), bucket.itemStack().get());
        consumeEmptyBucket(event.getPlayer(), event.getHand());
        event.setCancelled(true);
    }

    /** Flow expands a marked pool into a destination that has become the same fluid. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(@NotNull BlockFromToEvent event) {
        Block source = event.getBlock();
        if (!isFluid(source.getType())) {
            return;
        }
        LiquidType currentType = type;
        LiquidStore currentStore = store;
        if (currentType == null || currentStore == null) {
            return;
        }
        LiquidStore.Volume sourceVolume = currentStore.containing(
                source.getWorld().getName(), source.getX(), source.getY(), source.getZ());
        if (sourceVolume == null) {
            return;
        }
        LiquidDefinition definition = currentType.liquids().get(sourceVolume.liquid());
        if (definition == null || definition.base().material() != source.getType()) {
            return;
        }
        Block target = event.getToBlock();
        Bukkit.getRegionScheduler().run(Plugins.plugin(), target.getLocation(), ignored -> {
            LiquidStore.Volume current = currentStore.containing(
                    source.getWorld().getName(), source.getX(), source.getY(), source.getZ());
            if (current == null || !isFluid(source.getType())) {
                if (currentStore.removeBlock(source.getWorld().getName(),
                        source.getX(), source.getY(), source.getZ()) != null) {
                    save();
                    applyBiomes();
                }
                return;
            }
            if (!isFluid(target.getType())
                    || target.getType() != definition.base().material()) {
                return;
            }
            String originalBiome = target.getWorld().getBiome(
                    target.getX(), target.getZ()).key().asString();
            LiquidStore.Volume expanded = currentStore.addBlock(
                    target.getWorld().getName(), current.liquid(),
                    target.getX(), target.getY(), target.getZ(), originalBiome);
            save();
            applyBiome(expanded, definition);
        });
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        CORNERS.remove(event.getPlayer().getUniqueId());
    }

    private static boolean isFluid(@NotNull Material material) {
        return material == Material.WATER || material == Material.LAVA;
    }

    /** Package-visible event guard: fluids stay marked, everything else is trimmed. */
    static boolean shouldTrim(@NotNull Material material) {
        return !isFluid(material);
    }

    private static void giveBucket(@NotNull Player player, @NotNull ItemStack bucket) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(bucket);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private static void consumeEmptyBucket(@NotNull Player player,
                                           @NotNull EquipmentSlot hand) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack held = player.getInventory().getItem(hand);
        if (held == null || held.getType() != Material.BUCKET) {
            return;
        }
        if (held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
        } else if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }
    }

    private static void tick() {
        LiquidType currentType = type;
        LiquidStore currentStore = store;
        if (currentType == null || currentStore == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().execute(Plugins.plugin(),
                    () -> applyContact(player, currentType, currentStore), null, 1L);
        }
    }

    private static void applyContact(@NotNull Player player, @NotNull LiquidType currentType,
                                     @NotNull LiquidStore currentStore) {
        if (!player.isOnline()) {
            return;
        }
        Location location = player.getLocation();
        LiquidDefinition definition = resolve(currentStore, currentType,
                player.getWorld().getName(), location.getBlockX(),
                location.getBlockY(), location.getBlockZ());
        if (definition == null) {
            return;
        }
        if (definition.effect() != null) {
            PotionEffectType effect = effect(definition.effect());
            if (effect != null) {
                player.addPotionEffect(new PotionEffect(effect, 40,
                        definition.amplifier(), true, false, true));
            }
        }
        if (definition.damage() > 0) {
            player.damage(definition.damage());
        }
        if (definition.fireproof()) {
            player.setFireTicks(0);
        }
    }

    private static @Nullable PotionEffectType effect(@NotNull String value) {
        String id = value.contains(":") ? value : "minecraft:" + value.toLowerCase(Locale.ROOT);
        try {
            Key key = Key.key(id);
            return Registry.EFFECT.get(new NamespacedKey(key.namespace(), key.value()));
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static @NotNull LiquidStore load(@NotNull Path file) {
        try {
            return LiquidStore.load(file);
        } catch (IOException | RuntimeException corrupt) {
            Plugins.logger().log(Level.SEVERE,
                    "Could not read " + file + "; starting with no liquid volumes",
                    corrupt);
            try {
                if (Files.isRegularFile(file)) {
                    Files.move(file, file.resolveSibling(file.getFileName()
                                    + ".corrupt-" + System.currentTimeMillis()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ignored) {
                // The original file remains; the next save will report its own error.
            }
            return new LiquidStore(file);
        }
    }

    private static void save() {
        LiquidStore current = store;
        if (current == null) {
            return;
        }
        try {
            current.save();
        } catch (IOException error) {
            Plugins.logger().log(Level.SEVERE, "Could not save liquid volumes", error);
        }
    }

    /**
     * Applies saved colour volumes on the region that owns each box. Folia/Canvas reject
     * {@code setBiome} from the server thread during enable; scheduling per volume keeps
     * startup from disabling the plugin when a coloured liquid already exists.
     */
    private static void scheduleBiomeApplication() {
        LiquidType currentType = type;
        LiquidStore currentStore = store;
        if (currentType == null || currentStore == null) {
            return;
        }
        for (LiquidStore.Volume volume : currentStore.volumes()) {
            LiquidDefinition definition = currentType.liquids().get(volume.liquid());
            World world = Bukkit.getWorld(volume.world());
            if (definition == null || world == null) {
                continue;
            }
            try {
                Bukkit.getRegionScheduler().run(Plugins.plugin(), world,
                        volume.minX() >> 4, volume.minZ() >> 4,
                        ignored -> applyBiome(volume, definition));
            } catch (RuntimeException unavailable) {
                Plugins.logger().log(Level.FINE,
                        "Could not schedule liquid biome application", unavailable);
            }
        }
    }

    private static void applyBiomes() {
        LiquidType currentType = type;
        LiquidStore currentStore = store;
        if (currentType == null || currentStore == null) {
            return;
        }
        for (LiquidStore.Volume volume : currentStore.volumes()) {
            LiquidDefinition definition = currentType.liquids().get(volume.liquid());
            if (definition != null) {
                applyBiome(volume, definition);
            }
        }
    }

    private static void applyBiome(@NotNull LiquidStore.Volume volume,
                                   @NotNull LiquidDefinition definition) {
        if (definition.color() == null) {
            return;
        }
        World world = Bukkit.getWorld(volume.world());
        Biome biome = biome(definition.biomeKey());
        if (world == null || biome == null) {
            return;
        }
        for (int x = volume.minX(); x <= volume.maxX(); x++) {
            for (int z = volume.minZ(); z <= volume.maxZ(); z++) {
                world.setBiome(x, z, biome);
            }
        }
    }

    private static void restoreBiome(@NotNull LiquidStore.Volume volume) {
        if (volume.originalBiome() == null) {
            return;
        }
        World world = Bukkit.getWorld(volume.world());
        Biome biome;
        try {
            Key key = Key.key(volume.originalBiome());
            biome = biome(key);
        } catch (RuntimeException malformed) {
            return;
        }
        if (world == null || biome == null) {
            return;
        }
        for (int x = volume.minX(); x <= volume.maxX(); x++) {
            for (int z = volume.minZ(); z <= volume.maxZ(); z++) {
                world.setBiome(x, z, biome);
            }
        }
    }

    private static @Nullable Biome biome(@NotNull Key key) {
        return Registry.BIOME.get(new NamespacedKey(key.namespace(), key.value()));
    }

    private static @Nullable Key itemKey(@NotNull ItemStack stack) {
        if (!stack.hasItemMeta()) {
            return null;
        }
        String value = stack.getItemMeta().getPersistentDataContainer()
                .get(JavaItemCompiler.ITEM_ID_KEY, PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        try {
            return Key.key(value);
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static void consumeBucket(@NotNull Player player,
                                      @NotNull EquipmentSlot hand) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack held = player.getInventory().getItem(hand);
        if (held == null || held.getType().isAir()) {
            return;
        }
        int amount = held.getAmount();
        if (amount > 1) {
            held.setAmount(amount - 1);
            player.getInventory().addItem(new ItemStack(Material.BUCKET));
            return;
        }
        ItemStack empty = new ItemStack(Material.BUCKET);
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(empty);
        } else {
            player.getInventory().setItemInMainHand(empty);
        }
    }
}

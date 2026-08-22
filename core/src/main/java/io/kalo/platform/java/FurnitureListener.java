package io.kalo.platform.java;

import io.kalo.Kalo;
import io.kalo.content.furniture.Furniture;
import io.kalo.content.furniture.definition.FurnitureBehaviour;
import io.kalo.manager.RegistryManager;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles furniture-specific interactions: rotation, seating, storage, and jukebox.
 *
 * <p>This listener extends the base block listener with furniture-specific behaviour.
 * Furniture pieces that are also blocks (native or virtual) are handled by
 * {@link JavaBlockListener}; this listener adds the extra interactions on top.</p>
 *
 * <p>Furniture state (rotation, seat references) is stored in-memory keyed by location.
 * This avoids depending on PersistentDataContainer on Block (which is not available in
 * the Paper API at this version) and keeps the furniture listener self-contained.</p>
 */
public final class FurnitureListener implements Listener {
    private static final Logger LOGGER = Logger.getLogger(FurnitureListener.class.getName());

    /** In-memory rotation state for furniture: location string -> rotation (0-15). */
    private final Map<String, Integer> rotationState = new ConcurrentHashMap<>();

    /** In-memory seat state: location string -> seat entity UUID. */
    private final Map<String, UUID> seatState = new ConcurrentHashMap<>();

    /** Tracks which players are sitting on which seat entity. */
    private final Map<UUID, UUID> seatedPlayers = new ConcurrentHashMap<>();

    /** Cached furniture lookup by content key string. */
    private final Map<String, Furniture> furnitureByKey = new ConcurrentHashMap<>();

    /** Persistent storage for furniture inventories. */
    private final FurnitureStorage furnitureStorage = new FurnitureStorage();

    public FurnitureListener() {
        rebuildLookup();
    }

    /**
     * Rebuilds the furniture lookup from registries.
     */
    public void rebuildLookup() {
        furnitureByKey.clear();
        try {
            RegistryManager.GlobalRegistries registries = RegistryManager.GlobalRegistries.registries();
            registries.furniture().entries().forEach(entry ->
                    furnitureByKey.put(entry.key().asString(), entry.value()));
        } catch (RuntimeException ignored) {
            // Constructor can run before global registries have finished their first load.
        }
    }

    private static @NotNull String locationKey(@NotNull Location loc) {
        return loc.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    /**
     * Handles furniture placement with rotation based on player facing direction.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String id = JavaBlockItemCompiler.idOf(event.getItemInHand());
        if (id == null) {
            return;
        }

        Furniture furniture = resolveFurniture(id);
        if (furniture == null) {
            return;
        }

        FurnitureBehaviour behaviour = furniture.furnitureDefinition().behaviour();

        // Apply rotation based on player facing direction
        org.bukkit.block.Block placed = event.getBlockPlaced();
        int rotation = calculateRotation(event.getPlayer(), behaviour.restrictedRotation());
        rotationState.put(locationKey(placed.getLocation()), rotation);
    }

    /**
     * Handles right-click interactions on furniture: rotation, seating, storage, jukebox.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        org.bukkit.block.Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        // Check if this block is registered furniture by resolving through the block state allocator
        BlockStateAllocator allocator = getBlockStateAllocator();
        if (allocator == null) {
            return;
        }

        String id = resolveFurnitureAt(clicked);
        if (id == null) {
            return;
        }

        Furniture furniture = resolveFurniture(id);
        if (furniture == null) {
            return;
        }

        Player player = event.getPlayer();
        FurnitureBehaviour behaviour = furniture.furnitureDefinition().behaviour();

        // Handle rotation (sneak + right-click)
        if (behaviour.rotatable() && player.isSneaking()) {
            rotateFurniture(clicked, behaviour.restrictedRotation());
            event.setCancelled(true);
            return;
        }

        // Handle seating
        if (behaviour.seat() != null && !player.isSneaking()) {
            sitPlayer(player, clicked, behaviour.seat());
            event.setCancelled(true);
            return;
        }

        // Handle storage
        if (behaviour.storage() != null && !player.isSneaking()) {
            openStorage(player, clicked, behaviour.storage());
            event.setCancelled(true);
            return;
        }
    }

    /**
     * Handles left-click (break) on furniture.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        org.bukkit.block.Block worldBlock = event.getBlock();
        String locKey = locationKey(worldBlock.getLocation());

        // Check if anyone is sitting on this furniture
        UUID seatUuid = seatState.remove(locKey);
        if (seatUuid != null) {
            Entity seatEntity = Bukkit.getEntity(seatUuid);
            if (seatEntity != null) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getVehicle() == seatEntity) {
                        online.eject();
                    }
                }
                seatEntity.remove();
            }
        }

        // Clean up rotation state
        rotationState.remove(locKey);
    }

    /**
     * Ejects seated players when they disconnect.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID seatUuid = seatedPlayers.remove(event.getPlayer().getUniqueId());
        if (seatUuid != null) {
            Entity seatEntity = Bukkit.getEntity(seatUuid);
            if (seatEntity != null) {
                seatEntity.remove();
            }
        }
    }

    /**
     * Calculates the rotation value based on player facing direction.
     */
    private int calculateRotation(@NotNull Player player, @Nullable String restrictedRotation) {
        float yaw = player.getLocation().getYaw();
        int fullRotation = (int) ((yaw + 180) / 22.5) % 16; // 16 directions

        if ("very_strict".equalsIgnoreCase(restrictedRotation)) {
            return (fullRotation / 4) * 4;
        } else if ("strict".equalsIgnoreCase(restrictedRotation)) {
            return (fullRotation / 2) * 2;
        }

        return fullRotation; // 16 facings
    }

    /**
     * Rotates the furniture to the next facing.
     */
    private void rotateFurniture(@NotNull org.bukkit.block.Block block, @Nullable String restrictedRotation) {
        String locKey = locationKey(block.getLocation());
        int current = rotationState.getOrDefault(locKey, 0);
        int maxRotation;
        if ("very_strict".equalsIgnoreCase(restrictedRotation)) {
            maxRotation = 4;
        } else if ("strict".equalsIgnoreCase(restrictedRotation)) {
            maxRotation = 8;
        } else {
            maxRotation = 16;
        }

        int next = (current + (16 / maxRotation)) % 16;
        rotationState.put(locKey, next);

        // TODO: Update visual rotation of the display entity if virtual block
    }

    /**
     * Makes a player sit on the furniture.
     */
    private void sitPlayer(@NotNull Player player, @NotNull org.bukkit.block.Block block, @NotNull FurnitureBehaviour.Seat seat) {
        if (player.getVehicle() != null) {
            return;
        }

        Location seatLocation = block.getLocation().add(
                seat.offset().get(0),
                seat.offset().get(1),
                seat.offset().get(2));

        ArmorStand seatEntity = block.getWorld().spawn(seatLocation, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setSmall(true);
            stand.setMarker(true);
        });

        seatEntity.addPassenger(player);
        seatedPlayers.put(player.getUniqueId(), seatEntity.getUniqueId());
        seatState.put(locationKey(block.getLocation()), seatEntity.getUniqueId());
    }

    /**
     * Opens a storage container for the player.
     */
    private void openStorage(@NotNull Player player, @NotNull org.bukkit.block.Block block,
                            @NotNull FurnitureBehaviour.Storage storage) {
        switch (storage.type().toUpperCase()) {
            case "ENDERCHEST" -> {
                Inventory enderChest = player.getEnderChest();
                if (enderChest != null) {
                    player.openInventory(enderChest);
                }
                return;
            }
            case "DISPOSAL" -> {
                Inventory inventory = Bukkit.createInventory(null, storage.rows() * 9,
                        storage.title() != null ? storage.title() : "Disposal");
                player.openInventory(inventory);
                return;
            }
            case "PERSONAL" -> {
                // Personal storage - use persistent storage
                furnitureStorage.openStorage(player, block.getWorld().getUID(),
                        block.getX(), block.getY(), block.getZ(),
                        storage.rows(), storage.title());
                return;
            }
            default -> {
                // Regular shared storage - use persistent storage
                furnitureStorage.openStorage(player, block.getWorld().getUID(),
                        block.getX(), block.getY(), block.getZ(),
                        storage.rows(), storage.title());
            }
        }
    }

    /**
     * Attempts to resolve a furniture content key at a world block location
     * by checking if the block state allocator has an assignment for it.
     */
    private @Nullable String resolveFurnitureAt(@NotNull org.bukkit.block.Block block) {
        BlockStateAllocator allocator = getBlockStateAllocator();
        if (allocator == null) {
            return null;
        }

        // Check if this block is a carrier material with a Kalo assignment
        org.bukkit.block.data.BlockData blockData = block.getBlockData();
        String blockDataString = blockData.getAsString(false);

        // Look up in the JavaBlockListener's resolution path — delegate to the registry
        try {
            RegistryManager.GlobalRegistries registries = RegistryManager.GlobalRegistries.registries();
            for (it.unimi.dsi.fastutil.Pair<Key, Furniture> entry : registries.furniture().entries()) {
                Furniture furniture = entry.value();
                BlockStateAllocator.Assignment assignment = allocator.assignmentOf(furniture.definition().key());
                if (assignment != null) {
                    org.bukkit.block.data.BlockData assigned = blockDataFor(assignment);
                    if (assigned != null && assigned.getAsString(false).equals(blockDataString)) {
                        return entry.key().asString();
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Registries might not be initialized yet
        }

        return null;
    }

    private @Nullable org.bukkit.block.data.BlockData blockDataFor(@NotNull BlockStateAllocator.Assignment assignment) {
        try {
            return Bukkit.createBlockData(
                    assignment.carrier().vanillaBlock() + "[" + assignment.carrier().variantKey(assignment.state()) + "]");
        } catch (Exception e) {
            return null;
        }
    }

    private @Nullable BlockStateAllocator getBlockStateAllocator() {
        try {
            if (Kalo.plugin().registryManager() instanceof io.kalo.manager.RegistryManagerImpl impl) {
                return impl.blockStateAllocator();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Resolves a furniture from its content ID.
     */
    private @Nullable Furniture resolveFurniture(@NotNull String id) {
        try {
            Key key = Key.key(id);
            return RegistryManager.GlobalRegistries.registries().furniture().get(key).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}

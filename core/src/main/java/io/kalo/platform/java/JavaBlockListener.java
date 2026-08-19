package io.kalo.platform.java;

import io.kalo.content.block.Block;
import io.kalo.content.block.definition.BlockCarrier;
import io.kalo.content.block.definition.JavaBlockMode;
import io.kalo.manager.RegistryManager;
import io.kalo.utils.Constants;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Makes both Java block representations behave like Kalo content.
 *
 * <p>Native blocks borrow vanilla states and therefore need the vanilla carrier's
 * physics, interaction and note sound suppressed. Virtual blocks use a persistent
 * invisible {@link #VIRTUAL_ANCHOR} plus an {@link ItemDisplay}; the display carries the
 * Kalo key in its PDC, so no finite block-state allocator is involved.</p>
 */
public final class JavaBlockListener implements Listener {
    private static final Logger LOGGER = Logger.getLogger(JavaBlockListener.class.getName());

    /** Full-block collision anchor; Barrier is invisible unless a player holds a barrier. */
    public static final Material VIRTUAL_ANCHOR = Material.BARRIER;
    /** Kalo id stored on the persistent display entity, not inferred from its model. */
    public static final NamespacedKey VIRTUAL_BLOCK_ID_KEY =
            new NamespacedKey(Constants.PLUGIN_ID, "virtual_block");

    private final BlockStateAllocator allocator;

    /** Vanilla materials currently acting as carriers, for a cheap first check. */
    private final Set<Material> carrierMaterials = new HashSet<>();
    /** {@code "minecraft:note_block[instrument=harp,…]"} to the content key drawn as it. */
    private final Map<String, String> byBlockData = new HashMap<>();

    public JavaBlockListener(@NotNull BlockStateAllocator allocator) {
        this.allocator = allocator;
        rebuildLookup();
    }

    /**
     * Rebuilds the native state-to-content lookup.
     *
     * <p>Virtual displays are persistent entities and keep their own content id. They do
     * not need a global index and remain resolvable after a reload or chunk unload.</p>
     */
    public void rebuildLookup() {
        Map<String, String> lookup = new HashMap<>();
        Set<Material> materials = new HashSet<>();

        allocator.assignments().forEach((contentKey, assignment) -> {
            BlockCarrier carrier = assignment.carrier();
            try {
                BlockData data = blockDataFor(carrier, assignment.state());
                lookup.put(data.getAsString(false), contentKey);
                materials.add(data.getMaterial());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "Could not resolve the vanilla state for " + contentKey, e);
            }
        });

        synchronized (this) {
            byBlockData.clear();
            byBlockData.putAll(lookup);
            carrierMaterials.clear();
            carrierMaterials.addAll(materials);
        }
    }

    private static @NotNull BlockData blockDataFor(@NotNull BlockCarrier carrier, int state) {
        return Bukkit.createBlockData(carrier.vanillaBlock() + "[" + carrier.variantKey(state) + "]");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String id = JavaBlockItemCompiler.idOf(event.getItemInHand());
        if (id == null) {
            return;
        }

        Block block = resolveContent(id);
        if (block == null) {
            // Registered when the item was made, gone now — a pack was removed while the
            // item stayed in someone's inventory.
            event.setCancelled(true);
            return;
        }

        if (block.definition().java().mode() == JavaBlockMode.VIRTUAL) {
            placeVirtual(event, block);
            return;
        }

        BlockStateAllocator.Assignment assignment = allocator.assignmentOf(block.definition().key());
        if (assignment == null) {
            event.setCancelled(true);
            return;
        }

        // applyPhysics=false: a physics pass here would immediately recompute the carrier's
        // state from its neighbours and undo the assignment.
        event.getBlockPlaced().setBlockData(
                blockDataFor(assignment.carrier(), assignment.state()), false);
    }

    private void placeVirtual(@NotNull BlockPlaceEvent event, @NotNull Block block) {
        org.bukkit.block.Block placed = event.getBlockPlaced();
        try {
            // The anchor supplies ordinary full-block collision and survives chunk saves.
            // The visible geometry lives on the persistent display below.
            placed.setType(VIRTUAL_ANCHOR, false);
            spawnVirtualDisplay(placed, block);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Could not place virtual block " + block.key().asString(), e);
            removeVirtualDisplay(placed);
            event.setCancelled(true);
            event.getBlockReplacedState().update(true, false);
        }
    }

    private static @NotNull ItemDisplay spawnVirtualDisplay(@NotNull org.bukkit.block.Block placed,
                                                             @NotNull Block block) {
        Location location = placed.getLocation().add(0.5, 0.5, 0.5);
        return placed.getWorld().spawn(location, ItemDisplay.class, display -> {
            display.setItemStack(block.itemStack().get());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setPersistent(true);
            display.setInvulnerable(true);
            display.setGravity(false);
            display.setSilent(true);
            display.getPersistentDataContainer().set(
                    VIRTUAL_BLOCK_ID_KEY, PersistentDataType.STRING, block.key().asString());
        });
    }

    /** Stops vanilla rewriting a carrier or virtual anchor out from under custom content. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (isCarrier(event.getChangedType())
                || isCarrier(event.getBlock().getType())
                || resolveVirtual(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    /**
     * Retuning a note block, or interacting with a virtual anchor, must not mutate content.
     *
     * <p>Barrier is deliberately used for collision, but it is unbreakable in vanilla.
     * A left click therefore goes through this hook and is translated into the same Kalo
     * break/drop operation instead of leaving virtual content impossible to remove.</p>
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        org.bukkit.block.Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        // A content key can disappear during a reload while its persistent display is
        // still in a loaded chunk. Keep the orphan breakable so it cannot strand an
        // unbreakable Barrier in the world forever.
        Block virtual = resolveVirtual(clicked);
        if (clicked.getType() == VIRTUAL_ANCHOR && findVirtualDisplay(clicked) != null
                && virtual == null) {
            event.setCancelled(true);
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                ItemDisplay orphan = findVirtualDisplay(clicked);
                if (orphan != null) {
                    orphan.remove();
                    clicked.setType(Material.AIR, false);
                }
            }
            return;
        }

        if (virtual != null) {
            event.setCancelled(true);
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                breakVirtual(clicked, event.getPlayer(), virtual);
            }
        } else if (resolve(clicked) != null) {
            event.setCancelled(true);
        }
    }

    private static void breakVirtual(@NotNull org.bukkit.block.Block broken,
                                     @NotNull Player player,
                                     @NotNull Block block) {
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (block.definition().behaviour().unbreakable() && !creative) {
            return;
        }

        removeVirtualDisplay(broken);
        broken.setType(Material.AIR, false);
        if (!creative) {
            broken.getWorld().dropItemNaturally(broken.getLocation().add(0.5, 0.5, 0.5),
                    block.itemStack().get());
        }
    }

    /** A borrowed state has no musical meaning; playing it would be noise. */
    @EventHandler(ignoreCancelled = true)
    public void onNotePlay(NotePlayEvent event) {
        if (resolve(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        org.bukkit.block.Block broken = event.getBlock();
        Block block = resolve(broken);
        if (block == null) {
            return;
        }

        boolean creative = event.getPlayer().getGameMode() == GameMode.CREATIVE;
        if (block.definition().behaviour().unbreakable() && !creative) {
            event.setCancelled(true);
            return;
        }

        // Vanilla would drop the carrier or Barrier block; drop Kalo's item instead.
        event.setDropItems(false);
        if (broken.getType() == VIRTUAL_ANCHOR) {
            removeVirtualDisplay(broken);
        }
        if (!creative) {
            broken.getWorld().dropItemNaturally(broken.getLocation().add(0.5, 0.5, 0.5),
                    block.itemStack().get());
        }
    }

    /** Explosions remove the anchor without going through BlockBreakEvent. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(JavaBlockListener::removeVirtualDisplay);
    }

    /** Creeper/TNT explosions use a separate event but have the same orphan risk. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(JavaBlockListener::removeVirtualDisplay);
    }

    /** Identifies custom content drawn at a world block, native or virtual. */
    public @Nullable Block resolve(@NotNull org.bukkit.block.Block worldBlock) {
        Block virtual = resolveVirtual(worldBlock);
        if (virtual != null) {
            return virtual;
        }

        if (!isCarrier(worldBlock.getType())) {
            return null;
        }

        String contentKey;
        synchronized (this) {
            contentKey = byBlockData.get(worldBlock.getBlockData().getAsString(false));
        }
        return contentKey != null ? resolveContent(contentKey) : null;
    }

    /** Resolves the display attached to a barrier anchor at exactly this block position. */
    private static @Nullable Block resolveVirtual(@NotNull org.bukkit.block.Block worldBlock) {
        if (worldBlock.getType() != VIRTUAL_ANCHOR) {
            return null;
        }

        ItemDisplay display = findVirtualDisplay(worldBlock);
        if (display == null) {
            return null;
        }

        String id = display.getPersistentDataContainer()
                .get(VIRTUAL_BLOCK_ID_KEY, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }

        Block block = resolveContent(id);
        return block != null && block.definition().java().mode() == JavaBlockMode.VIRTUAL
                ? block : null;
    }

    private static void removeVirtualDisplay(@NotNull org.bukkit.block.Block worldBlock) {
        ItemDisplay display = findVirtualDisplay(worldBlock);
        if (display != null) {
            display.remove();
        }
    }

    private static @Nullable ItemDisplay findVirtualDisplay(@NotNull org.bukkit.block.Block worldBlock) {
        Location center = worldBlock.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : worldBlock.getWorld().getNearbyEntities(
                center, 0.35, 0.35, 0.35, candidate -> candidate instanceof ItemDisplay)) {
            if (!(entity instanceof ItemDisplay display)) {
                continue;
            }
            Location location = display.getLocation();
            if (location.getBlockX() == worldBlock.getX()
                    && location.getBlockY() == worldBlock.getY()
                    && location.getBlockZ() == worldBlock.getZ()
                    && display.getPersistentDataContainer().has(VIRTUAL_BLOCK_ID_KEY, PersistentDataType.STRING)) {
                return display;
            }
        }
        return null;
    }

    private synchronized boolean isCarrier(@NotNull Material material) {
        return carrierMaterials.contains(material);
    }

    /** Blocks and furniture share the carrier, so both registries are searched. */
    private static @Nullable Block resolveContent(@NotNull String id) {
        RegistryManager.GlobalRegistries registries = RegistryManager.GlobalRegistries.registries();
        Key key = Key.key(id);

        return registries.block().get(key)
                .map(block -> (Block) block)
                .or(() -> registries.furniture().get(key).map(furniture -> (Block) furniture))
                .orElse(null);
    }
}

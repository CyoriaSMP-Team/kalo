package io.kalo.platform.java;

import io.kalo.content.block.Block;
import io.kalo.content.block.definition.BlockCarrier;
import io.kalo.content.block.definition.JavaBlockMode;
import io.kalo.integration.GeyserIntegration;
import io.kalo.manager.RegistryManager;
import io.kalo.performance.RuntimeBudget;
import io.kalo.utils.Constants;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Makes both Java block representations behave like Kalo content.
 *
 * <p>Native blocks borrow vanilla states. Virtual blocks use a Barrier for collision, but
 * their identity lives in {@link VirtualBlockStore}; the {@link ItemDisplay} is only a
 * non-persistent renderer. This avoids using an entity as a database record, removes a
 * nearby-entity scan from every lookup, and lets displays be recreated per loaded chunk.</p>
 */
public final class JavaBlockListener implements Listener {
    private static final Logger LOGGER = Logger.getLogger(JavaBlockListener.class.getName());

    /** Full-block collision anchor; Barrier is invisible unless a player holds a barrier. */
    public static final Material VIRTUAL_ANCHOR = Material.BARRIER;
    /** Kept on render entities for legacy migration and cheap ownership checks. */
    public static final NamespacedKey VIRTUAL_BLOCK_ID_KEY =
            new NamespacedKey(Constants.PLUGIN_ID, "virtual_block");

    private final BlockStateAllocator allocator;
    private final VirtualBlockStore virtualBlocks;

    /** Immutable hot-path snapshots swapped atomically on reload. */
    private volatile Set<Material> carrierMaterials = Set.of();
    private volatile Map<String, String> byBlockData = Map.of();
    private volatile Map<String, Block> contentById = Map.of();
    /** Display view range follows the adaptive runtime budget; 1.0 is Paper's full range. */
    private volatile float displayViewRange = 1.0f;

    /** Renderer lookup is O(1); the entity UUID is not persistent Kalo state. */
    private final ConcurrentHashMap<BlockAddress, UUID> renderedDisplays = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkAddress, Set<BlockAddress>> renderedByChunk = new ConcurrentHashMap<>();
    /** Per-bone Java display rigs for animated Blockbench blocks and furniture. */
    private final ConcurrentHashMap<BlockAddress, RigInstance> rigs = new ConcurrentHashMap<>();

    public JavaBlockListener(@NotNull BlockStateAllocator allocator,
                             @NotNull VirtualBlockStore virtualBlocks) {
        this.allocator = allocator;
        this.virtualBlocks = virtualBlocks;
        rebuildLookup();
    }

    /** Rebuilds all read-mostly lookup tables and atomically swaps them into the hot path. */
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

        Map<String, Block> content = new HashMap<>();
        try {
            RegistryManager.GlobalRegistries registries = RegistryManager.GlobalRegistries.registries();
            registries.block().entries().forEach(entry ->
                    content.put(entry.key().asString(), entry.value()));
            registries.furniture().entries().forEach(entry ->
                    content.put(entry.key().asString(), entry.value()));
        } catch (RuntimeException ignored) {
            // Constructor can run before global registries have finished their first load.
            // KaloPluginImpl calls this again immediately after managers start.
        }

        byBlockData = Map.copyOf(lookup);
        carrierMaterials = Set.copyOf(materials);
        contentById = Map.copyOf(content);
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
            placed.setType(VIRTUAL_ANCHOR, false);
            virtualBlocks.put(placed.getWorld().getUID(), placed.getX(), placed.getY(), placed.getZ(),
                    block.key().asString());
            spawnOrRefreshVirtualDisplay(placed, block);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Could not place virtual block " + block.key().asString(), e);
            virtualBlocks.remove(placed.getWorld().getUID(), placed.getX(), placed.getY(), placed.getZ());
            removeVirtualDisplay(placed);
            event.setCancelled(true);
            event.getBlockReplacedState().update(true, false);
        }
    }

    private void spawnOrRefreshVirtualDisplay(@NotNull org.bukkit.block.Block placed,
                                              @NotNull Block block) {
        BlockbenchRigRegistry.Rig rig = BlockbenchRigRegistry.get(block.key());
        if (rig != null && rig.animateable() && rig.hasLoops()) {
            spawnOrRefreshRig(placed, block, rig);
            announceBedrockBlock(placed, block);
            return;
        }
        // A reload can turn an animated block back into a static one; drop the old rig.
        removeRig(BlockAddress.of(placed));

        ItemDisplay existing = findVirtualDisplay(placed, false);
        if (existing != null) {
            existing.setItemStack(block.itemStack().get());
            existing.setPersistent(false);
            existing.setViewRange(displayViewRange);
            existing.getPersistentDataContainer().set(
                    VIRTUAL_BLOCK_ID_KEY, PersistentDataType.STRING, block.key().asString());
            trackDisplay(placed, existing);
            announceBedrockBlock(placed, block);
            return;
        }

        Location location = placed.getLocation().add(0.5, 0.5, 0.5);
        ItemDisplay display = placed.getWorld().spawn(location, ItemDisplay.class, spawned -> {
            spawned.setItemStack(block.itemStack().get());
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            // The compact VirtualBlockStore is persistent state. Renderer entities are not.
            spawned.setPersistent(false);
            spawned.setViewRange(displayViewRange);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setSilent(true);
            spawned.getPersistentDataContainer().set(
                    VIRTUAL_BLOCK_ID_KEY, PersistentDataType.STRING, block.key().asString());
        });
        trackDisplay(placed, display);
        announceBedrockBlock(placed, block);
    }

    /**
     * Spawns one {@link ItemDisplay} per bone and remembers the pivots and clips that
     * drive them. Every display is placed at the block centre; a bone's authored pivot is
     * applied as a transform correction, so the rest pose is identical to the static
     * model.
     */
    private void spawnOrRefreshRig(@NotNull org.bukkit.block.Block placed,
                                   @NotNull Block block,
                                   @NotNull BlockbenchRigRegistry.Rig rig) {
        BlockAddress address = BlockAddress.of(placed);
        UUID previous = renderedDisplays.remove(address);
        if (previous != null) {
            Entity stale = Bukkit.getEntity(previous);
            if (stale != null) {
                stale.remove();
            }
        }
        removeRig(address);

        Location center = placed.getLocation().add(0.5, 0.5, 0.5);
        // Remove every renderer at this block, not just the one in the lookup map. A
        // plugin reload can leave a previous single display or a partial rig behind.
        for (Entity entity : placed.getWorld().getNearbyEntities(center,
                0.45, 0.45, 0.45, candidate -> candidate instanceof ItemDisplay)) {
            if (entity.getPersistentDataContainer().has(VIRTUAL_BLOCK_ID_KEY,
                    PersistentDataType.STRING)) {
                entity.remove();
            }
        }
        List<RigBone> bones = new ArrayList<>();
        for (BlockbenchRigRegistry.BoneRig bone : rig.bones()) {
            ItemStack stack = boneStack(bone.modelKey());
            ItemDisplay display = center.getWorld().spawn(center, ItemDisplay.class, spawned -> {
                spawned.setItemStack(stack);
                spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                spawned.setPersistent(false);
                spawned.setViewRange(displayViewRange);
                spawned.setInvulnerable(true);
                spawned.setGravity(false);
                spawned.setSilent(true);
                // Two-tick client interpolation matches the sampler period.
                spawned.setInterpolationDuration(2);
                spawned.getPersistentDataContainer().set(VIRTUAL_BLOCK_ID_KEY,
                        PersistentDataType.STRING, block.key().asString());
            });
            bones.add(new RigBone(display.getUniqueId(), bone.boneId(), bone.pivot()));
        }
        io.kalo.content.block.definition.BlockAnimation animation =
                block.definition().animation();
        List<io.kalo.model.animation.ModelAnimations.Clip> idle = rig.animations();
        io.kalo.model.animation.ModelAnimations.Clip namedIdle = animation == null
                ? null : io.kalo.model.animation.ModelAnimations.find(idle,
                animation.idle());
        if (namedIdle != null) {
            idle = List.of(namedIdle);
        }
        RigInstance instance = new RigInstance(System.nanoTime(), idle,
                rig.allAnimations(), rig.bones(), List.copyOf(bones),
                io.kalo.platform.java.RigControllers.create(rig.controllers(),
                        rig.allAnimations()));
        instance.task = Bukkit.getRegionScheduler().runAtFixedRate(
                io.kalo.utils.Plugins.plugin(), center,
                ignored -> tickRig(address), 2L, 2L);
        rigs.put(address, instance);
        renderedByChunk.computeIfAbsent(address.chunk(),
                ignored -> ConcurrentHashMap.newKeySet()).add(address);
    }

    /** One {@code ItemDisplay} payload whose item model is a single bone's cubes. */
    private static @NotNull ItemStack boneStack(@NotNull Key modelKey) {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, modelKey);
        return stack;
    }

    /**
     * Runs on the region that owns this block every two ticks. Sampled poses are applied
     * to the per-bone displays; the client interpolates between them.
     */
    private void tickRig(@NotNull BlockAddress address) {
        RigInstance rig = rigs.get(address);
        if (rig == null) {
            return;
        }
        long now = System.nanoTime();
        io.kalo.model.animation.ModelAnimations.Pose pose =
                new io.kalo.model.animation.ModelAnimations.Pose(java.util.Map.of());
        if (rig.activeClip != null) {
            double elapsed = (now - rig.activeStartNanos) / 1_000_000_000.0;
            if (rig.activeClip.finished(elapsed)) {
                rig.activeClip = null;
            } else {
                pose = rig.activeClip.sampleOnce(elapsed);
            }
        }
        if (rig.activeClip == null) {
            double elapsed = (now - rig.startedAtNanos) / 1_000_000_000.0;
            if (!rig.controllers.isEmpty()) {
                io.kalo.model.animation.ControllerRuntime.Signals signals =
                        new io.kalo.model.animation.ControllerRuntime.Signals(
                                false, true, false, false, false, false, elapsed, 0);
                pose = io.kalo.platform.java.RigControllers.sample(rig.controllers,
                        signals, 0.1, elapsed);
            }
            if (pose.bones().isEmpty()) {
                for (io.kalo.model.animation.ModelAnimations.Clip clip : rig.idleClips) {
                    pose = pose.merge(clip.sample(elapsed));
                }
            }
        }
        java.util.Map<String, org.bukkit.util.Transformation> transforms =
                BlockbenchRigTransforms.pose(rig.boneRigs, pose, 1.0, 0.0);
        for (RigBone bone : rig.bones) {
            Entity entity = Bukkit.getEntity(bone.entityId());
            if (!(entity instanceof ItemDisplay display) || !display.isValid()) {
                // The chunk went away without an unload event reaching us; the store
                // will rebuild the rig on the next load.
                removeRig(address);
                return;
            }
            org.bukkit.util.Transformation transform = transforms.get(bone.boneId());
            if (transform != null) {
                display.setTransformation(transform);
            }
        }
    }

    /**
     * Plays an interaction clip once on the per-bone rig; idle loops resume when it ends.
     */
    private boolean startInteraction(@NotNull org.bukkit.block.Block block,
                                     @NotNull String animation) {
        RigInstance rig = rigs.get(BlockAddress.of(block));
        if (rig == null) {
            return false;
        }
        io.kalo.model.animation.ModelAnimations.Clip clip =
                io.kalo.model.animation.ModelAnimations.find(rig.allClips, animation);
        if (clip == null) {
            return false;
        }
        rig.activeClip = clip;
        rig.activeStartNanos = System.nanoTime();
        return true;
    }

    private void removeRig(@NotNull BlockAddress address) {
        RigInstance rig = rigs.remove(address);
        if (rig == null) {
            return;
        }
        if (rig.task != null) {
            rig.task.cancel();
        }
        for (RigBone bone : rig.bones) {
            Entity entity = Bukkit.getEntity(bone.entityId());
            if (entity != null) {
                entity.remove();
            }
        }
    }

    private static void announceBedrockBlock(@NotNull org.bukkit.block.Block placed,
                                             @NotNull Block block) {
        GeyserIntegration.support().blockPlaced(placed.getWorld().getUID(),
                placed.getX(), placed.getY(), placed.getZ(), block.key().asString());
    }

    private record RigBone(@NotNull UUID entityId, @NotNull String boneId,
                           @NotNull io.kalo.model.ModelIR.Vec3 pivot) {
    }

    private static final class RigInstance {
        private final long startedAtNanos;
        private final List<io.kalo.model.animation.ModelAnimations.Clip> idleClips;
        private final List<io.kalo.model.animation.ModelAnimations.Clip> allClips;
        private final List<BlockbenchRigRegistry.BoneRig> boneRigs;
        private final List<RigBone> bones;
        private final List<io.kalo.model.animation.ControllerRuntime> controllers;
        private io.papermc.paper.threadedregions.scheduler.ScheduledTask task;
        private io.kalo.model.animation.ModelAnimations.Clip activeClip;
        private long activeStartNanos;

        private RigInstance(long startedAtNanos,
                            List<io.kalo.model.animation.ModelAnimations.Clip> idleClips,
                            List<io.kalo.model.animation.ModelAnimations.Clip> allClips,
                            List<BlockbenchRigRegistry.BoneRig> boneRigs,
                            List<RigBone> bones,
                            List<io.kalo.model.animation.ControllerRuntime> controllers) {
            this.startedAtNanos = startedAtNanos;
            this.idleClips = idleClips;
            this.allClips = allClips;
            this.boneRigs = boneRigs;
            this.bones = bones;
            this.controllers = controllers;
        }

        private long startedAtNanos() {
            return startedAtNanos;
        }
    }

    /**
     * Stops vanilla rewriting a carrier or virtual anchor out from under custom content.
     *
     * <p>Only where custom content actually is. Cancelling on the material alone reached
     * every note block, tripwire and scaffolding on the server, Kalo's or not — so vanilla
     * scaffolding stopped collapsing when unsupported and tripwire stopped updating, on
     * any server that merely had Kalo installed.</p>
     *
     * <p>The material test stays as a cheap gate: physics fires constantly and almost
     * nothing is a carrier, so the identity lookup is only paid for on the few that are.</p>
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        org.bukkit.block.Block block = event.getBlock();
        if (!isCarrier(block.getType()) && block.getType() != VIRTUAL_ANCHOR) {
            return;
        }
        if (resolve(block) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        org.bukkit.block.Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        String id = virtualId(clicked);
        if (id == null && clicked.getType() == VIRTUAL_ANCHOR) {
            // One-time compatibility path for worlds created by the old persistent-entity
            // implementation. Normal lookups never scan nearby entities.
            id = importLegacyAt(clicked);
        }
        Block virtual = id != null ? resolveContent(id) : null;

        // Keep an orphan breakable when its pack disappeared after it was placed.
        if (clicked.getType() == VIRTUAL_ANCHOR && id != null && virtual == null) {
            event.setCancelled(true);
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                virtualBlocks.remove(clicked.getWorld().getUID(), clicked.getX(), clicked.getY(), clicked.getZ());
                removeVirtualDisplay(clicked);
                clicked.setType(Material.AIR, false);
            }
            return;
        }

        if (virtual != null) {
            event.setCancelled(true);
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                breakVirtual(clicked, event.getPlayer(), virtual);
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                io.kalo.content.block.definition.BlockAnimation animation =
                        virtual.definition().animation();
                if (animation != null && animation.interact() != null) {
                    startInteraction(clicked, animation.interact());
                    GeyserIntegration.support().blockAnimationStarted(
                            clicked.getWorld().getUID(), clicked.getX(), clicked.getY(),
                            clicked.getZ(), virtual.key().asString(),
                            animation.interact());
                }
            }
        } else if (JavaBlockRules.preventsTuning(clicked.getType(), event.getAction())
                && resolve(clicked) != null) {
            // Right-clicking a note block re-tunes it, which moves it to a state the pack
            // draws as a different custom block — or as none. Only note blocks have that
            // problem; tripwire and scaffolding have nothing to tune.
            //
            // Denying the block's own interaction rather than cancelling the event matters
            // twice over. Cancelling outright also swallowed LEFT_CLICK_BLOCK, which is
            // where mining starts, so no native custom block could be broken at all — the
            // standard trick for an indestructible block, done by accident. And leaving
            // the item hand alone keeps blocks placeable against this one.
            //
            // JavaBlockRules.preventsTuning had encoded exactly this, tests and all, and
            // nothing in production called it.
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
    }

    private void breakVirtual(@NotNull org.bukkit.block.Block broken,
                              @NotNull Player player,
                              @NotNull Block block) {
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (block.definition().behaviour().unbreakable() && !creative) {
            return;
        }

        virtualBlocks.remove(broken.getWorld().getUID(), broken.getX(), broken.getY(), broken.getZ());
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

        event.setDropItems(false);
        if (virtualId(broken) != null) {
            virtualBlocks.remove(broken.getWorld().getUID(), broken.getX(), broken.getY(), broken.getZ());
            removeVirtualDisplay(broken);
        }
        if (!creative) {
            broken.getWorld().dropItemNaturally(broken.getLocation().add(0.5, 0.5, 0.5),
                    block.itemStack().get());
        }
    }

    /** Explosions remove virtual index records as well as renderer entities. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::removeVirtualIfPresent);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::removeVirtualIfPresent);
    }

    private void removeVirtualIfPresent(@NotNull org.bukkit.block.Block block) {
        if (virtualId(block) == null) {
            return;
        }
        virtualBlocks.remove(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        removeVirtualDisplay(block);
    }

    /** Identifies custom content drawn at a world block, native or virtual. */
    public @Nullable Block resolve(@NotNull org.bukkit.block.Block worldBlock) {
        String virtualId = virtualId(worldBlock);
        if (virtualId != null) {
            Block virtual = resolveContent(virtualId);
            if (virtual != null && virtual.definition().java().mode() == JavaBlockMode.VIRTUAL) {
                return virtual;
            }
        }

        if (!isCarrier(worldBlock.getType())) {
            return null;
        }

        String contentKey = byBlockData.get(worldBlock.getBlockData().getAsString(false));
        return contentKey != null ? resolveContent(contentKey) : null;
    }

    private @Nullable String virtualId(@NotNull org.bukkit.block.Block worldBlock) {
        if (worldBlock.getType() != VIRTUAL_ANCHOR) {
            return null;
        }
        return virtualBlocks.get(worldBlock.getWorld().getUID(),
                worldBlock.getX(), worldBlock.getY(), worldBlock.getZ());
    }

    /**
     * Imports one renderer from the pre-index implementation. This is intentionally a
     * fallback, not the normal lookup path.
     */
    private @Nullable String importLegacyAt(@NotNull org.bukkit.block.Block worldBlock) {
        ItemDisplay display = findVirtualDisplay(worldBlock, true);
        if (display == null) {
            return null;
        }
        String id = display.getPersistentDataContainer()
                .get(VIRTUAL_BLOCK_ID_KEY, PersistentDataType.STRING);
        if (id != null) {
            virtualBlocks.put(worldBlock.getWorld().getUID(), worldBlock.getX(), worldBlock.getY(), worldBlock.getZ(), id);
            display.setPersistent(false);
            trackDisplay(worldBlock, display);
        }
        return id;
    }

    private void removeVirtualDisplay(@NotNull org.bukkit.block.Block worldBlock) {
        BlockAddress address = BlockAddress.of(worldBlock);
        removeRig(address);
        UUID uuid = renderedDisplays.remove(address);
        removeAddressFromChunk(address);
        GeyserIntegration.support().blockRemoved(worldBlock.getWorld().getUID(),
                worldBlock.getX(), worldBlock.getY(), worldBlock.getZ());
        if (uuid != null) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof ItemDisplay display) {
                display.remove();
                return;
            }
        }

        // Cleanup/migration fallback only. Normal resolve never reaches an entity scan.
        ItemDisplay fallback = findVirtualDisplay(worldBlock, true);
        if (fallback != null) {
            fallback.remove();
        }
    }

    private @Nullable ItemDisplay findVirtualDisplay(@NotNull org.bukkit.block.Block worldBlock,
                                                      boolean allowFallbackScan) {
        BlockAddress address = BlockAddress.of(worldBlock);
        UUID uuid = renderedDisplays.get(address);
        if (uuid != null) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof ItemDisplay display && sameBlock(display.getLocation(), worldBlock)) {
                return display;
            }
            renderedDisplays.remove(address, uuid);
            removeAddressFromChunk(address);
        }

        if (!allowFallbackScan) {
            return null;
        }

        Location center = worldBlock.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : worldBlock.getWorld().getNearbyEntities(
                center, 0.35, 0.35, 0.35, candidate -> candidate instanceof ItemDisplay)) {
            if (entity instanceof ItemDisplay display
                    && sameBlock(display.getLocation(), worldBlock)
                    && display.getPersistentDataContainer().has(VIRTUAL_BLOCK_ID_KEY, PersistentDataType.STRING)) {
                trackDisplay(worldBlock, display);
                return display;
            }
        }
        return null;
    }

    private static boolean sameBlock(@NotNull Location location, @NotNull org.bukkit.block.Block block) {
        return location.getBlockX() == block.getX()
                && location.getBlockY() == block.getY()
                && location.getBlockZ() == block.getZ();
    }

    private void trackDisplay(@NotNull org.bukkit.block.Block block, @NotNull ItemDisplay display) {
        BlockAddress address = BlockAddress.of(block);
        UUID previous = renderedDisplays.put(address, display.getUniqueId());
        if (previous != null && !previous.equals(display.getUniqueId())) {
            Entity duplicate = Bukkit.getEntity(previous);
            if (duplicate instanceof ItemDisplay old) {
                old.remove();
            }
        }
        renderedByChunk.computeIfAbsent(address.chunk(), ignored -> ConcurrentHashMap.newKeySet())
                .add(address);
    }

    private void removeAddressFromChunk(@NotNull BlockAddress address) {
        ChunkAddress chunk = address.chunk();
        Set<BlockAddress> addresses = renderedByChunk.get(chunk);
        if (addresses != null) {
            addresses.remove(address);
            if (addresses.isEmpty()) {
                renderedByChunk.remove(chunk, addresses);
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        syncChunk(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        ChunkAddress chunkAddress = new ChunkAddress(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        Set<BlockAddress> addresses = renderedByChunk.remove(chunkAddress);
        if (addresses != null) {
            for (BlockAddress address : addresses) {
                removeRig(address);
                renderedDisplays.remove(address);
                GeyserIntegration.support().blockRemoved(chunk.getWorld().getUID(),
                        address.x(), address.y(), address.z());
            }
        }
        // Renderer entities are non-persistent; the store survives and recreates them on
        // the next load of this chunk.
    }

    /**
     * Migrates old persistent renderers once, reconciles the index with the actual world,
     * and ensures every indexed block in this loaded chunk has a renderer.
     */
    private void syncChunk(@NotNull Chunk chunk) {
        World world = chunk.getWorld();

        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof ItemDisplay display)) {
                continue;
            }
            String id = display.getPersistentDataContainer()
                    .get(VIRTUAL_BLOCK_ID_KEY, PersistentDataType.STRING);
            if (id == null) {
                continue;
            }
            Location location = display.getLocation();
            org.bukkit.block.Block anchor = world.getBlockAt(
                    location.getBlockX(), location.getBlockY(), location.getBlockZ());
            if (anchor.getType() != VIRTUAL_ANCHOR) {
                // A stale renderer with no anchor has no world content left to represent.
                display.remove();
                continue;
            }
            if (virtualBlocks.get(world.getUID(), anchor.getX(), anchor.getY(), anchor.getZ()) == null) {
                virtualBlocks.put(world.getUID(), anchor.getX(), anchor.getY(), anchor.getZ(), id);
            }
            display.setPersistent(false);
            trackDisplay(anchor, display);
        }

        for (VirtualBlockStore.Entry entry : virtualBlocks.entries(world.getUID(), chunk.getX(), chunk.getZ())) {
            org.bukkit.block.Block anchor = world.getBlockAt(entry.x(), entry.y(), entry.z());
            if (anchor.getType() != VIRTUAL_ANCHOR) {
                // The block was changed while Kalo was offline. The world wins; stale index
                // entries must not resurrect deleted content.
                virtualBlocks.remove(world.getUID(), entry.x(), entry.y(), entry.z());
                removeVirtualDisplay(anchor);
                continue;
            }
            Block content = resolveContent(entry.contentId());
            if (content != null && content.definition().java().mode() == JavaBlockMode.VIRTUAL) {
                spawnOrRefreshVirtualDisplay(anchor, content);
            }
        }
    }

    /** Schedules reconciliation for chunks that were already loaded before Kalo enabled. */
    public void initializeLoadedChunks(@NotNull JavaPlugin plugin) {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scheduleChunkSync(plugin, world, chunk.getX(), chunk.getZ());
            }
        }
    }

    /** Refreshes renderer item stacks and registry lookups after a Kalo hot reload. */
    public void refreshLoadedChunks(@NotNull JavaPlugin plugin) {
        initializeLoadedChunks(plugin);
    }

    /**
     * Applies an adaptive network/render budget. The expensive reconciliation is scheduled
     * per region, so a pressure transition never performs world mutation on the sampler thread.
     */
    public void applyPerformanceBudget(@NotNull JavaPlugin plugin, @NotNull RuntimeBudget budget) {
        displayViewRange = (float) Math.max(0.25, Math.min(1.0, budget.renderDistanceScale()));
        refreshLoadedChunks(plugin);
    }

    private void scheduleChunkSync(@NotNull JavaPlugin plugin,
                                   @NotNull World world,
                                   int chunkX,
                                   int chunkZ) {
        Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, () -> {
            if (world.isChunkLoaded(chunkX, chunkZ)) {
                syncChunk(world.getChunkAt(chunkX, chunkZ));
            }
        });
    }

    public int renderedDisplayCount() {
        return renderedDisplays.size();
    }

    public int indexedVirtualBlockCount() {
        return virtualBlocks.size();
    }

    private boolean isCarrier(@NotNull Material material) {
        return carrierMaterials.contains(material);
    }

    private @Nullable Block resolveContent(@NotNull String id) {
        return contentById.get(id);
    }

    private record ChunkAddress(@NotNull UUID worldId, int x, int z) {
    }

    private record BlockAddress(@NotNull UUID worldId, int x, int y, int z) {
        static @NotNull BlockAddress of(@NotNull org.bukkit.block.Block block) {
            return new BlockAddress(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        @NotNull ChunkAddress chunk() {
            return new ChunkAddress(worldId, x >> 4, z >> 4);
        }
    }
}

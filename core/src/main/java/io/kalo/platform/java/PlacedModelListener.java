package io.kalo.platform.java;

import io.kalo.content.item.Item;
import io.kalo.content.item.definition.PlacementDefinition;
import io.kalo.model.animation.ModelAnimations;
import io.kalo.integration.GeyserIntegration;
import io.kalo.manager.RegistryManager;
import io.kalo.utils.Constants;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places an item's Blockbench model as a world-space display entity.
 *
 * <p>The Java side is the saved, authoritative piece: an {@link ItemDisplay} carries the
 * item model and an {@link Interaction} provides a hitbox. Bedrock cannot render display
 * entities, so the same placement is announced through {@link GeyserIntegration} and a
 * client entity is spawned for nearby Bedrock sessions when the bridge is available.</p>
 *
 * <p>No sidecar file is written. Both entities are chunk-saved; a chunk load re-announces
 * the placement and an unload/removal takes the Bedrock entity down again.</p>
 */
public final class PlacedModelListener implements Listener {

    public static final NamespacedKey MODEL_KEY = new NamespacedKey(Constants.PLUGIN_ID,
            "placed_model");
    public static final NamespacedKey DISPLAY_KEY = new NamespacedKey(Constants.PLUGIN_ID,
            "placed_display");
    public static final NamespacedKey SCALE_KEY = new NamespacedKey(Constants.PLUGIN_ID,
            "placed_scale");
    public static final NamespacedKey STORAGE_KEY = new NamespacedKey(Constants.PLUGIN_ID,
            "placed_storage");
    public static final NamespacedKey STATE_KEY = new NamespacedKey(Constants.PLUGIN_ID,
            "placed_state");
    public static final NamespacedKey STORAGE_ROWS_KEY = new NamespacedKey(Constants.PLUGIN_ID,
            "placed_storage_rows");

    /** Player seat marker per placed hitbox. Seats are deliberately not saved. */
    private final Map<UUID, UUID> seats = new ConcurrentHashMap<>();
    private final Map<UUID, PlacedStorageHolder> openStorages = new ConcurrentHashMap<>();
    private final Map<UUID, Location> ownedBlocks = new ConcurrentHashMap<>();
    /** Per-bone Java animation rigs, keyed by their persistent interaction hitbox. */
    private final Map<UUID, PlacedRig> rigs = new ConcurrentHashMap<>();

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null
                || event.getPlayer().isSneaking()) {
            return;
        }
        ItemStack stack = event.getItem();
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        String id = stack.getItemMeta().getPersistentDataContainer()
                .get(JavaItemCompiler.ITEM_ID_KEY, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        Item item = RegistryManager.GlobalRegistries.registries().item()
                .get(Key.key(id)).orElse(null);
        if (item == null || item.definition().placement() == null) {
            return;
        }
        var target = event.getClickedBlock().getRelative(event.getBlockFace());
        if (!target.isEmpty() && !target.isReplaceable()) {
            return;
        }
        PlacementDefinition placement = item.definition().placement();
        Location location = target.getLocation().add(0.5, 0, 0.5);
        if (!location.getWorld().getNearbyEntities(location, 0.5, 0.5, 0.5, entity ->
                entity instanceof Interaction interaction
                        && interaction.getPersistentDataContainer().has(MODEL_KEY,
                        PersistentDataType.STRING)).isEmpty()) {
            return;
        }

        float yaw = event.getPlayer().getLocation().getYaw();
        UUID[] displayId = new UUID[1];
        Interaction hitbox = target.getWorld().spawn(location, Interaction.class, spawned -> {
            spawned.setInteractionWidth((float) placement.width());
            spawned.setInteractionHeight((float) placement.height());
            spawned.setResponsive(true);
            spawned.setPersistent(true);
            spawned.setSilent(true);
            spawned.setGravity(false);
            spawned.getPersistentDataContainer().set(MODEL_KEY, PersistentDataType.STRING,
                    item.key().asString());
            spawned.getPersistentDataContainer().set(SCALE_KEY, PersistentDataType.FLOAT,
                    (float) placement.scale());
            if (placement.storage() != null) {
                spawned.getPersistentDataContainer().set(STORAGE_ROWS_KEY,
                        PersistentDataType.INTEGER, placement.storage().rows());
            }
            spawned.setRotation(yaw, 0);
        });

        ItemDisplay display = target.getWorld().spawn(location, ItemDisplay.class, spawned -> {
            spawned.setItemStack(item.itemStack().get());
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setPersistent(true);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setSilent(true);
            spawned.setViewRange(64f);
            float scale = (float) placement.scale();
            spawned.setTransformation(new Transformation(new Vector3f(),
                    new AxisAngle4f(), new Vector3f(scale, scale, scale),
                    new AxisAngle4f()));
            spawned.setRotation(yaw, 0);
            spawned.getPersistentDataContainer().set(MODEL_KEY, PersistentDataType.STRING,
                    item.key().asString());
            spawned.getPersistentDataContainer().set(DISPLAY_KEY, PersistentDataType.STRING,
                    hitbox.getUniqueId().toString());
        });
        displayId[0] = display.getUniqueId();
        hitbox.getPersistentDataContainer().set(DISPLAY_KEY, PersistentDataType.STRING,
                displayId[0].toString());

        applyState(event.getPlayer(), hitbox, item, placement, 0);
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction hitbox)) {
            return;
        }
        String model = hitbox.getPersistentDataContainer().get(MODEL_KEY,
                PersistentDataType.STRING);
        if (model == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer().isSneaking()) {
            remove(hitbox);
            return;
        }
        Item item = RegistryManager.GlobalRegistries.registries().item()
                .get(Key.key(model)).orElse(null);
        PlacementDefinition placement = item == null ? null
                : item.definition().placement();
        if (placement == null) {
            return;
        }
        if (placement.storage() != null) {
            openStorage(event.getPlayer(), hitbox, item, placement);
            return;
        }
        if (!placement.states().isEmpty()) {
            cycleState(event.getPlayer(), hitbox, item, placement);
            return;
        }
        if (placement.seat() != null && !event.getPlayer().isSneaking()) {
            sit(event.getPlayer(), hitbox, placement.seat());
            return;
        }
        if (placement.interactAnimation() != null) {
            startInteraction(hitbox, placement.interactAnimation());
            GeyserIntegration.support().animationStarted(hitbox.getUniqueId(), model,
                    placement.interactAnimation());
        }
    }


    private void openStorage(@NotNull org.bukkit.entity.Player player,
                             @NotNull Interaction hitbox, @NotNull Item item,
                             @NotNull PlacementDefinition placement) {
        int rows = placement.storage() == null ? 3 : placement.storage().rows();
        String title = placement.storage() != null && placement.storage().title() != null
                ? placement.storage().title() : humanize(item.key().value());
        PlacedStorageHolder holder = new PlacedStorageHolder(hitbox.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                Component.text(title));
        holder.inventory = inventory;
        loadStorage(hitbox, inventory);
        openStorages.put(hitbox.getUniqueId(), holder);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onStorageClose(@NotNull InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PlacedStorageHolder holder) {
            Entity entity = Bukkit.getEntity(holder.hitboxId);
            if (entity instanceof Interaction hitbox) {
                saveStorage(hitbox, event.getInventory());
            }
            openStorages.remove(holder.hitboxId);
        }
    }

    private static void loadStorage(@NotNull Interaction hitbox, @NotNull Inventory inventory) {
        String encoded = hitbox.getPersistentDataContainer().get(STORAGE_KEY,
                PersistentDataType.STRING);
        ItemStack[] contents = PlacedStorageCodec.decode(encoded, inventory.getSize());
        if (contents == null) {
            return;
        }
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] != null) {
                inventory.setItem(slot, contents[slot]);
            }
        }
    }

    private static void saveStorage(@NotNull Interaction hitbox, @NotNull Inventory inventory) {
        hitbox.getPersistentDataContainer().set(STORAGE_KEY, PersistentDataType.STRING,
                PlacedStorageCodec.encode(inventory.getContents()));
    }

    private void dropStorage(@NotNull Interaction hitbox) {
        PlacedStorageHolder holder = openStorages.remove(hitbox.getUniqueId());
        Inventory inventory = holder == null ? null : holder.inventory;
        if (inventory == null) {
            int rows = hitbox.getPersistentDataContainer().getOrDefault(STORAGE_ROWS_KEY,
                    PersistentDataType.INTEGER, 3);
            inventory = Bukkit.createInventory(null, Math.max(9, Math.min(54, rows * 9)));
            loadStorage(hitbox, inventory);
        }
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && !stack.getType().isAir()) {
                hitbox.getWorld().dropItemNaturally(hitbox.getLocation(), stack);
            }
        }
        inventory.clear();
        hitbox.getPersistentDataContainer().remove(STORAGE_KEY);
    }

    private void cycleState(@NotNull org.bukkit.entity.Player player,
                            @NotNull Interaction hitbox, @NotNull Item item,
                            @NotNull PlacementDefinition placement) {
        int current = hitbox.getPersistentDataContainer().getOrDefault(STATE_KEY,
                PersistentDataType.INTEGER, 0);
        int next = (current + 1) % (placement.states().size() + 1);
        applyState(player, hitbox, item, placement, next);
    }

    private void applyState(@Nullable org.bukkit.entity.Player player,
                            @NotNull Interaction hitbox, @NotNull Item item,
                            @NotNull PlacementDefinition placement, int index) {
        PlacementDefinition.State state = index == 0 ? null
                : placement.states().get(index - 1);
        double scale = state != null && state.scale() != null
                ? state.scale() : placement.scale();
        int light = state != null && state.light() != null
                ? state.light() : placement.light();
        boolean solid = state != null && state.solid() != null && state.solid();
        Key model = state != null && state.model() != null ? state.model() : item.key();

        Item modelItem = RegistryManager.GlobalRegistries.registries().item()
                .get(model).orElse(item);
        if (!useRig(hitbox, modelItem.key(), scale,
                placement.idleAnimation())) {
            stopRig(hitbox.getUniqueId());
            Entity displayEntity = display(hitbox);
            ItemDisplay display = displayEntity instanceof ItemDisplay existing
                    ? existing
                    : spawnSingleDisplay(hitbox, modelItem, scale);
            display.setItemStack(modelItem.itemStack().get());
            display.setTransformation(new Transformation(new Vector3f(),
                    new AxisAngle4f(), new Vector3f((float) scale, (float) scale,
                    (float) scale), new AxisAngle4f()));
        }

        Location blockLocation = hitbox.getLocation().getBlock().getLocation();
        Location owned = ownedBlocks.remove(hitbox.getUniqueId());
        if (owned != null) {
            var block = owned.getBlock();
            if (block.getType() == org.bukkit.Material.BARRIER
                    || block.getType() == org.bukkit.Material.LIGHT) {
                block.setType(org.bukkit.Material.AIR, false);
            }
        }
        var block = blockLocation.getBlock();
        if (block.getType().isAir() || block.getType() == org.bukkit.Material.BARRIER
                || block.getType() == org.bukkit.Material.LIGHT) {
            if (solid) {
                block.setType(org.bukkit.Material.BARRIER, false);
                ownedBlocks.put(hitbox.getUniqueId(), blockLocation);
            } else if (light > 0) {
                block.setType(org.bukkit.Material.LIGHT, false);
                var data = block.getBlockData();
                if (data instanceof org.bukkit.block.data.type.Light lightData) {
                    lightData.setLevel(light);
                    block.setBlockData(lightData, false);
                }
                ownedBlocks.put(hitbox.getUniqueId(), blockLocation);
            }
        }
        if (state != null && state.sound() != null && player != null) {
            player.playSound(hitbox.getLocation(), state.sound(), 1f, 1f);
        }

        hitbox.getPersistentDataContainer().set(STATE_KEY, PersistentDataType.INTEGER, index);
        GeyserIntegration.support().rigStateChanged(hitbox.getUniqueId(),
                item.key().asString(), model.asString(), hitbox.getLocation().getX(),
                hitbox.getLocation().getY(), hitbox.getLocation().getZ(),
                hitbox.getLocation().getYaw(), (float) scale);
    }

    private static @NotNull ItemDisplay spawnSingleDisplay(@NotNull Interaction hitbox,
                                                            @NotNull Item item,
                                                            double scale) {
        ItemDisplay display = hitbox.getWorld().spawn(hitbox.getLocation(),
                ItemDisplay.class, spawned -> {
                    spawned.setItemStack(item.itemStack().get());
                    spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    spawned.setBillboard(Display.Billboard.FIXED);
                    spawned.setPersistent(true);
                    spawned.setInvulnerable(true);
                    spawned.setGravity(false);
                    spawned.setSilent(true);
                    spawned.setViewRange(64f);
                    spawned.setTransformation(new Transformation(new Vector3f(),
                            new AxisAngle4f(), new Vector3f((float) scale, (float) scale,
                            (float) scale), new AxisAngle4f()));
                    spawned.setRotation(hitbox.getLocation().getYaw(), 0f);
                    spawned.getPersistentDataContainer().set(MODEL_KEY,
                            PersistentDataType.STRING, item.key().asString());
                    spawned.getPersistentDataContainer().set(DISPLAY_KEY,
                            PersistentDataType.STRING, hitbox.getUniqueId().toString());
                });
        hitbox.getPersistentDataContainer().set(DISPLAY_KEY, PersistentDataType.STRING,
                display.getUniqueId().toString());
        return display;
    }

    /**
     * Replaces the single display with one display per bone when the model has authored
     * loop animations and a compiled rig. Returns false for the static path.
     */
    private boolean useRig(@NotNull Interaction hitbox, @NotNull Key modelKey,
                           double placementScale, @Nullable String idleAnimation) {
        BlockbenchRigRegistry.Rig rig = BlockbenchRigRegistry.getPlaced(modelKey);
        if (rig == null || !rig.animateable()) {
            return false;
        }
        stopRig(hitbox.getUniqueId());
        removeSingleDisplay(hitbox);

        List<BoneDisplay> displays = new ArrayList<>();
        for (BlockbenchRigRegistry.BoneRig bone : rig.bones()) {
            ItemStack stack = new ItemStack(Material.PAPER);
            stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL,
                    bone.modelKey());
            ItemDisplay display = hitbox.getWorld().spawn(hitbox.getLocation(),
                    ItemDisplay.class, spawned -> {
                        spawned.setItemStack(stack);
                        spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                        spawned.setBillboard(Display.Billboard.FIXED);
                        // The interaction is the saved state; rig displays are recreated on
                        // chunk load and must not become a second source of truth.
                        spawned.setPersistent(false);
                        spawned.setInvulnerable(true);
                        spawned.setGravity(false);
                        spawned.setSilent(true);
                        spawned.setViewRange(64f);
                        spawned.setInterpolationDuration(2);
                        spawned.setRotation(hitbox.getLocation().getYaw(), 0f);
                    });
            displays.add(new BoneDisplay(display.getUniqueId(), bone.boneId(),
                    bone.pivot()));
        }
        List<ModelAnimations.Clip> idle = rig.animations();
        ModelAnimations.Clip configuredIdle = ModelAnimations.find(idle, idleAnimation);
        if (configuredIdle != null) {
            idle = List.of(configuredIdle);
        }
        PlacedRig placed = new PlacedRig(displays, idle, rig.allAnimations(),
                rig.bones(), RigControllers.create(rig.controllers(),
                rig.allAnimations()), placementScale);
        placed.task = hitbox.getScheduler().runAtFixedRate(io.kalo.utils.Plugins.plugin(),
                ignored -> tickRig(hitbox.getUniqueId()), null, 2L, 2L);
        rigs.put(hitbox.getUniqueId(), placed);
        return true;
    }

    private void tickRig(@NotNull UUID hitboxId) {
        PlacedRig rig = rigs.get(hitboxId);
        if (rig == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(hitboxId);
        if (!(entity instanceof Interaction hitbox) || !hitbox.isValid()) {
            stopRig(hitboxId);
            return;
        }
        long now = System.nanoTime();
        ModelAnimations.Pose pose = new ModelAnimations.Pose(Map.of());
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
                pose = RigControllers.sample(rig.controllers, signals, 0.1, elapsed);
            }
            if (pose.bones().isEmpty()) {
                for (ModelAnimations.Clip clip : rig.idleClips) {
                    pose = pose.merge(clip.sample(elapsed));
                }
            }
        }
        java.util.Map<String, org.bukkit.util.Transformation> transforms =
                BlockbenchRigTransforms.pose(rig.boneRigs, pose,
                        rig.placementScale, 0.0);
        for (BoneDisplay bone : rig.bones) {
            Entity displayEntity = Bukkit.getEntity(bone.entityId());
            if (!(displayEntity instanceof ItemDisplay display) || !display.isValid()) {
                stopRig(hitboxId);
                return;
            }
            org.bukkit.util.Transformation transform = transforms.get(bone.boneId());
            if (transform != null) {
                display.setTransformation(transform);
            }
        }
    }

    /**
     * Plays an interaction clip once; when it finishes the idle loops resume. Returns
     * false when the model has no such clip, so Bedrock can still be told about it.
     */
    private boolean startInteraction(@NotNull Interaction hitbox, @NotNull String animation) {
        PlacedRig rig = rigs.get(hitbox.getUniqueId());
        if (rig == null) {
            return false;
        }
        ModelAnimations.Clip clip = ModelAnimations.find(rig.allClips, animation);
        if (clip == null) {
            return false;
        }
        rig.activeClip = clip;
        rig.activeStartNanos = System.nanoTime();
        return true;
    }

    private void stopRig(@NotNull UUID hitboxId) {
        PlacedRig rig = rigs.remove(hitboxId);
        if (rig == null) {
            return;
        }
        if (rig.task != null) {
            rig.task.cancel();
        }
        for (BoneDisplay bone : rig.bones) {
            Entity display = Bukkit.getEntity(bone.entityId());
            if (display != null) {
                display.remove();
            }
        }
    }

    private static void removeSingleDisplay(@NotNull Interaction hitbox) {
        String displayId = hitbox.getPersistentDataContainer().get(DISPLAY_KEY,
                PersistentDataType.STRING);
        if (displayId == null) {
            return;
        }
        try {
            Entity display = Bukkit.getEntity(UUID.fromString(displayId));
            if (display != null) {
                display.remove();
            }
        } catch (IllegalArgumentException ignored) {
            // Malformed id; the PDC entry is cleared below.
        }
        hitbox.getPersistentDataContainer().remove(DISPLAY_KEY);
    }

    private record BoneDisplay(@NotNull UUID entityId, @NotNull String boneId,
                               @NotNull io.kalo.model.ModelIR.Vec3 pivot) {
    }

    private static final class PlacedRig {
        private final List<BoneDisplay> bones;
        private final List<ModelAnimations.Clip> idleClips;
        private final List<ModelAnimations.Clip> allClips;
        private final List<BlockbenchRigRegistry.BoneRig> boneRigs;
        private final List<io.kalo.model.animation.ControllerRuntime> controllers;
        private final double placementScale;
        private final long startedAtNanos = System.nanoTime();
        private io.papermc.paper.threadedregions.scheduler.ScheduledTask task;
        private ModelAnimations.Clip activeClip;
        private long activeStartNanos;

        private PlacedRig(List<BoneDisplay> bones, List<ModelAnimations.Clip> idleClips,
                          List<ModelAnimations.Clip> allClips,
                          List<BlockbenchRigRegistry.BoneRig> boneRigs,
                          List<io.kalo.model.animation.ControllerRuntime> controllers,
                          double placementScale) {
            this.bones = List.copyOf(bones);
            this.idleClips = List.copyOf(idleClips);
            this.allClips = List.copyOf(allClips);
            this.boneRigs = List.copyOf(boneRigs);
            this.controllers = List.copyOf(controllers);
            this.placementScale = placementScale;
        }
    }

    private static @Nullable Entity display(@NotNull Interaction hitbox) {
        String displayId = hitbox.getPersistentDataContainer().get(DISPLAY_KEY,
                PersistentDataType.STRING);
        if (displayId == null) {
            return null;
        }
        try {
            return Bukkit.getEntity(UUID.fromString(displayId));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String humanize(String value) {
        String[] words = value.split("[_/]");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.isEmpty() ? value : result.toString();
    }

    private static final class PlacedStorageHolder implements InventoryHolder {
        private final UUID hitboxId;
        private Inventory inventory;

        private PlacedStorageHolder(UUID hitboxId) {
            this.hitboxId = hitboxId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private void sit(@NotNull org.bukkit.entity.Player player, @NotNull Interaction hitbox,
                     double height) {
        if (player.getVehicle() != null) {
            return;
        }
        ArmorStand seat = hitbox.getWorld().spawn(
                hitbox.getLocation().clone().add(0, height, 0), ArmorStand.class,
                spawned -> {
                    spawned.setInvisible(true);
                    spawned.setMarker(true);
                    spawned.setSmall(true);
                    spawned.setGravity(false);
                    spawned.setSilent(true);
                    spawned.setInvulnerable(true);
                    spawned.setPersistent(false);
                });
        if (!seat.addPassenger(player)) {
            seat.remove();
            return;
        }
        seats.put(hitbox.getUniqueId(), seat.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(@NotNull EntityDamageEvent event) {
        if (event.getEntity() instanceof Interaction hitbox
                && hitbox.getPersistentDataContainer().has(MODEL_KEY,
                PersistentDataType.STRING)) {
            event.setCancelled(true);
            remove(hitbox);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByPlayer(@NotNull EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Interaction hitbox
                && hitbox.getPersistentDataContainer().has(MODEL_KEY,
                PersistentDataType.STRING)) {
            event.setCancelled(true);
            remove(hitbox);
        }
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        // A Bedrock player joining after a model was placed has not had a chunk-load
        // event for that chunk, so ask the bridge to offer active models again.
        GeyserIntegration.support().refresh();
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        GeyserIntegration.support().forget(event.getPlayer().getUniqueId());
        java.util.List<UUID> stale = new java.util.ArrayList<>();
        seats.forEach((hitbox, seatId) -> {
            Entity seat = Bukkit.getEntity(seatId);
            if (seat != null && seat.getPassengers().contains(event.getPlayer())) {
                stale.add(hitbox);
            }
        });
        for (UUID hitbox : stale) {
            UUID seatId = seats.remove(hitbox);
            if (seatId != null) {
                Entity seat = Bukkit.getEntity(seatId);
                if (seat != null) {
                    seat.remove();
                }
            }
        }
    }

    @EventHandler
    public void onDismount(@NotNull EntityDismountEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Player)) {
            return;
        }
        UUID seatId = event.getDismounted().getUniqueId();
        seats.entrySet().removeIf(entry -> {
            if (!entry.getValue().equals(seatId)) {
                return false;
            }
            event.getDismounted().remove();
            return true;
        });
    }

    @EventHandler
    public void onChunkLoad(@NotNull ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (!(entity instanceof Interaction hitbox)) {
                continue;
            }
            String model = hitbox.getPersistentDataContainer().get(MODEL_KEY,
                    PersistentDataType.STRING);
            if (model == null) {
                continue;
            }
            Item item = RegistryManager.GlobalRegistries.registries().item()
                    .get(Key.key(model)).orElse(null);
            if (item != null && item.definition().placement() != null) {
                int index = hitbox.getPersistentDataContainer().getOrDefault(STATE_KEY,
                        PersistentDataType.INTEGER, 0);
                applyState(null, hitbox, item, item.definition().placement(), index);
            } else {
                Float scale = hitbox.getPersistentDataContainer().get(SCALE_KEY,
                        PersistentDataType.FLOAT);
                GeyserIntegration.support().rigPlaced(hitbox.getUniqueId(), model,
                        hitbox.getLocation().getX(), hitbox.getLocation().getY(),
                        hitbox.getLocation().getZ(), hitbox.getLocation().getYaw(),
                        scale == null ? 1f : scale);
            }
        }
    }

    @EventHandler
    public void onChunkUnload(@NotNull ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Interaction hitbox
                    && hitbox.getPersistentDataContainer().has(MODEL_KEY,
                    PersistentDataType.STRING)) {
                stopRig(hitbox.getUniqueId());
                UUID seat = seats.remove(hitbox.getUniqueId());
                if (seat != null) {
                    Entity marker = Bukkit.getEntity(seat);
                    if (marker != null) {
                        marker.remove();
                    }
                }
                GeyserIntegration.support().rigRemoved(hitbox.getUniqueId());
            }
        }
    }

    private void remove(@NotNull Interaction hitbox) {
        stopRig(hitbox.getUniqueId());
        dropStorage(hitbox);
        Location owned = ownedBlocks.remove(hitbox.getUniqueId());
        if (owned != null) {
            var block = owned.getBlock();
            if (block.getType() == org.bukkit.Material.BARRIER
                    || block.getType() == org.bukkit.Material.LIGHT) {
                block.setType(org.bukkit.Material.AIR, false);
            }
        }
        UUID seat = seats.remove(hitbox.getUniqueId());
        if (seat != null) {
            Entity marker = Bukkit.getEntity(seat);
            if (marker != null) {
                marker.remove();
            }
        }
        String displayId = hitbox.getPersistentDataContainer().get(DISPLAY_KEY,
                PersistentDataType.STRING);
        if (displayId != null) {
            try {
                Entity display = Bukkit.getEntity(UUID.fromString(displayId));
                if (display != null) {
                    display.remove();
                }
            } catch (IllegalArgumentException ignored) {
                // Malformed id: the interaction is still removed below.
            }
        }
        GeyserIntegration.support().rigRemoved(hitbox.getUniqueId());
        hitbox.remove();
    }
}

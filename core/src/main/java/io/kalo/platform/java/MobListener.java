package io.kalo.platform.java;

import io.kalo.content.mob.Mob;
import io.kalo.content.mob.MobDefinition;
import io.kalo.integration.GeyserIntegration;
import io.kalo.model.animation.ModelAnimations;
import io.kalo.manager.RegistryManager;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns and tracks custom mobs.
 *
 * <p>A custom mob is a real vanilla mob — the base type keeps its AI, hitbox, damage and
 * drops — made invisible while a display renders the Blockbench model. Bedrock gets a
 * client entity spawned at the same position and moved with the mob.</p>
 */
public final class MobListener implements Listener {

    /** Marks the base entity as a Kalo mob. */
    public static final NamespacedKey MOB_KEY =
            new NamespacedKey(Constants.PLUGIN_ID, "mob");
    /** Marks the display as belonging to a mob. */
    public static final NamespacedKey MOB_DISPLAY_KEY =
            new NamespacedKey(Constants.PLUGIN_ID, "mob_display");

    private final Map<UUID, Rig> rigs = new ConcurrentHashMap<>();

    /** Spawns one mob at a location. Returns null when the definition is unknown. */
    public @Nullable LivingEntity spawn(@NotNull Location location, @NotNull Mob mob) {
        MobDefinition definition = mob.definition();
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        Entity spawned = world.spawnEntity(location, definition.base());
        if (!(spawned instanceof LivingEntity living)) {
            spawned.remove();
            return null;
        }
        living.setPersistent(true);
        living.customName(definition.displayName());
        living.setCustomNameVisible(true);
        if (living.getAttribute(Attribute.MAX_HEALTH) != null) {
            living.getAttribute(Attribute.MAX_HEALTH).setBaseValue(definition.health());
            living.setHealth(Math.min(definition.health(),
                    living.getAttribute(Attribute.MAX_HEALTH).getValue()));
        }
        living.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING,
                mob.key().asString());
        // The custom model replaces the vanilla body; without one the base mob shows.
        living.setInvisible(MobModelRegistry.get(mob.key()) != null);
        attach(living, mob.key());
        return living;
    }

    private void attach(@NotNull LivingEntity entity, @NotNull Key mobKey) {
        if (rigs.containsKey(entity.getUniqueId())) {
            return;
        }
        MobModelRegistry.Piece piece = MobModelRegistry.get(mobKey);
        if (piece == null) {
            return;
        }
        BlockbenchRigRegistry.Rig boneRig = BlockbenchRigRegistry.getMob(mobKey);
        if (boneRig != null && boneRig.animateable()) {
            attachBoneRig(entity, mobKey, boneRig);
            return;
        }
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL,
                piece.modelKey());
        ItemDisplay display = entity.getWorld().spawn(entity.getLocation(),
                ItemDisplay.class, spawned -> {
                    spawned.setItemStack(stack);
                    spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    spawned.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
                    spawned.setPersistent(false);
                    spawned.setInvulnerable(true);
                    spawned.setGravity(false);
                    spawned.setSilent(true);
                    spawned.setViewRange(64f);
                    spawned.getPersistentDataContainer().set(MOB_DISPLAY_KEY,
                            PersistentDataType.STRING, entity.getUniqueId().toString());
                });
        display.setRotation(entity.getLocation().getYaw(), 0f);
        display.setTransformation(new Transformation(new Vector3f(0f, 0.5f, 0f),
                new Quaternionf(), new Vector3f(1f, 1f, 1f), new Quaternionf()));

        // If the model was compiled after this mob spawned, hide the vanilla body now.
        entity.setInvisible(true);
        Rig rig = Rig.single(display.getUniqueId());
        start(entity, mobKey, rig);
    }

    private void attachBoneRig(@NotNull LivingEntity entity, @NotNull Key mobKey,
                               @NotNull BlockbenchRigRegistry.Rig boneRig) {
        MobDefinition definition = mobDefinition(mobKey);
        List<ModelAnimations.Clip> idle = boneRig.animations();
        io.kalo.content.animation.RigAnimation config = definition == null
                ? io.kalo.content.animation.RigAnimation.none()
                : definition.animation();
        ModelAnimations.Clip named = ModelAnimations.find(idle, config.idle());
        if (named != null) {
            idle = List.of(named);
        }
        List<Rig.BoneDisplay> bones = new ArrayList<>();
        for (BlockbenchRigRegistry.BoneRig bone : boneRig.bones()) {
            ItemStack stack = new ItemStack(Material.PAPER);
            stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL,
                    bone.modelKey());
            ItemDisplay display = entity.getWorld().spawn(entity.getLocation(),
                    ItemDisplay.class, spawned -> {
                        spawned.setItemStack(stack);
                        spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                        spawned.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
                        spawned.setPersistent(false);
                        spawned.setInvulnerable(true);
                        spawned.setGravity(false);
                        spawned.setSilent(true);
                        spawned.setViewRange(64f);
                        spawned.setInterpolationDuration(2);
                        spawned.getPersistentDataContainer().set(MOB_DISPLAY_KEY,
                                PersistentDataType.STRING, entity.getUniqueId().toString());
                    });
            bones.add(new Rig.BoneDisplay(display.getUniqueId(), bone.boneId(),
                    bone.pivot()));
        }
        start(entity, mobKey, Rig.bones(bones, idle, boneRig.allAnimations(),
                boneRig.bones(), RigControllers.create(boneRig.controllers(),
                boneRig.allAnimations())));
    }

    private void start(@NotNull LivingEntity entity, @NotNull Key mobKey,
                       @NotNull Rig rig) {
        entity.setInvisible(true);
        rig.task = entity.getScheduler().runAtFixedRate(Plugins.plugin(),
                ignored -> follow(entity.getUniqueId()), null, 2L, 2L);
        rigs.put(entity.getUniqueId(), rig);
        Location location = entity.getLocation();
        GeyserIntegration.support().mobSpawned(entity.getUniqueId(), mobKey.asString(),
                entity.getWorld().getUID(), location.getX(), location.getY(),
                location.getZ(), location.getYaw());
    }

    private void follow(@NotNull UUID entityId) {
        Rig rig = rigs.get(entityId);
        if (rig == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(entityId);
        if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
            detach(entityId);
            return;
        }
        Location location = living.getLocation();
        if (!rig.bones.isEmpty()) {
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
                double dx = location.getX() - rig.lastX;
                double dz = location.getZ() - rig.lastZ;
                double distance = Math.sqrt(dx * dx + dz * dz);
                boolean moving = rig.seen && distance > 0.01;
                rig.lastX = location.getX();
                rig.lastZ = location.getZ();
                rig.seen = true;
                if (!rig.controllers.isEmpty()) {
                    ModelAnimations.Pose controlled = RigControllers.sample(
                            rig.controllers,
                            new io.kalo.model.animation.ControllerRuntime.Signals(
                                    moving, living.isOnGround(), living.isInWater(),
                                    false, false, living.getVelocity().getY() > 0.05,
                                    elapsed, moving ? distance / 0.1 : 0),
                            0.1, elapsed);
                    pose = pose.merge(controlled);
                }
                if (pose.bones().isEmpty()) {
                    for (ModelAnimations.Clip clip : rig.clips) {
                        pose = pose.merge(clip.sample(elapsed));
                    }
                }
            }
            java.util.Map<String, org.bukkit.util.Transformation> transforms =
                    BlockbenchRigTransforms.pose(rig.boneRigs, pose, 1.0, 0.5);
            for (Rig.BoneDisplay bone : rig.bones) {
                Entity displayEntity = Bukkit.getEntity(bone.entityId());
                if (!(displayEntity instanceof ItemDisplay display) || !display.isValid()) {
                    detach(entityId);
                    return;
                }
                display.teleport(location);
                display.setRotation(location.getYaw(), 0f);
                org.bukkit.util.Transformation transform = transforms.get(bone.boneId());
                if (transform != null) {
                    display.setTransformation(transform);
                }
            }
        } else {
            Entity displayEntity = Bukkit.getEntity(rig.displayId);
            if (!(displayEntity instanceof ItemDisplay display) || !display.isValid()) {
                detach(entityId);
                return;
            }
            display.teleport(location);
            display.setRotation(location.getYaw(), 0f);
        }
        if (++rig.ticks % 10 == 0) {
            GeyserIntegration.support().mobMoved(entityId, location.getX(),
                    location.getY(), location.getZ(), location.getYaw());
        }
    }

    private static @Nullable MobDefinition mobDefinition(@NotNull Key key) {
        return RegistryManager.GlobalRegistries.registries().mob().get(key)
                .map(Mob::definition).orElse(null);
    }

    private boolean startInteraction(@NotNull LivingEntity entity, @NotNull Key mobKey,
                                     @NotNull String animation) {
        Rig rig = rigs.get(entity.getUniqueId());
        if (rig == null || rig.bones.isEmpty()) {
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

    @EventHandler
    public void onInteract(@NotNull org.bukkit.event.player.PlayerInteractEntityEvent event) {
        String id = event.getRightClicked().getPersistentDataContainer().get(MOB_KEY,
                PersistentDataType.STRING);
        if (id == null || !(event.getRightClicked() instanceof LivingEntity living)) {
            return;
        }
        MobDefinition definition = mobDefinition(Key.key(id));
        if (definition == null || definition.animation().interact() == null) {
            return;
        }
        event.setCancelled(true);
        startInteraction(living, Key.key(id), definition.animation().interact());
        GeyserIntegration.support().mobAnimationStarted(living.getUniqueId(),
                definition.animation().interact());
    }

    private void detach(@NotNull UUID entityId) {
        Rig rig = rigs.remove(entityId);
        if (rig == null) {
            return;
        }
        if (rig.task != null) {
            rig.task.cancel();
        }
        if (!rig.bones.isEmpty()) {
            for (Rig.BoneDisplay bone : rig.bones) {
                Entity display = Bukkit.getEntity(bone.entityId());
                if (display != null) {
                    display.remove();
                }
            }
        } else {
            Entity display = Bukkit.getEntity(rig.displayId);
            if (display != null) {
                display.remove();
            }
        }
        GeyserIntegration.support().mobRemoved(entityId);
    }

    @EventHandler
    public void onDeath(@NotNull EntityDeathEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(MOB_KEY,
                PersistentDataType.STRING)) {
            detach(event.getEntity().getUniqueId());
        }
    }

    @EventHandler
    public void onRemove(@NotNull EntityRemoveEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(MOB_KEY,
                PersistentDataType.STRING)) {
            detach(event.getEntity().getUniqueId());
        }
    }

    @EventHandler
    public void onChunkLoad(@NotNull ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            String id = entity.getPersistentDataContainer().get(MOB_KEY,
                    PersistentDataType.STRING);
            if (id != null && entity instanceof LivingEntity living) {
                attach(living, Key.key(id));
            }
        }
    }

    @EventHandler
    public void onChunkUnload(@NotNull ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity.getPersistentDataContainer().has(MOB_KEY,
                    PersistentDataType.STRING)) {
                detach(entity.getUniqueId());
            }
        }
    }

    /** Reattaches mobs in chunks that were already loaded before Kalo started. */
    public void startAll() {
        org.bukkit.Bukkit.getGlobalRegionScheduler().run(Plugins.plugin(), ignored -> {
            for (World world : Bukkit.getWorlds()) {
                for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                    Bukkit.getRegionScheduler().execute(Plugins.plugin(), world,
                            chunk.getX(), chunk.getZ(), () -> {
                                if (!world.isChunkLoaded(chunk.getX(), chunk.getZ())) {
                                    return;
                                }
                                for (Entity entity : world.getChunkAt(chunk.getX(),
                                        chunk.getZ()).getEntities()) {
                                    String id = entity.getPersistentDataContainer().get(
                                            MOB_KEY, PersistentDataType.STRING);
                                    if (id != null && entity instanceof LivingEntity living) {
                                        attach(living, Key.key(id));
                                    }
                                }
                            });
                }
            }
        });
    }

    private static final class Rig {
        private final UUID displayId;
        private final List<BoneDisplay> bones;
        private final List<ModelAnimations.Clip> clips;
        private final List<ModelAnimations.Clip> allClips;
        private final List<BlockbenchRigRegistry.BoneRig> boneRigs;
        private final List<io.kalo.model.animation.ControllerRuntime> controllers;
        private final long startedAtNanos = System.nanoTime();
        private io.papermc.paper.threadedregions.scheduler.ScheduledTask task;
        private int ticks;
        private ModelAnimations.Clip activeClip;
        private long activeStartNanos;
        private double lastX;
        private double lastZ;
        private boolean seen;

        private Rig(UUID displayId, List<BoneDisplay> bones,
                    List<ModelAnimations.Clip> clips,
                    List<ModelAnimations.Clip> allClips,
                    List<BlockbenchRigRegistry.BoneRig> boneRigs,
                    List<io.kalo.model.animation.ControllerRuntime> controllers) {
            this.displayId = displayId;
            this.bones = List.copyOf(bones);
            this.clips = List.copyOf(clips);
            this.allClips = List.copyOf(allClips);
            this.boneRigs = List.copyOf(boneRigs);
            this.controllers = List.copyOf(controllers);
        }

        private static Rig single(UUID displayId) {
            return new Rig(displayId, List.of(), List.of(), List.of(), List.of(),
                    List.of());
        }

        private static Rig bones(List<BoneDisplay> bones,
                                 List<ModelAnimations.Clip> idleClips,
                                 List<ModelAnimations.Clip> allClips,
                                 List<BlockbenchRigRegistry.BoneRig> boneRigs,
                                 List<io.kalo.model.animation.ControllerRuntime> controllers) {
            return new Rig(null, bones, idleClips, allClips, boneRigs, controllers);
        }

        private record BoneDisplay(UUID entityId, String boneId,
                                   io.kalo.model.ModelIR.Vec3 pivot) {
        }
    }
}

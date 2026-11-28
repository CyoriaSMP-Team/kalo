package io.kalo.platform.java;

import io.kalo.content.armor.Armor;
import io.kalo.content.animation.RigAnimation;
import io.kalo.content.armor.ArmorSlot;
import io.kalo.manager.RegistryManager;
import io.kalo.model.animation.ModelAnimations;
import io.kalo.utils.Constants;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders Blockbench armor as a worn display rig on Java.
 *
 * <p>Java cannot attach a model to a player bone. Each equipped piece gets either one
 * display (static) or one display per bone when the model has authored loop animations.
 * The vanilla equipment layer is pointed at a transparent texture by
 * {@link JavaArmorCompiler}, so the displays are the only visible armor.</p>
 */
public final class WornArmorListener implements Listener {

    /** Marks a display this listener owns. */
    public static final NamespacedKey WORN_DISPLAY_KEY =
            new NamespacedKey(Constants.PLUGIN_ID, "worn_armor_display");

    private final Map<UUID, EnumMap<ArmorSlot, SlotRig>> displays =
            new ConcurrentHashMap<>();

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        start(event.getPlayer());
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        removeAll(event.getPlayer().getUniqueId());
    }

    /** Starts following every already-online player after a reload. */
    public void startAll(@NotNull Plugin plugin) {
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            start(player);
        }
    }

    private void start(@NotNull Player player) {
        // Five ticks is enough for a rigid model and costs one entity scan. Per-bone
        // models interpolate over the same period.
        player.getScheduler().runAtFixedRate(io.kalo.utils.Plugins.plugin(),
                task -> refresh(player), null, 5L, 5L);
    }

    private void refresh(@NotNull Player player) {
        if (!player.isOnline()) {
            removeAll(player.getUniqueId());
            return;
        }
        EnumMap<ArmorSlot, SlotRig> current = displays.computeIfAbsent(
                player.getUniqueId(), ignored -> new EnumMap<>(ArmorSlot.class));

        for (ArmorSlot slot : ArmorSlot.values()) {
            ItemStack stack = armorItem(player, slot);
            WornModelRegistry.Piece piece = pieceOf(stack);
            if (piece == null) {
                remove(current.remove(slot));
                continue;
            }
            SlotRig rig = current.get(slot);
            if (rig == null || !rig.contentKey.equals(piece.contentKey())
                    || !rig.valid()) {
                remove(rig);
                rig = spawn(player, piece);
                current.put(slot, rig);
            }
            update(player, rig);
        }
    }

    private static @NotNull ItemStack armorItem(@NotNull Player player, @NotNull ArmorSlot slot) {
        ItemStack stack = switch (slot) {
            case HEAD -> player.getInventory().getHelmet();
            case CHEST -> player.getInventory().getChestplate();
            case LEGS -> player.getInventory().getLeggings();
            case FEET -> player.getInventory().getBoots();
        };
        return stack == null ? new ItemStack(Material.AIR) : stack;
    }

    private static @Nullable WornModelRegistry.Piece pieceOf(@NotNull ItemStack stack) {
        if (stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        String id = stack.getItemMeta().getPersistentDataContainer()
                .get(JavaItemCompiler.ITEM_ID_KEY, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        Armor armor = RegistryManager.GlobalRegistries.registries().armor()
                .get(Key.key(id)).orElse(null);
        if (armor == null || armor.armorDefinition().armorModel() == null) {
            return null;
        }
        return WornModelRegistry.get(armor.key());
    }

    private static @NotNull SlotRig spawn(@NotNull Player player,
                                          WornModelRegistry.@NotNull Piece piece) {
        BlockbenchRigRegistry.Rig boneRig = BlockbenchRigRegistry.getWorn(piece.contentKey());
        if (boneRig != null && boneRig.animateable()) {
            List<BoneDisplay> bones = new ArrayList<>();
            for (BlockbenchRigRegistry.BoneRig bone : boneRig.bones()) {
                ItemDisplay display = spawnDisplay(player, bone.modelKey(), piece.contentKey());
                display.setInterpolationDuration(5);
                bones.add(new BoneDisplay(display.getUniqueId(), bone.boneId(),
                        bone.pivot()));
            }
            List<io.kalo.model.animation.ModelAnimations.Clip> idle =
                    boneRig.animations();
            io.kalo.model.animation.ModelAnimations.Clip named =
                    io.kalo.model.animation.ModelAnimations.find(idle,
                            piece.animation().idle());
            if (named != null) {
                idle = List.of(named);
            }
            return new SlotRig(piece.contentKey(), null, bones, idle,
                    boneRig.allAnimations(), boneRig.bones(),
                    RigControllers.create(boneRig.controllers(),
                            boneRig.allAnimations()), piece.animation());
        }
        ItemDisplay display = spawnDisplay(player, piece.modelKey(), piece.contentKey());
        return new SlotRig(piece.contentKey(), display.getUniqueId(), List.of(),
                List.of(), List.of(), List.of(), List.of(), piece.animation());
    }

    private static @NotNull ItemDisplay spawnDisplay(@NotNull Player player,
                                                     @NotNull Key modelKey,
                                                     @NotNull Key contentKey) {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, modelKey);
        return player.getWorld().spawn(player.getLocation(), ItemDisplay.class, spawned -> {
            spawned.setItemStack(stack);
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setSilent(true);
            spawned.setViewRange(64f);
            spawned.getPersistentDataContainer().set(WORN_DISPLAY_KEY,
                    PersistentDataType.STRING, contentKey.asString());
        });
    }

    private static void update(@NotNull Player player, @NotNull SlotRig rig) {
        Location feet = player.getLocation();
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
                if (!rig.controllers.isEmpty()) {
                    double speed = Math.sqrt(player.getVelocity().getX()
                            * player.getVelocity().getX()
                            + player.getVelocity().getZ()
                            * player.getVelocity().getZ());
                    ModelAnimations.Pose controlled = RigControllers.sample(
                            rig.controllers,
                            new io.kalo.model.animation.ControllerRuntime.Signals(
                                    speed > 0.02, player.isOnGround(),
                                    player.isInWater(), player.isSneaking(),
                                    player.isSprinting(),
                                    player.getVelocity().getY() > 0.05,
                                    elapsed, speed),
                            0.25, elapsed);
                    pose = pose.merge(controlled);
                }
                if (pose.bones().isEmpty()) {
                    for (ModelAnimations.Clip clip : rig.clips) {
                        pose = pose.merge(clip.sample(elapsed));
                    }
                }
            }
            Map<String, Transformation> transforms = BlockbenchRigTransforms.pose(
                    rig.boneRigs, pose, 1.0, 0.5);
            for (BoneDisplay bone : rig.bones) {
                Entity entity = Bukkit.getEntity(bone.entityId());
                if (!(entity instanceof ItemDisplay display) || !display.isValid()) {
                    continue;
                }
                display.teleport(feet);
                display.setRotation(feet.getYaw(), 0f);
                Transformation transform = transforms.get(bone.boneId());
                if (transform != null) {
                    display.setTransformation(transform);
                }
            }
            return;
        }
        Entity entity = Bukkit.getEntity(rig.singleDisplayId);
        if (entity instanceof ItemDisplay display && display.isValid()) {
            display.teleport(feet);
            display.setRotation(feet.getYaw(), 0f);
            // Item models render around (8,8,8); player-frame models put their feet at
            // y=0, so lift the display half a block.
            display.setTransformation(new Transformation(new Vector3f(0f, 0.5f, 0f),
                    new Quaternionf(), new Vector3f(1f, 1f, 1f), new Quaternionf()));
        }
    }

    @EventHandler
    public void onInteract(@NotNull org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                || event.getItem() != null && !event.getItem().getType().isAir()
                || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EnumMap<ArmorSlot, SlotRig> current = displays.get(event.getPlayer().getUniqueId());
        if (current == null) {
            return;
        }
        for (SlotRig rig : current.values()) {
            if (rig.animation.interact() == null) {
                continue;
            }
            ModelAnimations.Clip clip = ModelAnimations.find(rig.allClips,
                    rig.animation.interact());
            if (clip == null) {
                continue;
            }
            rig.activeClip = clip;
            rig.activeStartNanos = System.nanoTime();
            io.kalo.integration.GeyserIntegration.support().armorAnimationStarted(
                    event.getPlayer().getUniqueId(), rig.contentKey.asString(),
                    rig.animation.interact());
        }
    }

    private void removeAll(@NotNull UUID playerId) {
        EnumMap<ArmorSlot, SlotRig> current = displays.remove(playerId);
        if (current == null) {
            return;
        }
        current.values().forEach(WornArmorListener::remove);
    }

    private static void remove(@Nullable SlotRig rig) {
        if (rig == null) {
            return;
        }
        if (!rig.bones.isEmpty()) {
            for (BoneDisplay bone : rig.bones) {
                Entity entity = Bukkit.getEntity(bone.entityId());
                if (entity != null) {
                    entity.remove();
                }
            }
        } else {
            Entity entity = Bukkit.getEntity(rig.singleDisplayId);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    private record BoneDisplay(@NotNull UUID entityId, @NotNull String boneId,
                               @NotNull io.kalo.model.ModelIR.Vec3 pivot) {
    }

    private static final class SlotRig {
        private final Key contentKey;
        private final UUID singleDisplayId;
        private final List<BoneDisplay> bones;
        private final List<ModelAnimations.Clip> clips;
        private final List<ModelAnimations.Clip> allClips;
        private final List<BlockbenchRigRegistry.BoneRig> boneRigs;
        private final List<io.kalo.model.animation.ControllerRuntime> controllers;
        private final RigAnimation animation;
        private final long startedAtNanos = System.nanoTime();
        private ModelAnimations.Clip activeClip;
        private long activeStartNanos;

        private SlotRig(Key contentKey, @Nullable UUID singleDisplayId,
                        List<BoneDisplay> bones, List<ModelAnimations.Clip> clips,
                        List<ModelAnimations.Clip> allClips,
                        List<BlockbenchRigRegistry.BoneRig> boneRigs,
                        List<io.kalo.model.animation.ControllerRuntime> controllers,
                        RigAnimation animation) {
            this.contentKey = contentKey;
            this.singleDisplayId = singleDisplayId;
            this.bones = List.copyOf(bones);
            this.clips = List.copyOf(clips);
            this.allClips = List.copyOf(allClips);
            this.boneRigs = List.copyOf(boneRigs);
            this.controllers = List.copyOf(controllers);
            this.animation = animation;
        }

        private boolean valid() {
            if (!bones.isEmpty()) {
                for (BoneDisplay bone : bones) {
                    if (!(Bukkit.getEntity(bone.entityId()) instanceof ItemDisplay display)
                            || !display.isValid()) {
                        return false;
                    }
                }
                return true;
            }
            return singleDisplayId != null
                    && Bukkit.getEntity(singleDisplayId) instanceof ItemDisplay display
                    && display.isValid();
        }
    }
}

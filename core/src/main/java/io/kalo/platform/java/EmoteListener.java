package io.kalo.platform.java;

import io.kalo.content.emote.Emote;
import io.kalo.content.emote.EmoteDefinition;
import io.kalo.integration.GeyserIntegration;
import io.kalo.model.animation.ModelAnimations;
import io.kalo.platform.bedrock.BedrockRigCatalog;
import io.kalo.platform.bedrock.BedrockSkinOverlays;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays emotes around one or more performers.
 *
 * <p>A prop emote is one rigid display per performer. A player-body emote uses one display
 * per bone, hides the vanilla body, and samples the authored loops. Each cast member gets
 * their own rig and their own Bedrock client entity. A per-player skin is read from
 * {@code plugins/Kalo/skins/<uuid>.png} when cached, otherwise fetched from the player's
 * signed profile texture URL and applied to that player's Bedrock pack at the next
 * negotiation.</p>
 */
public final class EmoteListener implements Listener {

    public static final NamespacedKey EMOTE_DISPLAY_KEY =
            new NamespacedKey(Constants.PLUGIN_ID, "emote_display");

    private final Map<UUID, Active> active = new ConcurrentHashMap<>();
    private final Map<UUID, List<UUID>> sessions = new ConcurrentHashMap<>();
    private final PlayerSkinService skins;

    public EmoteListener() {
        this(PlayerSkinService.fromConfig(Constants.dataFolder().toPath(),
                io.kalo.utils.Plugins.plugin().getConfig()));
    }

    EmoteListener(@NotNull PlayerSkinService skins) {
        this.skins = skins;
    }

    public boolean play(@NotNull Player player, @NotNull Emote emote) {
        return play(player, emote, List.of());
    }

    /** Starts one emote with the lead plus the required cast members. */
    public boolean play(@NotNull Player lead, @NotNull Emote emote,
                        @NotNull List<Player> cast) {
        RigModelRegistry.Piece piece = RigModelRegistry.get(emote.key());
        if (piece == null || piece.kind() != RigModelRegistry.Kind.EMOTE) {
            return false;
        }
        EmoteDefinition definition = emote.definition();
        if (cast.size() != definition.performers().size() - 1) {
            return false;
        }
        stopSession(lead.getUniqueId());
        for (Player member : cast) {
            stopOne(member.getUniqueId());
        }

        List<Player> all = new ArrayList<>();
        all.add(lead);
        all.addAll(cast);
        List<UUID> started = new ArrayList<>();
        for (int index = 0; index < all.size(); index++) {
            Player performer = all.get(index);
            Active value = definition.rig() == EmoteDefinition.RigMode.PLAYER
                    ? startBody(performer, emote, piece,
                    definition.performers().get(index), lead.getUniqueId())
                    : startProp(performer, emote, piece,
                    definition.performers().get(index), lead.getUniqueId());
            if (value == null) {
                for (UUID id : started) {
                    stopOne(id);
                }
                return false;
            }
            active.put(performer.getUniqueId(), value);
            if (definition.rig() == EmoteDefinition.RigMode.PLAYER) {
                registerSkin(performer, emote.key());
            }
            started.add(performer.getUniqueId());
        }
        sessions.put(lead.getUniqueId(), List.copyOf(started));
        return true;
    }

    private @Nullable Active startProp(@NotNull Player player, @NotNull Emote emote,
                                       @NotNull RigModelRegistry.Piece piece,
                                       @NotNull EmoteDefinition.Performer performer,
                                       @NotNull UUID leadId) {
        ItemDisplay display = RigDisplay.spawn(player.getLocation(), piece.modelKey(),
                piece.yOffset(), piece.scale(), EMOTE_DISPLAY_KEY,
                player.getUniqueId().toString());
        return new Active(display.getUniqueId(), List.of(), List.of(), List.of(),
                piece.yOffset(), piece.scale(), endNanos(emote.definition()), emote.key(),
                BedrockRigCatalog.identifierFor(emote.key()), false, leadId, performer);
    }

    private @Nullable Active startBody(@NotNull Player player, @NotNull Emote emote,
                                       @NotNull RigModelRegistry.Piece piece,
                                       @NotNull EmoteDefinition.Performer performer,
                                       @NotNull UUID leadId) {
        BlockbenchRigRegistry.Rig rig = BlockbenchRigRegistry.getEmote(emote.key());
        if (rig == null || !rig.animateable()) {
            return null;
        }
        List<Active.BoneDisplay> bones = new ArrayList<>();
        for (BlockbenchRigRegistry.BoneRig bone : rig.bones()) {
            ItemDisplay display = spawnBone(player, bone, emote.key());
            bones.add(new Active.BoneDisplay(display.getUniqueId(), bone.boneId(),
                    bone.pivot()));
        }
        boolean wasInvisible = player.isInvisible();
        player.setInvisible(true);
        UUID rigId = bones.isEmpty() ? UUID.randomUUID() : bones.getFirst().entityId();
        return new Active(rigId, bones, rig.animations(), rig.bones(),
                piece.yOffset(), piece.scale(), endNanos(emote.definition()), emote.key(),
                BedrockRigCatalog.identifierFor(emote.key()), !wasInvisible, leadId,
                performer);
    }

    /**
     * Applies the performer's skin to their Bedrock session pack. A cached PNG wins; when
     * there is none, the signed profile texture URL is fetched asynchronously. The vanilla
     * Java rig keeps the pack's baked skin, because the Java pack is global.
     */
    private void registerSkin(@NotNull Player player, @NotNull Key emoteKey) {
        UUID playerId = player.getUniqueId();
        boolean bedrock = GeyserIntegration.support().isBedrock(playerId);
        String xuid = bedrock ? GeyserIntegration.support().xuid(playerId) : null;
        if (bedrock && xuid == null) {
            // Geyser knows the player is Bedrock but has not exposed the session XUID yet;
            // there is nowhere to apply the overlay and the baked skin stays in use.
            return;
        }
        String textureUrl = null;
        if (skins.fetchEnabled()) {
            try {
                textureUrl = PlayerSkinCache.textureUrl(
                        player.getPlayerProfile().getProperties());
            } catch (RuntimeException unavailableProfile) {
                textureUrl = null;
            }
        }
        String packPath = PlayerSkinCache.overlayPath(emoteKey);
        String bedrockXuid = xuid;
        skins.resolve(playerId, textureUrl, bytes -> {
            // The callback may run on the HTTP thread. The overlay map is concurrent, but
            // neither Geyser's connection lookup nor a resource-pack request is.
            if (!active.containsKey(playerId)) {
                return;
            }
            if (bedrock) {
                BedrockSkinOverlays.put(bedrockXuid, packPath, bytes);
            } else {
                sendJavaSkin(player, emoteKey, bytes);
            }
        });
    }

    private void sendJavaSkin(@NotNull Player player, @NotNull Key emoteKey,
                              byte @NotNull [] bytes) {
        try {
            player.getScheduler().execute(io.kalo.utils.Plugins.plugin(), () -> {
                if (!active.containsKey(player.getUniqueId())) {
                    return;
                }
                if (!(io.kalo.Kalo.plugin() instanceof io.kalo.KaloPluginImpl instance)) {
                    return;
                }
                instance.resourcePackManager().sendEmoteOverlay(player,
                        JavaEmoteSkinOverlay.pack(emoteKey, bytes));
            }, null, 1L);
        } catch (RuntimeException retired) {
            // The player left before the overlay could be delivered; the next body emote
            // will try again with the now-cached skin.
        }
    }

    /** Visible for tests: the body rig uses the same per-bone model keys as blocks. */
    static @NotNull ItemDisplay spawnBone(@NotNull Player player,
                                          @NotNull BlockbenchRigRegistry.BoneRig bone,
                                          @NotNull Key contentKey) {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL,
                bone.modelKey());
        return player.getWorld().spawn(player.getLocation(), ItemDisplay.class, spawned -> {
            spawned.setItemStack(stack);
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setSilent(true);
            spawned.setViewRange(64f);
            spawned.setInterpolationDuration(2);
            spawned.getPersistentDataContainer().set(EMOTE_DISPLAY_KEY,
                    PersistentDataType.STRING, contentKey.asString());
        });
    }

    private static @Nullable Long endNanos(@NotNull EmoteDefinition definition) {
        return definition.duration() <= 0 ? null
                : System.nanoTime() + (long) (definition.duration() * 1_000_000_000L);
    }

    private void tick(@NotNull UUID playerId) {
        Active value = active.get(playerId);
        if (value == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        Player lead = Bukkit.getPlayer(value.leadId);
        if (player == null || lead == null || !player.isOnline() || !lead.isOnline()
                || player.isSneaking() || lead.isSneaking()
                || (value.endNanos != null && System.nanoTime() >= value.endNanos)) {
            stopSession(value.leadId);
            return;
        }
        Location location = EmoteFormation.at(lead.getLocation(), value.performer);
        if (!value.bones.isEmpty()) {
            followBody(value, location);
        } else {
            Entity entity = Bukkit.getEntity(value.displayId);
            if (!(entity instanceof ItemDisplay display) || !display.isValid()) {
                stopSession(value.leadId);
                return;
            }
            RigDisplay.follow(display, location, value.yOffset, value.scale);
        }
        int ticks = ++value.ticks;
        if (value.bedrockIdentifier == null && ticks % 20 == 0) {
            String resolved = BedrockRigCatalog.identifierFor(value.contentKey);
            if (resolved != null) {
                value.bedrockIdentifier = resolved;
                spawnBedrock(player, value);
            }
        }
        if (ticks % 10 == 0 && value.bedrockIdentifier != null) {
            GeyserIntegration.support().clientRigMoved(value.displayId,
                    location.getX(), location.getY(), location.getZ(), location.getYaw());
        }
    }

    private void followBody(@NotNull Active value, @NotNull Location location) {
        double elapsed = (System.nanoTime() - value.startedAtNanos) / 1_000_000_000.0;
        ModelAnimations.Pose pose = new ModelAnimations.Pose(Map.of());
        for (ModelAnimations.Clip clip : value.clips) {
            pose = pose.merge(clip.sample(elapsed));
        }
        Map<String, Transformation> transforms = BlockbenchRigTransforms.pose(
                value.boneRigs, pose, value.scale, 0.5 + value.yOffset);
        for (Active.BoneDisplay bone : value.bones) {
            Entity entity = Bukkit.getEntity(bone.entityId());
            if (!(entity instanceof ItemDisplay display) || !display.isValid()) {
                continue;
            }
            display.teleport(location);
            display.setRotation(location.getYaw(), 0f);
            Transformation transform = transforms.get(bone.boneId());
            if (transform != null) {
                display.setTransformation(transform);
            }
        }
    }

    private void spawnBedrock(@NotNull Player player, @NotNull Active value) {
        GeyserIntegration.support().clientRigSpawned(value.displayId,
                value.bedrockIdentifier, player.getWorld().getUID(),
                player.getLocation().getX(), player.getLocation().getY(),
                player.getLocation().getZ(), player.getLocation().getYaw());
    }

    /** Stops the whole session this player leads, or the session they are part of. */
    public void stop(@NotNull UUID playerId) {
        for (Map.Entry<UUID, List<UUID>> entry : sessions.entrySet()) {
            if (entry.getKey().equals(playerId) || entry.getValue().contains(playerId)) {
                stopSession(entry.getKey());
                return;
            }
        }
        stopOne(playerId);
    }

    private void stopSession(@NotNull UUID leadId) {
        List<UUID> members = sessions.remove(leadId);
        if (members == null) {
            stopOne(leadId);
            return;
        }
        for (UUID member : members) {
            stopOne(member);
        }
    }

    private void stopOne(@NotNull UUID playerId) {
        Active value = active.remove(playerId);
        if (value == null) {
            return;
        }
        if (value.task != null) {
            value.task.cancel();
        }
        if (!value.bones.isEmpty()) {
            for (Active.BoneDisplay bone : value.bones) {
                Entity display = Bukkit.getEntity(bone.entityId());
                if (display != null) {
                    display.remove();
                }
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && value.hidPlayer) {
                player.setInvisible(false);
            }
        } else {
            Entity display = Bukkit.getEntity(value.displayId);
            if (display != null) {
                display.remove();
            }
        }
        if (value.bedrockIdentifier != null) {
            GeyserIntegration.support().clientRigRemoved(value.displayId);
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        stop(event.getPlayer().getUniqueId());
        String xuid = GeyserIntegration.support().xuid(event.getPlayer().getUniqueId());
        if (xuid != null) {
            BedrockSkinOverlays.forget(xuid);
        }
    }

    private static final class Active {
        private final UUID displayId;
        private @Nullable String bedrockIdentifier;
        private final List<BoneDisplay> bones;
        private final List<ModelAnimations.Clip> clips;
        private final List<BlockbenchRigRegistry.BoneRig> boneRigs;
        private final double yOffset;
        private final double scale;
        private final @Nullable Long endNanos;
        private final Key contentKey;
        private final boolean hidPlayer;
        private final UUID leadId;
        private final EmoteDefinition.Performer performer;
        private final long startedAtNanos = System.nanoTime();
        private io.papermc.paper.threadedregions.scheduler.ScheduledTask task;
        private int ticks;

        private Active(UUID displayId, List<BoneDisplay> bones,
                       List<ModelAnimations.Clip> clips,
                       List<BlockbenchRigRegistry.BoneRig> boneRigs,
                       double yOffset, double scale, @Nullable Long endNanos,
                       Key contentKey, @Nullable String bedrockIdentifier,
                       boolean hidPlayer, UUID leadId,
                       EmoteDefinition.Performer performer) {
            this.displayId = displayId;
            this.bones = List.copyOf(bones);
            this.clips = List.copyOf(clips);
            this.boneRigs = List.copyOf(boneRigs);
            this.yOffset = yOffset;
            this.scale = scale;
            this.endNanos = endNanos;
            this.contentKey = contentKey;
            this.bedrockIdentifier = bedrockIdentifier;
            this.hidPlayer = hidPlayer;
            this.leadId = leadId;
            this.performer = performer;
        }

        private record BoneDisplay(UUID entityId, String boneId,
                                   io.kalo.model.ModelIR.Vec3 pivot) {
        }
    }
}

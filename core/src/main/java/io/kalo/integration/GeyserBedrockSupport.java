package io.kalo.integration;

import io.kalo.platform.bedrock.BedrockAnimatedBlockRegistration;
import io.kalo.platform.bedrock.BedrockMobRegistration;
import io.kalo.platform.bedrock.BedrockPackStager;
import io.kalo.platform.bedrock.BedrockPlacedModelRegistration;
import io.kalo.platform.bedrock.BedrockRegistrationSnapshot;
import io.kalo.platform.bedrock.BedrockSkinOverlays;
import io.kalo.platform.bedrock.EmoteSkinOverlays;
import io.kalo.utils.Constants;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Geyser-backed Bedrock support.
 *
 * <p>The public Geyser API can register a custom entity but cannot spawn one. Client
 * entity spawning lives in Geyser's session and Cloudburst packet classes, which are not
 * part of the API jar. Those calls are therefore reflective and completely optional: if a
 * Geyser build moves them, placed models remain a Java-only feature instead of taking the
 * server down.</p>
 */
final class GeyserBedrockSupport implements BedrockSupport {

    private static final Logger LOGGER = Logger.getLogger(GeyserBedrockSupport.class.getName());
    private static final double RANGE = 64.0;
    private static final double RANGE_SQUARED = RANGE * RANGE;

    private final Map<UUID, Map<UUID, Long>> spawned = new ConcurrentHashMap<>();
    private final Map<UUID, Active> active = new ConcurrentHashMap<>();
    private final Map<String, ActiveBlock> activeBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> spawnedBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveMob> activeMobs = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Long>> spawnedMobs = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveRig> activeRigs = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Long>> spawnedRigs = new ConcurrentHashMap<>();
    private final org.bukkit.plugin.Plugin plugin;

    GeyserBedrockSupport(@NotNull org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
        // Walk-in coverage: a player who comes within range after placement must see the
        // model, not only one who was nearby when the piece went down.
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin,
                ignored -> refresh(), 20L, 20L);
        plugin.getServer().getGlobalRegionScheduler().run(plugin,
                ignored -> refreshExisting());
    }

    /** Re-announces placements already saved in loaded chunks after a plugin reload. */
    private void refreshExisting() {
        try {
            for (var world : Bukkit.getWorlds()) {
                for (var chunk : world.getLoadedChunks()) {
                    for (var entity : chunk.getEntities()) {
                        if (entity instanceof org.bukkit.entity.Interaction hitbox) {
                            String model = hitbox.getPersistentDataContainer().get(
                                    io.kalo.platform.java.PlacedModelListener.MODEL_KEY,
                                    org.bukkit.persistence.PersistentDataType.STRING);
                            if (model == null) {
                                continue;
                            }
                            Float scale = hitbox.getPersistentDataContainer().get(
                                    io.kalo.platform.java.PlacedModelListener.SCALE_KEY,
                                    org.bukkit.persistence.PersistentDataType.FLOAT);
                            rigPlaced(hitbox.getUniqueId(), model,
                                    hitbox.getLocation().getX(), hitbox.getLocation().getY(),
                                    hitbox.getLocation().getZ(),
                                    hitbox.getLocation().getYaw(),
                                    scale == null ? 1f : scale);
                        }
                    }
                }
            }
        } catch (Throwable unavailable) {
            LOGGER.log(Level.FINE, "Could not scan existing placed models for Bedrock",
                    unavailable);
        }
    }

    private record Active(UUID hitboxId, String modelId, double x, double y, double z,
                          float yaw, float scale) {
    }

    private record ActiveMob(UUID entityId, String mobId, UUID worldId,
                             double x, double y, double z, float yaw) {
    }

    private record ActiveRig(UUID entityId, String entityIdentifier, UUID worldId,
                             double x, double y, double z, float yaw, float pitch,
                             @org.jetbrains.annotations.Nullable UUID viewerId,
                             float scale) {
    }

    private record ActiveBlock(UUID worldId, int x, int y, int z, String blockId) {
        String key() {
            return keyOf(worldId, x, y, z);
        }

        static String keyOf(UUID worldId, int x, int y, int z) {
            return worldId + ":" + x + ":" + y + ":" + z;
        }
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public @Nullable String xuid(@NotNull UUID playerId) {
        try {
            GeyserConnection connection = GeyserApi.api().connectionByUuid(playerId);
            return connection == null ? null : connection.xuid();
        } catch (Throwable unavailable) {
            return null;
        }
    }

    @Override
    public boolean isBedrock(@NotNull UUID playerId) {
        try {
            return GeyserApi.api().connectionByUuid(playerId) != null;
        } catch (Throwable unavailable) {
            return false;
        }
    }

    @Override
    public void rigPlaced(@NotNull UUID hitboxId, @NotNull String modelId,
                          double x, double y, double z, float yaw, float scale) {
        active.put(hitboxId, new Active(hitboxId, modelId, x, y, z, yaw, scale));
        placeForPlayers(active.get(hitboxId));
    }

    @Override
    public void refresh() {
        active.values().forEach(this::placeForPlayers);
        activeBlocks.values().forEach(this::placeBlockForPlayers);
        activeMobs.values().forEach(this::placeMobForPlayers);
        activeRigs.values().forEach(this::placeRigForPlayers);
    }

    @Override
    public void clientRigSpawned(@NotNull UUID entityId, @NotNull String entityIdentifier,
                           @NotNull UUID worldId, double x, double y, double z, float yaw) {
        clientRigSpawned(entityId, entityIdentifier, worldId, x, y, z, yaw, 0f,
                null, 1f);
    }

    @Override
    public void clientRigSpawned(@NotNull UUID entityId, @NotNull String entityIdentifier,
                                 @NotNull UUID worldId, double x, double y, double z,
                                 float yaw, float scale) {
        clientRigSpawned(entityId, entityIdentifier, worldId, x, y, z, yaw, 0f,
                null, scale);
    }

    @Override
    public void clientRigSpawned(@NotNull UUID entityId, @NotNull String entityIdentifier,
                                 @NotNull UUID worldId, double x, double y, double z,
                                 float yaw, @org.jetbrains.annotations.Nullable UUID viewerId) {
        clientRigSpawned(entityId, entityIdentifier, worldId, x, y, z, yaw, 0f,
                viewerId, 1f);
    }

    @Override
    public void clientRigSpawned(@NotNull UUID entityId, @NotNull String entityIdentifier,
                                 @NotNull UUID worldId, double x, double y, double z,
                                 float yaw,
                                 @org.jetbrains.annotations.Nullable UUID viewerId,
                                 float scale) {
        clientRigSpawned(entityId, entityIdentifier, worldId, x, y, z, yaw, 0f,
                viewerId, scale);
    }

    @Override
    public void clientRigSpawned(@NotNull UUID entityId, @NotNull String entityIdentifier,
                                 @NotNull UUID worldId, double x, double y, double z,
                                 float yaw, float pitch,
                                 @org.jetbrains.annotations.Nullable UUID viewerId,
                                 float scale) {
        ActiveRig rig = new ActiveRig(entityId, entityIdentifier, worldId, x, y, z, yaw,
                pitch, viewerId, scale);
        activeRigs.put(entityId, rig);
        placeRigForPlayers(rig);
    }

    @Override
    public void clientRigMoved(@NotNull UUID entityId, double x, double y, double z, float yaw) {
        ActiveRig previous = activeRigs.get(entityId);
        clientRigMoved(entityId, x, y, z, yaw,
                previous == null ? 0f : previous.pitch(),
                previous == null ? 1f : previous.scale());
    }

    @Override
    public void clientRigMoved(@NotNull UUID entityId, double x, double y, double z,
                               float yaw, float scale) {
        ActiveRig previous = activeRigs.get(entityId);
        clientRigMoved(entityId, x, y, z, yaw,
                previous == null ? 0f : previous.pitch(), scale);
    }

    @Override
    public void clientRigMoved(@NotNull UUID entityId, double x, double y, double z,
                               float yaw, float pitch, float scale) {
        ActiveRig previous = activeRigs.get(entityId);
        if (previous == null) {
            return;
        }
        boolean scaleChanged = Math.abs(previous.scale() - scale) > 1e-3f;
        activeRigs.put(entityId, new ActiveRig(entityId, previous.entityIdentifier(),
                previous.worldId(), x, y, z, yaw, pitch, previous.viewerId(), scale));
        spawnedRigs.forEach((playerId, entities) -> {
            Long geyserId = entities.get(entityId);
            if (geyserId == null) {
                return;
            }
            try {
                GeyserConnection connection = GeyserApi.api().connectionByUuid(playerId);
                if (connection != null) {
                    send(connection, moveEntityPacket(geyserId, x, y, z, yaw, pitch));
                    if (scaleChanged) {
                        send(connection, scalePacket(geyserId, scale));
                    }
                }
            } catch (Throwable unavailable) {
                LOGGER.log(Level.FINE, "Bedrock rig move unavailable", unavailable);
            }
        });
    }

    @Override
    public void clientRigRemoved(@NotNull UUID entityId) {
        if (activeRigs.remove(entityId) == null) {
            return;
        }
        spawnedRigs.forEach((playerId, entities) -> {
            Long geyserId = entities.remove(entityId);
            if (geyserId == null) {
                return;
            }
            try {
                GeyserConnection connection = GeyserApi.api().connectionByUuid(playerId);
                if (connection != null) {
                    send(connection, removePacket(geyserId));
                }
            } catch (Throwable unavailable) {
                LOGGER.log(Level.FINE, "Bedrock rig removal unavailable", unavailable);
            }
        });
    }

    private void placeRigForPlayers(@Nullable ActiveRig rig) {
        if (rig == null) {
            return;
        }
        try {
            for (GeyserConnection connection : GeyserApi.api().onlineConnections()) {
                Player player = Bukkit.getPlayer(connection.javaUuid());
                if (player == null || !player.getWorld().getUID().equals(rig.worldId())) {
                    continue;
                }
                if (rig.viewerId() != null
                        && !player.getUniqueId().equals(rig.viewerId())) {
                    continue;
                }
                Location location = player.getLocation();
                double dx = location.getX() - rig.x();
                double dy = location.getY() - rig.y();
                double dz = location.getZ() - rig.z();
                if (dx * dx + dy * dy + dz * dz > RANGE_SQUARED) {
                    continue;
                }
                spawnRig(connection, rig);
            }
        } catch (Throwable unavailable) {
            LOGGER.log(Level.FINE, "Bedrock rig spawn unavailable", unavailable);
        }
    }

    private void spawnRig(GeyserConnection connection, ActiveRig rig) {
        try {
            UUID playerId = connection.javaUuid();
            Map<UUID, Long> entities = spawnedRigs.computeIfAbsent(playerId,
                    ignored -> new ConcurrentHashMap<>());
            if (entities.containsKey(rig.entityId())) {
                return;
            }
            long geyserId = nextEntityId(connection);
            Object packet = Class.forName("org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket")
                    .getConstructor().newInstance();
            call(packet, "setIdentifier", rig.entityIdentifier());
            call(packet, "setUniqueEntityId", geyserId);
            call(packet, "setRuntimeEntityId", geyserId);
            call(packet, "setPosition", vector3f((float) rig.x(), (float) rig.y(),
                    (float) rig.z()));
            call(packet, "setMotion", vector3f(0, 0, 0));
            call(packet, "setRotation", vector2f(rig.pitch(), rig.yaw()));
            call(packet, "setHeadRotation", rig.yaw());
            call(packet, "setBodyRotation", rig.yaw());
            applyScale(packet, rig.scale());
            send(connection, packet);
            entities.put(rig.entityId(), geyserId);
        } catch (Throwable unavailable) {
            LOGGER.log(Level.FINE, "Could not spawn Bedrock rig "
                    + rig.entityIdentifier(), unavailable);
        }
    }

    @Override
    public void mobSpawned(@NotNull UUID entityId, @NotNull String mobId,
                           @NotNull UUID worldId, double x, double y, double z, float yaw) {
        ActiveMob mob = new ActiveMob(entityId, mobId, worldId, x, y, z, yaw);
        activeMobs.put(entityId, mob);
        placeMobForPlayers(mob);
    }

    @Override
    public void mobAnimationStarted(@NotNull UUID entityId, @NotNull String animation) {
        ActiveMob mob = activeMobs.get(entityId);
        if (mob == null) {
            return;
        }
        BedrockMobRegistration registration = mobRegistration(mob.mobId());
        if (registration == null) {
            return;
        }
        String animationId = registration.animations().get(animation);
        if (animationId == null) {
            String wanted = io.kalo.model.animation.ModelAnimations.normalize(animation);
            animationId = registration.animations().entrySet().stream()
                    .filter(entry -> io.kalo.model.animation.ModelAnimations
                            .normalize(entry.getKey()).equals(wanted))
                    .map(java.util.Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
        if (animationId == null) {
            return;
        }
        final String resolved = animationId;
        spawnedMobs.forEach((playerId, entities) -> {
            Long geyserId = entities.get(entityId);
            if (geyserId == null) {
                return;
            }
            try {
                GeyserConnection connection = GeyserApi.api().connectionByUuid(playerId);
                if (connection != null) {
                    send(connection, animatePacket(geyserId, resolved));
                }
            } catch (Throwable unavailable) {
                LOGGER.log(Level.FINE, "Bedrock custom-mob animation unavailable", unavailable);
            }
        });
    }

    @Override
    public void mobMoved(@NotNull UUID entityId, double x, double y, double z, float yaw) {
        ActiveMob previous = activeMobs.get(entityId);
        if (previous == null) {
            return;
        }
        ActiveMob mob = new ActiveMob(entityId, previous.mobId(), previous.worldId(),
                x, y, z, yaw);
        activeMobs.put(entityId, mob);
        spawnedMobs.forEach((playerId, entities) -> {
            Long geyserId = entities.get(entityId);
            if (geyserId == null) {
                return;
            }
            try {
                GeyserConnection connection = GeyserApi.api().connectionByUuid(playerId);
                if (connection != null) {
                    send(connection, moveEntityPacket(geyserId, x, y, z, yaw));
                }
            } catch (Throwable unavailable) {
                LOGGER.log(Level.FINE, "Bedrock custom-mob move unavailable", unavailable);
            }
        });
    }

    @Override
    public void mobRemoved(@NotNull UUID entityId) {
        if (activeMobs.remove(entityId) == null) {
            return;
        }
        spawnedMobs.forEach((playerId, entities) -> {
            Long geyserId = entities.remove(entityId);
            if (geyserId == null) {
                return;
            }
            try {
                GeyserConnection connection = GeyserApi.api().connectionByUuid(playerId);
                if (connection != null) {
                    send(connection, removePacket(geyserId));
                }
            } catch (Throwable unavailable) {
                LOGGER.log(Level.FINE, "Bedrock custom-mob removal unavailable", unavailable);
            }
        });
    }

    private void placeMobForPlayers(@Nullable ActiveMob mob) {
        if (mob == null) {
            return;
        }
        BedrockMobRegistration registration = mobRegistration(mob.mobId());
        if (registration == null) {
            return;
        }
        try {
            for (GeyserConnection connection : GeyserApi.api().onlineConnections()) {
                Player player = Bukkit.getPlayer(connection.javaUuid());
                if (player == null || !player.getWorld().getUID().equals(mob.worldId())) {
                    continue;
                }
                Location location = player.getLocation();
                double dx = location.getX() - mob.x();
                double dy = location.getY() - mob.y();
                double dz = location.getZ() - mob.z();
                if (dx * dx + dy * dy + dz * dz > RANGE_SQUARED) {
                    continue;
                }
                spawnMob(connection, mob, registration);
            }
        } catch (Throwable unavailable) {
            LOGGER.log(Level.FINE, "Bedrock custom-mob spawn unavailable", unavailable);
        }
    }

    private @Nullable BedrockMobRegistration mobRegistration(String mobId) {
        return BedrockRegistrationSnapshot.await(Duration.ZERO)
                .flatMap(snapshot -> snapshot.mobs().stream()
                        .filter(mob -> mob.mobKey().asString().equals(mobId))
                        .findFirst())
                .orElse(null);
    }

    private void spawnMob(GeyserConnection connection, ActiveMob mob,
                          BedrockMobRegistration registration) {
        try {
            UUID playerId = connection.javaUuid();
            Map<UUID, Long> entities = spawnedMobs.computeIfAbsent(playerId,
                    ignored -> new ConcurrentHashMap<>());
            if (entities.containsKey(mob.entityId())) {
                return;
            }
            long geyserId = nextEntityId(connection);
            Object packet = Class.forName("org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket")
                    .getConstructor().newInstance();
            call(packet, "setIdentifier", registration.entityIdentifier());
            call(packet, "setUniqueEntityId", geyserId);
            call(packet, "setRuntimeEntityId", geyserId);
            call(packet, "setPosition", vector3f((float) mob.x(), (float) mob.y(),
                    (float) mob.z()));
            call(packet, "setMotion", vector3f(0, 0, 0));
            call(packet, "setRotation", vector2f(0, mob.yaw()));
            call(packet, "setHeadRotation", mob.yaw());
            call(packet, "setBodyRotation", mob.yaw());
            send(connection, packet);
            entities.put(mob.entityId(), geyserId);
        } catch (Throwable unavailable) {
            LOGGER.log(Level.FINE, "Could not spawn Bedrock custom mob "
                    + registration.entityIdentifier(), unavailable);
        }
    }

    private static Object moveEntityPacket(long geyserId, double x, double y, double z,
                                           float yaw) throws Exception {
        return moveEntityPacket(geyserId, x, y, z, yaw, 0f);
    }

    private static Object moveEntityPacket(long geyserId, double x, double y, double z,
                                           float yaw, float pitch) throws Exception {
        // Cloudburst names this MoveEntityAbsolutePacket; rotation is (pitch, yaw,
        // head-yaw) in one Vector3f, not the Vector2f AddEntityPacket takes.
        Object packet = Class.forName(
                        "org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket")
                .getConstructor().newInstance();
        call(packet, "setRuntimeEntityId", geyserId);
        call(packet, "setPosition", vector3f((float) x, (float) y, (float) z));
        call(packet, "setRotation", vector3f(pitch, yaw, yaw));
        call(packet, "setOnGround", true);
        call(packet, "setTeleported", true);
        call(packet, "setForceMove", true);
        return packet;
    }

    @Override
    public void blockPlaced(@NotNull UUID worldId, int x, int y, int z,
                            @NotNull String blockId) {
        if (blockRegistration(blockId) == null) {
            // Most virtual blocks are static; do not track every one of them. A later
            // chunk sync re-announces this block once the compiler snapshot is ready.
            return;
        }
        ActiveBlock block = new ActiveBlock(worldId, x, y, z, blockId);
        activeBlocks.put(block.key(), block);
        placeBlockForPlayers(block);
    }

    @Override
    public void armorAnimationStarted(@NotNull UUID playerId, @NotNull String armorId,
                                      @NotNull String animation) {
        try {
            GeyserConnection connection = GeyserApi.api().connectionByUuid(playerId);
            if (connection == null) {
                return;
            }
            String animationId = io.kalo.platform.bedrock.BedrockAnimationIds.armor(
                    Key.key(armorId), animation);
            send(connection, animatePacket(connection.playerEntity().geyserId(),
                    animationId));
        } catch (Throwable unavailable) {
            LOGGER.log(Level.FINE, "Bedrock armor animation unavailable", unavailable);
        }
    }

    @Override
    public void blockAnimationStarted(@NotNull UUID worldId, int x, int y, int z,
                                      @NotNull String blockId, @NotNull String animation) {
        String key = ActiveBlock.keyOf(worldId, x, y, z);
        if (!activeBlocks.containsKey(key)) {
            return;
        }
        BedrockAnimatedBlockRegistration registration = blockRegistration(blockId);
        if (registration == null) {
            return;
        }
        String animationId = registration.animations().get(animation);
        if (animationId == null) {
            String wanted = io.kalo.model.animation.ModelAnimations.normalize(animation);
            animationId = registration.animations().entrySet().stream()
                    .filter(entry -> io.kalo.model.animation.ModelAnimations
                            .normalize(entry.getKey()).equals(wanted))
                    .map(java.util.Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
        if (animationId == null) {
            return;
        }
        final String resolved = animationId;
        spawnedBlocks.forEach((playerId, entities) -> {
            Long geyserId = entities.get(key);
            if (geyserId == null) {
                return;
            }
            try {
                GeyserConnection connection = GeyserApi.api().connectionByUuid(playerId);
                if (connection != null) {
                    send(connection, animatePacket(geyserId, resolved));
                }
            } catch (Throwable unavailable) {
                LOGGER.log(Level.FINE, "Bedrock block animation unavailable", unavailable);
            }
        });
    }

    @Override
    public void blockRemoved(@NotNull UUID worldId, int x, int y, int z) {
        String key = ActiveBlock.keyOf(worldId, x, y, z);
        if (activeBlocks.remove(key) == null) {
            return;
        }
        spawnedBlocks.forEach((playerId, entities) -> {
            Long geyserId = entities.remove(key);
            if (geyserId == null) {
                return;
            }
            try {
                GeyserConnection connection = GeyserApi.api().connectionByUuid(playerId);
                if (connection != null) {
                    send(connection, removePacket(geyserId));
                }
            } catch (Throwable unavailable) {
                LOGGER.log(Level.FINE, "Bedrock animated-block removal unavailable", unavailable);
            }
        });
    }

    private void placeBlockForPlayers(@Nullable ActiveBlock block) {
        if (block == null) {
            return;
        }
        BedrockAnimatedBlockRegistration registration = blockRegistration(block.blockId());
        if (registration == null) {
            return;
        }
        try {
            for (GeyserConnection connection : GeyserApi.api().onlineConnections()) {
                Player player = Bukkit.getPlayer(connection.javaUuid());
                if (player == null || !player.getWorld().getUID().equals(block.worldId())) {
                    continue;
                }
                Location location = player.getLocation();
                double dx = location.getX() - (block.x() + 0.5);
                double dy = location.getY() - block.y();
                double dz = location.getZ() - (block.z() + 0.5);
                if (dx * dx + dy * dy + dz * dz > RANGE_SQUARED) {
                    continue;
                }
                spawnBlock(connection, block, registration);
            }
        } catch (Throwable unavailable) {
            LOGGER.log(Level.FINE, "Bedrock animated-block spawn unavailable", unavailable);
        }
    }

    private @Nullable BedrockAnimatedBlockRegistration blockRegistration(String blockId) {
        return BedrockRegistrationSnapshot.await(Duration.ZERO)
                .flatMap(snapshot -> snapshot.animatedBlocks().stream()
                        .filter(block -> block.blockKey().asString().equals(blockId))
                        .findFirst())
                .orElse(null);
    }

    private void spawnBlock(GeyserConnection connection, ActiveBlock block,
                            BedrockAnimatedBlockRegistration registration) {
        try {
            UUID playerId = connection.javaUuid();
            Map<String, Long> entities = spawnedBlocks.computeIfAbsent(playerId,
                    ignored -> new ConcurrentHashMap<>());
            if (entities.containsKey(block.key())) {
                return;
            }
            long geyserId = nextEntityId(connection);
            Object packet = Class.forName("org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket")
                    .getConstructor().newInstance();
            call(packet, "setIdentifier", registration.entityIdentifier());
            call(packet, "setUniqueEntityId", geyserId);
            call(packet, "setRuntimeEntityId", geyserId);
            call(packet, "setPosition", vector3f((float) (block.x() + 0.5),
                    (float) block.y(), (float) (block.z() + 0.5)));
            call(packet, "setMotion", vector3f(0, 0, 0));
            call(packet, "setRotation", vector2f(0, 0));
            call(packet, "setHeadRotation", 0f);
            call(packet, "setBodyRotation", 0f);
            send(connection, packet);
            entities.put(block.key(), geyserId);
        } catch (Throwable unavailable) {
            LOGGER.log(Level.FINE, "Could not spawn Bedrock animated block "
                    + registration.entityIdentifier(), unavailable);
        }
    }

    /**
     * Stages the new generated pack for every online Bedrock session and, when the
     * session was previously served different bytes, asks it to reconnect.
     *
     * <p>Bedrock negotiates packs during login. A session that is already playing cannot
     * be handed a replacement, so the only way it sees new content is another login
     * through the same join address. Sessions whose served digest already matches are
     * left alone, which keeps a rebuild from bouncing everyone repeatedly.</p>
     */
    @Override
    public void packUpdated() {
        Path source = new File(Constants.dataFolder(), "generated.mcpack").toPath();
        if (!Files.isRegularFile(source)) {
            return;
        }
        // Pack compilation runs on Kalo's async executor; Geyser's session list and
        // transfer call belong on the server thread that owns global state.
        try {
            plugin.getServer().getGlobalRegionScheduler().run(plugin,
                    ignored -> applyPackUpdate(source));
        } catch (Throwable schedulerUnavailable) {
            applyPackUpdate(source);
        }
    }

    private void applyPackUpdate(@NotNull Path source) {
        final String sourceDigest;
        try {
            sourceDigest = BedrockPackStager.digest(source);
        } catch (IOException unreadable) {
            LOGGER.log(Level.WARNING,
                    "Could not hash generated.mcpack; per-session Bedrock packs stay at their"
                            + " previous revision", unreadable);
            return;
        }
        boolean transfer = transferOnUpdate();
        Path dataFolder = Constants.dataFolder().toPath();
        io.kalo.platform.bedrock.SessionContentPolicy policy =
                io.kalo.platform.bedrock.SessionContentPolicy.fromPluginConfig();
        int staged = 0;
        int transferred = 0;
        java.util.List<? extends GeyserConnection> connections;
        try {
            connections = GeyserApi.api().onlineConnections();
        } catch (Throwable unavailable) {
            LOGGER.log(Level.FINE, "Could not list Bedrock sessions after a pack change",
                    unavailable);
            return;
        }
        for (GeyserConnection connection : connections) {
            String xuid = connection.xuid();
            if (xuid == null || xuid.isBlank()) {
                continue;
            }
            Map<String, byte[]> overlays = new TreeMap<>(
                    BedrockSkinOverlays.forXuid(xuid));
            UUID playerId = connection.javaUuid();
            if (playerId != null) {
                overlays.putAll(EmoteSkinOverlays.forPlayer(dataFolder, playerId));
            }
            org.bukkit.entity.Player player =
                    playerId == null ? null : Bukkit.getPlayer(playerId);
            java.util.function.Predicate<String> permissions = player != null
                    ? player::hasPermission : node -> false;
            final String sessionDigest;
            try {
                BedrockPackStager.stage(dataFolder, source, xuid, overlays, policy,
                        permissions);
                sessionDigest = BedrockPackStager.sessionDigest(sourceDigest, overlays,
                        policy.fingerprint(xuid, permissions));
                staged++;
            } catch (IOException notStaged) {
                LOGGER.log(Level.WARNING, "Could not stage the Bedrock pack for session "
                        + xuid + "; that session keeps the pack it already holds", notStaged);
                continue;
            }
            if (!needsReconnect(BedrockPackStager.servedDigest(xuid), sessionDigest,
                    transfer)) {
                continue;
            }
            try {
                if (connection.transfer(connection.joinAddress(), connection.joinPort())) {
                    transferred++;
                    LOGGER.info("Asked Bedrock player " + connection.bedrockUsername()
                            + " to reconnect for the updated Kalo pack");
                }
            } catch (Throwable unavailable) {
                // A denied transfer is not a server failure; the next login still stages
                // and serves the new copy.
                LOGGER.log(Level.FINE, "Could not transfer a Bedrock session after a pack change",
                        unavailable);
            }
        }
        if (staged > 0) {
            LOGGER.fine("Staged the updated Bedrock pack for " + staged + " session(s)");
        }
        if (transferred > 0) {
            LOGGER.info("Updated Bedrock pack applied to " + transferred
                    + " online session(s) via reconnect");
        }
    }

    /**
     * A session needs a reconnect when transfers are enabled and the revision it was
     * served is not the one on disk. A null served digest means the session predates
     * staging (or staging failed), so its pack is stale too.
     */
    static boolean needsReconnect(@Nullable String servedDigest, @NotNull String currentDigest,
                                  boolean transfer) {
        return transfer && !currentDigest.equals(servedDigest);
    }

    private boolean transferOnUpdate() {
        try {
            return plugin.getConfig().getBoolean("bedrock-packs.transfer", true);
        } catch (Throwable unavailable) {
            return true;
        }
    }

    private void placeForPlayers(@Nullable Active active) {
        if (active == null) {
            return;
        }
        BedrockPlacedModelRegistration registration = registration(active.modelId());
        if (registration == null) {
            return;
        }
        try {
            for (GeyserConnection connection : GeyserApi.api().onlineConnections()) {
                UUID playerId = connection.javaUuid();
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.getWorld().getName().equals(worldName(active.hitboxId()))) {
                    continue;
                }
                Location location = player.getLocation();
                double dx = location.getX() - active.x();
                double dy = location.getY() - active.y();
                double dz = location.getZ() - active.z();
                if (dx * dx + dy * dy + dz * dz > RANGE_SQUARED) {
                    continue;
                }
                spawn(connection, active.hitboxId(), registration, active.x(), active.y(),
                        active.z(), active.yaw(), active.scale());
            }
        } catch (Throwable unavailable) {
            LOGGER.log(Level.FINE, "Bedrock placed-model spawn unavailable", unavailable);
        }
    }

    @Override
    public void rigRemoved(@NotNull UUID hitboxId) {
        active.remove(hitboxId);
        spawned.forEach((playerId, entities) -> {
            Long geyserId = entities.remove(hitboxId);
            if (geyserId == null) {
                return;
            }
            try {
                GeyserConnection connection = GeyserApi.api().connectionByUuid(playerId);
                if (connection != null) {
                    send(connection, removePacket(geyserId));
                }
            } catch (Throwable unavailable) {
                LOGGER.log(Level.FINE, "Bedrock placed-model removal unavailable", unavailable);
            }
        });
    }

    @Override
    public void animationStarted(@NotNull UUID hitboxId, @NotNull String modelId,
                                 @NotNull String animation) {
        BedrockPlacedModelRegistration registration = registration(modelId);
        if (registration == null) {
            return;
        }
        String animationId = registration.animations().get(animation);
        if (animationId == null) {
            String wanted = io.kalo.model.animation.ModelAnimations.normalize(animation);
            animationId = registration.animations().entrySet().stream()
                    .filter(entry -> io.kalo.model.animation.ModelAnimations
                            .normalize(entry.getKey()).equals(wanted))
                    .map(java.util.Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
        if (animationId == null) {
            return;
        }
        final String resolvedAnimation = animationId;
        spawned.forEach((playerId, entities) -> {
            Long geyserId = entities.get(hitboxId);
            if (geyserId == null) {
                return;
            }
            try {
                GeyserConnection connection = GeyserApi.api().connectionByUuid(playerId);
                if (connection != null) {
                    send(connection, animatePacket(geyserId, resolvedAnimation));
                }
            } catch (Throwable unavailable) {
                LOGGER.log(Level.FINE, "Bedrock placed-model animation unavailable", unavailable);
            }
        });
    }

    @Override
    public void forget(@NotNull UUID playerId) {
        spawned.remove(playerId);
        spawnedBlocks.remove(playerId);
        spawnedMobs.remove(playerId);
        spawnedRigs.remove(playerId);
    }

    private @Nullable BedrockPlacedModelRegistration registration(String modelId) {
        return BedrockRegistrationSnapshot.await(Duration.ZERO)
                .flatMap(snapshot -> snapshot.placedModels().stream()
                        .filter(model -> model.javaKey().asString().equals(modelId))
                        .findFirst())
                .orElse(null);
    }

    private String worldName(UUID hitboxId) {
        var entity = Bukkit.getEntity(hitboxId);
        return entity == null ? "" : entity.getWorld().getName();
    }

    private void spawn(GeyserConnection connection, UUID hitboxId,
                       BedrockPlacedModelRegistration registration,
                       double x, double y, double z, float yaw, float scale) {
        try {
            Map<UUID, Long> entities = spawned.computeIfAbsent(connection.javaUuid(),
                    ignored -> new ConcurrentHashMap<>());
            if (entities.containsKey(hitboxId)) {
                return;
            }
            long geyserId = nextEntityId(connection);
            Object packet = Class.forName("org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket")
                    .getConstructor().newInstance();
            call(packet, "setIdentifier", registration.entityIdentifier());
            call(packet, "setUniqueEntityId", geyserId);
            call(packet, "setRuntimeEntityId", geyserId);
            Object position = vector3f((float) x, (float) y, (float) z);
            call(packet, "setPosition", position);
            call(packet, "setMotion", vector3f(0, 0, 0));
            call(packet, "setRotation", vector2f(0, yaw));
            call(packet, "setHeadRotation", yaw);
            call(packet, "setBodyRotation", yaw);
            applyScale(packet, scale);
            send(connection, packet);
            entities.put(hitboxId, geyserId);
        } catch (Throwable unavailable) {
            LOGGER.log(Level.FINE, "Could not spawn Bedrock placed model "
                    + registration.entityIdentifier(), unavailable);
        }
    }

    private static void applyScale(Object packet, float scale) {
        applyScale(packet, scale, false);
    }

    private static void applyScale(Object packet, float scale, boolean includeDefault) {
        if (scale == 1f && !includeDefault) {
            return;
        }
        try {
            Object metadata = call(packet, "getMetadata");
            Class<?> types = Class.forName(
                    "org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes");
            Object type = types.getField("SCALE").get(null);
            Method put = metadata.getClass().getMethod("put",
                    Class.forName("org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType"),
                    Object.class);
            put.invoke(metadata, type, scale);
        } catch (Throwable ignored) {
            // Scale is cosmetic; the entity still spawns at default size.
        }
    }

    /**
     * Updates an already-spawned custom entity's scale without respawning it. The
     * metadata type is the same one placed models use at spawn time.
     */
    private static Object scalePacket(long geyserId, float scale) throws Exception {
        Object packet = Class.forName(
                "org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket")
                .getConstructor().newInstance();
        call(packet, "setRuntimeEntityId", geyserId);
        applyScale(packet, scale, true);
        return packet;
    }

    private static long nextEntityId(GeyserConnection connection) throws Exception {
        Object cache = call(connection, "getEntityCache");
        Object id = call(cache, "getNextEntityId");
        if (id instanceof java.util.concurrent.atomic.AtomicLong atomic) {
            return atomic.incrementAndGet();
        }
        Method increment = id.getClass().getMethod("incrementAndGet");
        return ((Number) increment.invoke(id)).longValue();
    }

    private static Object removePacket(long geyserId) throws Exception {
        Object packet = Class.forName("org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket")
                .getConstructor().newInstance();
        call(packet, "setUniqueEntityId", geyserId);
        return packet;
    }

    private static Object animatePacket(long geyserId, String animationId) throws Exception {
        Object packet = Class.forName("org.cloudburstmc.protocol.bedrock.packet.AnimateEntityPacket")
                .getConstructor().newInstance();
        call(packet, "setAnimation", animationId);
        call(packet, "setController", "__runtime_controller");
        call(packet, "setNextState", "default");
        call(packet, "setStopExpression", "query.any_animation_finished");
        call(packet, "setStopExpressionVersion", 16777216);
        call(packet, "setBlendOutTime", 0f);
        Object ids = call(packet, "getRuntimeEntityIds");
        if (ids instanceof java.util.Collection<?> collection) {
            @SuppressWarnings("unchecked")
            java.util.Collection<Long> typed = (java.util.Collection<Long>) collection;
            typed.add(geyserId);
        }
        return packet;
    }

    private static void send(GeyserConnection connection, Object packet) throws Exception {
        Method method = method(connection.getClass(), "sendUpstreamPacket",
                packet.getClass());
        if (method == null) {
            method = method(connection.getClass(), "sendUpstreamPacket",
                    Class.forName("org.cloudburstmc.protocol.bedrock.packet.BedrockPacket"));
        }
        if (method == null) {
            throw new NoSuchMethodException("sendUpstreamPacket");
        }
        method.setAccessible(true);
        method.invoke(connection, packet);
    }

    private static Object call(Object target, String name, Object... arguments) throws Exception {
        Class<?>[] types = new Class<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            types[i] = arguments[i].getClass();
        }
        Method method = method(target.getClass(), name, types);
        if (method == null) {
            for (Method candidate : target.getClass().getMethods()) {
                if (!candidate.getName().equals(name)
                        || candidate.getParameterCount() != arguments.length) {
                    continue;
                }
                boolean compatible = true;
                for (int i = 0; i < arguments.length; i++) {
                    if (!matches(candidate.getParameterTypes()[i], types[i])) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) {
                    method = candidate;
                    break;
                }
            }
        }
        if (method == null) {
            throw new NoSuchMethodException(name);
        }
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    private static boolean matches(Class<?> parameter, Class<?> argument) {
        if (parameter.isPrimitive()) {
            if (parameter == boolean.class) {
                return argument == Boolean.class;
            }
            if (parameter == char.class) {
                return argument == Character.class;
            }
            if (parameter == byte.class) {
                return argument == Byte.class;
            }
            if (parameter == short.class) {
                return argument == Short.class;
            }
            if (parameter == int.class) {
                return argument == Integer.class;
            }
            if (parameter == long.class) {
                return argument == Long.class;
            }
            if (parameter == float.class) {
                return argument == Float.class;
            }
            if (parameter == double.class) {
                return argument == Double.class;
            }
        }
        return parameter.isAssignableFrom(argument);
    }

    private static @Nullable Method method(Class<?> type, String name, Class<?>... parameters) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, parameters);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Object vector3f(float x, float y, float z) throws Exception {
        Class<?> type = Class.forName("org.cloudburstmc.math.vector.Vector3f");
        return type.getMethod("from", float.class, float.class, float.class)
                .invoke(null, x, y, z);
    }

    private static Object vector2f(float x, float y) throws Exception {
        Class<?> type = Class.forName("org.cloudburstmc.math.vector.Vector2f");
        return type.getMethod("from", float.class, float.class)
                .invoke(null, x, y);
    }
}

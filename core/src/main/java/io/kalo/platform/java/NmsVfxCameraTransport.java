package io.kalo.platform.java;

import io.kalo.content.vfx.definition.VfxCameraFrame;
import io.kalo.integration.GeyserIntegration;
import io.kalo.utils.Plugins;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Java client-camera transport built on the vanilla "set camera" packet.
 *
 * <p>Bukkit has no camera API, so this class reaches the server's own classes at
 * runtime. The reflected route is deliberately narrow: resolve
 * {@code ClientboundSetCameraPacket}, build it from the NMS handle of a private
 * {@link Marker}, then pass it to the player's connection with
 * {@code ServerPlayer.connection.send(Packet)}. The marker carries the pose and is
 * visible only to the caster; the packet makes the client follow it. If any of those
 * signatures is missing, {@link #tryCreate()} returns {@code null} and the caller
 * keeps its no-op transport.</p>
 *
 * <p>The frame's field of view has no vanilla packet equivalent; the planner has
 * already folded it into the requested position as a dolly offset, so this transport
 * applies only the pose.</p>
 */
final class NmsVfxCameraTransport implements VfxCameraTransport {

    private static final String SET_CAMERA_PACKET =
            "net.minecraft.network.protocol.game.ClientboundSetCameraPacket";
    private static final String PACKET = "net.minecraft.network.protocol.Packet";
    private static final String ENTITY = "net.minecraft.world.entity.Entity";
    private static final String SERVER_PLAYER = "net.minecraft.server.level.ServerPlayer";

    private final Constructor<?> packetConstructor;
    private final Field connectionField;
    private final Method sendMethod;
    private final Map<UUID, Anchor> anchors = new ConcurrentHashMap<>();
    private volatile Method playerHandle;
    private volatile Method markerHandle;

    private NmsVfxCameraTransport(@NotNull Constructor<?> packetConstructor,
                                  @NotNull Field connectionField,
                                  @NotNull Method sendMethod) {
        this.packetConstructor = packetConstructor;
        this.connectionField = connectionField;
        this.sendMethod = sendMethod;
    }

    /** One camera entity and the session that was shown it. */
    private record Anchor(@NotNull Player viewer, @NotNull Marker marker) {
    }

    static @Nullable NmsVfxCameraTransport tryCreate() {
        try {
            ClassLoader loader = NmsVfxCameraTransport.class.getClassLoader();
            Class<?> entityClass = Class.forName(ENTITY, false, loader);
            Class<?> packetClass = Class.forName(PACKET, false, loader);
            Class<?> cameraPacketClass = Class.forName(SET_CAMERA_PACKET, false, loader);
            if (!packetClass.isAssignableFrom(cameraPacketClass)) {
                return null;
            }

            // The only constructor the transport needs is the one that takes the
            // entity whose client-side camera the packet should adopt.
            Constructor<?> constructor = cameraPacketClass.getConstructor(entityClass);

            Class<?> serverPlayerClass = Class.forName(SERVER_PLAYER, false, loader);
            Field connection = serverPlayerClass.getField("connection");
            Method send = connection.getType().getMethod("send", packetClass);
            return new NmsVfxCameraTransport(constructor, connection, send);
        } catch (Throwable missing) {
            // Absent or reshaped internals leave Java on the no-camera fallback.
            return null;
        }
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public void apply(@NotNull UUID playerId, @NotNull VfxCameraFrame frame) {
        try {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return;
            }
            if (GeyserIntegration.support().isBedrock(playerId)) {
                return;
            }

            Anchor current = anchors.get(playerId);
            if (current != null && !stillUsable(current, player)) {
                anchors.remove(playerId, current);
                retire(current.marker());
                current = null;
            }

            if (current == null) {
                Marker marker = spawnAnchor(player, frame);
                Anchor created = new Anchor(player, marker);
                anchors.put(playerId, created);
                try {
                    sendCamera(player, handle(marker));
                } catch (Throwable unsent) {
                    // Without the attachment the marker would be a stray entity and
                    // the map entry would promise a camera that was never adopted.
                    anchors.remove(playerId, created);
                    retire(marker);
                    throw unsent;
                }
                return;
            }

            // The packet stays attached to the entity, so each later frame only has
            // to move the marker to the requested pose.
            current.marker().teleport(pose(player.getWorld(), frame));
        } catch (Throwable trouble) {
            // A camera miss must not cancel the rest of the VFX or restore a view
            // the player did not ask for; leave the current view untouched.
            debug("Could not apply the VFX camera for " + playerId, trouble);
        }
    }

    @Override
    public void clear(@NotNull UUID playerId) {
        Anchor anchor = anchors.remove(playerId);
        try {
            if (!GeyserIntegration.support().isBedrock(playerId)) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    try {
                        sendCamera(player, handle(player));
                    } catch (Throwable unsent) {
                        debug("Could not restore the VFX camera for " + playerId, unsent);
                    }
                }
            }
        } catch (Throwable trouble) {
            debug("Could not clear the VFX camera for " + playerId, trouble);
        } finally {
            // The marker goes even when the restore packet could not be sent: an
            // offline player has no live camera, and a client whose marker vanished
            // falls back to its own view.
            if (anchor != null) {
                retire(anchor.marker());
            }
        }
    }

    private static boolean stillUsable(@NotNull Anchor anchor, @NotNull Player player) {
        Marker marker = anchor.marker();
        return anchor.viewer() == player
                && marker.isValid()
                && marker.getWorld().equals(player.getWorld());
    }

    private static @NotNull Marker spawnAnchor(@NotNull Player player,
                                               @NotNull VfxCameraFrame frame) {
        World world = player.getWorld();
        Marker marker = world.spawn(pose(world, frame), Marker.class, anchor -> {
            anchor.setPersistent(false);
            anchor.setVisibleByDefault(false);
            anchor.setSilent(true);
            anchor.setGravity(false);
        });
        try {
            // showEntity is the counterpart to setVisibleByDefault(false): only the
            // casting player receives the spawn packet for this anchor.
            player.showEntity(Plugins.plugin(), marker);
            return marker;
        } catch (Throwable unsent) {
            retire(marker);
            throw unsent;
        }
    }

    private static @NotNull Location pose(@NotNull World world,
                                          @NotNull VfxCameraFrame frame) {
        return new Location(world, frame.x(), frame.y(), frame.z(),
                frame.yaw(), frame.pitch());
    }

    /**
     * Builds a set-camera packet for {@code cameraEntity} and sends it through the
     * player's network listener.
     *
     * <p>The listener is reached exactly as a plugin would reach it without a
     * compile-time server dependency: {@code CraftPlayer.getHandle()} returns the
     * {@code ServerPlayer}, whose public {@code connection} field exposes
     * {@code send(Packet)}.</p>
     */
    private void sendCamera(@NotNull Player player, @NotNull Object cameraEntity)
            throws ReflectiveOperationException {
        Object packet = packetConstructor.newInstance(cameraEntity);
        Object serverPlayer = handle(player);
        Object connection = connectionField.get(serverPlayer);
        if (connection == null) {
            throw new IllegalStateException("ServerPlayer.connection is not ready");
        }
        sendMethod.invoke(connection, packet);
    }

    private @NotNull Object handle(@NotNull Object bukkitEntity)
            throws ReflectiveOperationException {
        boolean isPlayer = bukkitEntity instanceof Player;
        Method accessor = isPlayer ? playerHandle : markerHandle;
        if (accessor == null || !accessor.getDeclaringClass().isInstance(bukkitEntity)) {
            accessor = bukkitEntity.getClass().getMethod("getHandle");
            if (isPlayer) {
                playerHandle = accessor;
            } else {
                markerHandle = accessor;
            }
        }
        return accessor.invoke(bukkitEntity);
    }

    private static void retire(@Nullable Marker marker) {
        if (marker == null) {
            return;
        }
        try {
            if (marker.isValid()) {
                marker.remove();
            }
        } catch (Throwable trouble) {
            debug("Could not remove a VFX camera anchor", trouble);
        }
    }

    private static void debug(@NotNull String message, @NotNull Throwable cause) {
        try {
            Plugins.logger().log(Level.FINE, message, cause);
        } catch (Throwable ignored) {
            // Logging is best effort and must not replace the failure being reported.
        }
    }
}

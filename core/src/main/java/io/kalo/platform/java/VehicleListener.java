package io.kalo.platform.java;

import io.kalo.content.vehicle.Vehicle;
import io.kalo.content.vehicle.VehicleDefinition;
import io.kalo.integration.GeyserIntegration;
import io.kalo.platform.bedrock.BedrockRigCatalog;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rideable multi-seat vehicles with a real carrier entity.
 *
 * <p>The carrier is an invisible living entity — an armour stand by default — and every
 * declared seat is a separate invisible marker stand teleported to its offset each tick.
 * Players ride the markers, so each seat keeps its own position while the carrier moves;
 * seat zero is the driver. Java draws a display on the carrier and Bedrock gets a client
 * entity moved with it.</p>
 */
public final class VehicleListener implements Listener {

    public static final NamespacedKey VEHICLE_KEY =
            new NamespacedKey(Constants.PLUGIN_ID, "vehicle");
    public static final NamespacedKey VEHICLE_SEAT_KEY =
            new NamespacedKey(Constants.PLUGIN_ID, "vehicle_seat");
    public static final NamespacedKey VEHICLE_DISPLAY_KEY =
            new NamespacedKey(Constants.PLUGIN_ID, "vehicle_display");

    private final Map<UUID, Active> active = new ConcurrentHashMap<>();

    /** Spawns a vehicle and seats the player in the driver seat. */
    public @Nullable Entity spawn(@NotNull Player driver, @NotNull Vehicle vehicle) {
        RigModelRegistry.Piece piece = RigModelRegistry.get(vehicle.key());
        if (piece == null || piece.kind() != RigModelRegistry.Kind.VEHICLE) {
            return null;
        }
        VehicleDefinition definition = vehicle.definition();
        World world = driver.getWorld();
        Entity entity = world.spawnEntity(driver.getLocation(), definition.base());
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            return null;
        }
        living.setPersistent(true);
        living.setInvisible(true);
        living.setInvulnerable(true);
        living.setSilent(true);
        living.setGravity(false);
        if (living instanceof ArmorStand stand) {
            stand.setMarker(false);
            stand.setSmall(false);
            stand.setBasePlate(false);
            stand.setArms(false);
        }
        living.getPersistentDataContainer().set(VEHICLE_KEY, PersistentDataType.STRING,
                vehicle.key().asString());
        attach(living, definition, piece);
        Active value = active.get(living.getUniqueId());
        if (value != null) {
            mount(value, 0, driver);
        }
        return living;
    }

    private void attach(@NotNull LivingEntity vehicle, @NotNull VehicleDefinition definition,
                        @NotNull RigModelRegistry.Piece piece) {
        if (active.containsKey(vehicle.getUniqueId())) {
            return;
        }
        ItemDisplay display = RigDisplay.spawn(vehicle.getLocation(), piece.modelKey(),
                piece.yOffset(), piece.scale(), VEHICLE_DISPLAY_KEY,
                vehicle.getUniqueId().toString());
        String identifier = BedrockRigCatalog.identifierFor(definition.key());
        Active value = new Active(display.getUniqueId(), identifier, piece.yOffset(),
                piece.scale(), definition);
        value.seatMarkers = new ArrayList<>(createSeats(vehicle, definition));
        value.task = vehicle.getScheduler().runAtFixedRate(Plugins.plugin(),
                ignored -> tick(vehicle.getUniqueId()), null, 1L, 1L);
        active.put(vehicle.getUniqueId(), value);
        if (identifier != null) {
            GeyserIntegration.support().clientRigSpawned(display.getUniqueId(), identifier,
                    vehicle.getWorld().getUID(), vehicle.getLocation().getX(),
                    vehicle.getLocation().getY(), vehicle.getLocation().getZ(),
                    vehicle.getLocation().getYaw());
        }
    }

    private @NotNull List<UUID> createSeats(@NotNull LivingEntity vehicle,
                                            @NotNull VehicleDefinition definition) {
        List<UUID> markers = new ArrayList<>();
        for (int index = 0; index < definition.seats().size(); index++) {
            UUID marker = createSeat(vehicle, index, definition.seats().get(index));
            if (marker != null) {
                markers.add(marker);
            }
        }
        return markers;
    }

    private @Nullable UUID createSeat(@NotNull LivingEntity vehicle, int index,
                                      @NotNull VehicleDefinition.Seat seat) {
        Location location = VehicleSeats.seat(vehicle.getLocation(), seat);
        ArmorStand marker = vehicle.getWorld().spawn(location, ArmorStand.class, spawned -> {
            spawned.setMarker(true);
            spawned.setSmall(true);
            spawned.setInvisible(true);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setSilent(true);
            spawned.setPersistent(false);
            spawned.getPersistentDataContainer().set(VEHICLE_SEAT_KEY,
                    PersistentDataType.STRING, vehicle.getUniqueId() + ":" + index);
        });
        return marker.getUniqueId();
    }

    private void mount(@NotNull Active value, int seatIndex, @NotNull Player player) {
        if (seatIndex < 0 || seatIndex >= value.seatMarkers.size()) {
            return;
        }
        Entity marker = Bukkit.getEntity(value.seatMarkers.get(seatIndex));
        if (marker != null) {
            marker.addPassenger(player);
        }
    }

    private @Nullable Player seatPlayer(@NotNull Active value, int seatIndex) {
        if (seatIndex < 0 || seatIndex >= value.seatMarkers.size()) {
            return null;
        }
        Entity marker = Bukkit.getEntity(value.seatMarkers.get(seatIndex));
        if (marker == null) {
            return null;
        }
        return marker.getPassengers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .findFirst()
                .orElse(null);
    }

    private void tick(@NotNull UUID vehicleId) {
        Active value = active.get(vehicleId);
        if (value == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(vehicleId);
        if (!(entity instanceof LivingEntity vehicle) || !vehicle.isValid()
                || vehicle.isDead()) {
            stop(vehicleId);
            return;
        }
        Entity displayEntity = Bukkit.getEntity(value.displayId);
        if (!(displayEntity instanceof ItemDisplay display) || !display.isValid()) {
            stop(vehicleId);
            return;
        }

        ensureSeats(vehicle, value);
        Location carrier = vehicle.getLocation();
        for (int index = 0; index < value.seatMarkers.size(); index++) {
            Entity marker = Bukkit.getEntity(value.seatMarkers.get(index));
            if (!(marker instanceof ArmorStand stand)) {
                UUID replacement = createSeat(vehicle, index, value.definition.seats().get(index));
                if (replacement != null) {
                    value.seatMarkers.set(index, replacement);
                }
                continue;
            }
            stand.teleport(VehicleSeats.seat(carrier, value.definition.seats().get(index)));
            stand.setRotation(carrier.getYaw(), 0f);
        }

        VehicleDefinition definition = value.definition;
        Player driver = seatPlayer(value, 0);
        if (driver == null) {
            // Legacy/vehicles spawned before seat markers: accept a carrier passenger.
            driver = vehicle.getPassengers().stream()
                    .filter(Player.class::isInstance)
                    .map(Player.class::cast)
                    .findFirst()
                    .orElse(null);
        }
        double throttle = 0;
        boolean jump = false;
        float yaw = carrier.getYaw();
        if (driver != null && driver.isOnline()) {
            Input input = driver.getCurrentInput();
            jump = input.isJump();
            if (input.isSneak()) {
                for (UUID markerId : value.seatMarkers) {
                    if (Bukkit.getEntity(markerId) instanceof ArmorStand stand) {
                        stand.eject();
                    }
                }
                vehicle.eject();
                vehicle.setVelocity(new Vector(0, 0, 0));
                RigDisplay.follow(display, carrier, value.yOffset, value.scale);
                return;
            }
            if (input.isLeft()) {
                yaw -= value.definition.turnSpeed();
            }
            if (input.isRight()) {
                yaw += value.definition.turnSpeed();
            }
            if (input.isForward()) {
                throttle = value.definition.speed();
            } else if (input.isBackward()) {
                throttle = -value.definition.speed() / 2.0;
            }
            vehicle.setRotation(yaw, 0f);
            driver.setRotation(yaw, driver.getPitch());
        }
        boolean inWater = vehicle.getLocation().getBlock().getType()
                == org.bukkit.Material.WATER;
        boolean blocked = value.definition.physics().collision()
                && throttle != 0 && blockedAhead(vehicle.getLocation(), yaw, throttle);
        VehicleMotion.Motion motion = VehicleMotion.step(value.definition.physics(),
                yaw, throttle, inWater, jump, blocked);
        vehicle.setVelocity(new Vector(motion.x(), motion.y(), motion.z()));
        RigDisplay.follow(display, vehicle.getLocation(), value.yOffset, value.scale);
        int ticks = ++value.ticks;
        if (value.bedrockIdentifier == null && ticks % 20 == 0) {
            String resolved = BedrockRigCatalog.identifierFor(value.definition.key());
            if (resolved != null) {
                value.bedrockIdentifier = resolved;
                GeyserIntegration.support().clientRigSpawned(value.displayId, resolved,
                        vehicle.getWorld().getUID(), vehicle.getLocation().getX(),
                        vehicle.getLocation().getY(), vehicle.getLocation().getZ(), yaw);
            }
        }
        if (ticks % 2 == 0 && value.bedrockIdentifier != null) {
            GeyserIntegration.support().clientRigMoved(value.displayId,
                    vehicle.getLocation().getX(), vehicle.getLocation().getY(),
                    vehicle.getLocation().getZ(), yaw);
        }
    }

    private static boolean blockedAhead(@NotNull Location from, float yaw, double throttle) {
        double dx = -Math.sin(Math.toRadians(yaw)) * throttle;
        double dz = Math.cos(Math.toRadians(yaw)) * throttle;
        Location next = from.clone().add(dx, 0, dz);
        for (double y = 0; y <= 1.0; y += 0.9) {
            if (next.clone().add(0, y, 0).getBlock().isSolid()) {
                return true;
            }
        }
        return false;
    }

    private void ensureSeats(@NotNull LivingEntity vehicle, @NotNull Active value) {
        while (value.seatMarkers.size() < value.definition.seats().size()) {
            int index = value.seatMarkers.size();
            UUID replacement = createSeat(vehicle, index, value.definition.seats().get(index));
            if (replacement == null) {
                break;
            }
            value.seatMarkers.add(replacement);
        }
    }

    private static VehicleDefinition vehicleDefinition(Key key) {
        return io.kalo.manager.RegistryManager.GlobalRegistries.registries().vehicle()
                .get(key).map(Vehicle::definition).orElse(null);
    }

    private void stop(@NotNull UUID vehicleId) {
        Active value = active.remove(vehicleId);
        if (value == null) {
            return;
        }
        if (value.task != null) {
            value.task.cancel();
        }
        for (UUID markerId : value.seatMarkers) {
            if (Bukkit.getEntity(markerId) instanceof ArmorStand stand) {
                stand.eject();
                stand.remove();
            }
        }
        Entity display = Bukkit.getEntity(value.displayId);
        if (display != null) {
            display.remove();
        }
        if (value.bedrockIdentifier != null) {
            GeyserIntegration.support().clientRigRemoved(value.displayId);
        }
    }

    @EventHandler
    public void onInteract(@NotNull PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        UUID vehicleId = null;
        String direct = clicked.getPersistentDataContainer().get(VEHICLE_KEY,
                PersistentDataType.STRING);
        if (direct != null) {
            vehicleId = clicked.getUniqueId();
        } else {
            String seatRef = clicked.getPersistentDataContainer().get(VEHICLE_SEAT_KEY,
                    PersistentDataType.STRING);
            if (seatRef != null) {
                int colon = seatRef.indexOf(':');
                if (colon > 0) {
                    try {
                        vehicleId = UUID.fromString(seatRef.substring(0, colon));
                    } catch (IllegalArgumentException ignored) {
                        // Malformed marker; it will be recreated on the next tick.
                    }
                }
            }
        }
        if (vehicleId == null) {
            return;
        }
        Active value = active.get(vehicleId);
        if (value == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.getVehicle() != null) {
            return;
        }
        for (int index = 0; index < value.seatMarkers.size(); index++) {
            Entity marker = Bukkit.getEntity(value.seatMarkers.get(index));
            if (marker != null && marker.getPassengers().isEmpty()) {
                marker.addPassenger(player);
                return;
            }
        }
    }

    @EventHandler
    public void onDeath(@NotNull EntityDeathEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(VEHICLE_KEY,
                PersistentDataType.STRING)) {
            stop(event.getEntity().getUniqueId());
        }
    }

    @EventHandler
    public void onRemove(@NotNull EntityRemoveEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(VEHICLE_KEY,
                PersistentDataType.STRING)) {
            stop(event.getEntity().getUniqueId());
        }
    }

    @EventHandler
    public void onChunkLoad(@NotNull ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            String id = entity.getPersistentDataContainer().get(VEHICLE_KEY,
                    PersistentDataType.STRING);
            if (id != null && entity instanceof LivingEntity living) {
                RigModelRegistry.Piece piece = RigModelRegistry.get(Key.key(id));
                VehicleDefinition definition = vehicleDefinition(Key.key(id));
                if (piece != null && definition != null) {
                    attach(living, definition, piece);
                }
            }
        }
    }

    @EventHandler
    public void onChunkUnload(@NotNull ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity.getPersistentDataContainer().has(VEHICLE_KEY,
                    PersistentDataType.STRING)) {
                stop(entity.getUniqueId());
            }
        }
    }

    /** Reattaches vehicles in chunks loaded before Kalo started. */
    public void startAll() {
        Bukkit.getGlobalRegionScheduler().run(Plugins.plugin(), ignored -> {
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
                                            VEHICLE_KEY, PersistentDataType.STRING);
                                    if (id != null && entity instanceof LivingEntity living) {
                                        RigModelRegistry.Piece piece =
                                                RigModelRegistry.get(Key.key(id));
                                        VehicleDefinition definition = vehicleDefinition(
                                                Key.key(id));
                                        if (piece != null && definition != null) {
                                            attach(living, definition, piece);
                                        }
                                    }
                                }
                            });
                }
            }
        });
    }

    private static final class Active {
        private final UUID displayId;
        private @Nullable String bedrockIdentifier;
        private final double yOffset;
        private final double scale;
        private final VehicleDefinition definition;
        private List<UUID> seatMarkers = List.of();
        private io.papermc.paper.threadedregions.scheduler.ScheduledTask task;
        private int ticks;

        private Active(UUID displayId, @Nullable String bedrockIdentifier,
                       double yOffset, double scale, VehicleDefinition definition) {
            this.displayId = displayId;
            this.bedrockIdentifier = bedrockIdentifier;
            this.yOffset = yOffset;
            this.scale = scale;
            this.definition = definition;
        }
    }
}

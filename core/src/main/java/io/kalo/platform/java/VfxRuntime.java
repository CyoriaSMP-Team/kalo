package io.kalo.platform.java;

import io.kalo.content.vfx.definition.VfxCameraFrame;
import io.kalo.content.vfx.definition.VfxCameraPlan;
import io.kalo.content.vfx.definition.VfxCastOptions;
import io.kalo.content.vfx.definition.VfxCondition;
import io.kalo.content.vfx.definition.VfxConditionContext;
import io.kalo.content.vfx.definition.VfxDefinition;
import io.kalo.content.vfx.definition.VfxModelPlacement;
import io.kalo.content.vfx.definition.VfxPlayback;
import io.kalo.content.vfx.definition.VfxRecipe;
import io.kalo.content.vfx.definition.VfxSimulation;
import io.kalo.content.vfx.definition.VfxSpriteBudget;
import io.kalo.integration.GeyserIntegration;
import io.kalo.integration.PlaceholderApiIntegration;
import io.kalo.platform.bedrock.BedrockVfxRegistry;
import io.kalo.utils.Plugins;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Plays a VFX as Java item displays.
 *
 * <p>The simulation is pure and deterministic; this class only turns each frame into
 * {@link ItemDisplay} spawn/move/remove operations. Loops use {@link VfxPlayback} for
 * build-up, repeated sections and a graceful fade, and sound events are played from the
 * recipe's timeline.</p>
 */
public final class VfxRuntime implements Listener {

    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final VfxCameraTransport CAMERA = VfxCameraTransports.create();

    public VfxRuntime() {
    }

    public static boolean play(@NotNull Player caster, @NotNull VfxDefinition definition) {
        return play(caster, definition, VfxCastOptions.ONCE);
    }

    public static boolean play(@NotNull Player caster, @NotNull VfxDefinition definition,
                               @NotNull VfxCastOptions options) {
        Active previous = ACTIVE.remove(caster.getUniqueId());
        if (previous != null) {
            stop(previous);
        }
        Location origin = castLocation(caster.getLocation(), options);
        double yaw = caster.getLocation().getYaw() + (float) options.turn();
        long seed = options.seed() == null
                ? System.nanoTime() ^ definition.key().hashCode()
                : options.seed();
        VfxSimulation simulation = new VfxSimulation(definition.recipe(),
                new VfxRecipe.Vec3(origin.getX(), origin.getY(), origin.getZ()),
                yaw, seed);
        Active active = new Active(SEQUENCE.incrementAndGet(), caster.getUniqueId(),
                origin, yaw, definition, simulation,
                new VfxPlayback(definition.recipe(), options.loop(), options.cycles(),
                        options.speed(), options.loopTicks()),
                options);
        ACTIVE.put(caster.getUniqueId(), active);
        enforceBudget(active);
        try {
            // Camera layers read the caster's eye every tick, so they use the caster's
            // scheduler even when the rest of the effect is fixed at the origin.
            if (options.follow() || definition.cameraLayerCount() > 0) {
                active.task = caster.getScheduler().runAtFixedRate(Plugins.plugin(),
                        ignored -> tick(active), null, 1L, 1L);
            } else {
                active.task = Bukkit.getRegionScheduler().runAtFixedRate(Plugins.plugin(),
                        origin, ignored -> tick(active), 1L, 1L);
            }
            return true;
        } catch (RuntimeException unsupported) {
            ACTIVE.remove(caster.getUniqueId(), active);
            removeDisplays(active);
            return false;
        }
    }

    /** Requests a graceful stop; a loop with a fade keeps running to the end of the fade. */
    public static void stop(@NotNull Player caster) {
        Active active = ACTIVE.get(caster.getUniqueId());
        if (active == null) {
            return;
        }
        requestStop(active);
    }

    /** Requests a graceful stop for every cast carrying this tag. */
    public static void stopTag(@NotNull String tag) {
        for (Active active : List.copyOf(ACTIVE.values())) {
            if (active.options.tagOr(active.definition.key().asString()).equals(tag)) {
                requestStop(active);
            }
        }
    }

    public static void clear() {
        for (Active active : List.copyOf(ACTIVE.values())) {
            ACTIVE.remove(active.casterId, active);
            stop(active);
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        Active active = ACTIVE.remove(event.getPlayer().getUniqueId());
        if (active != null) {
            stop(active);
        }
    }

    private static void requestStop(@NotNull Active active) {
        if (active.cancelled) {
            return;
        }
        if (active.options.finishOnStop() && active.playback.requestStop()) {
            active.delayRemaining = 0;
            return;
        }
        if (ACTIVE.remove(active.casterId, active)) {
            stop(active);
        }
    }

    private static void tick(@NotNull Active active) {
        if (active.cancelled) {
            return;
        }
        Player caster = Bukkit.getPlayer(active.casterId);
        boolean casterAnchored = active.options.follow()
                || active.definition.cameraLayerCount() > 0;
        if (caster == null || !caster.isOnline()
                || (casterAnchored && !caster.getWorld().equals(active.world))) {
            stop(active);
            return;
        }

        if (active.delayRemaining > 0) {
            active.delayRemaining--;
            return;
        }

        if (!checkConditions(active, caster)) {
            return;
        }

        Location frameOrigin = active.options.follow()
                ? castLocation(caster.getLocation(), active.options) : active.origin;
        double frameYaw = active.options.follow()
                ? caster.getLocation().getYaw() + (float) active.options.turn()
                : active.yaw;

        VfxPlayback.Frame frame = active.playback.next();
        if (frame == null) {
            if (ACTIVE.remove(active.casterId, active)) {
                stop(active);
            }
            return;
        }

        VfxSimulation.CameraBasis camera = null;
        if (active.definition.cameraLayerCount() > 0) {
            Location eye = caster.getEyeLocation();
            Location view = caster.getLocation();
            camera = new VfxSimulation.CameraBasis(eye.getX(), eye.getY(), eye.getZ(),
                    view.getYaw(), view.getPitch());
        }
        List<VfxSimulation.Sprite> sprites = active.simulation.frame(frame.recipeTick(),
                frame.cycle(), frame.fromSpawn(), frame.toSpawn(),
                new VfxRecipe.Vec3(frameOrigin.getX(), frameOrigin.getY(),
                        frameOrigin.getZ()), frameYaw, active.options.scale(), camera);
        Set<Integer> live = sprites.stream().map(VfxSimulation.Sprite::moteId)
                .collect(Collectors.toSet());

        Iterator<Map.Entry<Integer, ItemDisplay>> iterator =
                active.displays.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ItemDisplay> entry = iterator.next();
            if (!live.contains(entry.getKey())) {
                remove(entry.getValue());
                iterator.remove();
            }
        }

        for (VfxSimulation.Sprite sprite : sprites) {
            ItemDisplay display = active.displays.get(sprite.moteId());
            Location location = new Location(active.world, sprite.x(), sprite.y(), sprite.z());
            if (display == null || !display.isValid()) {
                if (display != null) {
                    GeyserIntegration.support().clientRigRemoved(display.getUniqueId());
                }
                display = spawn(active, sprite, caster);
                active.displays.put(sprite.moteId(), display);
                sendBedrockSpawn(active, sprite, display, caster);
            } else {
                display.teleport(location);
                sendBedrockMove(active, display, sprite, caster);
            }
            apply(display, sprite.size());
        }
        updateModels(active, frame, frameOrigin, frameYaw, caster);
        updateCamera(active, frame, camera);
        playSounds(active, frame, frameOrigin, caster);
    }

    private static void updateCamera(@NotNull Active active,
                                     @NotNull VfxPlayback.Frame frame,
                                     @Nullable VfxSimulation.CameraBasis camera) {
        if (camera == null) {
            return;
        }
        VfxCameraFrame pose = VfxCameraPlan.activeFrame(active.definition.recipe(), camera,
                frame.recipeTick(), active.options.scale());
        if (pose != null) {
            VfxCameraDelivery.apply(CAMERA, active.casterId, pose);
            active.cameraActive = true;
        } else if (active.cameraActive) {
            VfxCameraDelivery.clear(CAMERA, active.casterId);
            active.cameraActive = false;
        }
    }

    private static boolean checkConditions(@NotNull Active active,
                                            @NotNull Player caster) {
        if (active.options.until() == null && active.options.whileCondition() == null) {
            return true;
        }
        if (active.conditionCounter > 0) {
            active.conditionCounter--;
            return true;
        }
        active.conditionCounter = active.options.checkTicks() - 1;
        Map<String, String> values = new HashMap<>();
        values.put("player", caster.getName());
        values.put("target", caster.getName());
        values.put("name", caster.getName());
        values.put("uuid", caster.getUniqueId().toString());
        values.put("world", caster.getWorld().getName());
        values.put("health", Double.toString(caster.getHealth()));
        values.put("max_health", Double.toString(caster.getMaxHealth()));
        values.put("food", Integer.toString(caster.getFoodLevel()));
        values.put("level", Integer.toString(caster.getLevel()));
        values.put("xp", Float.toString(caster.getExp()));
        values.put("x", Double.toString(caster.getLocation().getX()));
        values.put("y", Double.toString(caster.getLocation().getY()));
        values.put("z", Double.toString(caster.getLocation().getZ()));
        values.put("gamemode", caster.getGameMode().name().toLowerCase());
        VfxCondition.Context context = VfxConditionContext.create(values,
                name -> PlaceholderApiIntegration.resolve(caster, name),
                caster::hasPermission);
        if (active.options.until() != null
                && active.options.until().evaluate(context)) {
            requestStop(active);
            return !active.cancelled;
        }
        if (active.options.whileCondition() != null
                && !active.options.whileCondition().evaluate(context)) {
            requestStop(active);
            return !active.cancelled;
        }
        return true;
    }

    private static void playSounds(@NotNull Active active,
                                   @NotNull VfxPlayback.Frame frame,
                                   @NotNull Location origin, @NotNull Player caster) {
        if (active.options.quiet()) {
            return;
        }
        if (active.lastSoundTick == frame.recipeTick()
                && active.lastSoundCycle == frame.cycle()) {
            return;
        }
        active.lastSoundTick = frame.recipeTick();
        active.lastSoundCycle = frame.cycle();
        for (VfxRecipe.Sound sound : active.definition.recipe().sounds()) {
            if (sound.tick() >= frame.fromSpawn() && sound.tick() < frame.toSpawn()
                    && sound.tick() == frame.recipeTick()) {
                float volume = (float) (sound.volume() * active.options.volume());
                float pitch = (float) sound.pitch();
                if (active.options.onlyCaster()) {
                    caster.playSound(origin, sound.name(), volume, pitch);
                } else {
                    active.world.playSound(origin, sound.name(), volume, pitch);
                }
            }
        }
    }

    private static void enforceBudget(@NotNull Active incoming) {
        int maximum = Plugins.plugin().getConfig().getInt("vfx.max-sprites", 4000);
        if (maximum <= 0) {
            return;
        }
        List<VfxSpriteBudget.Usage> usages = ACTIVE.values().stream()
                .map(active -> new VfxSpriteBudget.Usage(active.sequence,
                        active.simulation.maxSprites()))
                .toList();
        for (long sequence : VfxSpriteBudget.stopOldest(usages, maximum)) {
            if (sequence == incoming.sequence) {
                continue;
            }
            Active oldest = ACTIVE.values().stream()
                    .filter(active -> active.sequence == sequence)
                    .findFirst().orElse(null);
            if (oldest != null && ACTIVE.remove(oldest.casterId, oldest)) {
                stop(oldest);
            }
        }
    }

    private static @NotNull ItemDisplay spawn(@NotNull Active active,
                                              @NotNull VfxSimulation.Sprite sprite,
                                              @NotNull Player caster) {
        Location location = new Location(active.world, sprite.x(), sprite.y(), sprite.z());
        ItemDisplay display = active.world.spawn(location, ItemDisplay.class, spawned -> {
            spawned.setItemStack(stack(active.definition.spriteModelFor(sprite.layerIndex())));
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setSilent(true);
            spawned.setViewRange(64f);
            spawned.setInterpolationDuration(1);
            spawned.setVisibleByDefault(!active.options.onlyCaster());
            apply(spawned, sprite.size());
        });
        if (active.options.onlyCaster()) {
            caster.showEntity(Plugins.plugin(), display);
        }
        return display;
    }

    private static @NotNull Location castLocation(@NotNull Location location,
                                                  @NotNull VfxCastOptions options) {
        VfxRecipe.Vec3 placed = io.kalo.content.vfx.definition.VfxPlacement.offset(
                location.getX(), location.getY(), location.getZ(), location.getYaw(),
                options.offset());
        return new Location(location.getWorld(), placed.x(), placed.y(), placed.z(),
                location.getYaw(), location.getPitch());
    }

    private static void apply(@NotNull ItemDisplay display, double size) {
        float scale = (float) size;
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(scale, scale, scale), new Quaternionf()));
    }

    private static @NotNull ItemStack stack(@NotNull net.kyori.adventure.key.Key model) {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, model);
        return stack;
    }

    private static void stop(@NotNull Active active) {
        active.cancelled = true;
        if (active.task != null) {
            active.task.cancel();
            active.task = null;
        }
        if (active.cameraActive) {
            VfxCameraDelivery.clear(CAMERA, active.casterId);
            active.cameraActive = false;
        }
        removeDisplays(active);
    }

    private static void updateModels(@NotNull Active active,
                                     @NotNull VfxPlayback.Frame frame,
                                     @NotNull Location frameOrigin, double frameYaw,
                                     @NotNull Player caster) {
        List<VfxRecipe.Model> models = active.definition.recipe().models();
        VfxRecipe.Vec3 origin = new VfxRecipe.Vec3(frameOrigin.getX(), frameOrigin.getY(),
                frameOrigin.getZ());
        Set<Integer> live = new HashSet<>();
        for (int index = 0; index < models.size(); index++) {
            VfxRecipe.Model model = models.get(index);
            if (frame.recipeTick() < model.start() || frame.recipeTick() >= model.end()) {
                continue;
            }
            VfxModelPlacement.Placed placed = VfxModelPlacement.at(origin, frameYaw,
                    model, frame.recipeTick());
            live.add(index);
            ItemDisplay display = active.models.get(index);
            if (display == null || !display.isValid()) {
                if (display != null) {
                    GeyserIntegration.support().clientRigRemoved(display.getUniqueId());
                }
                display = spawnModel(active, model, placed, caster);
                active.models.put(index, display);
                sendBedrockModelSpawn(active, index, placed, display, caster);
            } else {
                display.teleport(new Location(active.world, placed.x(), placed.y(),
                        placed.z()));
                applyModel(display, placed);
                sendBedrockModelMove(display, placed);
            }
        }
        Iterator<Map.Entry<Integer, ItemDisplay>> iterator =
                active.models.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ItemDisplay> entry = iterator.next();
            if (!live.contains(entry.getKey())) {
                removeModel(entry.getValue());
                iterator.remove();
            }
        }
    }

    private static @NotNull ItemDisplay spawnModel(@NotNull Active active,
                                                   @NotNull VfxRecipe.Model model,
                                                   @NotNull VfxModelPlacement.Placed placed,
                                                   @NotNull Player caster) {
        Location location = new Location(active.world, placed.x(), placed.y(), placed.z(),
                placed.yaw(), placed.pitch());
        ItemDisplay display = active.world.spawn(location, ItemDisplay.class, spawned -> {
            spawned.setItemStack(stack(model.model()));
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setSilent(true);
            spawned.setViewRange(64f);
            spawned.setInterpolationDuration(1);
            spawned.setVisibleByDefault(!active.options.onlyCaster());
            applyModel(spawned, placed);
        });
        if (active.options.onlyCaster()) {
            caster.showEntity(Plugins.plugin(), display);
        }
        return display;
    }

    private static void applyModel(@NotNull ItemDisplay display,
                                   @NotNull VfxModelPlacement.Placed placed) {
        display.setRotation(placed.yaw(), placed.pitch());
        Quaternionf rotation = new Quaternionf()
                .rotateZ((float) Math.toRadians(placed.roll()));
        float scale = placed.scale();
        display.setTransformation(new Transformation(new Vector3f(), rotation,
                new Vector3f(scale, scale, scale), new Quaternionf()));
    }

    private static void sendBedrockModelSpawn(@NotNull Active active, int modelIndex,
                                              @NotNull VfxModelPlacement.Placed placed,
                                              @NotNull ItemDisplay display,
                                              @NotNull Player caster) {
        String identifier = BedrockVfxRegistry.modelFor(active.definition.key(), modelIndex);
        if (identifier == null) {
            return;
        }
        VfxBedrockDelivery.modelSpawn(GeyserIntegration.support(), display.getUniqueId(),
                identifier, active.world.getUID(), placed,
                active.options.onlyCaster() ? caster.getUniqueId() : null);
    }

    private static void sendBedrockModelMove(@NotNull ItemDisplay display,
                                             @NotNull VfxModelPlacement.Placed placed) {
        VfxBedrockDelivery.modelMove(GeyserIntegration.support(), display.getUniqueId(),
                placed);
    }

    private static void removeModel(@NotNull ItemDisplay display) {
        GeyserIntegration.support().clientRigRemoved(display.getUniqueId());
        if (display.isValid()) {
            display.remove();
        }
    }

    private static void removeDisplays(@NotNull Active active) {
        for (ItemDisplay display : active.displays.values()) {
            remove(display);
        }
        active.displays.clear();
        for (ItemDisplay display : active.models.values()) {
            removeModel(display);
        }
        active.models.clear();
    }

    private static void sendBedrockSpawn(@NotNull Active active,
                                         @NotNull VfxSimulation.Sprite sprite,
                                         @NotNull ItemDisplay display,
                                         @NotNull Player caster) {
        String identifier = BedrockVfxRegistry.entityFor(active.definition.key(),
                sprite.layerIndex());
        if (identifier == null) {
            return;
        }
        float yaw = VfxDefinition.isCameraLayer(sprite.layerIndex())
                ? caster.getLocation().getYaw() : 0f;
        VfxBedrockDelivery.spawn(GeyserIntegration.support(), display.getUniqueId(),
                identifier, active.world.getUID(), sprite, yaw,
                active.options.onlyCaster() ? caster.getUniqueId() : null,
                active.options.scale());
    }

    private static void sendBedrockMove(@NotNull Active active,
                                        @NotNull ItemDisplay display,
                                        @NotNull VfxSimulation.Sprite sprite,
                                        @NotNull Player caster) {
        float yaw = VfxDefinition.isCameraLayer(sprite.layerIndex())
                ? caster.getLocation().getYaw() : 0f;
        VfxBedrockDelivery.move(GeyserIntegration.support(), display.getUniqueId(),
                sprite, yaw, active.options.scale());
    }

    private static void remove(@NotNull ItemDisplay display) {
        GeyserIntegration.support().clientRigRemoved(display.getUniqueId());
        if (display.isValid()) {
            display.remove();
        }
    }

    private static final class Active {
        private final long sequence;
        private final UUID casterId;
        private final Location origin;
        private final double yaw;
        private final World world;
        private final VfxDefinition definition;
        private final VfxSimulation simulation;
        private final VfxPlayback playback;
        private final VfxCastOptions options;
        private final Map<Integer, ItemDisplay> displays = new HashMap<>();
        private final Map<Integer, ItemDisplay> models = new HashMap<>();
        private int delayRemaining;
        private int conditionCounter;
        private boolean cameraActive;
        private int lastSoundTick = -1;
        private long lastSoundCycle = -1;
        private io.papermc.paper.threadedregions.scheduler.ScheduledTask task;
        private volatile boolean cancelled;

        private Active(long sequence, @NotNull UUID casterId, @NotNull Location origin,
                       double yaw, @NotNull VfxDefinition definition,
                       @NotNull VfxSimulation simulation,
                       @NotNull VfxPlayback playback,
                       @NotNull VfxCastOptions options) {
            this.sequence = sequence;
            this.casterId = casterId;
            this.origin = origin;
            this.yaw = yaw;
            this.world = origin.getWorld();
            this.definition = definition;
            this.simulation = simulation;
            this.playback = playback;
            this.options = options;
            this.delayRemaining = options.delayTicks();
        }
    }
}

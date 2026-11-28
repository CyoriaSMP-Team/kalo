package io.kalo.platform.java;

import io.kalo.model.ModelIR;
import io.kalo.model.animation.ModelAnimations;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publish point between the Java pack compiler and the world runtime.
 *
 * <p>A per-bone display rig needs three things the pack JSON cannot carry at runtime: the
 * Blockbench pivot of each bone, the model key for that bone's cubes, and the sampled
 * animation clips. The compiler already has all three while it writes the pack, so it
 * records them here; {@link JavaBlockListener} reads them when the block is placed or a
 * chunk loads.</p>
 *
 * <p>The registry is rebuilt from the model files on every pack generation, so it cannot
 * go stale relative to the generated pack.</p>
 */
public final class BlockbenchRigRegistry {

    private static final Map<Key, Rig> RIGS = new ConcurrentHashMap<>();
    /** Placed item rigs live in their own map: an item and a block may share a key. */
    private static final Map<Key, Rig> PLACED = new ConcurrentHashMap<>();
    private static final Map<Key, Rig> MOBS = new ConcurrentHashMap<>();
    private static final Map<Key, Rig> WORN = new ConcurrentHashMap<>();
    private static final Map<Key, Rig> EMOTES = new ConcurrentHashMap<>();

    private BlockbenchRigRegistry() {
    }

    /** One display entity's worth of geometry: a bone, its pivot and its Java model. */
    public record BoneRig(
            @NotNull String boneId,
            @NotNull String boneName,
            @Nullable String parentId,
            @NotNull ModelIR.Vec3 pivot,
            @NotNull Key modelKey
    ) {
    }

    public record Rig(
            @NotNull Key contentKey,
            @NotNull List<BoneRig> bones,
            @NotNull List<ModelAnimations.Clip> animations,
            @NotNull List<ModelAnimations.Clip> allAnimations,
            @NotNull List<io.kalo.model.ModelIR.Controller> controllers
    ) {
        public Rig {
            bones = List.copyOf(bones);
            animations = List.copyOf(animations);
            allAnimations = List.copyOf(allAnimations);
            controllers = List.copyOf(controllers);
        }

        public Rig(@NotNull Key contentKey, @NotNull List<BoneRig> bones,
                   @NotNull List<ModelAnimations.Clip> animations,
                   @NotNull List<ModelAnimations.Clip> allAnimations) {
            this(contentKey, bones, animations, allAnimations, List.of());
        }

        /** Backward-compatible constructor for a rig whose clips are all loops. */
        public Rig(@NotNull Key contentKey, @NotNull List<BoneRig> bones,
                   @NotNull List<ModelAnimations.Clip> animations) {
            this(contentKey, bones, animations, animations);
        }

        public boolean hasLoops() {
            return !animations.isEmpty();
        }

        public boolean animateable() {
            return !bones.isEmpty() && !allAnimations.isEmpty();
        }
    }

    public static void clear() {
        RIGS.clear();
        PLACED.clear();
        MOBS.clear();
        WORN.clear();
        EMOTES.clear();
    }

    public static void put(@NotNull Rig rig) {
        RIGS.put(rig.contentKey(), rig);
    }

    public static @Nullable Rig get(@NotNull Key contentKey) {
        return RIGS.get(contentKey);
    }

    public static void putPlaced(@NotNull Rig rig) {
        PLACED.put(rig.contentKey(), rig);
    }

    public static @Nullable Rig getPlaced(@NotNull Key contentKey) {
        return PLACED.get(contentKey);
    }

    public static void putMob(@NotNull Rig rig) {
        MOBS.put(rig.contentKey(), rig);
    }

    public static @Nullable Rig getMob(@NotNull Key contentKey) {
        return MOBS.get(contentKey);
    }

    public static void putWorn(@NotNull Rig rig) {
        WORN.put(rig.contentKey(), rig);
    }

    public static @Nullable Rig getWorn(@NotNull Key contentKey) {
        return WORN.get(contentKey);
    }

    public static void putEmote(@NotNull Rig rig) {
        EMOTES.put(rig.contentKey(), rig);
    }

    public static @Nullable Rig getEmote(@NotNull Key contentKey) {
        return EMOTES.get(contentKey);
    }

    public static @NotNull Collection<Rig> all() {
        return List.copyOf(RIGS.values());
    }
}

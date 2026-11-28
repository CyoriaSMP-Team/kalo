package io.kalo.platform.java;

import io.kalo.model.ModelIR.Vec3;
import io.kalo.model.animation.ModelAnimations.BonePose;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a sampled bone pose into a display-entity transform.
 *
 * <p>The per-bone Java model keeps its cubes at their authored model coordinates, and
 * the display entity sits at the block centre. Rotating around a bone pivot therefore
 * needs the correction {@code t = d - R*S*d}, where {@code d} is the pivot offset from
 * the model centre in blocks. That makes the whole transform read as
 * {@code T * R * S}: rotate and scale around the pivot, then apply the authored position
 * channel.</p>
 *
 * <p>Blockbench stores rotations in degrees and the Bedrock client applies them in ZYX
 * order, so the quaternion is built the same way. X/Y are not negated here: that mirror
 * belongs to Bedrock geometry, while Java renders authored space.</p>
 */
final class BlockbenchRigTransforms {

    /** Display models are centred on (8,8,8) in the 16-unit authored space. */
    private static final float CENTRE = 8.0f;

    private BlockbenchRigTransforms() {
    }

    static @NotNull Transformation transform(@NotNull Vec3 pivot,
                                             @NotNull BonePose pose) {
        return transform(pivot, pose, 1.0);
    }

    /**
     * @param baseScale the placement/display scale the whole model is drawn at; pivots
     *                  and authored positions scale with it
     */
    static @NotNull Transformation transform(@NotNull Vec3 pivot, @NotNull BonePose pose,
                                             double baseScale) {
        return transform(pivot, pose, baseScale, 0.0);
    }

    /**
     * Composes every bone's local pose through its parent chain before converting it to
     * a display transform. A child inherits the parent's rotation, position and scale;
     * the result is keyed by bone id.
     */
    static @NotNull Map<String, Transformation> pose(
            @NotNull List<BlockbenchRigRegistry.BoneRig> bones,
            @NotNull io.kalo.model.animation.ModelAnimations.Pose pose,
            double baseScale, double baseYOffset) {
        Map<String, BlockbenchRigRegistry.BoneRig> byId = new LinkedHashMap<>();
        for (BlockbenchRigRegistry.BoneRig bone : bones) {
            byId.put(bone.boneId(), bone);
        }
        Map<String, Affine> done = new HashMap<>();
        Map<String, Transformation> result = new LinkedHashMap<>();
        for (BlockbenchRigRegistry.BoneRig bone : bones) {
            Affine world = compose(bone, byId, done, pose, new HashSet<>());
            result.put(bone.boneId(), toTransformation(world, baseScale, baseYOffset));
        }
        return result;
    }

    private static @NotNull Affine compose(
            @NotNull BlockbenchRigRegistry.BoneRig bone,
            @NotNull Map<String, BlockbenchRigRegistry.BoneRig> byId,
            @NotNull Map<String, Affine> done,
            @NotNull io.kalo.model.animation.ModelAnimations.Pose pose,
            @NotNull Set<String> visiting) {
        Affine cached = done.get(bone.boneId());
        if (cached != null) {
            return cached;
        }
        Affine local = local(bone, pose);
        if (!visiting.add(bone.boneId())) {
            // A corrupt cycle must not recurse; the imported IR rejects these, but a
            // hand-edited registry should still render something.
            return local;
        }
        BlockbenchRigRegistry.BoneRig parent = bone.parentId() == null
                ? null : byId.get(bone.parentId());
        Affine world = parent == null ? local
                : compose(parent, byId, done, pose, visiting).then(local);
        visiting.remove(bone.boneId());
        done.put(bone.boneId(), world);
        return world;
    }

    private static @NotNull Affine local(
            @NotNull BlockbenchRigRegistry.BoneRig bone,
            @NotNull io.kalo.model.animation.ModelAnimations.Pose pose) {
        io.kalo.model.animation.ModelAnimations.BonePose sampled =
                pose.bone(bone.boneId());
        Quaternionf rotation = new Quaternionf().rotationZYX(
                (float) Math.toRadians(sampled.rotation().z()),
                (float) Math.toRadians(sampled.rotation().y()),
                (float) Math.toRadians(sampled.rotation().x()));
        Vector3f scale = new Vector3f(
                (float) sampled.scale().x(), (float) sampled.scale().y(),
                (float) sampled.scale().z());
        Matrix3f linear = new Matrix3f().rotation(rotation).scale(scale);

        Vector3f pivot = new Vector3f((float) bone.pivot().x(),
                (float) bone.pivot().y(), (float) bone.pivot().z());
        Vector3f translation = new Vector3f(pivot)
                .add((float) sampled.position().x(), (float) sampled.position().y(),
                        (float) sampled.position().z())
                .sub(linear.transform(new Vector3f(pivot), new Vector3f()));
        return new Affine(linear, translation);
    }

    private static @NotNull Transformation toTransformation(@NotNull Affine affine,
                                                            double baseScale,
                                                            double baseYOffset) {
        Vector3f centre = new Vector3f(CENTRE, CENTRE, CENTRE);
        Vector3f offset = affine.linear.transform(new Vector3f(centre), new Vector3f())
                .add(affine.translation)
                .sub(centre)
                .mul((float) (baseScale / 16.0));
        offset.y += (float) baseYOffset;
        Quaternionf rotation = affine.linear.getNormalizedRotation(new Quaternionf());
        Vector3f scale = affine.linear.getScale(new Vector3f());
        return new Transformation(offset, rotation, scale, new Quaternionf());
    }

    private record Affine(@NotNull Matrix3f linear, @NotNull Vector3f translation) {
        private @NotNull Affine then(@NotNull Affine child) {
            Matrix3f combined = new Matrix3f(linear).mul(child.linear);
            Vector3f moved = linear.transform(new Vector3f(child.translation), new Vector3f())
                    .add(translation);
            return new Affine(combined, moved);
        }
    }

    /**
     * @param baseYOffset lifts an entity-space model whose feet are at y=0 onto the
     *                    display origin; item models are rendered around (8,8,8)
     */
    static @NotNull Transformation transform(@NotNull Vec3 pivot, @NotNull BonePose pose,
                                             double baseScale, double baseYOffset) {
        Vector3f offset = new Vector3f(
                (float) ((pivot.x() - CENTRE) / 16.0 * baseScale),
                (float) ((pivot.y() - CENTRE) / 16.0 * baseScale),
                (float) ((pivot.z() - CENTRE) / 16.0 * baseScale));
        Vector3f scale = new Vector3f(
                (float) (pose.scale().x() * baseScale),
                (float) (pose.scale().y() * baseScale),
                (float) (pose.scale().z() * baseScale));
        Quaternionf rotation = new Quaternionf().rotationZYX(
                (float) Math.toRadians(pose.rotation().z()),
                (float) Math.toRadians(pose.rotation().y()),
                (float) Math.toRadians(pose.rotation().x()));

        Vector3f rotated = rotation.transform(new Vector3f(offset).mul(scale),
                new Vector3f());
        Vector3f translation = new Vector3f(offset).sub(rotated)
                .add((float) (pose.position().x() / 16.0 * baseScale),
                        (float) (pose.position().y() / 16.0 * baseScale),
                        (float) (pose.position().z() / 16.0 * baseScale));
        translation.y += (float) baseYOffset;
        return new Transformation(translation, rotation, scale, new Quaternionf());
    }
}

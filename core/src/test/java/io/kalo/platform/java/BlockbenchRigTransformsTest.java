package io.kalo.platform.java;

import io.kalo.model.ModelIR.Vec3;
import io.kalo.model.animation.ModelAnimations.BonePose;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockbenchRigTransformsTest {

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 1e-5);
        assertEquals(expected.y, actual.y, 1e-5);
        assertEquals(expected.z, actual.z, 1e-5);
    }

    @Test
    void theRestPoseIsIdentityForACentredPivot() {
        Transformation transform = BlockbenchRigTransforms.transform(
                new Vec3(8, 8, 8), BonePose.NEUTRAL);
        assertVector(new Vector3f(0, 0, 0), transform.getTranslation());
        assertVector(new Vector3f(1, 1, 1), transform.getScale());
        assertVector(new Vector3f(1, 0, 0),
                transform.getLeftRotation().transform(new Vector3f(1, 0, 0)));
    }

    @Test
    void aZRotationFollowsTheAuthoredSign() {
        Transformation transform = BlockbenchRigTransforms.transform(new Vec3(8, 8, 8),
                new BonePose(new Vec3(0, 0, -60), Vec3.ZERO, Vec3.ONE));
        // Rotating +X by -60 degrees around Z lands at (cos60, -sin60).
        assertVector(new Vector3f(0.5f, -0.8660254f, 0),
                transform.getLeftRotation().transform(new Vector3f(1, 0, 0)));
        assertVector(new Vector3f(0, 0, 0), transform.getTranslation());
    }

    @Test
    void rotatingAroundAnOffCentrePivotMovesTheDisplayBackOntoIt() {
        // Pivot (0,0,0) is d=(-0.5,-0.5,-0.5) from the block centre. A +90 degree
        // Z rotation maps (-0.5,-0.5) to (0.5,-0.5), so t = d - R*d = (-1, 0, 0).
        Transformation transform = BlockbenchRigTransforms.transform(new Vec3(0, 0, 0),
                new BonePose(new Vec3(0, 0, 90), Vec3.ZERO, Vec3.ONE));
        assertVector(new Vector3f(-1, 0, 0), transform.getTranslation());
    }

    @Test
    void scalingAroundAnOffCentrePivotKeepsThePivotFixed() {
        Transformation transform = BlockbenchRigTransforms.transform(new Vec3(0, 0, 0),
                new BonePose(Vec3.ZERO, Vec3.ZERO, new Vec3(2, 2, 2)));
        // t = d - S*d with d=(-0.5,-0.5,-0.5) and S=2 → (0.5,0.5,0.5).
        assertVector(new Vector3f(0.5f, 0.5f, 0.5f), transform.getTranslation());
        assertVector(new Vector3f(2, 2, 2), transform.getScale());
    }

    @Test
    void aPlacementScaleMultipliesPivotsAndPositions() {
        Transformation transform = BlockbenchRigTransforms.transform(new Vec3(0, 0, 0),
                new BonePose(Vec3.ZERO, new Vec3(16, 8, -16), Vec3.ONE), 2.0);
        // Offset d is doubled, position (1,0.5,-1) is doubled, and scale is 2.
        assertVector(new Vector3f(3f, 2f, -1f), transform.getTranslation());
        assertVector(new Vector3f(2, 2, 2), transform.getScale());
    }

    @Test
    void anEntitySpaceBaseOffsetLiftsTheModelOntoTheOrigin() {
        Transformation transform = BlockbenchRigTransforms.transform(
                new Vec3(8, 8, 8), BonePose.NEUTRAL, 1.0, 0.5);
        assertVector(new Vector3f(0f, 0.5f, 0f), transform.getTranslation());
    }

    @Test
    void aChildInheritsItsParentsRotationAroundTheParentPivot() {
        BlockbenchRigRegistry.BoneRig parent = new BlockbenchRigRegistry.BoneRig(
                "p", "root", null, new Vec3(8, 8, 8),
                net.kyori.adventure.key.Key.key("testpack", "item/root"));
        BlockbenchRigRegistry.BoneRig child = new BlockbenchRigRegistry.BoneRig(
                "c", "arm", "p", new Vec3(8, 16, 8),
                net.kyori.adventure.key.Key.key("testpack", "item/arm"));
        var poses = Map.of("p", new BonePose(new Vec3(0, 0, 90), Vec3.ZERO, Vec3.ONE));

        var transforms = BlockbenchRigTransforms.pose(java.util.List.of(parent, child),
                new io.kalo.model.animation.ModelAnimations.Pose(poses), 1.0, 0.0);

        // The child's own pivot is (0,0.5,0) relative to the model centre. The parent
        // rotation turns it to (-0.5,0,0), which is the whole point of propagation.
        org.bukkit.util.Transformation childTransform = transforms.get("c");
        Vector3f moved = childTransform.getLeftRotation()
                .transform(new Vector3f(0, 0.5f, 0))
                .add(childTransform.getTranslation());
        assertVector(new Vector3f(-0.5f, 0, 0), moved);
        assertVector(new Vector3f(0, 0, 0), childTransform.getTranslation());
    }

    @Test
    void aChildInheritsParentPositionAndScale() {
        BlockbenchRigRegistry.BoneRig parent = new BlockbenchRigRegistry.BoneRig(
                "p", "root", null, new Vec3(8, 8, 8),
                net.kyori.adventure.key.Key.key("testpack", "item/root"));
        BlockbenchRigRegistry.BoneRig child = new BlockbenchRigRegistry.BoneRig(
                "c", "arm", "p", new Vec3(8, 16, 8),
                net.kyori.adventure.key.Key.key("testpack", "item/arm"));
        var poses = Map.of("p", new BonePose(Vec3.ZERO, new Vec3(0, 0, 16),
                new Vec3(2, 2, 2)));

        var transforms = BlockbenchRigTransforms.pose(java.util.List.of(parent, child),
                new io.kalo.model.animation.ModelAnimations.Pose(poses), 1.0, 0.0);

        // Position adds (0,0,16) model units = one block; scale doubles the child.
        assertVector(new Vector3f(0, 0, 1), transforms.get("c").getTranslation());
        assertVector(new Vector3f(2, 2, 2), transforms.get("c").getScale());
    }

    @Test
    void thePositionChannelIsAddedInBlocks() {
        Transformation transform = BlockbenchRigTransforms.transform(new Vec3(8, 8, 8),
                new BonePose(Vec3.ZERO, new Vec3(16, 8, -16), Vec3.ONE));
        assertVector(new Vector3f(1, 0.5f, -1), transform.getTranslation());
    }
}

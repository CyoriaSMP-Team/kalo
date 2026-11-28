package io.kalo.content.vfx.definition;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxSimulationTest {

    private static VfxRecipe recipe(VfxRecipe.Shape shape) {
        return new VfxRecipe(20, null, List.of(
                new VfxRecipe.Layer("glow", 0, 20, 40, VfxRecipe.Vec3.ZERO, shape,
                        2.0, 0.2, 0.2, 0.3, 10, 0, 0xFFFFFF)), List.of());
    }

    @Test
    void aBurstSpawnsEveryMoteInsideItsLifetimeAndShape() {
        VfxSimulation simulation = new VfxSimulation(recipe(VfxRecipe.Shape.SPHERE),
                VfxRecipe.Vec3.ZERO, 0, 42);

        assertEquals(40, simulation.motes().size());
        Set<Integer> seen = new HashSet<>();
        for (int tick = 0; tick <= 20; tick++) {
            for (VfxSimulation.Sprite sprite : simulation.frame(tick)) {
                seen.add(sprite.moteId());
            }
        }
        assertEquals(40, seen.size(), "every mote must appear at least once");

        for (VfxSimulation.Mote mote : simulation.motes()) {
            List<VfxSimulation.Sprite> atSpawn = simulation.frame(mote.spawnTick());
            VfxSimulation.Sprite sprite = atSpawn.stream()
                    .filter(candidate -> candidate.moteId() == mote.id())
                    .findFirst().orElseThrow();
            double distance = Math.sqrt(sprite.x() * sprite.x()
                    + sprite.y() * sprite.y() + sprite.z() * sprite.z());
            assertTrue(distance <= 2.0 + 1e-9, "radius exceeded: " + distance);

            assertTrue(simulation.frame(mote.spawnTick() + mote.lifetime()).stream()
                    .noneMatch(candidate -> candidate.moteId() == mote.id()));
        }
    }

    @Test
    void theSameSeedProducesTheSameFrameAndDifferentSeedsDoNot() {
        VfxSimulation first = new VfxSimulation(recipe(VfxRecipe.Shape.RING),
                VfxRecipe.Vec3.ZERO, 30, 7);
        VfxSimulation second = new VfxSimulation(recipe(VfxRecipe.Shape.RING),
                VfxRecipe.Vec3.ZERO, 30, 7);
        VfxSimulation other = new VfxSimulation(recipe(VfxRecipe.Shape.RING),
                VfxRecipe.Vec3.ZERO, 30, 8);

        assertEquals(first.frame(10), second.frame(10));
        assertFalse(first.frame(10).equals(other.frame(10)));
    }

    @Test
    void aLoopCycleUsesFreshSpriteIdsAndFiltersItsSection() {
        VfxRecipe recipe = recipe(VfxRecipe.Shape.SPHERE);
        VfxSimulation simulation = new VfxSimulation(recipe, VfxRecipe.Vec3.ZERO, 0, 11);
        int stride = simulation.motes().size() + 1;

        VfxSimulation.Sprite cycleTwo = simulation.frame(4, 2, 0, 20).get(0);
        assertTrue(cycleTwo.moteId() >= 2 * stride);
        assertTrue(simulation.frame(4, 0, 0, 20).stream()
                .noneMatch(sprite -> sprite.moteId() >= stride));

        assertTrue(simulation.frame(4, 1, 10, 20).stream()
                .allMatch(sprite -> sprite.moteId() >= stride));
    }

    @Test
    void frameOriginTurnAndScaleMoveAndGrowTheWholeEffect() {
        VfxSimulation simulation = new VfxSimulation(recipe(VfxRecipe.Shape.RING),
                VfxRecipe.Vec3.ZERO, 0, 5);
        VfxSimulation.Mote mote = simulation.motes().get(0);
        VfxSimulation.Sprite base = simulation.frame(mote.spawnTick(), 0, 0, 20,
                VfxRecipe.Vec3.ZERO, 0, 1.0).stream()
                .filter(sprite -> sprite.moteId() == mote.id()).findFirst().orElseThrow();
        VfxSimulation.Sprite scaled = simulation.frame(mote.spawnTick(), 0, 0, 20,
                new VfxRecipe.Vec3(10, 20, 30), 0, 2.0).stream()
                .filter(sprite -> sprite.moteId() == mote.id()).findFirst().orElseThrow();

        assertEquals(base.x() * 2 + 10, scaled.x(), 1e-9);
        assertEquals(base.y() * 2 + 20, scaled.y(), 1e-9);
        assertEquals(base.z() * 2 + 30, scaled.z(), 1e-9);
        assertEquals(base.size() * 2, scaled.size(), 1e-9);
    }

    @Test
    void yawRotatesTheEffectWithoutChangingItsDistanceFromTheOrigin() {
        VfxRecipe recipe = recipe(VfxRecipe.Shape.RING);
        VfxSimulation straight = new VfxSimulation(recipe, VfxRecipe.Vec3.ZERO, 0, 3);
        VfxSimulation turned = new VfxSimulation(recipe, VfxRecipe.Vec3.ZERO, 90, 3);
        VfxSimulation.Mote mote = straight.motes().get(0);
        VfxSimulation.Sprite a = straight.frame(mote.spawnTick()).stream()
                .filter(sprite -> sprite.moteId() == mote.id()).findFirst().orElseThrow();
        VfxSimulation.Sprite b = turned.frame(mote.spawnTick()).stream()
                .filter(sprite -> sprite.moteId() == mote.id()).findFirst().orElseThrow();

        assertEquals(Math.sqrt(a.x() * a.x() + a.z() * a.z()),
                Math.sqrt(b.x() * b.x() + b.z() * b.z()), 1e-9);
        assertTrue(Math.abs(a.x() - b.x()) > 0.05 || Math.abs(a.z() - b.z()) > 0.05);
    }
}

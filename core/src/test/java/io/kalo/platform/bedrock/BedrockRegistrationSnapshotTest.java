package io.kalo.platform.bedrock;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The handshake between Kalo's asynchronous compiler and Geyser's startup palette event.
 *
 * <p>Every generation that begins has to be resolved one way or the other. Production
 * resolved none of them for a while — the compiler opened a generation in its constructor
 * and nothing ever published the result — so GeyserBridge waited out its full timeout on
 * every startup and registered zero custom blocks.</p>
 */
class BedrockRegistrationSnapshotTest {

    private static BedrockBlockRegistration block(String key) {
        return new BedrockBlockRegistration(key, "geyser_custom:" + key,
                "minecraft:note_block[instrument=harp,note=0,powered=true]",
                "minecraft:geometry.full_block", null, 3.0f, Map.of("*", "tex"));
    }

    @Test
    void aPublishedGenerationIsWhatTheBridgeReads() {
        var generation = BedrockRegistrationSnapshot.beginGeneration();
        BedrockRegistrationSnapshot.publishSuccess(generation, List.of(block("mypack:ruby")), List.of());

        Optional<BedrockRegistrationSnapshot.Registrations> read =
                BedrockRegistrationSnapshot.await(Duration.ofSeconds(1));
        assertTrue(read.isPresent());
        assertEquals(1, read.get().blocks().size());
        assertEquals("mypack:ruby", read.get().blocks().getFirst().javaKey());
    }

    /**
     * A failed compile must answer, not go quiet. Otherwise Geyser cannot tell it apart
     * from a slow one and stalls its palette event for the whole timeout to reach the same
     * conclusion — thirty seconds of frozen startup.
     */
    @Test
    void aFailedGenerationResolvesImmediatelyInsteadOfTimingOut() {
        var generation = BedrockRegistrationSnapshot.beginGeneration();
        BedrockRegistrationSnapshot.publishFailure(generation);

        long start = System.nanoTime();
        Optional<BedrockRegistrationSnapshot.Registrations> read =
                BedrockRegistrationSnapshot.await(Duration.ofSeconds(5));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertTrue(read.isPresent());
        assertTrue(read.get().blocks().isEmpty());
        assertTrue(elapsedMillis < 1_000, "await blocked for " + elapsedMillis + "ms");
    }

    /**
     * Two overlapping generations must not let the older one win. A regenerate that starts
     * while an earlier compile is still writing would otherwise hand Geyser the palette it
     * was about to replace.
     */
    @Test
    void asupersededGenerationCannotPublishOverTheCurrentOne() {
        var stale = BedrockRegistrationSnapshot.beginGeneration();
        var current = BedrockRegistrationSnapshot.beginGeneration();

        BedrockRegistrationSnapshot.publishSuccess(stale, List.of(block("mypack:stale")), List.of());
        BedrockRegistrationSnapshot.publishSuccess(current, List.of(block("mypack:current")), List.of());

        Optional<BedrockRegistrationSnapshot.Registrations> read =
                BedrockRegistrationSnapshot.await(Duration.ofSeconds(1));
        assertTrue(read.isPresent());
        assertEquals("mypack:current", read.get().blocks().getFirst().javaKey());
    }

    @Test
    void placedModelRegistrationsTravelWithTheSameGeneration() {
        var generation = BedrockRegistrationSnapshot.beginGeneration();
        var placed = new BedrockPlacedModelRegistration(
                net.kyori.adventure.key.Key.key("mypack", "chair"),
                "kalo:model_mypack_chair",
                "geometry.kalo.mypack_chair.placed",
                "textures/items/mypack_chair_t0",
                Map.of("swing", "animation.kalo.mypack_chair.swing"),
                List.of("swing"), 1.5);
        BedrockRegistrationSnapshot.publishSuccess(generation,
                List.of(block("mypack:ruby")), List.of(), List.of(placed));

        var read = BedrockRegistrationSnapshot.await(Duration.ofSeconds(1)).orElseThrow();
        assertEquals(1, read.placedModels().size());
        assertEquals("kalo:model_mypack_chair",
                read.placedModels().getFirst().entityIdentifier());
    }

    @Test
    void vehicleAndEmoteRigsTravelWithTheSameGeneration() {
        var generation = BedrockRegistrationSnapshot.beginGeneration();
        var rig = new BedrockRigRegistration(
                net.kyori.adventure.key.Key.key("mypack", "speeder"),
                "kalo:vehicle_mypack_speeder_vehicle");
        BedrockRegistrationSnapshot.publishSuccess(generation,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(rig));

        var read = BedrockRegistrationSnapshot.await(Duration.ofSeconds(1)).orElseThrow();
        assertEquals(1, read.rigs().size());
        assertEquals("kalo:vehicle_mypack_speeder_vehicle",
                read.rigs().getFirst().entityIdentifier());
    }

    @Test
    void mobRegistrationsTravelWithTheSameGeneration() {
        var generation = BedrockRegistrationSnapshot.beginGeneration();
        var mob = new BedrockMobRegistration(
                net.kyori.adventure.key.Key.key("mypack", "golem"),
                "kalo:mob_mypack_golem");
        BedrockRegistrationSnapshot.publishSuccess(generation,
                List.of(), List.of(), List.of(), List.of(), List.of(mob));

        var read = BedrockRegistrationSnapshot.await(Duration.ofSeconds(1)).orElseThrow();
        assertEquals(1, read.mobs().size());
        assertEquals("kalo:mob_mypack_golem",
                read.mobs().getFirst().entityIdentifier());
    }

    @Test
    void animatedBlockRegistrationsTravelWithTheSameGeneration() {
        var generation = BedrockRegistrationSnapshot.beginGeneration();
        var animated = new BedrockAnimatedBlockRegistration(
                net.kyori.adventure.key.Key.key("mypack", "fan"),
                "kalo:block_mypack_fan");
        BedrockRegistrationSnapshot.publishSuccess(generation,
                List.of(block("mypack:fan")), List.of(), List.of(), List.of(animated));

        var read = BedrockRegistrationSnapshot.await(Duration.ofSeconds(1)).orElseThrow();
        assertEquals(1, read.animatedBlocks().size());
        assertEquals("kalo:block_mypack_fan",
                read.animatedBlocks().getFirst().entityIdentifier());
    }
}

package io.kalo.content.liquid;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquidStoreTest {

    @Test
    void adjacentBucketPlacementsExpandOneVolumeAndPersist(@TempDir Path folder)
            throws Exception {
        Path file = folder.resolve("liquids.json");
        LiquidStore store = new LiquidStore(file);
        Key acid = Key.key("testpack", "acid");
        Key lava = Key.key("testpack", "magma");

        LiquidStore.Volume first = store.addBlock("world", acid, 0, 64, 0,
                "minecraft:plains");
        LiquidStore.Volume expanded = store.addBlock("world", acid, 1, 64, 0,
                "minecraft:plains");
        store.addBlock("world", lava, 8, 64, 8, "minecraft:plains");

        assertEquals(first.id(), expanded.id());
        assertEquals(0, expanded.minX());
        assertEquals(1, expanded.maxX());
        assertEquals(2, store.size());
        assertTrue(store.containing("world", 0, 64, 0) != null);
        assertTrue(store.containing("world", 1, 64, 0) != null);
        assertEquals(acid, store.containing("world", 1, 64, 0).liquid());

        store.save();
        assertTrue(Files.isRegularFile(file));
        String json = Files.readString(file);
        assertTrue(json.contains("\"liquid\":\"testpack:acid\""), json);
        Path artifacts = Path.of("build", "reports", "liquid");
        Files.createDirectories(artifacts);
        Files.writeString(artifacts.resolve("liquids.json"), json);

        LiquidStore loaded = LiquidStore.load(file);
        assertEquals(2, loaded.size());
        LiquidStore.Volume loadedFirst = loaded.volumes().get(0);
        assertEquals(first.id(), loadedFirst.id());
        assertEquals("minecraft:plains", loadedFirst.originalBiome());
        assertEquals(1, loadedFirst.maxX());
    }

    @Test
    void removingABlockFromTheMiddleSplitsTheVolumeAndPersists(@TempDir Path folder)
            throws Exception {
        Path file = folder.resolve("liquids.json");
        LiquidStore store = new LiquidStore(file);
        Key acid = Key.key("testpack", "acid");
        store.addVolume("world", acid, 0, 60, 0, 2, 62, 2, "minecraft:plains");

        LiquidStore.Volume removed = store.removeBlock("world", 1, 61, 1);

        assertNotNull(removed);
        assertEquals(6, store.size(), "a removed centre block leaves six boxes");
        assertEquals(26, store.volumes().stream().mapToLong(volume ->
                (long) (volume.maxX() - volume.minX() + 1)
                        * (volume.maxY() - volume.minY() + 1)
                        * (volume.maxZ() - volume.minZ() + 1)).sum());
        assertTrue(store.containing("world", 1, 61, 1) == null);
        store.volumes().forEach(volume -> {
            assertEquals(acid, volume.liquid());
            assertEquals("minecraft:plains", volume.originalBiome());
        });

        store.save();
        LiquidStore loaded = LiquidStore.load(file);
        assertEquals(6, loaded.size());
        assertTrue(loaded.containing("world", 1, 61, 1) == null);
        assertTrue(loaded.containing("world", 0, 60, 0) != null);
    }

    @Test
    void removingABoxSplitsTheVolumeAndPersists(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("liquids.json");
        LiquidStore store = new LiquidStore(file);
        Key acid = Key.key("testpack", "acid");
        store.addVolume("world", acid, 0, 60, 0, 4, 64, 4, "minecraft:plains");

        List<LiquidStore.Volume> removed = store.removeBox("world",
                2, 62, 2, 3, 63, 3);

        assertEquals(1, removed.size());
        assertEquals(2, removed.get(0).minX());
        assertEquals(62, removed.get(0).minY());
        assertEquals(3, removed.get(0).maxX());
        assertEquals(63, removed.get(0).maxY());
        assertEquals(6, store.size());
        assertTrue(store.containing("world", 2, 62, 2) == null);
        assertTrue(store.containing("world", 0, 60, 0) != null);

        store.save();
        LiquidStore loaded = LiquidStore.load(file);
        assertEquals(6, loaded.size());
        assertTrue(loaded.containing("world", 3, 63, 3) == null);

        Path artifacts = Path.of("build", "reports", "liquid");
        Files.createDirectories(artifacts);
        Files.copy(file, artifacts.resolve("after-sponge-trim.json"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void removingScatteredCellsUsesRunsAndPersists(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("liquids.json");
        LiquidStore store = new LiquidStore(file);
        Key acid = Key.key("testpack", "acid");
        store.addVolume("world", acid, 0, 60, 0, 4, 64, 4, "minecraft:plains");

        List<LiquidStore.Volume> removed = store.removeBlocks("world",
                List.of(new LiquidStore.BlockPos(1, 61, 1),
                        new LiquidStore.BlockPos(2, 61, 1),
                        new LiquidStore.BlockPos(3, 63, 3)));

        assertEquals(2, removed.size(), "the x-run and the isolated cell are two removals");
        assertTrue(store.containing("world", 1, 61, 1) == null);
        assertTrue(store.containing("world", 2, 61, 1) == null);
        assertTrue(store.containing("world", 3, 63, 3) == null);
        assertTrue(store.containing("world", 4, 64, 4) != null);

        store.save();
        assertEquals(store.size(), LiquidStore.load(file).size());
    }

    @Test
    void removingTheOnlyBlockLeavesNoVolume(@TempDir Path folder) {
        LiquidStore store = new LiquidStore(folder.resolve("liquids.json"));
        Key acid = Key.key("testpack", "acid");
        store.addBlock("world", acid, 4, 64, 4, "minecraft:plains");

        assertNotNull(store.removeBlock("world", 4, 64, 4));
        assertEquals(0, store.size());
        assertTrue(store.containing("world", 4, 64, 4) == null);
    }

    @Test
    void theFirstOverlappingVolumeWinsAndRemovalWorks(@TempDir Path folder) {
        LiquidStore store = new LiquidStore(folder.resolve("liquids.json"));
        Key first = Key.key("testpack", "first");
        Key second = Key.key("testpack", "second");
        LiquidStore.Volume outer = store.addVolume("world", first,
                0, 60, 0, 10, 70, 10, "minecraft:plains");
        store.addVolume("world", second,
                2, 62, 2, 3, 63, 3, "minecraft:desert");

        assertNotNull(store.containing("world", 2, 62, 2));
        assertEquals(first, store.containing("world", 2, 62, 2).liquid());
        assertTrue(store.remove(outer));
        assertEquals(second, store.containing("world", 2, 62, 2).liquid());
    }
}

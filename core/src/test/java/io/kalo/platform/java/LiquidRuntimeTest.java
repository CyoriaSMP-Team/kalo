package io.kalo.platform.java;

import io.kalo.content.liquid.LiquidStore;
import io.kalo.content.liquid.LiquidType;
import io.kalo.content.liquid.definition.LiquidDefinition;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquidRuntimeTest {

    @Test
    void contactResolvesTheFirstVolumeToItsDefinition(@TempDir Path folder) {
        Key acidKey = Key.key("testpack", "acid");
        LiquidDefinition acid = new LiquidDefinition(acidKey, "Acid",
                LiquidDefinition.Base.WATER, 0x3FBF4A, "POISON", 1, 1.0, false);
        LiquidStore store = new LiquidStore(folder.resolve("liquids.json"));
        store.addVolume("world", acidKey, 0, 60, 0, 4, 64, 4, "minecraft:plains");
        LiquidType type = new LiquidType();
        type.put(acid);

        assertNotNull(LiquidRuntime.resolve(store, type, "world", 2, 62, 2));
        assertEquals(acid, LiquidRuntime.resolve(store, type, "world", 2, 62, 2));
        assertNull(LiquidRuntime.resolve(store, type, "world", 8, 62, 2));
        assertNull(LiquidRuntime.resolve(store, type, "other", 2, 62, 2));
    }

    @Test
    void spongeAbsorptionTrimsTheAbsorbedBox(@TempDir Path folder) {
        Key acidKey = Key.key("testpack", "acid");
        LiquidStore store = new LiquidStore(folder.resolve("liquids.json"));
        store.addVolume("world", acidKey, 0, 60, 0, 4, 64, 4,
                "minecraft:plains");
        List<LiquidStore.BlockPos> absorbed = new ArrayList<>();
        for (int x = 2; x <= 3; x++) {
            for (int y = 62; y <= 63; y++) {
                for (int z = 2; z <= 3; z++) {
                    absorbed.add(new LiquidStore.BlockPos(x, y, z));
                }
            }
        }

        List<LiquidStore.Volume> removed = LiquidRuntime.absorb(store, "world", absorbed);

        assertEquals(1, removed.size());
        LiquidStore.Volume cell = removed.get(0);
        assertEquals(2, cell.minX());
        assertEquals(62, cell.minY());
        assertEquals(2, cell.minZ());
        assertEquals(3, cell.maxX());
        assertEquals(63, cell.maxY());
        assertEquals(3, cell.maxZ());
        assertEquals("minecraft:plains", cell.originalBiome());
        assertNull(store.containing("world", 2, 62, 2));
        assertNull(store.containing("world", 3, 63, 3));
        assertNotNull(store.containing("world", 0, 60, 0));
        assertNotNull(store.containing("world", 4, 64, 4));
        assertEquals(6, store.size(), "the interior box leaves six surrounding boxes");
    }

    @Test
    void spongeAbsorptionHandlesScatteredCells(@TempDir Path folder) {
        Key acidKey = Key.key("testpack", "acid");
        LiquidStore store = new LiquidStore(folder.resolve("liquids.json"));
        store.addVolume("world", acidKey, 0, 60, 0, 4, 64, 4,
                "minecraft:plains");

        List<LiquidStore.Volume> removed = LiquidRuntime.absorb(store, "world",
                List.of(new LiquidStore.BlockPos(1, 61, 1),
                        new LiquidStore.BlockPos(3, 63, 3)));

        assertEquals(2, removed.size(), "each isolated run is its own removal");
        assertNull(store.containing("world", 1, 61, 1));
        assertNull(store.containing("world", 3, 63, 3));
        assertNotNull(store.containing("world", 2, 62, 2));
        assertNotNull(store.containing("world", 0, 60, 0));
        assertTrue(store.size() > 1);
    }

    @Test
    void deliberateBankRemovalTrimsTheEdgeAndTheLastBlock(@TempDir Path folder) {
        Key acidKey = Key.key("testpack", "acid");
        LiquidStore store = new LiquidStore(folder.resolve("liquids.json"));
        store.addVolume("world", acidKey, 0, 64, 0, 2, 64, 0,
                "minecraft:plains");

        LiquidStore.Volume edge = LiquidRuntime.bankRemoved(store, "world", 0, 64, 0);

        assertNotNull(edge);
        assertEquals(0, edge.minX());
        assertEquals(0, edge.maxX());
        assertEquals("minecraft:plains", edge.originalBiome());
        assertNull(store.containing("world", 0, 64, 0));
        assertNotNull(store.containing("world", 1, 64, 0));
        assertNotNull(store.containing("world", 2, 64, 0));
        assertEquals(1, store.size());

        assertNotNull(LiquidRuntime.bankRemoved(store, "world", 1, 64, 0));
        assertNotNull(LiquidRuntime.bankRemoved(store, "world", 2, 64, 0));
        assertEquals(0, store.size(), "removing every cell leaves no marked volume");
    }

    @Test
    void aBankBlockOutsideTheVolumeIsLeftForFlowToExpand(@TempDir Path folder) {
        Key acidKey = Key.key("testpack", "acid");
        LiquidStore store = new LiquidStore(folder.resolve("liquids.json"));
        store.addVolume("world", acidKey, 0, 64, 0, 2, 64, 0,
                "minecraft:plains");

        // Breaking the wall beside the pool is not a trim: flow will mark the new cell.
        assertNull(LiquidRuntime.bankRemoved(store, "world", 3, 64, 0));
        assertEquals(1, store.size());
        assertNotNull(store.containing("world", 2, 64, 0));
        assertNull(store.containing("world", 3, 64, 0));
    }

    @Test
    void liquidEventHandlersRunAtMonitorAfterCancellationChecks() throws Exception {
        assertMonitor("onSpongeAbsorb", SpongeAbsorbEvent.class);
        assertMonitor("onBlockPlace", BlockPlaceEvent.class);
        assertMonitor("onBlockBreak", BlockBreakEvent.class);
        assertMonitor("onBlockPhysics", BlockPhysicsEvent.class);
    }

    @Test
    void fluidMaterialsStayMarkedAndOtherMaterialsAreTrimmed() {
        assertFalse(LiquidRuntime.shouldTrim(Material.WATER));
        assertFalse(LiquidRuntime.shouldTrim(Material.LAVA));
        assertTrue(LiquidRuntime.shouldTrim(Material.STONE));
        assertTrue(LiquidRuntime.shouldTrim(Material.AIR));
    }

    @Test
    void spongePlaceBreakPhysicsAreOrderIndependent(@TempDir Path folder) {
        Key acidKey = Key.key("testpack", "acid");
        LiquidStore spongeFirst = new LiquidStore(folder.resolve("sponge-first.json"));
        LiquidStore eventsFirst = new LiquidStore(folder.resolve("events-first.json"));
        spongeFirst.addVolume("world", acidKey, 0, 60, 0, 4, 64, 4,
                "minecraft:plains");
        eventsFirst.addVolume("world", acidKey, 0, 60, 0, 4, 64, 4,
                "minecraft:plains");
        List<LiquidStore.BlockPos> absorbed = new ArrayList<>();
        for (int x = 2; x <= 3; x++) {
            for (int y = 62; y <= 63; y++) {
                for (int z = 2; z <= 3; z++) {
                    absorbed.add(new LiquidStore.BlockPos(x, y, z));
                }
            }
        }

        assertFalse(LiquidRuntime.absorb(spongeFirst, "world", absorbed).isEmpty());
        assertNotNull(LiquidRuntime.bankRemoved(spongeFirst, "world", 1, 61, 1));
        assertNotNull(LiquidRuntime.bankRemoved(spongeFirst, "world", 4, 64, 4));
        assertNotNull(LiquidRuntime.bankRemoved(spongeFirst, "world", 0, 60, 0));

        assertNotNull(LiquidRuntime.bankRemoved(eventsFirst, "world", 1, 61, 1));
        assertNotNull(LiquidRuntime.bankRemoved(eventsFirst, "world", 4, 64, 4));
        assertNotNull(LiquidRuntime.bankRemoved(eventsFirst, "world", 0, 60, 0));
        assertFalse(LiquidRuntime.absorb(eventsFirst, "world", absorbed).isEmpty());

        assertEquals(cells(spongeFirst), cells(eventsFirst),
                "the marked cells must not depend on event ordering");
        assertEquals(114, cells(spongeFirst).size());
    }

    @Test
    void repeatedSpongePlaceAndPhysicsOperationsAreIdempotent(@TempDir Path folder) {
        Key acidKey = Key.key("testpack", "acid");
        LiquidStore store = new LiquidStore(folder.resolve("liquids.json"));
        store.addVolume("world", acidKey, 0, 0, 0, 2, 2, 2, "minecraft:plains");
        LiquidStore.BlockPos centre = new LiquidStore.BlockPos(1, 1, 1);

        assertEquals(1, LiquidRuntime.absorb(store, "world",
                List.of(centre, centre)).size());
        Set<LiquidStore.BlockPos> afterSponge = cells(store);
        assertTrue(LiquidRuntime.absorb(store, "world", List.of(centre)).isEmpty());
        assertNull(LiquidRuntime.bankRemoved(store, "world", 1, 1, 1));
        assertEquals(afterSponge, cells(store));

        assertNotNull(LiquidRuntime.bankRemoved(store, "world", 0, 0, 0));
        Set<LiquidStore.BlockPos> afterBank = cells(store);
        assertNull(LiquidRuntime.bankRemoved(store, "world", 0, 0, 0));
        assertEquals(afterBank, cells(store));
    }

    @Test
    void absorptionOutsideAnyVolumeIsAHarmlessNoOp(@TempDir Path folder) {
        Key acidKey = Key.key("testpack", "acid");
        LiquidStore store = new LiquidStore(folder.resolve("liquids.json"));
        store.addVolume("world", acidKey, 0, 60, 0, 2, 62, 2,
                "minecraft:plains");

        assertTrue(LiquidRuntime.absorb(store, "world",
                List.of(new LiquidStore.BlockPos(10, 10, 10))).isEmpty());
        assertNull(LiquidRuntime.bankRemoved(store, "world", 10, 10, 10));
        assertEquals(1, store.size());
    }

    private static void assertMonitor(String name, Class<?> event) throws Exception {
        Method method = LiquidRuntime.class.getMethod(name, event);
        EventHandler handler = method.getAnnotation(EventHandler.class);
        assertNotNull(handler);
        assertEquals(EventPriority.MONITOR, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }

    private static Set<LiquidStore.BlockPos> cells(LiquidStore store) {
        Set<LiquidStore.BlockPos> cells = new HashSet<>();
        for (LiquidStore.Volume volume : store.volumes()) {
            for (int x = volume.minX(); x <= volume.maxX(); x++) {
                for (int y = volume.minY(); y <= volume.maxY(); y++) {
                    for (int z = volume.minZ(); z <= volume.maxZ(); z++) {
                        cells.add(new LiquidStore.BlockPos(x, y, z));
                    }
                }
            }
        }
        return cells;
    }
}

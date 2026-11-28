package net.momirealms.craftengine;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvents;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the shim to the signatures read from CraftEngine's source on its {@code dev} branch (the
 * {@code main} branch was not read, so a stable release could differ). Plugins link by exact name
 * and descriptor, so drift is a NoSuchMethodError in someone else's plugin. The check is equality:
 * a member not in the verified list must not exist, so nothing is invented.
 */
class CraftEngineApiSignatureTest {

    private static Set<String> declared(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic() && !m.isBridge())
                .map(CraftEngineApiSignatureTest::describe)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String describe(Method m) {
        String params = Arrays.stream(m.getParameterTypes()).map(Class::getTypeName)
                .collect(Collectors.joining(","));
        return (Modifier.isStatic(m.getModifiers()) ? "static " : "")
                + m.getReturnType().getTypeName() + " " + m.getName() + "(" + params + ")";
    }

    private static Set<String> set(String... values) {
        return new TreeSet<>(Arrays.asList(values));
    }

    @Test
    void itemsBlocksAndFurnitureMatchVerifiedSignatures() {
        assertEquals(set("static boolean isCustomItem(org.bukkit.inventory.ItemStack)"),
                declared(CraftEngineItems.class));
        assertEquals(set(
                "static boolean isCustomBlock(org.bukkit.block.Block)",
                "static boolean remove(org.bukkit.block.Block)",
                "static boolean remove(org.bukkit.block.Block,boolean)"),
                declared(CraftEngineBlocks.class));
        assertEquals(set(
                "static boolean isFurniture(org.bukkit.entity.Entity)",
                "static boolean isCollisionEntity(org.bukkit.entity.Entity)",
                "static boolean isSeat(org.bukkit.entity.Entity)",
                "static boolean remove(org.bukkit.entity.Entity)"),
                declared(CraftEngineFurniture.class));
    }

    @Test
    void reloadEventMatchesVerifiedSignatures() {
        assertEquals(set(
                "boolean isFirstReload()",
                "static org.bukkit.event.HandlerList getHandlerList()",
                "org.bukkit.event.HandlerList getHandlers()"),
                declared(CraftEngineReloadEvent.class));
        assertEquals(org.bukkit.event.Event.class, CraftEngineReloadEvent.class.getSuperclass());
        assertTrue(Modifier.isFinal(CraftEngineReloadEvent.class.getModifiers()));
    }

    @Test
    void factoryProducesTheFlag() {
        assertTrue(CraftEngineReloadEvents.create(true).isFirstReload());
        assertEquals(false, CraftEngineReloadEvents.create(false).isFirstReload());
    }

    @Test
    void apiClassesAreFinalWithNoPublicConstructor() {
        for (Class<?> type : new Class<?>[]{CraftEngineItems.class, CraftEngineBlocks.class,
                CraftEngineFurniture.class, CraftEngineReloadEvent.class}) {
            assertTrue(Modifier.isFinal(type.getModifiers()), type.getSimpleName() + " is final");
            assertEquals(0, type.getConstructors().length, type.getSimpleName() + " has no public constructor");
        }
    }
}

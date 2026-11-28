package dev.lone.itemsadder;

import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import dev.lone.itemsadder.api.ItemsAdder;
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
 * Pins the shim to the signatures in ItemsAdder's published Javadoc (API 4.0.17-beta-12-test-8).
 * Plugins compiled against it link by exact name and descriptor, so drift is a NoSuchMethodError
 * in someone else's plugin. The check is equality: a member not in the verified list must not
 * exist either, so nothing is invented.
 */
class ItemsAdderApiSignatureTest {

    private static Set<String> declared(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic() && !m.isBridge())
                .map(ItemsAdderApiSignatureTest::describe)
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
    void customStackMatchesVerifiedSignatures() {
        assertEquals(set(
                "static java.util.Set getNamespacedIdsInRegistry()",
                "static boolean isInRegistry(java.lang.String)",
                "static dev.lone.itemsadder.api.CustomStack byItemStack(org.bukkit.inventory.ItemStack)",
                "static dev.lone.itemsadder.api.CustomStack getInstance(java.lang.String)",
                "java.lang.String getNamespace()",
                "java.lang.String getId()",
                "java.lang.String getNamespacedID()",
                "boolean matchNamespacedID(dev.lone.itemsadder.api.CustomStack)",
                "org.bukkit.inventory.ItemStack getItemStack()"
        ), declared(CustomStack.class));
    }

    @Test
    void itemsAdderMatchesVerifiedSignatures() {
        assertEquals(set(
                "static java.util.List getAllItems()",
                "static java.util.List getAllItems(java.lang.String)",
                "static java.util.List getAllItems(org.bukkit.Material)",
                "static boolean areItemsLoaded()",
                "static boolean isCustomItem(org.bukkit.inventory.ItemStack)",
                "static boolean isCustomItem(java.lang.String)",
                "static org.bukkit.inventory.ItemStack getCustomItem(java.lang.String)"
        ), declared(ItemsAdder.class));
    }

    @Test
    void loadDataEventMatchesVerifiedSignatures() throws Exception {
        assertEquals(set(
                "org.bukkit.event.HandlerList getHandlers()",
                "static org.bukkit.event.HandlerList getHandlerList()"
        ), declared(ItemsAdderLoadDataEvent.class));
        ItemsAdderLoadDataEvent.class.getConstructor(boolean.class, boolean.class);
        ItemsAdderLoadDataEvent.class.getConstructor(boolean.class);
        assertEquals(org.bukkit.event.Event.class, ItemsAdderLoadDataEvent.class.getSuperclass());
    }

    @Test
    void loadDataEventIsAsynchronousLikeTheOriginal() {
        assertTrue(new ItemsAdderLoadDataEvent(false, false).isAsynchronous());
    }

    @Test
    void noPublicConstructorsOnTheApiClassesThatHaveNoneVerified() {
        assertEquals(0, CustomStack.class.getConstructors().length);
        assertEquals(0, ItemsAdder.class.getConstructors().length);
    }
}

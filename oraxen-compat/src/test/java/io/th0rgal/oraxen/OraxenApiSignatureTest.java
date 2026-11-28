package io.th0rgal.oraxen;

import io.th0rgal.oraxen.api.OraxenBlocks;
import io.th0rgal.oraxen.api.OraxenFurniture;
import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.api.events.OraxenItemsLoadedEvent;
import io.th0rgal.oraxen.items.ItemBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the shim to the public signatures read from Oraxen's published source (master). A plugin
 * compiled against Oraxen links by exact name and descriptor, so drift is a NoSuchMethodError in
 * someone else's plugin, not a compile error here. The check is equality, not containment: a
 * member that is not in the verified list must not exist either, so nothing is invented.
 */
class OraxenApiSignatureTest {

    /** "static? modifiers returnType name(erased,params)" for every public declared method. */
    private static Set<String> declared(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic() && !m.isBridge())
                .map(OraxenApiSignatureTest::describe)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String describe(Method m) {
        String params = Arrays.stream(m.getParameterTypes()).map(Class::getTypeName)
                .collect(Collectors.joining(","));
        String mods = (Modifier.isStatic(m.getModifiers()) ? "static " : "")
                + (Modifier.isSynchronized(m.getModifiers()) ? "synchronized " : "");
        return mods + m.getReturnType().getTypeName() + " " + m.getName() + "(" + params + ")";
    }

    private static Set<String> set(String... values) {
        return new TreeSet<>(Arrays.asList(values));
    }

    @Test
    void oraxenItemsMatchesVerifiedSignatures() {
        assertEquals(set(
                "static void loadItems()",
                "static java.lang.String getIdByItem(io.th0rgal.oraxen.items.ItemBuilder)",
                "static java.lang.String getIdByItem(org.bukkit.inventory.ItemStack)",
                "static boolean exists(java.lang.String)",
                "static boolean exists(org.bukkit.inventory.ItemStack)",
                "static java.util.Optional getOptionalItemById(java.lang.String)",
                "static io.th0rgal.oraxen.items.ItemBuilder getItemById(java.lang.String)",
                "static io.th0rgal.oraxen.items.ItemBuilder getBuilderByItem(org.bukkit.inventory.ItemStack)",
                "static java.util.List getUnexcludedItems()",
                "static java.util.List getUnexcludedItems(java.io.File)",
                "static boolean hasMechanic(java.lang.String,java.lang.String)",
                "static java.util.Map getMap()",
                "static java.util.Map getEntriesAsMap()",
                "static java.util.Set getEntries()",
                "static java.util.Collection getItems()",
                "static java.util.Set getNames()",
                "static java.lang.String[] nameArray()",
                "static java.util.stream.Stream nameStream()",
                "static java.util.stream.Stream itemStream()",
                "static java.util.stream.Stream entryStream()",
                "static java.lang.String[] getItemNames()"
        ), declared(OraxenItems.class));
    }

    @Test
    void itemBuilderMatchesVerifiedSignatures() {
        assertEquals(set(
                "org.bukkit.Material getType()",
                "io.th0rgal.oraxen.items.ItemBuilder setType(org.bukkit.Material)",
                "io.th0rgal.oraxen.items.ItemBuilder setAmount(int)",
                "boolean hasLores()",
                "io.th0rgal.oraxen.items.ItemBuilder setUnbreakable(boolean)",
                "java.lang.Integer getDurability()",
                "io.th0rgal.oraxen.items.ItemBuilder setDurability(java.lang.Integer)",
                "boolean hasItemModel()",
                "org.bukkit.NamespacedKey getItemModel()",
                "io.th0rgal.oraxen.items.ItemBuilder setItemModel(org.bukkit.NamespacedKey)",
                "io.th0rgal.oraxen.items.ItemBuilder setCustomModelData(int)",
                "io.th0rgal.oraxen.items.ItemBuilder addItemFlags(org.bukkit.inventory.ItemFlag[])",
                "java.util.List getItemFlags()",
                "io.th0rgal.oraxen.items.ItemBuilder addEnchant(org.bukkit.enchantments.Enchantment,int)",
                "io.th0rgal.oraxen.items.ItemBuilder addEnchants(java.util.Map)",
                "io.th0rgal.oraxen.items.ItemBuilder setCustomTag(org.bukkit.NamespacedKey,org.bukkit.persistence.PersistentDataType,java.lang.Object)",
                "java.lang.Object getCustomTag(org.bukkit.NamespacedKey,org.bukkit.persistence.PersistentDataType)",
                "io.th0rgal.oraxen.items.ItemBuilder removeCustomTag(org.bukkit.NamespacedKey)",
                "boolean hasOraxenMeta()",
                "org.bukkit.inventory.ItemStack getReferenceClone()",
                "io.th0rgal.oraxen.items.ItemBuilder clone()",
                "synchronized io.th0rgal.oraxen.items.ItemBuilder regen()",
                "org.bukkit.inventory.ItemStack[] buildArray(int)",
                "org.bukkit.inventory.ItemStack build()"
        ), declared(ItemBuilder.class));
    }

    @Test
    void oraxenBlocksMatchesVerifiedSignatures() {
        assertEquals(set(
                "static java.util.Set getBlockIDs()",
                "static boolean isOraxenBlock(org.bukkit.block.Block)",
                "static boolean isOraxenBlock(java.lang.String)",
                "static void place(java.lang.String,org.bukkit.Location)",
                "static org.bukkit.block.data.BlockData getOraxenBlockData(java.lang.String)",
                "static boolean remove(org.bukkit.Location,org.bukkit.entity.Player)",
                "static boolean remove(org.bukkit.Location,org.bukkit.entity.Player,boolean)"
        ), declared(OraxenBlocks.class));
    }

    @Test
    void oraxenFurnitureMatchesVerifiedSignatures() {
        assertEquals(set(
                "static java.util.Set getFurnitureIDs()",
                "static boolean isFurniture(org.bukkit.block.Block)",
                "static boolean isFurniture(java.lang.String)"
        ), declared(OraxenFurniture.class));
    }

    @Test
    void itemBuilderConstructors() throws Exception {
        ItemBuilder.class.getConstructor(org.bukkit.Material.class);
        ItemBuilder.class.getConstructor(org.bukkit.inventory.ItemStack.class);
    }

    @Test
    void loadedEventHasHandlerList() throws Exception {
        Method m = OraxenItemsLoadedEvent.class.getMethod("getHandlerList");
        assertEquals(true, Modifier.isStatic(m.getModifiers()));
        assertEquals(org.bukkit.event.Event.class, OraxenItemsLoadedEvent.class.getSuperclass());
    }
}

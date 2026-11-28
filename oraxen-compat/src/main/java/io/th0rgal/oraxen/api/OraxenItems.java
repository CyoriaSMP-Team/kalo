package io.th0rgal.oraxen.api;

import io.kalo.compat.CompatIds;
import io.kalo.compat.KaloItemLookup;
import io.th0rgal.oraxen.items.ItemBuilder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Stream;

/**
 * Oraxen's item API, answered from Kalo's registries.
 *
 * <p>Not implemented, so a call fails with {@code NoSuchMethodError} rather than returning
 * something wrong: {@code ITEM_ID} (Oraxen stores a flat id under it; Kalo stores a namespaced
 * key under its own entry, so the value would differ), {@code getItemStacksByName} and the
 * deprecated {@code getSectionsNames} (their meaning is not verified).</p>
 */
public class OraxenItems {

    /** Kalo owns loading. */
    public static void loadItems() {
    }

    public static String getIdByItem(final ItemBuilder item) {
        return getIdByItem(item.build());
    }

    public static String getIdByItem(final ItemStack item) {
        String key = KaloItemLookup.keyOf(item);
        return key == null ? null : CompatIds.fromKaloKey(key);
    }

    public static boolean exists(final String itemId) {
        return KaloItemLookup.stackFor(itemId) != null;
    }

    public static boolean exists(final ItemStack itemStack) {
        return KaloItemLookup.keyOf(itemStack) != null;
    }

    public static Optional<ItemBuilder> getOptionalItemById(final String id) {
        return Optional.ofNullable(getItemById(id));
    }

    public static ItemBuilder getItemById(final String id) {
        ItemStack stack = KaloItemLookup.stackFor(id);
        return stack == null ? null : new ItemBuilder(stack);
    }

    public static ItemBuilder getBuilderByItem(ItemStack item) {
        String id = getIdByItem(item);
        return id == null ? null : getItemById(id);
    }

    public static List<ItemBuilder> getUnexcludedItems() {
        return new ArrayList<>(getItems());
    }

    /** Kalo does not group items by source file, so no file has any. */
    public static List<ItemBuilder> getUnexcludedItems(final File file) {
        return new ArrayList<>();
    }

    /** Kalo has no Oraxen-style mechanics, so no item has one. */
    public static boolean hasMechanic(String itemID, String mechanicID) {
        return false;
    }

    /** Kalo does not group items by source file. */
    public static Map<File, Map<String, ItemBuilder>> getMap() {
        return Collections.emptyMap();
    }

    public static Map<String, ItemBuilder> getEntriesAsMap() {
        Map<String, ItemBuilder> result = new LinkedHashMap<>();
        for (String name : getNames()) {
            ItemBuilder builder = getItemById(name);
            if (builder != null) {
                result.put(name, builder);
            }
        }
        return result;
    }

    public static Set<Entry<String, ItemBuilder>> getEntries() {
        return new LinkedHashSet<>(getEntriesAsMap().entrySet());
    }

    public static Collection<ItemBuilder> getItems() {
        return getEntriesAsMap().values();
    }

    public static Set<String> getNames() {
        Set<String> names = new LinkedHashSet<>();
        for (String key : KaloItemLookup.keys()) {
            names.add(CompatIds.fromKaloKey(key));
        }
        return names;
    }

    public static String[] nameArray() {
        return getNames().toArray(new String[0]);
    }

    public static Stream<String> nameStream() {
        return getNames().stream();
    }

    public static Stream<ItemBuilder> itemStream() {
        return getItems().stream();
    }

    public static Stream<Entry<String, ItemBuilder>> entryStream() {
        return getEntries().stream();
    }

    public static String[] getItemNames() {
        return nameArray();
    }
}

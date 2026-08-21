package io.kalo.migration;

import org.bukkit.Material;
import org.bukkit.inventory.ItemType;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Material validation shared by importers that emit Java item definitions. */
final class MigrationMaterials {

    /*
     * Material#isItem is backed by Paper's live registry in current versions and cannot
     * run in the importers' server-free unit tests. Paper generates one typed ItemType
     * field for every obtainable item (AIR is deliberately the sole untyped entry), so
     * these field names provide the same current-version membership without booting a
     * server or maintaining a second hand-written material list.
     */
    private static final Set<String> OBTAINABLE_ITEM_NAMES = Arrays.stream(ItemType.class.getFields())
            .filter(MigrationMaterials::isTypedItemField)
            .map(Field::getName)
            .collect(Collectors.toUnmodifiableSet());

    private MigrationMaterials() {
    }

    static @NotNull Material item(@NotNull String value) {
        Material material = Material.matchMaterial(value.toUpperCase(Locale.ROOT));
        if (material == null) {
            throw new IllegalArgumentException("unknown material '" + value + "'");
        }
        if (!OBTAINABLE_ITEM_NAMES.contains(material.name())) {
            throw new IllegalArgumentException("material '" + value + "' is not an obtainable item");
        }
        return material;
    }

    private static boolean isTypedItemField(@NotNull Field field) {
        return field.getType() == ItemType.Typed.class;
    }
}

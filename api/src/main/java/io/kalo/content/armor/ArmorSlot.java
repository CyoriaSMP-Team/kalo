package io.kalo.content.armor;

import org.jetbrains.annotations.NotNull;

/**
 * Where a piece of armor is worn.
 *
 * <p>Platform-neutral by design: the Java compiler maps these onto Bukkit's
 * {@code EquipmentSlot} and Bedrock maps them onto its own slot names, neither of which
 * belongs in the definition layer.</p>
 */
public enum ArmorSlot {
    HEAD,
    CHEST,
    LEGS,
    FEET;

    /**
     * Whether this slot is drawn on vanilla's leggings layer.
     *
     * <p>Vanilla paints leggings on a different model than the other three pieces, so a
     * leggings texture goes in a different equipment layer and lives under a different
     * texture directory.</p>
     */
    public boolean usesLeggingsLayer() {
        return this == LEGS;
    }

    public @NotNull String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}

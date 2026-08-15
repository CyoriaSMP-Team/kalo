package io.kalo.content.item.definition;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * Java-platform escape hatch, read only by the Java compiler.
 *
 * <p>This is the one place {@link Material} is allowed to appear. Keeping it here rather
 * than in {@link ItemDefinition} itself is what lets a Bedrock compiler consume the same
 * definition without having to interpret a Java-specific choice.</p>
 *
 * @param baseMaterial the vanilla item the custom model is applied to
 */
public record JavaOptions(@NotNull Material baseMaterial) {

    /**
     * {@code PAPER} is the conventional carrier for custom models: it has no vanilla
     * behaviour of its own to suppress and no attributes to strip.
     */
    public static @NotNull JavaOptions defaults() {
        return new JavaOptions(Material.PAPER);
    }
}

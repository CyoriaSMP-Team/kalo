package io.kalo.content.item.definition;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Text and visual presentation shared by every platform.
 *
 * <p>Adventure's {@link Component} is the one third-party type allowed into the
 * definition layer. It is a text representation rather than a platform binding — the
 * same component can be rendered for Java or translated for Bedrock.</p>
 *
 * @param name  {@code null} means "fall back to the generated translation key"
 */
public record DisplayProperties(
        @Nullable Component name,
        @NotNull @Unmodifiable List<Component> lore,
        boolean enchantmentGlint
) {
    public DisplayProperties {
        lore = List.copyOf(lore);
    }

    public static @NotNull DisplayProperties empty() {
        return new DisplayProperties(null, List.of(), false);
    }
}

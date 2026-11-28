package com.nexomc.nexo.glyphs;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Nexo glyph tag resolver, reduced to the members zMenu links against.
 *
 * <p>zMenu loads {@code hooks/NexoTagResolverLoader} whenever a plugin named Nexo is
 * enabled and calls {@code GlyphTag.INSTANCE.getRESOLVER()}. Kalo has no Nexo glyph
 * font, so the resolver is empty and MiniMessage leaves unknown tags alone.</p>
 */
public final class GlyphTag {

    public static final GlyphTag INSTANCE = new GlyphTag();

    private GlyphTag() {
    }

    public TagResolver getRESOLVER() {
        return TagResolver.empty();
    }
}

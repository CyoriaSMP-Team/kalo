package com.nexomc.nexo.glyphs

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver

/**
 * Nexo's glyph tag resolver, reduced to the member zMenu links against.
 *
 * zMenu's `NexoTagResolverLoader` runs on [com.nexomc.nexo.api.events.NexoItemsLoadedEvent]
 * and reads `GlyphTag.INSTANCE.getRESOLVER()`; without this class the event handler fails
 * with `NoClassDefFoundError`. Kalo serves no Nexo glyph font, so the resolver is empty and
 * MiniMessage leaves unknown tags as they are.
 */
object GlyphTag {
    @Suppress("PropertyName")
    val RESOLVER: TagResolver = TagResolver.empty()
}

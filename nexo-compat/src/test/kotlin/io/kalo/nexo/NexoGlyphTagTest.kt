package io.kalo.nexo

import com.nexomc.nexo.glyphs.GlyphTag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** zMenu links to `GlyphTag.INSTANCE.getRESOLVER()` by name; pin that shape. */
class NexoGlyphTagTest {
    @Test
    fun glyphTagExposesInstanceAndResolverLikeNexo() {
        val c = GlyphTag::class.java
        val instance = c.getField("INSTANCE")
        assertTrue(Modifier.isStatic(instance.modifiers), "INSTANCE must be static")
        val getter = c.getMethod("getRESOLVER")
        assertEquals(TagResolver::class.java, getter.returnType)
        assertNotNull(getter.invoke(instance.get(null)))
    }
}

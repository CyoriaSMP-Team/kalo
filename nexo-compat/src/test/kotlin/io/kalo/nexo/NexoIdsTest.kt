package io.kalo.nexo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NexoIdsTest {
    private val keys = listOf("zpack:ruby_sword", "apack:ruby_sword", "apack:emerald")

    @Test
    fun bareIdMatchesValue() {
        assertEquals("apack:emerald", NexoIds.resolve("emerald", keys))
    }

    @Test
    fun duplicateValuePicksFirstNamespaceDeterministically() {
        assertEquals("apack:ruby_sword", NexoIds.resolve("ruby_sword", keys))
        assertEquals("apack:ruby_sword", NexoIds.resolve("ruby_sword", keys.reversed()))
    }

    @Test
    fun namespacedIdIsTakenLiterally() {
        assertEquals("zpack:ruby_sword", NexoIds.resolve("zpack:ruby_sword", keys))
        assertNull(NexoIds.resolve("other:ruby_sword", keys))
    }

    @Test
    fun unknownOrEmptyIsNull() {
        assertNull(NexoIds.resolve("nope", keys))
        assertNull(NexoIds.resolve("", keys))
    }

    @Test
    fun kaloKeyMapsToFlatNexoId() {
        assertEquals("ruby_sword", NexoIds.fromKaloKey("apack:ruby_sword"))
    }
}

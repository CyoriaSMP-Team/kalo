package io.kalo.nexo

/**
 * Nexo ids are flat ("ruby_sword"); Kalo keys are namespaced by pack ("mypack:ruby_sword").
 * This is the one place that translates between them, so the rule is stated once.
 *
 * - A Nexo id with a colon is taken as a full Kalo key.
 * - A bare id matches the Kalo key whose value is that id. If several packs define the same
 *   value the lexicographically first namespace wins, so the answer is stable across restarts
 *   instead of depending on load order.
 */
object NexoIds {
    /** The Nexo-facing id for a Kalo key string such as "mypack:ruby_sword". */
    @JvmStatic
    fun fromKaloKey(key: String): String = key.substringAfter(':')

    /** Picks the Kalo key a Nexo id refers to from [keys] ("namespace:value" strings), or null. */
    @JvmStatic
    fun resolve(id: String, keys: Collection<String>): String? {
        if (id.isEmpty()) return null
        if (id.contains(':')) return keys.firstOrNull { it == id }
        return keys.filter { it.substringAfter(':') == id }.minOrNull()
    }
}

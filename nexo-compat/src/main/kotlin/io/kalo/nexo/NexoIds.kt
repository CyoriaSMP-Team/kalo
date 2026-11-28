package io.kalo.nexo

import io.kalo.compat.CompatIds

/** Nexo-facing view of the shared id rule; see [CompatIds]. */
object NexoIds {
    @JvmStatic
    fun fromKaloKey(key: String): String = CompatIds.fromKaloKey(key)

    @JvmStatic
    fun resolve(id: String, keys: Collection<String>): String? = CompatIds.resolve(id, keys)
}

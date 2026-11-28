package io.kalo.compat

import com.nexomc.nexo.api.events.NexoItemsLoadedEvent
import io.kalo.event.RegistryInitializeEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.server.ServerLoadEvent
import org.bukkit.plugin.Plugin

/**
 * Entry point for the embedded compatibility layer. The names Kalo answers to are declared in
 * `provides` in Kalo's own plugin descriptor (see `core/build.gradle.kts`); this class only
 * wires the runtime side, such as the events those APIs promise.
 */
object KaloCompat {
    /** Plugin names Kalo claims. Keep in step with `provides`; a name appears here only once its API exists. */
    @JvmField
    val PROVIDED_NAMES: List<String> = listOf("Nexo")

    @JvmStatic
    fun enable(owner: Plugin) {
        owner.server.pluginManager.registerEvents(Events(owner), owner)
        owner.logger.info("Compatibility layer active: answering as ${PROVIDED_NAMES.joinToString()} " +
            "(a real plugin of the same name always wins).")
    }

    private class Events(private val owner: Plugin) : Listener {
        /** After every plugin has enabled, so listeners registered in their onEnable receive it. */
        @EventHandler
        fun onServerLoad(event: ServerLoadEvent) = fire()

        /** Kalo reads packs at HIGHEST on this event; MONITOR runs after, on /kalo reload. */
        @EventHandler(priority = EventPriority.MONITOR)
        fun onKaloContentLoaded(event: RegistryInitializeEvent) = fire()

        private fun fire() = owner.server.pluginManager.callEvent(NexoItemsLoadedEvent())
    }
}

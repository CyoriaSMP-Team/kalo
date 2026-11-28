package com.nexomc.nexo

import com.nexomc.nexo.api.events.NexoItemsLoadedEvent
import io.kalo.event.RegistryInitializeEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.server.ServerLoadEvent
import org.bukkit.plugin.java.JavaPlugin

/**
 * Registers as the plugin named "Nexo" so `depend: [Nexo]` is satisfied, and answers the Nexo
 * API from Kalo. Do not install it next to the real Nexo: two plugins cannot share a name.
 */
class NexoCompatPlugin : JavaPlugin(), Listener {
    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        logger.info("Kalo is answering as Nexo. Supported API: NexoItems (see docs/NEXO_COMPAT.md).")
    }

    /** After every plugin has enabled, so listeners registered in their onEnable receive it. */
    @EventHandler
    fun onServerLoad(event: ServerLoadEvent) = fireLoaded()

    /** Kalo reads packs at HIGHEST on this event; MONITOR runs after, on /kalo reload. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onKaloContentLoaded(event: RegistryInitializeEvent) = fireLoaded()

    private fun fireLoaded() = server.pluginManager.callEvent(NexoItemsLoadedEvent())
}

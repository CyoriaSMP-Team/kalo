package com.nexomc.nexo.api.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** Fired once Kalo's content is loaded, which is when Nexo plugins expect their items to exist. */
class NexoItemsLoadedEvent : Event() {
    override fun getHandlers(): HandlerList = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
    }
}

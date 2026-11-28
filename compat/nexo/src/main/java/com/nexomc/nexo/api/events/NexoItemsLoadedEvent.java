package com.nexomc.nexo.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after the item registry is available, so consumers can (re)read it.
 *
 * <p>Three installed plugins listen for this event: zMenu clears its item cache, NOrder
 * loads its custom items, and ODailyQuests reloads its quest config. None of them call a
 * method on the event, so the payload is empty — only the type and the Bukkit event
 * plumbing matter.</p>
 */
public final class NexoItemsLoadedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public NexoItemsLoadedEvent() {
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}

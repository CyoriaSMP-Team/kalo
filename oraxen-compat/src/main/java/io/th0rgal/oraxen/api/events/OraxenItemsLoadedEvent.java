package io.th0rgal.oraxen.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired once Kalo's content is loaded, which is when Oraxen plugins expect their items to exist. */
public class OraxenItemsLoadedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

package dev.lone.itemsadder.api.Events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Async event ItemsAdder fires when it has finished loading. Constructed asynchronous, as in
 * ItemsAdder, so it must be called off the main thread. The flags are accepted but not stored:
 * no accessor for them is verified, so none exists. The nested {@code Cause} enum is not provided.
 */
public class ItemsAdderLoadDataEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public ItemsAdderLoadDataEvent(boolean isReload, boolean skipRecipes) {
        super(true);
    }

    @Deprecated
    public ItemsAdderLoadDataEvent(boolean isReload) {
        this(isReload, false);
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

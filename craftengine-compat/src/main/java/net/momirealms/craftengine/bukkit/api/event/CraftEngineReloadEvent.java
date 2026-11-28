package net.momirealms.craftengine.bukkit.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when Kalo's content has loaded, which is when plugins that listen for CraftEngine's reload
 * expect to re-read their items. CraftEngine's own constructor takes its plugin class and
 * {@code plugin()} returns it; Kalo has no such class, so neither exists here and construction goes
 * through {@link CraftEngineReloadEvents}. Listening to the event, and {@code isFirstReload()}, work.
 */
public final class CraftEngineReloadEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final boolean firstReload;

    CraftEngineReloadEvent(boolean firstReload) {
        this.firstReload = firstReload;
    }

    public boolean isFirstReload() {
        return firstReload;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}

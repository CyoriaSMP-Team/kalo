package io.kalo.event;

import io.kalo.pack.ResourcePack;
import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired on the pack generation thread once every content type has contributed its assets
 * and before the pack is written to disk. The last chance for third parties to add to or
 * override the generated pack.
 */
@Getter
public class AsyncResourcePackGenerationEvent extends Event {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final ResourcePack resourcePack;

    public AsyncResourcePackGenerationEvent(@NotNull ResourcePack resourcePack) {
        super(true);
        this.resourcePack = resourcePack;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}

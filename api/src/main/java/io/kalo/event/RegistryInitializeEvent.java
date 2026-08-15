package io.kalo.event;

import io.kalo.manager.RegistryManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor
public class RegistryInitializeEvent extends Event {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final RegistryManager.GlobalRegistries registries;

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}

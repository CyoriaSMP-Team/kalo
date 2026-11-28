package net.momirealms.craftengine.bukkit.api.event;

/** Kalo-side factory for {@link CraftEngineReloadEvent}; not part of CraftEngine's API. */
public final class CraftEngineReloadEvents {
    private CraftEngineReloadEvents() {
    }

    public static CraftEngineReloadEvent create(boolean firstReload) {
        return new CraftEngineReloadEvent(firstReload);
    }
}

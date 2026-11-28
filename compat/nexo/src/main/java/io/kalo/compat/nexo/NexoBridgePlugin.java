package io.kalo.compat.nexo;

import com.nexomc.nexo.api.events.NexoItemsLoadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Declares the plugin name {@code Nexo} so consumers that hardcode it load, and serves
 * their item calls from Kalo.
 *
 * <p>This is not Nexo. It implements the API surface those consumers actually link
 * against and nothing else.</p>
 */
public final class NexoBridgePlugin extends JavaPlugin implements Listener {

    /** The fallback only runs when this fork does not fire {@link ServerLoadEvent}. */
    private static final long FALLBACK_DELAY_TICKS = 200L;

    private final AtomicBoolean published = new AtomicBoolean();

    @Override
    public void onEnable() {
        NexoItemBridge.rebuildIndex();
        getLogger().info("Nexo item API served by Kalo: "
                + NexoItemBridge.indexSize() + " unique item ids resolve");
        getLogger().info("Remove this jar before installing real Nexo; "
                + "Bukkit allows one plugin per name.");

        getServer().getPluginManager().registerEvents(this, this);

        // A one-tick delay is not enough: plugins enable one after another, and zMenu
        // registers its listener many seconds after this one. ServerLoadEvent is fired
        // once every plugin is enabled, so it is the first moment a broadcast reaches
        // everyone.
        Bukkit.getGlobalRegionScheduler().runDelayed(this,
                task -> publish("fallback timer"), FALLBACK_DELAY_TICKS);
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        publish("server load");
    }

    private void publish(String trigger) {
        if (!published.compareAndSet(false, true)) {
            return;
        }
        NexoItemBridge.rebuildIndex();
        getServer().getPluginManager().callEvent(new NexoItemsLoadedEvent());
        getLogger().info("Published " + NexoItemBridge.indexSize()
                + " item ids to consumers (" + trigger + ")");
    }
}

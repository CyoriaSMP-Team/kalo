package io.kalo;

import io.kalo.manager.CommandManager;
import io.kalo.manager.ContentManagerImpl;
import io.kalo.manager.Context;
import io.kalo.manager.Managerial;
import io.kalo.manager.RegistryManagerImpl;
import io.kalo.manager.Reloadable;
import io.kalo.manager.ResourcePackManagerImpl;
import io.kalo.platform.java.JavaBlockListener;
import io.kalo.platform.java.JavaRecipeListener;
import io.kalo.platform.java.VirtualBlockStore;
import io.kalo.performance.PerformanceManager;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

public final class KaloPluginImpl extends JavaPlugin implements KaloPlugin {
    @Getter @Accessors(fluent = true)
    private final RegistryManagerImpl registryManager = new RegistryManagerImpl();
    @Getter @Accessors(fluent = true)
    private final ContentManagerImpl contentManager = new ContentManagerImpl();
    @Getter @Accessors(fluent = true)
    private final ResourcePackManagerImpl resourcePackManager = new ResourcePackManagerImpl();
    @Getter @Accessors(fluent = true)
    private final PerformanceManager performance = new PerformanceManager();

    private final CommandManager commandManager = new CommandManager();
    private final VirtualBlockStore virtualBlockStore = new VirtualBlockStore();
    private JavaBlockListener blockListener;

    private final List<Managerial> managers = List.of(
            registryManager,
            contentManager,
            performance,
            resourcePackManager,
            commandManager
    );

    @Override
    public void onEnable() {
        Kalo.registerPlugin(this);

        // Writes config.yml on first run without overwriting an edited one.
        saveDefaultConfig();

        Path virtualBlockFile = getDataFolder().toPath().resolve("virtual-blocks.kvb");
        try {
            virtualBlockStore.load(virtualBlockFile);
        } catch (IOException e) {
            // The index is the source of truth for virtual content. Starting with an empty
            // index after a corrupt read would silently strand Barrier anchors in worlds.
            getLogger().log(Level.SEVERE,
                    "Could not load " + virtualBlockFile + "; refusing to start rather than lose virtual blocks", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Context context = new Context(this);
        managers.forEach(manager -> manager.preload(context));

        blockListener = new JavaBlockListener(registryManager.blockStateAllocator(), virtualBlockStore);
        getServer().getPluginManager().registerEvents(blockListener, this);
        getServer().getPluginManager().registerEvents(new JavaRecipeListener(), this);

        // Content loads here rather than on ServerLoadEvent, which is what Neko did.
        // Geyser asks for custom blocks partway through its own enable — before
        // ServerLoadEvent — so waiting meant handing it an empty registry and Bedrock
        // players seeing none of the content.
        managers.forEach(manager -> manager.start(context));

        // Atomic hot-path tables must be rebuilt after every pack has populated the
        // registries. Loaded chunks are then reconciled on their own region scheduler.
        blockListener.rebuildLookup();
        blockListener.applyPerformanceBudget(this, performance.snapshot().budget());
        performance.addPressureListener(snapshot ->
                org.bukkit.Bukkit.getGlobalRegionScheduler().execute(this, () -> {
                    JavaBlockListener listener = blockListener;
                    if (listener != null) {
                        listener.applyPerformanceBudget(this, snapshot.budget());
                    }
                }));

        // Subscribes to Geyser's block event. Must come after content is loaded, since
        // Geyser may already be enabled and fire immediately.
        io.kalo.integration.GeyserIntegration.registerIfPresent(this);
        io.kalo.integration.PlaceholderApiIntegration.initialize(this);
        io.kalo.integration.MythicMobsIntegration.initialize(this);
        io.kalo.integration.ModelEngineIntegration.initialize(this);
    }

    @Override
    public void onDisable() {
        // Registry/content teardown must never race the asynchronous pack compiler.
        resourcePackManager.awaitIdle();
        Context context = new Context(this);
        io.kalo.integration.GeyserIntegration.unregister();
        io.kalo.integration.PlaceholderApiIntegration.unregister();
        io.kalo.integration.MythicMobsIntegration.unregister();
        io.kalo.integration.ModelEngineIntegration.unregister();
        for (int i = managers.size() - 1; i >= 0; i--) {
            managers.get(i).end(context);
        }
        resourcePackManager.shutdown();

        if (blockListener != null) {
            HandlerList.unregisterAll(blockListener);
            blockListener = null;
        }

        try {
            virtualBlockStore.close();
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not flush virtual blocks during shutdown", e);
        }

        Kalo.unregisterPlugin();
    }

    public int indexedVirtualBlockCount() {
        JavaBlockListener listener = blockListener;
        return listener != null ? listener.indexedVirtualBlockCount() : virtualBlockStore.size();
    }

    public int renderedVirtualDisplayCount() {
        JavaBlockListener listener = blockListener;
        return listener != null ? listener.renderedDisplayCount() : 0;
    }

    @Override
    public void reload() {
        // Finish the current immutable registry view before replacing it. This closes a
        // race where an async pack build could observe half old / half new registries.
        resourcePackManager.awaitIdle();

        List<Reloadable> reloadableList = managers.stream()
                // ResourcePackManager has a dedicated hot-reload path that preserves an
                // unchanged HTTP host/URL instead of tearing it down every reload.
                .filter(manager -> manager != resourcePackManager)
                .filter(manager -> manager instanceof Reloadable)
                .map(manager -> (Reloadable) manager)
                .toList();

        Context context = new Context(this);
        for (int i = reloadableList.size() - 1; i >= 0; i--) {
            reloadableList.get(i).end(context);
        }
        reloadConfig();
        reloadableList.forEach(reloadable -> reloadable.preload(context));
        reloadableList.forEach(reloadable -> reloadable.start(context));

        if (blockListener != null) {
            blockListener.rebuildLookup();
            blockListener.refreshLoadedChunks(this);
        }
        resourcePackManager.reloadManager(context);
    }
}

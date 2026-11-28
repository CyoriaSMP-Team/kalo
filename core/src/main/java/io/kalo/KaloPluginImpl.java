package io.kalo;

import io.kalo.manager.CommandManager;
import io.kalo.manager.ContentManagerImpl;
import io.kalo.manager.Context;
import io.kalo.manager.Managerial;
import io.kalo.manager.RegistryManagerImpl;
import io.kalo.manager.Reloadable;
import io.kalo.manager.ResourcePackManagerImpl;
import io.kalo.platform.java.FurnitureListener;
import io.kalo.platform.java.GuiListener;
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

    /**
     * Created on first use because it resolves the plugin data folder, which does not
     * exist yet when fields are initialised.
     */
    private io.kalo.items.ItemIdentityImpl items;

    @Override
    public io.kalo.items.ItemIdentityImpl items() {
        io.kalo.items.ItemIdentityImpl current = items;
        if (current == null) {
            current = new io.kalo.items.ItemIdentityImpl(
                    getDataFolder().toPath().resolve("external-items.yml"));
            items = current;
        }
        return current;
    }

    private final CommandManager commandManager = new CommandManager();
    private final VirtualBlockStore virtualBlockStore = new VirtualBlockStore();
    private JavaBlockListener blockListener;
    private FurnitureListener furnitureListener;
    private GuiListener guiListener;
    private io.kalo.platform.java.PlacedModelListener placedModelListener;
    private io.kalo.platform.java.WornArmorListener wornArmorListener;
    private io.kalo.platform.java.MobListener mobListener;
    private io.kalo.platform.java.EmoteListener emoteListener;
    private io.kalo.platform.java.VehicleListener vehicleListener;
    private io.kalo.platform.java.ContentEntitlementListener entitlementListener;
    private io.kalo.platform.java.HudRuntime hudRuntime;
    private io.kalo.platform.java.DialogRuntime dialogRuntime;
    private io.kalo.platform.java.VfxRuntime vfxRuntime;
    private io.kalo.platform.java.LiquidRuntime liquidRuntime;
    private io.kalo.converter.ExternalItemObserver externalItemObserver;

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
        items = new io.kalo.items.ItemIdentityImpl(
                getDataFolder().toPath().resolve("external-items.yml"));

        // Writes config.yml on first run without overwriting an edited one.
        saveDefaultConfig();
        installPauseDialog();

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
        furnitureListener = new FurnitureListener();
        guiListener = new GuiListener();
        placedModelListener = new io.kalo.platform.java.PlacedModelListener();
        wornArmorListener = new io.kalo.platform.java.WornArmorListener();
        mobListener = new io.kalo.platform.java.MobListener();
        emoteListener = new io.kalo.platform.java.EmoteListener();
        vehicleListener = new io.kalo.platform.java.VehicleListener();
        getServer().getPluginManager().registerEvents(blockListener, this);
        getServer().getPluginManager().registerEvents(furnitureListener, this);
        getServer().getPluginManager().registerEvents(guiListener, this);
        getServer().getPluginManager().registerEvents(placedModelListener, this);
        getServer().getPluginManager().registerEvents(wornArmorListener, this);
        getServer().getPluginManager().registerEvents(mobListener, this);
        getServer().getPluginManager().registerEvents(emoteListener, this);
        getServer().getPluginManager().registerEvents(vehicleListener, this);
        getServer().getPluginManager().registerEvents(new JavaRecipeListener(), this);
        entitlementListener = new io.kalo.platform.java.ContentEntitlementListener();
        getServer().getPluginManager().registerEvents(entitlementListener, this);
        hudRuntime = new io.kalo.platform.java.HudRuntime();
        getServer().getPluginManager().registerEvents(hudRuntime, this);
        dialogRuntime = new io.kalo.platform.java.DialogRuntime();
        getServer().getPluginManager().registerEvents(dialogRuntime, this);
        vfxRuntime = new io.kalo.platform.java.VfxRuntime();
        getServer().getPluginManager().registerEvents(vfxRuntime, this);
        liquidRuntime = new io.kalo.platform.java.LiquidRuntime();
        getServer().getPluginManager().registerEvents(liquidRuntime, this);

        // A client with chat blocked cannot run any command, so menus need a click path.
        if (getConfig().getBoolean("menu-item.enabled", false)) {
            getServer().getPluginManager().registerEvents(
                    new io.kalo.platform.java.MenuItemListener(this), this);
        }


        if (getConfig().getBoolean("external-content.enabled", false)
                && getConfig().getBoolean("external-content.auto-learn", false)) {
            try {
                externalItemObserver = new io.kalo.converter.ExternalItemObserver(this, items());
                getServer().getPluginManager().registerEvents(externalItemObserver, this);
            } catch (IOException invalid) {
                getLogger().log(Level.SEVERE,
                        "Could not initialize external item observation; native content is unaffected",
                        invalid);
            }
        }

        // Content loads here rather than on ServerLoadEvent, which is what Neko did.
        // Geyser asks for custom blocks partway through its own enable — before
        // ServerLoadEvent — so waiting meant handing it an empty registry and Bedrock
        // players seeing none of the content.
        managers.forEach(manager -> manager.start(context));
        installDialogs();
        installLiquidBiomes();
        io.kalo.platform.java.LiquidRuntime.initialize(
                registryManager.liquidType(), getDataFolder().toPath());

        // Atomic hot-path tables must be rebuilt after every pack has populated the
        // registries. Loaded chunks are then reconciled on their own region scheduler.
        blockListener.rebuildLookup();
        blockListener.applyPerformanceBudget(this, performance.snapshot().budget());
        io.kalo.model.ModelRegistry.applyRetentionScale(
                performance.snapshot().budget().cacheRetentionScale());
        if (wornArmorListener != null) {
            wornArmorListener.startAll(this);
        }
        if (mobListener != null) {
            mobListener.startAll();
        }
        if (vehicleListener != null) {
            vehicleListener.startAll();
        }
        performance.addPressureListener(snapshot ->
                org.bukkit.Bukkit.getGlobalRegionScheduler().execute(this, () -> {
                    io.kalo.model.ModelRegistry.applyRetentionScale(
                            snapshot.budget().cacheRetentionScale());
                    JavaBlockListener listener = blockListener;
                    if (listener != null) {
                        listener.applyPerformanceBudget(this, snapshot.budget());
                    }
                }));

        // Subscribes to Geyser's block event. Must come after content is loaded, since
        // Geyser may already be enabled and fire immediately.
        io.kalo.integration.GeyserIntegration.registerIfPresent(this);
        // Optional forms are behind a classloader guard: Cumulus/Geyser classes are not
        // touched on Java-only or proxy-backed server processes.
        io.kalo.integration.BedrockFormsIntegration.initialize(this);
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

        if (externalItemObserver != null) {
            externalItemObserver.close();
            externalItemObserver = null;
        }
        if (blockListener != null) {
            HandlerList.unregisterAll(blockListener);
            blockListener = null;
        }
        if (furnitureListener != null) {
            HandlerList.unregisterAll(furnitureListener);
            furnitureListener = null;
        }
        if (guiListener != null) {
            HandlerList.unregisterAll(guiListener);
            guiListener = null;
        }
        if (placedModelListener != null) {
            HandlerList.unregisterAll(placedModelListener);
            placedModelListener = null;
        }
        if (wornArmorListener != null) {
            HandlerList.unregisterAll(wornArmorListener);
            wornArmorListener = null;
        }
        if (mobListener != null) {
            HandlerList.unregisterAll(mobListener);
            mobListener = null;
        }
        if (emoteListener != null) {
            HandlerList.unregisterAll(emoteListener);
            emoteListener = null;
        }
        if (vehicleListener != null) {
            HandlerList.unregisterAll(vehicleListener);
            vehicleListener = null;
        }
        if (entitlementListener != null) {
            HandlerList.unregisterAll(entitlementListener);
            entitlementListener = null;
        }
        io.kalo.platform.java.HudRuntime.clear();
        if (hudRuntime != null) {
            HandlerList.unregisterAll(hudRuntime);
            hudRuntime = null;
        }
        io.kalo.platform.java.DialogRuntime.clear();
        if (dialogRuntime != null) {
            HandlerList.unregisterAll(dialogRuntime);
            dialogRuntime = null;
        }
        io.kalo.platform.java.VfxRuntime.clear();
        if (vfxRuntime != null) {
            HandlerList.unregisterAll(vfxRuntime);
            vfxRuntime = null;
        }
        io.kalo.platform.java.LiquidRuntime.shutdown();
        if (liquidRuntime != null) {
            HandlerList.unregisterAll(liquidRuntime);
            liquidRuntime = null;
        }

        try {
            virtualBlockStore.close();
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not flush virtual blocks during shutdown", e);
        }

        Kalo.unregisterPlugin();
    }

    /** Writes the color-biome data pack; the game registers new biomes on restart. */
    private void installLiquidBiomes() {
        if (getServer().getWorlds().isEmpty()) {
            return;
        }
        int updated = 0;
        var definitions = registryManager.liquidType().ordered();
        for (var world : getServer().getWorlds()) {
            try {
                if (io.kalo.content.liquid.LiquidBiomePack.install(
                        world.getWorldFolder().toPath(), definitions)) {
                    updated++;
                }
            } catch (IOException | RuntimeException error) {
                getLogger().log(Level.SEVERE,
                        "Failed to generate the liquid biome data pack for "
                                + world.getName(), error);
            }
        }
        if (updated > 0) {
            getLogger().info("Liquid colour data pack updated in " + updated
                    + " world(s). Restart the server to register new biomes.");
        }
    }

    /** Builds the owned data pack that registers every Kalo dialog. */
    private void installDialogs() {
        java.util.List<io.kalo.content.dialog.definition.DialogDefinition> definitions =
                registryManager.dialogType().ordered();
        if (getServer().getWorlds().isEmpty()) {
            if (!definitions.isEmpty()) {
                getLogger().warning("Cannot install dialog datapack: no loaded world");
            }
            return;
        }
        int updatedWorlds = 0;
        for (var world : getServer().getWorlds()) {
            try {
                if (io.kalo.content.dialog.DialogPack.install(
                        world.getWorldFolder().toPath(), definitions)) {
                    updatedWorlds++;
                }
            } catch (IOException | RuntimeException error) {
                getLogger().log(Level.SEVERE,
                        "Failed to generate the native dialog datapack for "
                                + world.getName(), error);
            }
        }
        if (updatedWorlds > 0) {
            getLogger().info("Native dialog datapack updated in " + updatedWorlds
                    + " world(s) (dialogs=" + definitions.size()
                    + "). Run /reload or restart to load the dialog registry.");
        }
    }

    /** Build the Java pause-screen dialog registry as an owned Kalo datapack. */
    private void installPauseDialog() {
        var menu = getConfig().getConfigurationSection("pause-menu.dialog");
        if (menu == null || !menu.getBoolean("enabled", false)) return;
        try {
            if (getServer().getWorlds().isEmpty()) {
                throw new IOException("Cannot install pause dialog: no loaded world");
            }
            var settings = io.kalo.pack.branding.PauseMenuDialogPack.parse(menu);
            boolean updated = io.kalo.pack.branding.PauseMenuDialogPack.install(
                    getServer().getWorlds().get(0).getWorldFolder().toPath(), settings);
            getLogger().info("Native pause dialog datapack " + (updated ? "updated" : "unchanged")
                    + " (buttons=" + settings.buttons().size()
                    + "). Load the world datapack registry to apply changes.");
        } catch (IOException | RuntimeException error) {
            getLogger().log(Level.SEVERE, "Failed to generate native pause dialog datapack", error);
        }
    }

    /** Accessed by the command route after listeners have been initialized. */
    public GuiListener guiListener() { return guiListener; }

    public io.kalo.platform.java.MobListener mobListener() { return mobListener; }

    public io.kalo.platform.java.EmoteListener emoteListener() { return emoteListener; }

    public io.kalo.platform.java.VehicleListener vehicleListener() { return vehicleListener; }

    public int indexedVirtualBlockCount() {
        JavaBlockListener listener = blockListener;
        return listener != null ? listener.indexedVirtualBlockCount() : virtualBlockStore.size();
    }

    public int renderedVirtualDisplayCount() {
        JavaBlockListener listener = blockListener;
        return listener != null ? listener.renderedDisplayCount() : 0;
    }

    /**
     * Rebuilds animated-block display rigs in already-loaded chunks after the pack
     * compiler has republished {@code BlockbenchRigRegistry}. Chunk sync is scheduled
     * per region, so this never touches world state from the pack worker.
     */
    public void refreshAnimatedBlocks() {
        JavaBlockListener listener = blockListener;
        if (listener != null) {
            listener.refreshLoadedChunks(this);
        }
        io.kalo.platform.java.MobListener mobs = mobListener;
        if (mobs != null) {
            mobs.startAll();
        }
        io.kalo.platform.java.VehicleListener vehicles = vehicleListener;
        if (vehicles != null) {
            vehicles.startAll();
        }
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
        installPauseDialog();
        reloadableList.forEach(reloadable -> reloadable.preload(context));
        reloadableList.forEach(reloadable -> reloadable.start(context));
        installDialogs();
        installLiquidBiomes();
        io.kalo.platform.java.LiquidRuntime.initialize(
                registryManager.liquidType(), getDataFolder().toPath());

        if (blockListener != null) {
            blockListener.rebuildLookup();
            blockListener.refreshLoadedChunks(this);
        }
        if (furnitureListener != null) {
            furnitureListener.rebuildLookup();
        }
        if (guiListener != null) {
            guiListener.rebuildLookup();
        }
        resourcePackManager.reloadManager(context);
    }
}

package io.kalo;

import io.kalo.manager.*;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.plugin.java.JavaPlugin;
import io.kalo.platform.java.JavaBlockListener;
import io.kalo.platform.java.JavaRecipeListener;

import java.util.List;

public final class KaloPluginImpl extends JavaPlugin implements KaloPlugin {
    @Getter @Accessors(fluent = true)
    private final RegistryManagerImpl registryManager = new RegistryManagerImpl();
    @Getter @Accessors(fluent = true)
    private final ContentManagerImpl contentManager = new ContentManagerImpl();
    @Getter @Accessors(fluent = true)
    private final ResourcePackManagerImpl resourcePackManager = new ResourcePackManagerImpl();

    private final CommandManager commandManager = new CommandManager();

    private final List<Managerial> managers = List.of(
            registryManager,
            contentManager,
            resourcePackManager,
            commandManager
    );

    @Override
    public void onEnable() {
        Kalo.registerPlugin(this);

        // Writes config.yml on first run without overwriting an edited one.
        saveDefaultConfig();

        Context context = new Context(this);
        managers.forEach(manager -> manager.preload(context));
        getServer().getPluginManager().registerEvents(
                new JavaBlockListener(registryManager.blockStateAllocator()), this);
        getServer().getPluginManager().registerEvents(new JavaRecipeListener(), this);

        // Content loads here rather than on ServerLoadEvent, which is what Neko did.
        // Geyser asks for custom blocks partway through its own enable — before
        // ServerLoadEvent — so waiting meant handing it an empty registry and Bedrock
        // players seeing none of the content.
        //
        // The cost is that an add-on registering its own content types must be enabled
        // before Kalo, which it declares with `load: AFTER` on Kalo in its plugin.yml.
        managers.forEach(manager -> manager.start(context));

        // Subscribes to Geyser's block event. Must come after content is loaded, since
        // Geyser may already be enabled and fire immediately.
        io.kalo.integration.GeyserIntegration.registerIfPresent(this);

        io.kalo.integration.PlaceholderApiIntegration.initialize(this);
    }

    @Override
    public void onDisable() {
        Context context = new Context(this);
        // Stop optional-dependency callbacks before any registry or pack state they can
        // observe begins tearing down.
        io.kalo.integration.GeyserIntegration.unregister();
        io.kalo.integration.PlaceholderApiIntegration.unregister();

        // Unwind in reverse dependency order: commands and pack generation stop before
        // the content and registries they read are torn down.
        for (int i = managers.size() - 1; i >= 0; i--) {
            managers.get(i).end(context);
        }

        Kalo.unregisterPlugin();
    }

    @Override
    public void reload() {
        List<Reloadable> reloadableList = managers.stream()
                .filter(manager -> manager instanceof Reloadable)
                .map(manager -> (Reloadable) manager)
                .toList();

        Context context = new Context(this);
        for (int i = reloadableList.size() - 1; i >= 0; i--) {
            reloadableList.get(i).end(context);
        }

        // `/kalo reload` promises to reload Kalo's configuration as well as its packs.
        // Without this, edits to base-pack, Bedrock mode, or pack hosting were ignored
        // until the whole server restarted.
        reloadConfig();

        reloadableList.forEach(reloadable -> reloadable.preload(context));
        reloadableList.forEach(reloadable -> reloadable.start(context));
    }
}

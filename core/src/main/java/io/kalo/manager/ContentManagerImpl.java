package io.kalo.manager;

import io.kalo.content.ContentsPack;
import io.kalo.content.ContentsPackImpl;
import io.kalo.content.PackLoader;
import io.kalo.content.feature.builtin.HelloWorldFeature;
import io.kalo.content.item.Item;
import io.kalo.content.item.ItemImpl;
import io.kalo.event.RegistryInitializeEvent;
import io.kalo.utils.Constants;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Optional;
import java.util.logging.Logger;

public final class ContentManagerImpl implements ContentManager, Managerial, Reloadable, Listener {
    private static final Logger LOGGER = Logger.getLogger(ContentManagerImpl.class.getName());

    @Override
    public void preload(@NotNull Context context) {
        Bukkit.getPluginManager().registerEvents(this, context.plugin());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRegistryInitialize(RegistryInitializeEvent event) {
        RegistryManager.GlobalRegistries.registries().features().register(HelloWorldFeature.KEY, new HelloWorldFeature.Factory());

        loadPacks(event.getRegistries());
    }

    private void loadPacks(@NotNull RegistryManager.GlobalRegistries registries) {
        LOGGER.info("Loading packs...");

        File packsFolder = new File(Constants.dataFolder(), "packs");
        if (!packsFolder.exists() && !packsFolder.mkdirs()) {
            LOGGER.warning("Could not create the packs folder at " + packsFolder);
            return;
        }

        File[] packFolders = packsFolder.listFiles();
        if (packFolders == null) {
            return;
        }

        int loadedPacksCnt = 0;
        for (File packFolder : packFolders) {
            ContentsPack pack = PackLoader.loadPack(packFolder);
            if (pack == null) {
                continue;
            }

            registries.contentsPacks().register(ContentsPackImpl.createKey(pack.id()), pack);
            registries.mergeAll(pack.registries());
            loadedPacksCnt++;
        }
        LOGGER.info("Loaded " + loadedPacksCnt + " packs!");
    }

    @Override
    public void start(@NotNull Context context) {
    }

    @Override
    public void end(@NotNull Context context) {
        HandlerList.unregisterAll(this);
    }

    @Override
    public @NotNull Optional<Item> getItemByStack(@NotNull ItemStack itemStack) {
        // Single PDC read plus one registry lookup. This used to stream every registered
        // item and test the stack against each, which is a real per-event cost in an
        // inventory click handler.
        String id = ItemImpl.idOf(itemStack);
        if (id == null) {
            return Optional.empty();
        }
        return RegistryManager.GlobalRegistries.registries().item().get(Key.key(id));
    }
}

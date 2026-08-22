package io.kalo.manager;

import io.kalo.content.ContentsPack;
import io.kalo.content.ContentsPackImpl;
import io.kalo.content.PackLoader;
import io.kalo.content.feature.builtin.HelloWorldFeature;
import io.kalo.content.feature.builtin.AbilityFeature;
import io.kalo.content.feature.builtin.StatsFeature;
import io.kalo.content.feature.builtin.AnimationFeature;
import io.kalo.content.feature.builtin.MobFeature;
import io.kalo.content.feature.builtin.ModelFeature;
import io.kalo.content.feature.builtin.SkillFeature;
import io.kalo.content.feature.builtin.CooldownFeature;
import io.kalo.content.feature.builtin.ParticleFeature;
import io.kalo.content.feature.builtin.SoundFeature;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.logging.Level;
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
        RegistryManager.GlobalRegistries.registries().features().register(AbilityFeature.KEY, new AbilityFeature.Factory());
        RegistryManager.GlobalRegistries.registries().features().register(StatsFeature.KEY, new StatsFeature.Factory());
        RegistryManager.GlobalRegistries.registries().features().register(AnimationFeature.KEY, new AnimationFeature.Factory());
        RegistryManager.GlobalRegistries.registries().features().register(MobFeature.KEY, new MobFeature.Factory());
        RegistryManager.GlobalRegistries.registries().features().register(ModelFeature.KEY, new ModelFeature.Factory());
        RegistryManager.GlobalRegistries.registries().features().register(SkillFeature.KEY, new SkillFeature.Factory());
        RegistryManager.GlobalRegistries.registries().features().register(CooldownFeature.KEY, new CooldownFeature.Factory());
        RegistryManager.GlobalRegistries.registries().features().register(ParticleFeature.KEY, new ParticleFeature.Factory());
        RegistryManager.GlobalRegistries.registries().features().register(SoundFeature.KEY, new SoundFeature.Factory());

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

        // Stable discovery makes duplicate-id diagnostics and first-load behaviour
        // reproducible across filesystems.
        Arrays.sort(packFolders, Comparator.comparing(File::getName));

        int loadedPacksCnt = 0;
        for (File packFolder : packFolders) {
            ContentsPack pack = PackLoader.loadPack(packFolder, id -> {
                Key key = ContentsPackImpl.createKey(id);
                if (registries.contentsPacks().get(key).isEmpty()) {
                    return true;
                }
                LOGGER.warning("Skipping pack at " + packFolder + ": duplicate pack id '" + id + "'");
                return false;
            });
            if (pack == null) {
                continue;
            }

            net.kyori.adventure.key.Key packKey = ContentsPackImpl.createKey(pack.id());
            if (registries.contentsPacks().get(packKey).isPresent()) {
                LOGGER.warning("Skipping pack at " + pack.packFolder() + ": duplicate pack id '"
                        + pack.id() + "'");
                continue;
            }

            String conflict = firstConflict(registries, pack.registries());
            if (conflict != null) {
                LOGGER.warning("Skipping pack '" + pack.id() + "' at " + pack.packFolder()
                        + ": registry key '" + conflict + "' is already registered");
                continue;
            }

            try {
                // Preflight above keeps mergeAll atomic across its four registry merges;
                // without it a late conflict could leave half a rejected pack registered.
                registries.mergeAll(pack.registries());
                registries.contentsPacks().register(packKey, pack);
                loadedPacksCnt++;
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Could not register pack '" + pack.id()
                        + "' from " + pack.packFolder(), e);
            }
        }
        LOGGER.info("Loaded " + loadedPacksCnt + " packs!");
    }

    private static String firstConflict(@NotNull RegistryManager.GlobalRegistries target,
                                        @NotNull io.kalo.registry.Registries source) {
        String conflict = firstConflict(target.item(), source.item());
        if (conflict == null) conflict = firstConflict(target.block(), source.block());
        if (conflict == null) conflict = firstConflict(target.furniture(), source.furniture());
        if (conflict == null) conflict = firstConflict(target.armor(), source.armor());
        if (conflict == null) conflict = firstConflict(target.painting(), source.painting());
        if (conflict == null) conflict = firstConflict(target.musicDisc(), source.musicDisc());
        if (conflict == null) conflict = firstConflict(target.gui(), source.gui());
        return conflict;
    }

    private static <T> String firstConflict(@NotNull io.kalo.registry.Registry<T> target,
                                            @NotNull io.kalo.registry.Registry<T> source) {
        for (it.unimi.dsi.fastutil.Pair<Key, T> entry : source.entries()) {
            if (target.get(entry.key()).isPresent()) {
                return entry.key().asString();
            }
        }
        return null;
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
        final Key key;
        try {
            key = Key.key(id);
        } catch (RuntimeException ignored) {
            // Item PDC can be edited by commands/tools; malformed data is not content.
            return Optional.empty();
        }

        RegistryManager.GlobalRegistries registries = RegistryManager.GlobalRegistries.registries();
        Item item = registries.item().get(key).orElse(null);
        if (item != null) {
            return Optional.of(item);
        }
        // Armor has an item form and carries the same item id marker, but lives in its
        // own registry so a lookup limited to item() made it invisible to this API.
        return registries.armor().get(key).map(Item.class::cast);
    }
}

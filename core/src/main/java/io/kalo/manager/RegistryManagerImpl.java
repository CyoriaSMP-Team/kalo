package io.kalo.manager;

import io.kalo.content.ContentType;
import io.kalo.content.ContentsPack;
import io.kalo.content.item.ItemType;
import io.kalo.content.block.BlockType;
import io.kalo.content.furniture.FurnitureType;
import io.kalo.content.armor.ArmorType;
import io.kalo.content.recipe.RecipeType;
import io.kalo.content.feature.FeatureFactory;
import io.kalo.event.RegistryInitializeEvent;
import io.kalo.registry.DirectScalableRegistry;
import io.kalo.registry.DirectWritableRegistry;
import io.kalo.registry.MappedRegistry;
import io.kalo.registry.Registry;
import io.kalo.registry.Registries;
import io.kalo.registry.RegistriesImpl;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public final class RegistryManagerImpl implements RegistryManager, Managerial, Reloadable {
    private static final Logger LOGGER = Logger.getLogger(RegistryManagerImpl.class.getName());

    private final io.kalo.platform.java.BlockStateAllocator blockStateAllocator =
            new io.kalo.platform.java.BlockStateAllocator(io.kalo.content.block.definition.BlockCarrier.NOTE_BLOCK);
    private final RecipeType recipeType = new RecipeType();
    private final GlobalRegistries globalRegistries = new GlobalRegistriesImpl(blockStateAllocator, recipeType);

    @Override
    public void preload(@NotNull Context context) {
        LOGGER.info("Initializing registries...");

        globalRegistries.unlockAll();
        globalRegistries.clearAll();
        recipeType.clear();
        try {
            java.nio.file.Path stateFile =
                    new File(context.plugin().getDataFolder(), "block-states.json").toPath();
            blockStateAllocator.load(stateFile);
            // Write through on each new assignment rather than only on shutdown: a crash
            // between pack generation and shutdown would otherwise lose them.
            blockStateAllocator.attach(stateFile);
        } catch (IOException e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Could not load block state assignments", e);
        }
    }

    @Override
    public void start(@NotNull Context context) {
        Bukkit.getPluginManager().callEvent(new RegistryInitializeEvent(globalRegistries));

        globalRegistries.lockAll();

        // After every pack is in: a recipe may reference content from a pack that had not
        // been read when the recipe itself was parsed.
        int recipes = recipeType.registerAll();
        if (recipes > 0) {
            LOGGER.info("Registered " + recipes + " recipe(s)");
        }

        LOGGER.info("Successfully initialized registries!");
    }

    @Override
    public void end(@NotNull Context context) {
        try {
            blockStateAllocator.save(new File(context.plugin().getDataFolder(), "block-states.json").toPath());
        } catch (IOException e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Could not save block state assignments", e);
        }
    }

    public @NotNull io.kalo.platform.java.BlockStateAllocator blockStateAllocator() {
        return blockStateAllocator;
    }

    @Override
    public @NotNull GlobalRegistries registries() {
        return globalRegistries;
    }

    public static final class GlobalRegistriesImpl extends RegistriesImpl implements GlobalRegistries {
        @Getter @Accessors(fluent = true)
        private final Registry<ContentType<?>> types;
        @Getter @Accessors(fluent = true)
        private final DirectWritableRegistry<FeatureFactory<?>> features;
        @Getter @Accessors(fluent = true)
        private final DirectWritableRegistry<ContentsPack> contentsPacks;

        private GlobalRegistriesImpl(@NotNull io.kalo.platform.java.BlockStateAllocator blockStateAllocator,
                                    @NotNull RecipeType recipeType) {
            Map<Key, ContentType<?>> typeMap = Map.of(
                    ItemType.KEY, new ItemType(),
                    BlockType.KEY, new BlockType(blockStateAllocator),
                    FurnitureType.KEY, new FurnitureType(blockStateAllocator),
                    ArmorType.KEY, new ArmorType(),
                    RecipeType.KEY, recipeType);
            this.types = create(new MappedRegistry<>(typeMap));
            this.features = create(new DirectScalableRegistry<>());
            this.contentsPacks = create(new DirectScalableRegistry<>());
        }

        @Override
        public void mergeAll(@NotNull Registries registries) {
            item().merge(registries.item());
            block().merge(registries.block());
            furniture().merge(registries.furniture());
            armor().merge(registries.armor());
        }
    }
}

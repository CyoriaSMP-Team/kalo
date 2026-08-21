package io.kalo.manager;

import io.kalo.content.ContentType;
import io.kalo.content.ContentsPack;
import io.kalo.content.item.ItemType;
import io.kalo.content.block.BlockType;
import io.kalo.content.furniture.FurnitureType;
import io.kalo.content.armor.ArmorType;
import io.kalo.content.recipe.RecipeType;
import io.kalo.content.sound.SoundType;
import io.kalo.content.glyph.GlyphType;
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
    private final SoundType soundType = new SoundType();
    private final GlyphType glyphType = new GlyphType();
    private final GlobalRegistries globalRegistries = new GlobalRegistriesImpl(blockStateAllocator, recipeType, soundType, glyphType);

    @Override
    public void preload(@NotNull Context context) {
        LOGGER.info("Initializing registries...");

        // Validate the irreplaceable carrier-state mapping before clearing the currently
        // usable registries. On reload, a corrupt file should fail loudly without first
        // discarding every old content entry.
        java.nio.file.Path stateFile =
                new File(context.plugin().getDataFolder(), "block-states.json").toPath();
        loadBlockStateAssignments(blockStateAllocator, stateFile);

        globalRegistries.unlockAll();
        globalRegistries.clearAll();
        // clearAll() empties the type registry as well, so the built-ins go back in
        // before anything tries to read them.
        ((GlobalRegistriesImpl) globalRegistries).registerBuiltinTypes();
        soundType.clear();
        glyphType.clear();
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
        // Recipes are server-global Bukkit state, not merely data in this manager. They
        // must disappear on a plugin disable as well as on reload. Clearing here also
        // means preload no longer has to double-clear them on the next lifecycle.
        recipeType.clear();
        try {
            blockStateAllocator.save(new File(context.plugin().getDataFolder(), "block-states.json").toPath());
        } catch (IOException e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Could not save block state assignments", e);
        }
    }

    public @NotNull SoundType soundType() {
        return soundType;
    }

    public @NotNull GlyphType glyphType() {
        return glyphType;
    }

    public @NotNull io.kalo.platform.java.BlockStateAllocator blockStateAllocator() {
        return blockStateAllocator;
    }

    static void loadBlockStateAssignments(
            @NotNull io.kalo.platform.java.BlockStateAllocator allocator,
            @NotNull java.nio.file.Path stateFile
    ) {
        try {
            allocator.load(stateFile);
            // Write through on each new assignment rather than only on shutdown: a crash
            // between pack generation and shutdown would otherwise lose them.
            allocator.attach(stateFile);
        } catch (IOException e) {
            // Continuing with an empty/unattached allocator would reassign carrier states
            // and silently turn already-placed blocks into different content.
            throw new IllegalStateException(
                    "Could not load persistent block state assignments from " + stateFile, e);
        }
    }

    @Override
    public @NotNull GlobalRegistries registries() {
        return globalRegistries;
    }

    public static final class GlobalRegistriesImpl extends RegistriesImpl implements GlobalRegistries {
        @Getter @Accessors(fluent = true)
        private final DirectWritableRegistry<ContentType<?>> types;
        @Getter @Accessors(fluent = true)
        private final DirectWritableRegistry<FeatureFactory<?>> features;
        @Getter @Accessors(fluent = true)
        private final DirectWritableRegistry<ContentsPack> contentsPacks;

        private final io.kalo.platform.java.BlockStateAllocator blockStateAllocator;
        private final RecipeType recipeType;
        private final SoundType soundType;
        private final GlyphType glyphType;

        private GlobalRegistriesImpl(@NotNull io.kalo.platform.java.BlockStateAllocator blockStateAllocator,
                                    @NotNull RecipeType recipeType,
                                    @NotNull SoundType soundType,
                                    @NotNull GlyphType glyphType) {
            // Writable rather than a fixed map: add-ons register their own content types
            // during RegistryInitializeEvent, before packs are read.
            this.types = create(new DirectScalableRegistry<>());
            this.blockStateAllocator = blockStateAllocator;
            this.recipeType = recipeType;
            this.soundType = soundType;
            this.glyphType = glyphType;
            registerBuiltinTypes();
            this.features = create(new DirectScalableRegistry<>());
            this.contentsPacks = create(new DirectScalableRegistry<>());
        }

        /**
         * Puts Kalo's own content types back.
         *
         * <p>Needed after {@code clearAll()}, which now empties the type registry too:
         * it became writable so add-ons could contribute, and a reload would otherwise
         * leave the server with no content types at all.</p>
         */
        void registerBuiltinTypes() {
            types.register(ItemType.KEY, new ItemType());
            types.register(BlockType.KEY, new BlockType(blockStateAllocator));
            types.register(FurnitureType.KEY, new FurnitureType(blockStateAllocator));
            types.register(ArmorType.KEY, new ArmorType());
            types.register(RecipeType.KEY, recipeType);
            types.register(SoundType.KEY, soundType);
            types.register(GlyphType.KEY, glyphType);
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

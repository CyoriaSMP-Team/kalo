package io.kalo.content.furniture;

import io.kalo.config.ConfigSchema;
import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.block.BlockConfigSchema;
import io.kalo.content.block.BlockType;
import io.kalo.content.block.definition.BlockDefinition;
import io.kalo.content.feature.FeatureBuilder;
import io.kalo.pack.ResourcePack;
import io.kalo.platform.java.BlockStateAllocator;
import io.kalo.platform.java.JavaBlockCompiler;
import io.kalo.registry.Registries;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Static furniture: a named, placeable model backed by a reserved note-block state.
 * Entity-backed furniture is deliberately a later mode; this mode is stable on Paper/Folia.
 */
public final class FurnitureType implements ContentType<Furniture> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "furniture");
    private final BlockStateAllocator allocator;

    public FurnitureType(@NotNull BlockStateAllocator allocator) { this.allocator = allocator; }
    @Override public @NotNull String id() { return "furniture"; }
    @Override public @NotNull Class<Furniture> clazz() { return Furniture.class; }
    @Override public @NotNull Iterable<Furniture> contents(@NotNull Registries registries) { return registries.furniture(); }

    @Override
    public boolean load(@NotNull PackContext pack, @NotNull Registries registries, @NotNull ConfigurationSection config) {
        ConfigSchema.Result result = new BlockConfigSchema().validate(config);
        if (!result.isSuccess()) {
            Plugins.logger().warning("Failed to load furniture '" + config.getName() + "' in pack '" + pack.namespace() + "'");
            result.getErrors().forEach(error -> Plugins.logger().warning("  " + error));
            return false;
        }
        Key key = pack.key(config.getName());
        try {
            BlockDefinition definition = BlockType.parseDefinition(key, config);
            List<FeatureBuilder> features = BlockType.parseFeatures(key, config.getConfigurationSection("features"));
            registries.furniture().register(key, entry -> {
                entry.key(key).definition(definition).features(features);
            });
            return true;
        } catch (Exception e) {
            Plugins.logger().log(Level.WARNING, "Failed to load furniture '" + key.asString() + "'", e);
            return false;
        }
    }

    private volatile java.util.Set<String> uncompilable = java.util.Set.of();

    @Override
    public void compilePack(@NotNull ResourcePack resourcePack, @NotNull Iterable<Furniture> contents) {
        List<io.kalo.content.block.Block> blocks = new ArrayList<>();
        contents.forEach(blocks::add);
        uncompilable = JavaBlockCompiler.compileBlocks(resourcePack, blocks, allocator).keySet();
    }

    /** See {@link io.kalo.content.block.BlockType#uncompilable()}. */
    public @NotNull java.util.Set<String> uncompilable() {
        return uncompilable;
    }
}

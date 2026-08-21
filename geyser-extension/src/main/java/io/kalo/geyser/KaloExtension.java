package io.kalo.geyser;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.NonVanillaCustomBlockData;
import org.geysermc.geyser.api.block.custom.component.BoxComponent;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.component.GeometryComponent;
import org.geysermc.geyser.api.block.custom.component.MaterialInstance;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.component.java.JavaItemDataComponents;
import org.geysermc.geyser.api.util.Identifier;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Registers Kalo's custom blocks with Geyser so Bedrock players see them.
 *
 * <p>Kalo's resource pack describes what the blocks <em>look</em> like, but Bedrock will
 * not render a block it has never been told exists. Registration happens through Geyser's
 * API at runtime, which is why this extension exists at all: the Paper plugin cannot do
 * it, because Geyser is a different process.</p>
 *
 * <p>The two sides meet at {@code bedrock-mappings.json}, which the Paper plugin writes
 * into its data folder. Point {@code mappings-file} in this extension's config at it, or
 * drop a copy next to this extension.</p>
 */
public class KaloExtension implements Extension {

    private static final String MAPPINGS_FILE = "bedrock-mappings.json";

    private KaloMappings mappings = KaloMappings.empty();

    @Subscribe
    public void onDefineCustomItems(GeyserDefineCustomItemsEvent event) {
        mappings = loadMappings();

        if (mappings.items().isEmpty()) {
            logger().info("No Kalo items to register. Copy " + MAPPINGS_FILE
                    + " from plugins/Kalo/ into " + dataFolder() + " once the server has generated it.");
            return;
        }

        int registered = 0;
        for (KaloMappings.ItemEntry entry : mappings.items()) {
            try {
                CustomItemDefinition.Builder definition = CustomItemDefinition.builder(
                                Identifier.of(entry.bedrockId()), Identifier.of(entry.model()))
                        .bedrockOptions(CustomItemBedrockOptions.builder().icon(entry.icon()))
                        .component(JavaItemDataComponents.MAX_STACK_SIZE, entry.maxStackSize());

                if (entry.displayName() != null) {
                    definition.displayName(entry.displayName());
                }
                if (entry.maxDamage() != null) {
                    definition.component(JavaItemDataComponents.MAX_DAMAGE, entry.maxDamage());
                }
                if (entry.enchantmentGlint()) {
                    definition.component(JavaItemDataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                }

                event.register(Identifier.of(entry.javaIdentifier()), definition.build());
                registered++;
            } catch (Exception e) {
                logger().error("Could not register Kalo item " + entry.bedrockId(), e);
            }
        }

        logger().info("Registered " + registered + " Kalo item(s) with Geyser");
    }

    @Subscribe
    public void onDefineCustomBlocks(GeyserDefineCustomBlocksEvent event) {
        mappings = loadMappings();

        if (mappings.blocks().isEmpty()) {
            logger().info("No Kalo blocks to register. Copy " + MAPPINGS_FILE
                    + " from plugins/Kalo/ into " + dataFolder() + " once the server has generated it.");
            return;
        }

        int registered = 0;
        for (KaloMappings.BlockEntry entry : mappings.blocks()) {
            try {
                if (entry.javaIdentifier() == null) {
                    logger().error("Cannot map Kalo block " + entry.javaKey()
                            + ": its Java carrier state is missing");
                    continue;
                }

                CustomBlockData block = build(entry);
                event.register(block);
                // Defining a custom block only adds it to Bedrock's palette. The
                // override is what makes the note-block state actually present in the
                // Java world translate to that palette entry.
                event.registerOverride(entry.javaIdentifier(), block.defaultBlockState());
                registered++;
            } catch (Exception e) {
                // One malformed entry should not cost every other block its registration.
                logger().error("Could not register Kalo block " + entry.javaKey(), e);
            }
        }

        logger().info("Registered " + registered + " Kalo block(s) with Geyser");
    }

    private CustomBlockData build(KaloMappings.BlockEntry entry) {
        String[] parts = entry.bedrockId().split(":", 2);
        String namespace = parts.length == 2 ? parts[0] : "kalo";
        String name = parts.length == 2 ? parts[1] : parts[0];

        CustomBlockComponents.Builder components = CustomBlockComponents.builder()
                .displayName(entry.displayName() != null ? entry.displayName() : name)
                // Kalo's Java carrier is a full note block, so collision and selection
                // stay full-sized on Bedrock even when the visual geometry is smaller.
                .selectionBox(BoxComponent.fullBox())
                .collisionBoxes(BoxComponent.fullBox())
                .geometry(GeometryComponent.builder()
                        .identifier(entry.geometry() != null
                                ? entry.geometry()
                                : "minecraft:geometry.full_block")
                        .build());

        if (entry.materialInstances().isEmpty()) {
            // Compatibility with mappings generated before material metadata existed.
            // This is exact for cube_all and at least avoids Geyser's colon-separated
            // identifier fallback, which never matches Kalo's terrain-atlas key.
            addMaterial(components, "*", entry.bedrockId().replace(':', '_').replace('/', '_'));
        } else {
            entry.materialInstances().forEach((material, texture) ->
                    addMaterial(components, material, texture));
        }

        if (entry.hardness() != null) {
            components.destructibleByMining(entry.hardness());
        }

        return NonVanillaCustomBlockData.builder()
                .namespace(namespace)
                .name(name)
                // Kalo blocks are obtained from the plugin's own items, so they should not
                // clutter the Bedrock creative menu with a second copy.
                .includedInCreativeInventory(false)
                .components(components.build())
                .build();
    }

    private static void addMaterial(CustomBlockComponents.Builder components,
                                    String name, String texture) {
        components.materialInstance(name, MaterialInstance.builder()
                .texture(texture)
                .renderMethod("opaque")
                .faceDimming(true)
                .ambientOcclusion(true)
                .build());
    }

    private KaloMappings loadMappings() {
        Path file = dataFolder().resolve(MAPPINGS_FILE);
        try {
            return KaloMappings.load(file);
        } catch (IOException e) {
            logger().error("Could not read " + file, e);
            return KaloMappings.empty();
        }
    }

    /** Exposed for tests, which have no Geyser runtime to hand. */
    List<KaloMappings.BlockEntry> loadedBlocks() {
        return mappings.blocks();
    }
}

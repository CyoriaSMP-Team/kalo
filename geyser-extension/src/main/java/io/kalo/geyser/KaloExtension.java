package io.kalo.geyser;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.NonVanillaCustomBlockData;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.extension.Extension;

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
    public void onDefineCustomBlocks(GeyserDefineCustomBlocksEvent event) {
        mappings = loadMappings();

        if (mappings.isEmpty()) {
            logger().info("No Kalo blocks to register. Copy " + MAPPINGS_FILE
                    + " from plugins/Kalo/ into " + dataFolder() + " once the server has generated it.");
            return;
        }

        int registered = 0;
        for (KaloMappings.BlockEntry entry : mappings.blocks()) {
            try {
                event.register(build(entry));
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

        CustomBlockComponents components = CustomBlockComponents.builder()
                .displayName(name)
                .build();

        return NonVanillaCustomBlockData.builder()
                .namespace(namespace)
                .name(name)
                // Kalo blocks are obtained from the plugin's own items, so they should not
                // clutter the Bedrock creative menu with a second copy.
                .includedInCreativeInventory(false)
                .components(components)
                .build();
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

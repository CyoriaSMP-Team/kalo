package io.kalo.integration;

import io.kalo.content.block.Block;
import io.kalo.manager.RegistryManager;
import io.kalo.utils.Plugins;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.NonVanillaCustomBlockData;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Registers Kalo's blocks with Geyser from inside the server process.
 *
 * <p>Most servers run Geyser as a plugin rather than standalone, which means it shares
 * this JVM — and then a separate extension jar is pure ceremony. The extension existed
 * only because the two were assumed to be different processes: it made the server owner
 * install a second artifact and hand-copy {@code bedrock-mappings.json} between them, and
 * every regeneration meant copying it again or serving stale blocks.</p>
 *
 * <p>{@code EventRegistrar.of} is Geyser's own hook for exactly this: an owner for event
 * subscriptions that is not an extension. Nothing here needs the extension to exist.</p>
 *
 * <p>The standalone extension remains for servers that genuinely run Geyser in another
 * process, which this cannot reach.</p>
 */
public final class GeyserBridge {

    private GeyserBridge() {
    }

    /**
     * Subscribes to Geyser's block definition event when Geyser is in this JVM.
     *
     * <p>Called through {@link GeyserIntegration}, which does the is-it-present check —
     * this class names Geyser types in its own signatures, so loading it at all requires
     * Geyser on the classpath.</p>
     */
    static void register(@NotNull Object owner) {
        EventRegistrar registrar = EventRegistrar.of(owner);

        GeyserApi.api().eventBus().subscribe(registrar, GeyserDefineCustomBlocksEvent.class,
                GeyserBridge::onDefineCustomBlocks);

        Plugins.logger().info("Registering Kalo blocks with Geyser directly — no extension needed");
    }

    /**
     * Reads straight from the live registries rather than from a mapping file.
     *
     * <p>This is the real win over the extension: the two halves cannot drift, because
     * there is no file in between to go stale.</p>
     */
    private static void onDefineCustomBlocks(@NotNull GeyserDefineCustomBlocksEvent event) {
        int registered = 0;

        for (Block block : allBlocks()) {
            try {
                if (!block.definition().bedrock().enabled()) {
                    continue;
                }
                event.register(build(block));
                registered++;
            } catch (Exception e) {
                // One block failing must not cost the rest their registration.
                Plugins.logger().log(Level.WARNING,
                        "Could not register " + block.key().asString() + " with Geyser", e);
            }
        }

        Plugins.logger().info("Registered " + registered + " block(s) with Geyser natively");
    }

    private static @NotNull Iterable<Block> allBlocks() {
        RegistryManager.GlobalRegistries registries = RegistryManager.GlobalRegistries.registries();

        java.util.List<Block> blocks = new java.util.ArrayList<>();
        registries.block().forEach(blocks::add);
        // Furniture is block-backed, so Bedrock needs it registered the same way.
        registries.furniture().forEach(blocks::add);
        return blocks;
    }

    private static @NotNull CustomBlockData build(@NotNull Block block) {
        var key = block.definition().key();

        CustomBlockComponents components = CustomBlockComponents.builder()
                .displayName(key.value())
                .build();

        return NonVanillaCustomBlockData.builder()
                .namespace(key.namespace())
                .name(key.value())
                // Obtained through Kalo's own items, so a second copy in the Bedrock
                // creative menu would only be confusing.
                .includedInCreativeInventory(false)
                .components(components)
                .build();
    }
}

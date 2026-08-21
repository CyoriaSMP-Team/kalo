package io.kalo.integration;

import io.kalo.content.item.Item;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.manager.RegistryManager;
import io.kalo.platform.bedrock.BedrockBlockRegistration;
import io.kalo.platform.bedrock.BedrockRegistrationSnapshot;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.NonVanillaCustomBlockData;
import org.geysermc.geyser.api.block.custom.component.BoxComponent;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.component.GeometryComponent;
import org.geysermc.geyser.api.block.custom.component.MaterialInstance;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.SessionLoadResourcePacksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.component.java.JavaItemDataComponents;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    private static final java.util.concurrent.atomic.AtomicReference<CachedPack> CACHED_PACK =
            new java.util.concurrent.atomic.AtomicReference<>();

    private GeyserBridge() {
    }

    /**
     * Subscribes to Geyser's block definition event when Geyser is in this JVM.
     *
     * <p>Called through {@link GeyserIntegration}, which does the is-it-present check —
     * this class names Geyser types in its own signatures, so loading it at all requires
     * Geyser on the classpath.</p>
     */
    static @NotNull Object register(@NotNull Object owner) {
        EventRegistrar registrar = EventRegistrar.of(owner);

        // Geyser-Spigot enables its Bukkit plugin first, but deliberately defers
        // GeyserImpl.start() (and these definition events) to ServerLoadEvent. Paper
        // enables Kalo and executes this subscription before that event is fired.
        GeyserApi.api().eventBus().subscribe(registrar, GeyserDefineCustomBlocksEvent.class,
                GeyserBridge::onDefineCustomBlocks);
        GeyserApi.api().eventBus().subscribe(registrar, GeyserDefineCustomItemsEvent.class,
                GeyserBridge::onDefineCustomItems);
        GeyserApi.api().eventBus().subscribe(registrar, GeyserDefineResourcePacksEvent.class,
                GeyserBridge::onDefineResourcePacks);
        // Initial generation finishes before this bridge is installed. The per-session
        // hook remains a defensive fallback for a Geyser reload or a pack that was
        // temporarily unavailable during its resource-pack lifecycle event.
        GeyserApi.api().eventBus().subscribe(registrar, SessionLoadResourcePacksEvent.class,
                GeyserBridge::onSessionLoadResourcePacks);

        Plugins.logger().info("Registering Kalo items, blocks and resource pack with Geyser directly"
                + " — no extension needed");
        return registrar;
    }

    /** Unsubscribes on Kalo disable so Geyser cannot call into torn-down registries. */
    static void unregister(@NotNull Object registered) {
        GeyserApi.api().eventBus().unregisterAll((EventRegistrar) registered);
    }

    /**
     * Reads straight from the live registries rather than from a mapping file.
     *
     * <p>This is the real win over the extension: the two halves cannot drift, because
     * there is no file in between to go stale.</p>
     */
    private static void onDefineCustomBlocks(@NotNull GeyserDefineCustomBlocksEvent event) {
        if (!bedrockOutputEnabled()) {
            return;
        }

        var snapshot = BedrockRegistrationSnapshot.await(Duration.ofSeconds(30));
        if (snapshot.isEmpty()) {
            Plugins.logger().warning("Kalo's Bedrock compiler did not finish before Geyser froze its"
                    + " custom-block palette; no incomplete blocks were registered");
            return;
        }

        int registered = 0;
        for (BedrockBlockRegistration block : snapshot.get()) {
            try {
                if (block.javaIdentifier() == null) {
                    Plugins.logger().warning("Cannot map Kalo block " + block.javaKey()
                            + ": its Java carrier state is missing");
                    continue;
                }
                CustomBlockData data = build(block);

                event.register(data);
                event.registerOverride(block.javaIdentifier(), data.defaultBlockState());
                registered++;
            } catch (Exception e) {
                // One block failing must not cost the rest their registration.
                Plugins.logger().log(Level.WARNING,
                        "Could not register " + block.javaKey() + " with Geyser", e);
            }
        }

        Plugins.logger().info("Registered and mapped " + registered + " block(s) with Geyser natively");
    }

    /** Registers the same Geyser v2 definitions that the standalone mapping file carries. */
    private static void onDefineCustomItems(@NotNull GeyserDefineCustomItemsEvent event) {
        if (!bedrockOutputEnabled()) {
            return;
        }
        int registered = 0;
        for (Item item : allItems()) {
            ItemDefinition definition = item.definition();
            if (!definition.bedrock().enabled()
                    || definition.model() instanceof ModelDefinition.Vanilla
                    || !(definition.model() instanceof ModelDefinition.Sprite)) {
                continue;
            }

            try {
                var key = definition.key();
                String bedrockId = key.asString();
                String icon = definition.bedrock().iconOverride() != null
                        ? definition.bedrock().iconOverride()
                        : key.namespace() + "_" + key.value();
                String javaIdentifier = "minecraft:"
                        + definition.java().baseMaterial().name().toLowerCase(Locale.ROOT);

                CustomItemDefinition.Builder custom = CustomItemDefinition.builder(
                                Identifier.of(bedrockId), Identifier.of(key.asString()))
                        .bedrockOptions(CustomItemBedrockOptions.builder().icon(icon))
                        .component(JavaItemDataComponents.MAX_STACK_SIZE,
                                definition.behaviour().maxStackSize());

                if (definition.display().name() != null) {
                    custom.displayName(PlainTextComponentSerializer.plainText()
                            .serialize(definition.display().name()));
                }
                if (definition.behaviour().maxDurability() != null) {
                    custom.component(JavaItemDataComponents.MAX_DAMAGE,
                            definition.behaviour().maxDurability());
                }
                if (definition.display().enchantmentGlint()) {
                    custom.component(JavaItemDataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                }

                event.register(Identifier.of(javaIdentifier), custom.build());
                registered++;
            } catch (Exception e) {
                Plugins.logger().log(Level.WARNING,
                        "Could not register " + definition.key().asString() + " with Geyser", e);
            }
        }

        Plugins.logger().info("Registered " + registered + " item(s) with Geyser natively");
    }

    private static @NotNull Iterable<Item> allItems() {
        RegistryManager.GlobalRegistries registries = RegistryManager.GlobalRegistries.registries();

        List<Item> items = new ArrayList<>();
        registries.item().forEach(items::add);
        registries.armor().forEach(items::add);
        return items;
    }

    private static @NotNull CustomBlockData build(@NotNull BedrockBlockRegistration block) {
        String[] identifier = block.bedrockIdentifier().split(":", 2);
        String namespace = identifier.length == 2 ? identifier[0] : "kalo";
        String name = identifier.length == 2 ? identifier[1] : identifier[0];

        CustomBlockComponents.Builder components = CustomBlockComponents.builder()
                .displayName(block.displayName() != null ? block.displayName() : name)
                // Java's carrier is a full note block. Keeping these boxes full-sized
                // preserves server collision and targeting even for decorative geometry.
                .selectionBox(BoxComponent.fullBox())
                .collisionBoxes(BoxComponent.fullBox())
                .geometry(GeometryComponent.builder().identifier(block.geometry()).build())
                .destructibleByMining(block.hardness());

        block.materialInstances().forEach((material, texture) ->
                addMaterial(components, material, texture));

        CustomBlockComponents builtComponents = components
                .build();

        return NonVanillaCustomBlockData.builder()
                .namespace(namespace)
                .name(name)
                // Obtained through Kalo's own items, so a second copy in the Bedrock
                // creative menu would only be confusing.
                .includedInCreativeInventory(false)
                .components(builtComponents)
                .build();
    }

    private static void addMaterial(@NotNull CustomBlockComponents.Builder components,
                                    @NotNull String name, @NotNull String texture) {
        components.materialInstance(name, MaterialInstance.builder()
                .texture(texture)
                .renderMethod("opaque")
                .faceDimming(true)
                .ambientOcclusion(true)
                .build());
    }

    private static void onDefineResourcePacks(@NotNull GeyserDefineResourcePacksEvent event) {
        if (!bedrockOutputEnabled()) {
            return;
        }
        ResourcePack pack = generatedPack();
        if (pack == null) {
            Plugins.logger().info("Kalo's generated.mcpack is not ready yet; it will be attached when a"
                    + " Bedrock player connects");
            return;
        }
        if (containsPack(event.resourcePacks(), pack)) {
            return;
        }

        try {
            event.register(pack);
            Plugins.logger().info("Registered generated.mcpack with Geyser");
        } catch (Exception e) {
            Plugins.logger().log(Level.WARNING, "Could not register generated.mcpack with Geyser", e);
        }
    }

    private static void onSessionLoadResourcePacks(@NotNull SessionLoadResourcePacksEvent event) {
        if (!bedrockOutputEnabled()) {
            return;
        }
        ResourcePack pack = generatedPack();
        if (pack == null || containsPack(event.resourcePacks(), pack)) {
            return;
        }

        try {
            event.register(pack,
                    new org.geysermc.geyser.api.pack.option.ResourcePackOption<?>[0]);
        } catch (Exception e) {
            Plugins.logger().log(Level.WARNING,
                    "Could not attach generated.mcpack to a Bedrock connection", e);
        }
    }

    private static boolean containsPack(@NotNull List<ResourcePack> packs,
                                        @NotNull ResourcePack candidate) {
        return packs.stream().anyMatch(pack -> pack.uuid().equals(candidate.uuid()));
    }

    /**
     * Reads the manifest through Geyser's supported path codec, once per generated pack.
     *
     * <p>This is called on every Bedrock connection as well as at startup. Re-reading and
     * re-parsing the archive per joining player is real work at a thousand items, so the
     * result is held until the file changes — identity, not age, decides: a regenerated
     * pack has a new size or timestamp and invalidates the cache on its own.</p>
     */
    private static @Nullable ResourcePack generatedPack() {
        Path path = new java.io.File(Constants.dataFolder(), "generated.mcpack").toPath();

        long size;
        long modified;
        try {
            if (!Files.isRegularFile(path)) {
                CACHED_PACK.set(null);
                return null;
            }
            size = Files.size(path);
            modified = Files.getLastModifiedTime(path).toMillis();
        } catch (java.io.IOException e) {
            Plugins.logger().log(Level.WARNING, "Could not stat " + path, e);
            return null;
        }

        CachedPack cached = CACHED_PACK.get();
        if (cached != null && cached.size() == size && cached.modified() == modified) {
            return cached.pack();
        }

        try {
            ResourcePack pack = ResourcePack.create(PackCodec.path(path));
            CACHED_PACK.set(new CachedPack(pack, size, modified));
            return pack;
        } catch (Exception e) {
            Plugins.logger().log(Level.WARNING, "Could not open " + path + " as a Bedrock resource pack", e);
            return null;
        }
    }

    private record CachedPack(@NotNull ResourcePack pack, long size, long modified) {
    }

    private static boolean bedrockOutputEnabled() {
        if (!(io.kalo.Kalo.plugin() instanceof org.bukkit.plugin.java.JavaPlugin plugin)) {
            return true;
        }
        String configured = plugin.getConfig().getString("bedrock", "auto");
        return configured == null
                || !(configured.equalsIgnoreCase("never") || configured.equalsIgnoreCase("false"));
    }
}

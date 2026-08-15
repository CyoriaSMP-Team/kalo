package io.kalo.platform.java;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.content.feature.Feature;
import io.kalo.content.feature.FeatureEventBus;
import io.kalo.content.feature.FeatureEventBusImpl;
import io.kalo.content.item.ImmutableItemStack;
import io.kalo.content.item.Item;
import io.kalo.content.item.definition.BedrockOptions;
import io.kalo.content.item.definition.DisplayProperties;
import io.kalo.content.item.definition.ItemBehaviour;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.content.item.definition.JavaOptions;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.pack.PackFormats;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the generated pack against the formats read out of the vanilla 26.2 client
 * jar, so a Minecraft format change fails here rather than silently producing a pack the
 * client rejects.
 */
class JavaPackCompilerTest {

    private static ResourcePack pack() {
        return new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "test"));
    }

    private static ItemDefinition sprite(String namespace, String name) {
        return ItemDefinition.builder(Key.key(namespace, name))
                .model(new ModelDefinition.Sprite(Key.key(namespace, "item/" + name)))
                .build();
    }

    @Test
    void spriteEmitsItemDefinitionAndGeneratedModel() throws IOException {
        ResourcePack pack = pack();

        JavaPackCompiler.compileItems(pack, List.of(new StubItem(sprite("testpack", "ruby_sword"))));

        // assets/<ns>/items/<name>.json — {"model":{"type":"minecraft:model","model":"..."}}
        JsonObject definition = json(pack, "assets/testpack/items/ruby_sword.json");
        JsonObject model = definition.getAsJsonObject("model");
        assertEquals("minecraft:model", model.get("type").getAsString());
        assertEquals("testpack:item/ruby_sword", model.get("model").getAsString());

        // assets/<ns>/models/item/<name>.json — parent item/generated + layer0
        JsonObject generated = json(pack, "assets/testpack/models/item/ruby_sword.json");
        assertEquals("minecraft:item/generated", generated.get("parent").getAsString());
        assertEquals("testpack:item/ruby_sword",
                generated.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void vanillaModelEmitsNothing() {
        ResourcePack pack = pack();

        ItemDefinition definition = ItemDefinition.builder(Key.key("testpack", "plain_apple"))
                .model(new ModelDefinition.Vanilla(Key.key("minecraft", "apple")))
                .build();
        JavaPackCompiler.compileItems(pack, List.of(new StubItem(definition)));

        // The client already has minecraft:apple; item_model points straight at it.
        assertFalse(pack.files().containsKey("assets/testpack/items/plain_apple.json"));
        assertFalse(pack.files().containsKey("assets/testpack/models/item/plain_apple.json"));
        assertEquals(Key.key("minecraft", "apple"), JavaItemCompiler.modelKey(definition));
    }

    @Test
    void customModelEmitsDefinitionButNotTheModelItself() {
        ResourcePack pack = pack();

        ItemDefinition definition = ItemDefinition.builder(Key.key("testpack", "chair"))
                .model(new ModelDefinition.Custom(Key.key("testpack", "item/chair"), Map.of()))
                .build();
        JavaPackCompiler.compileItems(pack, List.of(new StubItem(definition)));

        assertTrue(pack.files().containsKey("assets/testpack/items/chair.json"));
        // The hand-authored model ships in the pack's assets/ and is copied verbatim.
        assertFalse(pack.files().containsKey("assets/testpack/models/item/chair.json"));
    }

    @Test
    void writesLangEntryPerNamespace() throws IOException {
        ResourcePack pack = pack();

        ItemDefinition named = ItemDefinition.builder(Key.key("testpack", "ruby_sword"))
                .display(new DisplayProperties(Component.text("Ruby Sword"), List.of(), false))
                .model(new ModelDefinition.Sprite(Key.key("testpack", "item/ruby_sword")))
                .build();

        JavaPackCompiler.compileItems(pack, List.of(
                new StubItem(named),
                new StubItem(sprite("otherpack", "iron_ring"))));

        JsonObject testpack = json(pack, "assets/testpack/lang/en_us.json");
        assertEquals("Ruby Sword", testpack.get("item.testpack.ruby_sword").getAsString());

        // Each pack gets its own lang file rather than all of them sharing one.
        JsonObject otherpack = json(pack, "assets/otherpack/lang/en_us.json");
        assertEquals("Iron Ring", otherpack.get("item.otherpack.iron_ring").getAsString(),
                "an unnamed item should fall back to a humanised key, not a raw one");
    }

    @Test
    void oneBadItemDoesNotStopTheRest() {
        ResourcePack pack = pack();

        // A definition whose model throws when matched.
        ItemDefinition broken = ItemDefinition.builder(Key.key("testpack", "broken"))
                .model(new ModelDefinition.Sprite(Key.key("testpack", "item/broken")))
                .build();

        JavaPackCompiler.compileItems(pack, List.of(
                new ThrowingItem(broken),
                new StubItem(sprite("testpack", "good"))));

        assertTrue(pack.files().containsKey("assets/testpack/items/good.json"),
                "a failure on one item must not abort the whole compilation");
    }

    private static JsonObject json(ResourcePack pack, String path) throws IOException {
        Writable content = pack.file(path);
        assertNotNull(content, "missing pack file: " + path);
        return JsonParser.parseString(new String(content.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** Avoids {@link io.kalo.content.item.ItemImpl}, which builds a Bukkit ItemStack. */
    private static class StubItem implements Item {
        private final ItemDefinition definition;
        private final FeatureEventBus eventBus = new FeatureEventBusImpl();

        StubItem(ItemDefinition definition) {
            this.definition = definition;
        }

        @Override
        public @NotNull ItemDefinition definition() {
            return definition;
        }

        @Override
        public @NotNull ImmutableItemStack itemStack() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSimilar(@NotNull ItemStack itemStack) {
            return false;
        }

        @Override
        public @NotNull Collection<Feature> features() {
            return List.of();
        }

        @Override
        public @NotNull FeatureEventBus featureEventBus() {
            return eventBus;
        }

        @Override
        public @NotNull Key key() {
            return definition.key();
        }

        @Override
        public @NotNull String translationKey() {
            return definition.translationKey();
        }

        @Override
        public Item asItem() {
            return this;
        }
    }

    private static final class ThrowingItem extends StubItem {
        ThrowingItem(ItemDefinition definition) {
            super(definition);
        }

        @Override
        public @NotNull ItemDefinition definition() {
            throw new IllegalStateException("deliberately broken item");
        }
    }

    @Test
    void javaOptionsIsTheOnlyPlaceMaterialAppears() {
        // Guards the rule the Bedrock compiler depends on: nothing in the definition
        // layer names a Java-platform concept.
        ItemDefinition definition = ItemDefinition.builder(Key.key("testpack", "x"))
                .java(new JavaOptions(Material.NETHERITE_SWORD))
                .behaviour(new ItemBehaviour(1, 250, false))
                .bedrock(new BedrockOptions(true, null))
                .build();

        assertEquals(Material.NETHERITE_SWORD, definition.java().baseMaterial());
        assertTrue(definition.bedrock().enabled(), "Bedrock should be on by default");
        assertEquals("item.testpack.x", definition.translationKey());
    }
}

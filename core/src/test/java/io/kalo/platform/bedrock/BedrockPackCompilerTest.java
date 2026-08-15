package io.kalo.platform.bedrock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.content.feature.Feature;
import io.kalo.content.feature.FeatureEventBus;
import io.kalo.content.feature.FeatureEventBusImpl;
import io.kalo.content.item.ImmutableItemStack;
import io.kalo.content.item.Item;
import io.kalo.content.item.definition.BedrockOptions;
import io.kalo.content.item.definition.DisplayProperties;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.pack.PackFormats;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
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

class BedrockPackCompilerTest {

    private static ResourcePack javaPackWith(String texturePath) {
        ResourcePack java = new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "java"));
        java.file("assets/minecraft/blockstates/note_block.json", Writable.string("{}"));
        java.file(texturePath, Writable.bytes(new byte[]{1, 2, 3}));
        return java;
    }

    private static ItemDefinition sprite(String name) {
        Key key = Key.key("testpack", name);
        return ItemDefinition.builder(key)
                .display(new DisplayProperties(Component.text("Ruby Sword"), List.of(), false))
                .model(new ModelDefinition.Sprite(Key.key("testpack", "item/" + name)))
                .build();
    }

    private static BedrockPackCompiler.Result compile(ResourcePack java, List<Item> items) {
        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));
        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.add(items);
        return compiler.finish();
    }

    @Test
    void bedrockPackDoesNotCarryTheJavaAssetTree() throws IOException {
        // A Bedrock pack has its own layout and ignores assets/ entirely; shipping it
        // was tens of kilobytes of dead weight in every download.
        ResourcePack java = javaPackWith("assets/testpack/textures/item/ruby_sword.png");

        BedrockPackCompiler.Result result = compile(java, List.of(new StubItem(sprite("ruby_sword"))));

        assertTrue(result.pack().files().keySet().stream().noneMatch(p -> p.startsWith("assets/")),
                "bedrock pack should contain no assets/ entries, got: " + result.pack().files().keySet());
        assertNotNull(result.pack().file("manifest.json"));
        assertNotNull(result.pack().file("textures/item_texture.json"));
    }

    @Test
    void separateAddsAccumulateIntoOneMapping() throws IOException {
        // Items and armor are separate content types compiled in separate passes. Writing
        // the mapping file per pass meant whichever ran last erased the other's items.
        ResourcePack java = javaPackWith("assets/testpack/textures/item/ruby_sword.png");
        java.file("assets/testpack/textures/item/ruby_helmet.png", Writable.bytes(new byte[]{4}));

        ResourcePack bedrock = new ResourcePackImpl(PackMeta.of(0, "bedrock"));
        BedrockPackCompiler compiler = new BedrockPackCompiler(java, bedrock);
        compiler.add(List.of(new StubItem(sprite("ruby_sword"))));
        compiler.add(List.of(new StubItem(sprite("ruby_helmet"))));

        JsonObject mappings = json(compiler.finish().mappings());
        JsonObject items = mappings.getAsJsonObject("items");

        // Both map onto PAPER by default, so they share one vanilla item entry.
        int total = items.entrySet().stream().mapToInt(e -> e.getValue().getAsJsonArray().size()).sum();
        assertEquals(2, total, "both passes' items should survive: " + items);
    }

    @Test
    void spriteTextureIsCopiedIntoBedrocksFlatItemAtlas() throws IOException {
        ResourcePack java = javaPackWith("assets/testpack/textures/item/ruby_sword.png");

        BedrockPackCompiler.Result result = compile(java, List.of(new StubItem(sprite("ruby_sword"))));

        // Bedrock resolves icons by atlas shorthand, not by path.
        assertNotNull(result.pack().file("textures/items/testpack_ruby_sword.png"));

        JsonObject atlas = json(result.pack().file("textures/item_texture.json"));
        assertTrue(atlas.getAsJsonObject("texture_data").has("testpack_ruby_sword"));
    }

    @Test
    void displayNameIsRenderedTextNotTheKey() throws IOException {
        // Geyser shows this verbatim; the key would appear literally in the inventory.
        ResourcePack java = javaPackWith("assets/testpack/textures/item/ruby_sword.png");

        JsonObject mappings = json(compile(java, List.of(new StubItem(sprite("ruby_sword")))).mappings());
        JsonObject entry = mappings.getAsJsonObject("items")
                .getAsJsonArray("minecraft:paper").get(0).getAsJsonObject();

        assertEquals("Ruby Sword", entry.get("display_name").getAsString());
        assertEquals("testpack:ruby_sword", entry.get("bedrock_identifier").getAsString());
    }

    @Test
    void vanillaModelledItemsNeedNoBedrockEntry() throws IOException {
        ResourcePack java = javaPackWith("assets/testpack/textures/item/x.png");
        ItemDefinition vanilla = ItemDefinition.builder(Key.key("testpack", "plain_apple"))
                .model(new ModelDefinition.Vanilla(Key.key("minecraft", "apple")))
                .build();

        BedrockPackCompiler.Result result = compile(java, List.of(new StubItem(vanilla)));

        assertEquals(0, result.mappedCount(), "Bedrock already draws vanilla items correctly");
        assertEquals(0, result.skippedCount(), "and this is not a gap, so it is not a skip");
    }

    @Test
    void customModelsAreReportedAsSkippedRatherThanSilentlyDropped() throws IOException {
        // A pack author whose item appears on Java but not Bedrock deserves to know why.
        ResourcePack java = javaPackWith("assets/testpack/textures/item/x.png");
        ItemDefinition custom = ItemDefinition.builder(Key.key("testpack", "fancy"))
                .model(new ModelDefinition.Custom(Key.key("testpack", "item/fancy"), Map.of()))
                .build();

        BedrockPackCompiler.Result result = compile(java, List.of(new StubItem(custom)));

        assertEquals(0, result.mappedCount());
        assertEquals(1, result.skippedCount());
    }

    @Test
    void bedrockCanBeDisabledPerItem() throws IOException {
        ResourcePack java = javaPackWith("assets/testpack/textures/item/ruby_sword.png");
        ItemDefinition off = ItemDefinition.builder(Key.key("testpack", "java_only"))
                .model(new ModelDefinition.Sprite(Key.key("testpack", "item/ruby_sword")))
                .bedrock(new BedrockOptions(false, null))
                .build();

        assertEquals(0, compile(java, List.of(new StubItem(off))).mappedCount());
    }

    @Test
    void manifestUsesArrayVersionsAndAStableUuid() throws IOException {
        ResourcePack java = javaPackWith("assets/testpack/textures/item/ruby_sword.png");

        JsonObject first = json(compile(java, List.of(new StubItem(sprite("ruby_sword")))).pack().file("manifest.json"));
        JsonObject second = json(compile(java, List.of(new StubItem(sprite("ruby_sword")))).pack().file("manifest.json"));

        JsonObject header = first.getAsJsonObject("header");
        // Bedrock rejects a string version here.
        assertTrue(header.get("version").isJsonArray());
        assertEquals(3, header.getAsJsonArray("version").size());
        assertTrue(header.has("min_engine_version"));

        // A changing uuid makes Bedrock treat it as a brand new pack every regeneration.
        assertEquals(header.get("uuid").getAsString(),
                second.getAsJsonObject("header").get("uuid").getAsString());
    }

    private static JsonObject json(Writable writable) throws IOException {
        assertNotNull(writable);
        return JsonParser.parseString(new String(writable.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private record StubItem(ItemDefinition definition) implements Item {
        @Override
        public @NotNull ItemDefinition definition() {
            return definition;
        }

        @Override
        public @NotNull ImmutableItemStack itemStack() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSimilar(@NotNull org.bukkit.inventory.ItemStack itemStack) {
            return false;
        }

        @Override
        public @NotNull Collection<Feature> features() {
            return List.of();
        }

        @Override
        public @NotNull FeatureEventBus featureEventBus() {
            return new FeatureEventBusImpl();
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
}

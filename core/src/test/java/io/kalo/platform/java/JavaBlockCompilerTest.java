package io.kalo.platform.java;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.content.block.Block;
import io.kalo.content.block.definition.BlockBehaviour;
import io.kalo.content.block.definition.BlockCarrier;
import io.kalo.content.block.definition.BlockDefinition;
import io.kalo.content.block.definition.BlockModelDefinition;
import io.kalo.content.block.definition.JavaBlockOptions;
import io.kalo.content.feature.Feature;
import io.kalo.content.feature.FeatureEventBus;
import io.kalo.content.feature.FeatureEventBusImpl;
import io.kalo.content.item.ImmutableItemStack;
import io.kalo.content.item.definition.BedrockOptions;
import io.kalo.content.item.definition.DisplayProperties;
import io.kalo.pack.PackFormats;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaBlockCompilerTest {

    private static ResourcePack pack() {
        return new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "test"));
    }

    private static BlockDefinition cubeAll(String namespace, String name) {
        return BlockDefinition.builder(Key.key(namespace, name))
                .model(new BlockModelDefinition.CubeAll(Key.key(namespace, "block/" + name)))
                .build();
    }

    @Test
    void cubeAllEmitsModelItemDefinitionAndBlockState() throws IOException {
        ResourcePack pack = pack();
        BlockStateAllocator allocator = new BlockStateAllocator(BlockCarrier.NOTE_BLOCK);

        JavaBlockCompiler.compileBlocks(pack, List.of(new StubBlock(cubeAll("testpack", "ruby_block"))), allocator);

        JsonObject model = json(pack, "assets/testpack/models/block/ruby_block.json");
        assertEquals("minecraft:block/cube_all", model.get("parent").getAsString());
        assertEquals("testpack:block/ruby_block", model.getAsJsonObject("textures").get("all").getAsString());

        // The held item should look like the block.
        JsonObject item = json(pack, "assets/testpack/items/ruby_block.json");
        assertEquals("testpack:block/ruby_block",
                item.getAsJsonObject("model").get("model").getAsString());

        assertNotNull(pack.file(JavaBlockCompiler.NOTE_BLOCK_STATES_PATH));
    }

    @Test
    void noteBlockStatesEnumeratesEveryState() throws IOException {
        ResourcePack pack = pack();
        BlockStateAllocator allocator = new BlockStateAllocator(BlockCarrier.NOTE_BLOCK);

        JavaBlockCompiler.compileBlocks(pack, List.of(new StubBlock(cubeAll("testpack", "ruby_block"))), allocator);

        JsonObject variants = json(pack, JavaBlockCompiler.NOTE_BLOCK_STATES_PATH).getAsJsonObject("variants");

        // Every state must appear: one the client is not told about renders as missing
        // texture, and a note block is a block players can already place themselves.
        assertEquals(BlockCarrier.NOTE_BLOCK.stateCount(), variants.size());

        long custom = variants.entrySet().stream()
                .filter(e -> !e.getValue().getAsJsonObject().get("model").getAsString()
                        .equals("minecraft:block/note_block"))
                .count();
        assertEquals(1, custom, "exactly the one allocated state should point at a custom model");
    }

    @Test
    void separateCompilePassesDoNotClobberEachOther() throws IOException {
        // Blocks and furniture are separate content types that share one carrier, so they
        // compile in separate passes but write the same note_block.json. Without merging,
        // whichever ran second erased the other's states and every one of those blocks
        // rendered as a plain note block.
        ResourcePack pack = pack();
        BlockStateAllocator allocator = new BlockStateAllocator(BlockCarrier.NOTE_BLOCK);

        JavaBlockCompiler.compileBlocks(pack, List.of(new StubBlock(cubeAll("testpack", "ruby_block"))), allocator);
        JavaBlockCompiler.compileBlocks(pack, List.of(new StubBlock(cubeAll("testpack", "oak_chair"))), allocator);

        JsonObject variants = json(pack, JavaBlockCompiler.NOTE_BLOCK_STATES_PATH).getAsJsonObject("variants");

        long custom = variants.entrySet().stream()
                .filter(e -> !e.getValue().getAsJsonObject().get("model").getAsString()
                        .equals("minecraft:block/note_block"))
                .count();
        assertEquals(2, custom, "both passes' blocks should survive in the shared block state file");
    }

    @Test
    void customModelIsReferencedButNotEmitted() {
        ResourcePack pack = pack();
        BlockStateAllocator allocator = new BlockStateAllocator(BlockCarrier.NOTE_BLOCK);

        BlockDefinition definition = BlockDefinition.builder(Key.key("testpack", "chair"))
                .model(new BlockModelDefinition.Custom(Key.key("testpack", "block/chair"), Map.of()))
                .build();
        JavaBlockCompiler.compileBlocks(pack, List.of(new StubBlock(definition)), allocator);

        // Ships in the pack's assets/ and is copied verbatim.
        assertTrue(pack.files().containsKey("assets/testpack/items/chair.json"));
        assertEquals(null, pack.file("assets/testpack/models/block/chair.json"));
    }

    @Test
    void virtualBlocksDoNotConsumeAStateButStillGetAnItemDefinition() throws IOException {
        ResourcePack pack = pack();
        BlockStateAllocator allocator = new BlockStateAllocator();
        BlockDefinition definition = BlockDefinition.builder(Key.key("testpack", "unlimited"))
                .model(new BlockModelDefinition.CubeAll(Key.key("testpack", "block/unlimited")))
                .java(JavaBlockOptions.virtual())
                .build();

        JavaBlockCompiler.compileBlocks(pack, List.of(new StubBlock(definition)), allocator);

        assertNull(allocator.assignmentOf(definition.key()),
                "virtual content must not consume a finite native state");
        assertNotNull(pack.file("assets/testpack/items/unlimited.json"));
        assertNotNull(pack.file("assets/testpack/models/block/unlimited.json"));
        assertNull(pack.file(JavaBlockCompiler.NOTE_BLOCK_STATES_PATH),
                "a virtual-only pass should not generate a carrier blockstates file");
    }

    @Test
    void switchingAnExistingNativeKeyToVirtualKeepsItsLegacyStateRenderable() throws IOException {
        ResourcePack pack = pack();
        BlockStateAllocator allocator = new BlockStateAllocator();
        Key key = Key.key("testpack", "legacy");
        allocator.allocate(key, BlockCarrier.NOTE_BLOCK);
        BlockDefinition definition = BlockDefinition.builder(key)
                .model(new BlockModelDefinition.CubeAll(Key.key("testpack", "block/legacy")))
                .java(JavaBlockOptions.virtual())
                .build();

        JavaBlockCompiler.compileBlocks(pack, List.of(new StubBlock(definition)), allocator);

        JsonObject variants = json(pack, JavaBlockCompiler.NOTE_BLOCK_STATES_PATH)
                .getAsJsonObject("variants");
        String customModel = variants.entrySet().stream()
                .map(entry -> entry.getValue().getAsJsonObject().get("model").getAsString())
                .filter(model -> model.equals("testpack:block/legacy"))
                .findFirst()
                .orElse(null);
        assertEquals("testpack:block/legacy", customModel);
    }

    @Test
    void blockWithoutAModelIsRejectedRatherThanRenderedMissing() {
        // Items can fall back to their base material's look; a block cannot.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> BlockDefinition.builder(Key.key("testpack", "no_model")).build());
    }

    private static JsonObject json(ResourcePack pack, String path) throws IOException {
        Writable content = pack.file(path);
        assertNotNull(content, "missing pack file: " + path);
        return JsonParser.parseString(new String(content.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** Avoids BlockImpl, which builds a Bukkit ItemStack. */
    private static class StubBlock implements Block {
        private final BlockDefinition definition;
        private final FeatureEventBus eventBus = new FeatureEventBusImpl();

        StubBlock(BlockDefinition definition) {
            this.definition = definition;
        }

        @Override
        public @NotNull BlockDefinition definition() {
            return definition;
        }

        @Override
        public @NotNull ImmutableItemStack itemStack() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull java.util.Collection<Feature> features() {
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
    }

    @Test
    void javaBlockOptionsIsTheOnlyPlaceTheCarrierAppears() {
        BlockDefinition definition = BlockDefinition.builder(Key.key("testpack", "x"))
                .model(new BlockModelDefinition.CubeAll(Key.key("testpack", "block/x")))
                .behaviour(new BlockBehaviour(3.0f, true))
                .java(new JavaBlockOptions(BlockCarrier.NOTE_BLOCK))
                .bedrock(new BedrockOptions(true, null))
                .display(DisplayProperties.empty())
                .build();

        assertEquals(BlockCarrier.NOTE_BLOCK, definition.java().carrier());
        assertTrue(definition.bedrock().enabled());
        assertEquals("block.testpack.x", definition.translationKey());
    }
}

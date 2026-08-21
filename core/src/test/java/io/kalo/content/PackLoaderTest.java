package io.kalo.content;

import io.kalo.TestKalo;
import io.kalo.content.glyph.GlyphType;
import io.kalo.content.recipe.RecipeType;
import io.kalo.content.sound.SoundType;
import io.kalo.manager.RegistryManagerImpl;
import io.kalo.registry.Registries;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PackLoaderTest {

    private RegistryManagerImpl manager;
    private RecordingType type;

    @BeforeEach
    void installRegistries() {
        manager = TestKalo.install();
        type = new RecordingType();
        manager.registries().types().register(Key.key("example", "widget"), type);
    }

    @AfterEach
    void clearSingleton() {
        TestKalo.uninstall();
    }

    @Test
    void malformedEntriesAndOneThrowingAddonDoNotDiscardTheRestOfThePack(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pack.yml"), """
                id: testpack
                version: '1'
                author: Tester
                """);
        Path configs = Files.createDirectory(dir.resolve("configs"));
        Files.writeString(configs.resolve("content.yml"), """
                scalar: not-a-section
                broken:
                  type: example:widget
                  throw: true
                good:
                  type: example:widget
                """);
        Files.writeString(configs.resolve("ignored.txt"), """
                also_ignored:
                  type: example:widget
                """);

        ContentsPack pack = PackLoader.loadPack(dir.toFile(), Logger.getAnonymousLogger());

        assertNotNull(pack);
        assertEquals(List.of("good"), type.loaded);
    }

    @Test
    void rejectedDuplicateIdNeverInvokesRegistrylessContentTypes(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pack.yml"), """
                id: duplicate
                version: '1'
                author: Tester
                """);
        Path configs = Files.createDirectory(dir.resolve("configs"));
        Files.writeString(configs.resolve("content.yml"), """
                unique_recipe:
                  type: recipe
                  result: minecraft:paper
                  ingredients:
                    a: minecraft:stick
                unique_sound:
                  type: sound
                  sounds: [ambient/unique]
                unique_glyph:
                  type: glyph
                  texture: font/unique
                  character: U+E123
                """);

        ContentsPack pack = PackLoader.loadPack(
                dir.toFile(), Logger.getAnonymousLogger(), ignored -> false);

        assertNull(pack);
        assertEquals(0, builtin(RecipeType.KEY, RecipeType.class).size());
        assertEquals(0, builtin(SoundType.KEY, SoundType.class).size());
        assertEquals(0, builtin(GlyphType.KEY, GlyphType.class).size());
    }

    private <T> T builtin(Key key, Class<T> type) {
        return type.cast(manager.registries().types().get(key).orElseThrow());
    }

    private static final class RecordingType implements ContentType<Content> {
        private final List<String> loaded = new ArrayList<>();

        @Override
        public String id() {
            return "widget";
        }

        @Override
        public Class<Content> clazz() {
            return Content.class;
        }

        @Override
        public boolean load(PackContext pack, Registries registries, ConfigurationSection config) {
            if (config.getBoolean("throw")) {
                throw new IllegalStateException("deliberate test failure");
            }
            loaded.add(config.getName());
            return true;
        }

        @Override
        public Iterable<Content> contents(Registries registries) {
            return List.of();
        }
    }
}

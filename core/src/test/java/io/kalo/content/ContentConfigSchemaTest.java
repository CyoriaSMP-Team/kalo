package io.kalo.content;

import io.kalo.TestKalo;
import io.kalo.config.ConfigSchema;
import io.kalo.manager.RegistryManagerImpl;
import io.kalo.pack.ResourcePack;
import io.kalo.registry.Registries;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentConfigSchemaTest {

    private RegistryManagerImpl manager;

    @BeforeEach
    void installRegistries() {
        manager = TestKalo.install();
        manager.registries().types().register(Key.key("example", "widget"), new NoopType());
    }

    @AfterEach
    void clearSingleton() {
        TestKalo.uninstall();
    }

    @Test
    void qualifiedAddonTypeUsesTheNamespaceItRegistered() throws Exception {
        ConfigSchema.Result result = validate("""
                entry:
                  type: example:widget
                """);

        assertTrue(result.isSuccess(), () -> String.join(", ", result.getErrors()));
    }

    @Test
    void unqualifiedBuiltinTypeStillDefaultsToKalo() throws Exception {
        ConfigSchema.Result result = validate("""
                entry:
                  type: item
                """);

        assertTrue(result.isSuccess(), () -> String.join(", ", result.getErrors()));
    }

    @Test
    void scalarFeatureEntryIsAValidationErrorInsteadOfAnException() throws Exception {
        ConfigSchema.Result result = validate("""
                entry:
                  type: item
                  features:
                    broken: not-a-section
                """);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("must be a configuration section")));
    }

    @Test
    void namespacedContentNameIsRejectedBecauseThePackOwnsItsNamespace() throws Exception {
        ConfigSchema.Result result = validate("""
                'other:entry':
                  type: item
                """);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("Invalid content key")));
    }

    @Test
    void malformedQualifiedTypeIsReportedInsteadOfEscapingValidation() throws Exception {
        ConfigSchema.Result result = validate("""
                entry:
                  type: 'Bad Namespace:widget'
                """);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("Invalid type id")));
    }

    private static ConfigSchema.Result validate(String yaml) throws Exception {
        YamlConfiguration root = new YamlConfiguration();
        root.loadFromString(yaml);
        ConfigurationSection section = root.getConfigurationSection(root.getKeys(false).iterator().next());
        return new ContentConfigSchema().validate(section);
    }

    private static final class NoopType implements ContentType<Content> {
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
            return true;
        }

        @Override
        public Iterable<Content> contents(Registries registries) {
            return List.of();
        }

        @Override
        public void compilePack(ResourcePack resourcePack, Iterable<Content> contents) {
        }
    }
}

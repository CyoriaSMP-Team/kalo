package io.kalo.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Detection is the part most likely to go wrong: these formats overlap, and misreading a
 * file produces output that looks converted but is not. Each vendor gets a sample here.
 */
class ImportersTest {

    private static YamlConfiguration yaml(String content) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(content);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return config;
    }

    private static String detected(String content) {
        Importer importer = Importers.detect(yaml(content));
        assertNotNull(importer, "nothing recognised:\n" + content);
        return importer.name();
    }

    @Test
    void itemsAdderIsRecognisedByItsNamespaceDeclaration() {
        assertEquals("ItemsAdder", detected("""
                info:
                  namespace: mypack
                items:
                  ruby:
                    resource:
                      material: PAPER
                """));
    }

    @Test
    void oraxenIsRecognisedByItsPackSection() {
        assertEquals("Oraxen", detected("""
                ruby_sword:
                  material: PAPER
                  Pack:
                    textures: [item/ruby.png]
                """));
    }

    @Test
    void nekoIsRecognisedByItsPropertiesSection() {
        assertEquals("Neko", detected("""
                ruby_sword:
                  type: item
                  properties:
                    type: NETHERITE_SWORD
                    name: "<red>Ruby</red>"
                """));
    }

    @Test
    void craftEngineIsRecognisedByNamespacedKeysUnderAKind() {
        assertEquals("CraftEngine", detected("""
                items:
                  default:ruby_sword:
                    material: paper
                    data:
                      item-model: default:ruby_sword
                """));
    }

    @Test
    void anUnrecognisableFileIsRefusedRatherThanGuessedAt() {
        // Producing plausible nonsense from an unknown format is worse than saying no.
        assertNull(Importers.detect(yaml("just: a value\nanother: one\n")));
    }

    @Test
    void everyShippedImporterHasAName() {
        assertTrue(Importers.all().size() >= 4, Importers.all().toString());
        Importers.all().forEach(importer -> assertTrue(!importer.name().isBlank()));
    }

    @Test
    void nekoItemsConvertIncludingTheFeatureNamespaceChange() {
        ImportReport report = new ImportReport();
        YamlConfiguration out = yaml(new NekoImporter().convert(yaml("""
                ruby_sword:
                  type: item
                  properties:
                    type: NETHERITE_SWORD
                    name: "<red>Ruby</red>"
                    lore:
                      - "line"
                  features:
                    hello:
                      id: "neko:hello_world"
                      arguments:
                        msg: "hi"
                """), "mypack", report));

        assertEquals("item", out.getString("ruby_sword.type"));
        assertEquals("<red>Ruby</red>", out.getString("ruby_sword.display.name"));
        assertEquals("NETHERITE_SWORD", out.getString("ruby_sword.java.base_material"));
        // neko: becomes kalo:, since the fork kept the feature but not the namespace.
        assertEquals("kalo:hello_world", out.getString("ruby_sword.features.hello.id"));
        assertEquals("hi", out.getString("ruby_sword.features.hello.arguments.msg"));

        // Neko had no model support, so an imported item has no appearance of its own.
        assertTrue(report.unsupported().stream().anyMatch(u -> u.contains("no model")),
                report.unsupported().toString());
    }

    @Test
    void unsupportedNekoTypesAreNotReportedAsImported() {
        ImportReport report = new ImportReport();
        YamlConfiguration out = yaml(new NekoImporter().convert(yaml("""
                custom_block:
                  type: block
                  properties:
                    type: STONE
                """), "mypack", report));

        assertTrue(report.imported().isEmpty(), report.imported().toString());
        assertTrue(report.unsupported().stream().anyMatch(path -> path.contains("custom_block.type")),
                report.unsupported().toString());
        assertNull(out.get("custom_block"));
    }

    @Test
    void craftEngineStripsItsOwnNamespaceFromTheKey() {
        // The pack being imported into supplies the namespace; keeping CraftEngine's
        // would produce mypack:default:ruby_sword.
        ImportReport report = new ImportReport();
        YamlConfiguration out = yaml(new CraftEngineImporter().convert(yaml("""
                items:
                  default:ruby_sword:
                    material: paper
                    data:
                      item-model: default:ruby_sword
                      display-name: "Ruby Sword"
                """), "mypack", report));

        assertEquals("item", out.getString("ruby_sword.type"));
        assertEquals("Ruby Sword", out.getString("ruby_sword.display.name"));
        assertEquals("ruby_sword", out.getString("ruby_sword.model.sprite"));
        assertEquals("PAPER", out.getString("ruby_sword.java.base_material"));
    }

    @Test
    void craftEngineBlocksRaiseTheWorldMigrationWarning() {
        ImportReport report = new ImportReport();
        new CraftEngineImporter().convert(yaml("""
                blocks:
                  default:ruby_block:
                    data:
                      item-model: default:ruby_block
                """), "mypack", report);

        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("NOT migrated")),
                report.warnings().toString());
    }
}

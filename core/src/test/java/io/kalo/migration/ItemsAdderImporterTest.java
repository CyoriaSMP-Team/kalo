package io.kalo.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemsAdderImporterTest {

    private static YamlConfiguration source(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return config;
    }

    private static YamlConfiguration convert(String yaml, ImportReport report) {
        return source(ItemsAdderImporter.convert(source(yaml), report));
    }

    @Test
    void anItemUnderTheItemsMapConvertsCleanly() {
        ImportReport report = new ImportReport();
        YamlConfiguration out = convert("""
                info:
                  namespace: mypack
                items:
                  ruby_sword:
                    display_name: "Ruby Sword"
                    resource:
                      material: NETHERITE_SWORD
                      generate: true
                      textures:
                        - item/ruby_sword.png
                    durability:
                      max_custom_durability: 250
                """, report);

        assertEquals("item", out.getString("ruby_sword.type"));
        assertEquals("Ruby Sword", out.getString("ruby_sword.display.name"));
        assertEquals("item/ruby_sword", out.getString("ruby_sword.model.sprite"));
        // ItemsAdder nests material inside resource, unlike Oraxen.
        assertEquals("NETHERITE_SWORD", out.getString("ruby_sword.java.base_material"));
        assertEquals(250, out.getInt("ruby_sword.behaviour.durability"));

        assertEquals(List.of("mypack:ruby_sword"), report.imported());
    }

    @Test
    void theNamespaceComesFromTheFileNotTheFolder() {
        assertEquals("mypack", ItemsAdderImporter.namespaceOf(source("""
                info:
                  namespace: mypack
                items: {}
                """)));
        // Which is also how an ItemsAdder file is told apart from an Oraxen one.
        assertNull(ItemsAdderImporter.namespaceOf(source("ruby_sword:\n  material: PAPER\n")));
    }

    @Test
    void recognisesItsOwnFormat() {
        assertTrue(ItemsAdderImporter.looksLikeItemsAdder(source("info:\n  namespace: x\nitems: {}\n")));
        assertFalse(ItemsAdderImporter.looksLikeItemsAdder(source("ruby_sword:\n  material: PAPER\n")));
    }

    @Test
    void behavioursAreReportedRatherThanGuessedAt() {
        ImportReport report = new ImportReport();
        convert("""
                info:
                  namespace: mypack
                items:
                  ruby_sword:
                    resource:
                      material: PAPER
                    behaviours:
                      wings: {}
                """, report);

        assertTrue(report.unsupported().stream().anyMatch(p -> p.contains("behaviours")),
                report.unsupported().toString());
    }

    @Test
    void nonItemSectionsAreCalledOutSoAnEmptyResultIsNotMistakenForNothingToDo() {
        // Someone importing a file full of blocks should not conclude they had none.
        ImportReport report = new ImportReport();
        convert("""
                info:
                  namespace: mypack
                items: {}
                blocks:
                  ruby_block: {}
                  emerald_block: {}
                """, report);

        assertTrue(report.unsupported().stream().anyMatch(p -> p.startsWith("blocks (2")),
                report.unsupported().toString());
    }

    @Test
    void aModelPathBecomesACustomModel() {
        ImportReport report = new ImportReport();
        YamlConfiguration out = convert("""
                info:
                  namespace: mypack
                items:
                  chair:
                    resource:
                      material: PAPER
                      model_path: block/chair
                """, report);

        assertEquals("block/chair", out.getString("chair.model.custom"));
    }

    @Test
    void aFileWithNoItemsSectionSaysSoRatherThanSilentlyDoingNothing() {
        ImportReport report = new ImportReport();
        convert("info:\n  namespace: mypack\n", report);

        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("nothing to import")),
                report.warnings().toString());
    }

    @Test
    void aBadMaterialFailsThatItemAlone() {
        ImportReport report = new ImportReport();
        YamlConfiguration out = convert("""
                info:
                  namespace: mypack
                items:
                  broken:
                    resource:
                      material: NOPE
                  good:
                    resource:
                      material: PAPER
                """, report);

        assertEquals(1, report.failed().size());
        assertEquals("item", out.getString("good.type"));
    }
}

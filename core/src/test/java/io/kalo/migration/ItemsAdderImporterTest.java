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
    void blocksInTheirOwnSectionAreImported() {
        ImportReport report = new ImportReport();
        YamlConfiguration out = convert("""
                info:
                  namespace: mypack
                blocks:
                  ruby_block:
                    display_name: "Ruby Block"
                    resource:
                      textures:
                        - block/ruby_block.png
                    specific_properties:
                      block:
                        hardness: 3
                """, report);

        assertEquals("block", out.getString("ruby_block.type"));
        assertEquals("block/ruby_block", out.getString("ruby_block.model.cube_all"));
        assertEquals(3.0, out.getDouble("ruby_block.behaviour.hardness"), 1e-9);
    }

    @Test
    void aBlocksOnlyFileStillImports() {
        // Regression: bailing out when there is no items section imported nothing from a
        // perfectly normal blocks-only file.
        ImportReport report = new ImportReport();
        YamlConfiguration out = convert("""
                info:
                  namespace: mypack
                blocks:
                  ruby_block:
                    resource:
                      textures: [block/ruby_block.png]
                """, report);

        assertEquals("block", out.getString("ruby_block.type"));
        assertEquals(1, report.imported().size());
    }

    @Test
    void importingBlocksWarnsThatThePlacedWorldIsNotMigrated() {
        ImportReport report = new ImportReport();
        convert("""
                info:
                  namespace: mypack
                blocks:
                  ruby_block:
                    resource:
                      textures: [block/ruby_block.png]
                """, report);

        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("NOT migrated")),
                report.warnings().toString());
    }

    @Test
    void furnitureConvertsAndReportsWhatBlockBackedFurnitureCannotDo() {
        ImportReport report = new ImportReport();
        YamlConfiguration out = convert("""
                info:
                  namespace: mypack
                furniture:
                  oak_chair:
                    display_name: "Oak Chair"
                    resource:
                      model_path: block/oak_chair
                    entity: ITEM_DISPLAY
                    hitbox:
                      width: 1
                    sit: true
                """, report);

        assertEquals("furniture", out.getString("oak_chair.type"));

        String unsupported = report.unsupported().toString();
        assertTrue(unsupported.contains("entity"), unsupported);
        assertTrue(unsupported.contains("hitbox"), unsupported);
        assertTrue(unsupported.contains("sit"), unsupported);

        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("static block")),
                report.warnings().toString());
    }

    @Test
    void nonItemSectionsAreCalledOutSoAnEmptyResultIsNotMistakenForNothingToDo() {
        // Someone importing a file full of blocks should not conclude they had none.
        ImportReport report = new ImportReport();
        convert("""
                info:
                  namespace: mypack
                items: {}
                entities:
                  a: {}
                  b: {}
                """, report);

        assertTrue(report.unsupported().stream().anyMatch(p -> p.startsWith("entities (2")),
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

    @Test
    void aBlockOnlyMaterialCannotBecomeAnItemBaseMaterial() {
        ImportReport report = new ImportReport();
        YamlConfiguration out = convert("""
                info:
                  namespace: mypack
                items:
                  water_item:
                    resource:
                      material: WATER
                """, report);

        assertEquals(1, report.failed().size());
        assertTrue(report.failed().getFirst().contains("not an obtainable item"),
                report.failed().toString());
        assertNull(out.get("water_item"));
    }

    @Test
    void generateFalseRequiresTheHandAuthoredModelItPromises() {
        ImportReport report = new ImportReport();
        YamlConfiguration out = convert("""
                info:
                  namespace: mypack
                items:
                  broken:
                    resource:
                      material: PAPER
                      generate: false
                      textures: [item/broken.png]
                """, report);

        assertEquals(1, report.failed().size());
        assertTrue(report.failed().getFirst().contains("model_path"), report.failed().toString());
        assertNull(out.get("broken"));
    }

    @Test
    void knownButUnconvertedItemAndResourceOptionsAreReported() {
        ImportReport report = new ImportReport();
        convert("""
                info:
                  namespace: mypack
                items:
                  ruby:
                    permission: mypack.ruby
                    enable_light: true
                    specific_properties:
                      trim: true
                    resource:
                      material: PAPER
                      generate: true
                      textures: [item/ruby.png]
                      model_id: 42
                """, report);

        String unsupported = report.unsupported().toString();
        assertTrue(unsupported.contains("ruby.permission"), unsupported);
        assertTrue(unsupported.contains("ruby.enable_light"), unsupported);
        assertTrue(unsupported.contains("ruby.specific_properties"), unsupported);
        assertTrue(unsupported.contains("ruby.resource.model_id"), unsupported);
    }

    @Test
    void unsupportedBlockPropertiesAreReportedInsteadOfDisappearing() {
        ImportReport report = new ImportReport();
        convert("""
                info:
                  namespace: mypack
                blocks:
                  ruby_block:
                    resource:
                      textures: [block/ruby.png]
                    specific_properties:
                      block:
                        hardness: 3
                        light_level: 12
                        break_tools_whitelist: [PICKAXE]
                """, report);

        String unsupported = report.unsupported().toString();
        assertTrue(unsupported.contains("block.light_level"), unsupported);
        assertTrue(unsupported.contains("block.break_tools_whitelist"), unsupported);
    }
}

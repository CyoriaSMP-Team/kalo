package io.kalo.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These pin the importer's own assumptions about the Oraxen/Nexo format, which was
 * written from the documented shape rather than from a corpus of real packs. They are
 * therefore a regression guard, not proof the format is right — the report is what makes
 * a mismatch visible to whoever runs the import.
 */
class OraxenImporterTest {

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
        return source(OraxenImporter.convert(source(yaml), "mypack", report));
    }

    @Test
    void aSpriteItemMapsAcrossCleanly() {
        ImportReport report = new ImportReport();
        YamlConfiguration out = convert("""
                ruby_sword:
                  displayname: "<red>Ruby Sword</red>"
                  material: NETHERITE_SWORD
                  Pack:
                    generate_model: true
                    textures:
                      - item/ruby_sword.png
                  durability:
                    value: 250
                """, report);

        assertEquals("item", out.getString("ruby_sword.type"));
        assertEquals("<red>Ruby Sword</red>", out.getString("ruby_sword.display.name"));
        // The .png goes: Kalo keys are texture keys, not file paths.
        assertEquals("item/ruby_sword", out.getString("ruby_sword.model.sprite"));
        assertEquals("NETHERITE_SWORD", out.getString("ruby_sword.java.base_material"));
        assertEquals(250, out.getInt("ruby_sword.behaviour.durability"));

        assertEquals(1, report.imported().size());
        assertFalse(report.hasProblems());
    }

    @Test
    void mechanicsAreReportedRatherThanGuessedAt() {
        // Oraxen's behaviour system has no mechanical equivalent in Kalo's features, so a
        // mapping would be invention. Dropping it silently is how a server owner finds
        // out from their players.
        ImportReport report = new ImportReport();
        convert("""
                ruby_sword:
                  material: PAPER
                  Mechanics:
                    durability:
                      value: 500
                """, report);

        assertTrue(report.unsupported().stream().anyMatch(path -> path.contains("Mechanics")),
                report.unsupported().toString());
        assertTrue(report.hasProblems());
    }

    @Test
    void anUnrecognisedKeyIsReportedSoNewerFormatsAreVisible() {
        ImportReport report = new ImportReport();
        convert("""
                ruby_sword:
                  material: PAPER
                  some_future_option: true
                """, report);

        assertTrue(report.unsupported().contains("ruby_sword.some_future_option"),
                report.unsupported().toString());
    }

    @Test
    void extraTextureLayersAreCalledOutBecauseOnlyTheFirstSurvives() {
        ImportReport report = new ImportReport();
        YamlConfiguration out = convert("""
                tinted:
                  material: LEATHER_HELMET
                  Pack:
                    textures:
                      - item/base.png
                      - item/overlay.png
                """, report);

        assertEquals("item/base", out.getString("tinted.model.sprite"));
        assertTrue(report.unsupported().stream().anyMatch(path -> path.contains("textures")),
                report.unsupported().toString());
    }

    @Test
    void aHandAuthoredModelBecomesACustomModel() {
        ImportReport report = new ImportReport();
        YamlConfiguration out = convert("""
                chair:
                  material: PAPER
                  Pack:
                    model: block/chair
                """, report);

        assertEquals("block/chair", out.getString("chair.model.custom"));
    }

    @Test
    void legacyColourCodesAreFlaggedNotHalfTranslated() {
        // Kalo is MiniMessage only; a partial translation would produce visibly wrong text.
        ImportReport report = new ImportReport();
        convert("""
                old:
                  material: PAPER
                  displayname: "&cRuby Sword"
                """, report);

        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("legacy")),
                report.warnings().toString());
    }

    @Test
    void aBadMaterialFailsThatItemAndNotTheWholeImport() {
        ImportReport report = new ImportReport();
        YamlConfiguration out = convert("""
                broken:
                  material: NOT_A_REAL_MATERIAL
                good:
                  material: PAPER
                """, report);

        assertEquals(1, report.failed().size());
        assertTrue(report.failed().get(0).contains("NOT_A_REAL_MATERIAL"), report.failed().toString());
        assertEquals("item", out.getString("good.type"), "the rest of the file should still import");
    }

    @Test
    void loreCarriesOver() {
        ImportReport report = new ImportReport();
        YamlConfiguration out = convert("""
                ruby:
                  material: PAPER
                  lore:
                    - "line one"
                    - "line two"
                """, report);

        assertEquals(2, out.getStringList("ruby.display.lore").size());
    }

    @Test
    void anItemCarryingTheNoteblockMechanicBecomesABlock() {
        // Oraxen has no separate block section: a custom block is an item with a mechanic.
        ImportReport report = new ImportReport();
        YamlConfiguration out = convert("""
                ruby_block:
                  displayname: "<red>Ruby Block</red>"
                  material: PAPER
                  Pack:
                    textures:
                      - block/ruby_block.png
                  Mechanics:
                    noteblock:
                      custom_variation: 37
                      hardness: 3
                """, report);

        assertEquals("block", out.getString("ruby_block.type"));
        assertEquals("block/ruby_block", out.getString("ruby_block.model.cube_all"));
    }

    @Test
    void oraxensStateNumberIsDeliberatelyNotCarriedOver() {
        // Adopting it would collide with states Kalo has already handed out.
        ImportReport report = new ImportReport();
        convert("""
                ruby_block:
                  material: PAPER
                  Pack:
                    textures: [block/ruby_block.png]
                  Mechanics:
                    noteblock:
                      custom_variation: 37
                """, report);

        assertTrue(report.unsupported().stream().anyMatch(p -> p.contains("custom_variation")),
                report.unsupported().toString());
    }

    @Test
    void importingBlocksWarnsThatThePlacedWorldIsNotMigrated() {
        // The most expensive thing to discover after going live.
        ImportReport report = new ImportReport();
        convert("""
                ruby_block:
                  material: PAPER
                  Pack:
                    textures: [block/ruby_block.png]
                  Mechanics:
                    noteblock:
                      custom_variation: 37
                """, report);

        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("NOT migrated")),
                report.warnings().toString());
    }

    @Test
    void anItemOnlyImportDoesNotRaiseTheWorldWarning() {
        ImportReport report = new ImportReport();
        convert("ruby_sword:\n  material: PAPER\n", report);

        assertFalse(report.warnings().stream().anyMatch(w -> w.contains("NOT migrated")),
                report.warnings().toString());
    }

    @Test
    void furnitureConvertsButSaysWhatItLost() {
        // The shape and name come across; the behaviour does not. Producing something
        // that merely looks converted is the worst outcome here.
        ImportReport report = new ImportReport();
        YamlConfiguration out = convert("""
                oak_chair:
                  displayname: "Oak Chair"
                  material: PAPER
                  Pack:
                    model: block/oak_chair
                  Mechanics:
                    furniture:
                      type: DISPLAY_ENTITY
                      seat:
                        height: 0.5
                      hitbox:
                        width: 1
                      rotatable: true
                """, report);

        assertEquals("furniture", out.getString("oak_chair.type"));
        assertEquals("block/oak_chair", out.getString("oak_chair.model.custom"));

        String unsupported = report.unsupported().toString();
        assertTrue(unsupported.contains("seat"), unsupported);
        assertTrue(unsupported.contains("hitbox"), unsupported);
        assertTrue(unsupported.contains("rotatable"), unsupported);
        assertTrue(unsupported.contains("type"), unsupported);
    }

    @Test
    void furnitureRaisesBothTheStaticAndTheWorldWarnings() {
        ImportReport report = new ImportReport();
        convert("""
                oak_chair:
                  material: PAPER
                  Pack:
                    model: block/oak_chair
                  Mechanics:
                    furniture:
                      rotatable: true
                """, report);

        String warnings = report.warnings().toString();
        assertTrue(warnings.contains("static block"), warnings);
        assertTrue(warnings.contains("NOT migrated"), warnings);
    }

    @Test
    void texturePathsWithoutAnExtensionAreLeftAlone() {
        assertEquals("item/ruby", OraxenImporter.stripExtension("item/ruby"));
        assertEquals("item/ruby", OraxenImporter.stripExtension("item/ruby.png"));
        // A dot in a directory name must not be mistaken for an extension.
        assertEquals("my.pack/item/ruby", OraxenImporter.stripExtension("my.pack/item/ruby"));
    }

    @Test
    void theReportReadsAsAChecklist() {
        ImportReport report = new ImportReport();
        convert("""
                ruby:
                  material: PAPER
                  Mechanics:
                    x: 1
                """, report);

        assertTrue(report.lines().stream().anyMatch(line -> line.startsWith("Imported 1")),
                report.lines().toString());
        assertTrue(report.lines().stream().anyMatch(line -> line.contains("by hand")),
                report.lines().toString());
    }
}

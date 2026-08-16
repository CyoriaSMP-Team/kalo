package io.kalo.pack;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackValidatorTest {

    private static ResourcePack pack() {
        return new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "test"));
    }

    private static void addSpriteItem(ResourcePack pack, String name, String texture) {
        pack.file("assets/testpack/items/" + name + ".json",
                Writable.string("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"testpack:item/" + name + "\"}}"));
        pack.file("assets/testpack/models/item/" + name + ".json",
                Writable.string("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + texture + "\"}}"));
    }

    @Test
    void aCompletePackHasNoProblems() {
        ResourcePack pack = pack();
        addSpriteItem(pack, "ruby", "testpack:item/ruby");
        pack.file("assets/testpack/textures/item/ruby.png", Writable.bytes(new byte[]{1}));

        assertEquals(List.of(), PackValidator.validate(pack));
    }

    @Test
    void aMissingTextureIsNamedWithTheFileThatWantedIt() {
        // The whole point: a typo used to be invisible until a player saw a magenta cube.
        ResourcePack pack = pack();
        addSpriteItem(pack, "ruby", "testpack:item/ruby_typo");
        pack.file("assets/testpack/textures/item/ruby.png", Writable.bytes(new byte[]{1}));

        List<String> problems = PackValidator.validate(pack);
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("ruby_typo"), problems.get(0));
        assertTrue(problems.get(0).contains("models/item/ruby.json"), problems.get(0));
    }

    @Test
    void vanillaTexturesAreNotExpectedInThePack() {
        // They live in the client. Flagging them would bury real problems in noise.
        ResourcePack pack = pack();
        pack.file("assets/testpack/models/item/x.json",
                Writable.string("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/apple\"}}"));

        assertEquals(List.of(), PackValidator.validate(pack));
    }

    @Test
    void anItemDefinitionPointingAtAMissingModelIsCaught() {
        ResourcePack pack = pack();
        pack.file("assets/testpack/items/ghost.json",
                Writable.string("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"testpack:item/ghost\"}}"));

        List<String> problems = PackValidator.validate(pack);
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("testpack:item/ghost"), problems.get(0));
    }

    @Test
    void aBlockStateReferencingAMissingModelIsCaught() {
        ResourcePack pack = pack();
        pack.file("assets/minecraft/blockstates/note_block.json", Writable.string(
                "{\"variants\":{\"instrument=harp,note=0,powered=true\":{\"model\":\"testpack:block/gone\"}}}"));

        List<String> problems = PackValidator.validate(pack);
        assertTrue(problems.stream().anyMatch(p -> p.contains("testpack:block/gone")), problems.toString());
    }

    @Test
    void vanillaBlockModelsInABlockStateAreFine() {
        // The generated note_block.json points every unused state at the vanilla model.
        ResourcePack pack = pack();
        pack.file("assets/minecraft/blockstates/note_block.json", Writable.string(
                "{\"variants\":{\"instrument=harp,note=0,powered=false\":{\"model\":\"minecraft:block/note_block\"}}}"));

        assertEquals(List.of(), PackValidator.validate(pack));
    }

    @Test
    void equipmentTexturesAreLookedUpUnderTheirOwnRoot() {
        // Armor layers are not item textures and live somewhere else entirely.
        ResourcePack pack = pack();
        pack.file("assets/testpack/equipment/ruby_helmet.json",
                Writable.string("{\"layers\":{\"humanoid\":[{\"texture\":\"testpack:ruby\"}]}}"));

        List<String> missing = PackValidator.validate(pack);
        assertEquals(1, missing.size(), missing.toString());

        pack.file("assets/testpack/textures/entity/equipment/humanoid/ruby.png", Writable.bytes(new byte[]{1}));
        assertEquals(List.of(), PackValidator.validate(pack));
    }

    @Test
    void aLeggingsOnlyTextureSatisfiesTheReference() {
        ResourcePack pack = pack();
        pack.file("assets/testpack/equipment/ruby_leggings.json",
                Writable.string("{\"layers\":{\"humanoid_leggings\":[{\"texture\":\"testpack:ruby\"}]}}"));
        pack.file("assets/testpack/textures/entity/equipment/humanoid_leggings/ruby.png",
                Writable.bytes(new byte[]{1}));

        assertEquals(List.of(), PackValidator.validate(pack));
    }

    @Test
    void problemsAreReportedInAStableOrder() {
        // The same broken pack should report the same way every run, or diffing two runs
        // is useless.
        ResourcePack pack = pack();
        pack.file("assets/testpack/items/b.json",
                Writable.string("{\"model\":{\"model\":\"testpack:item/b\"}}"));
        pack.file("assets/testpack/items/a.json",
                Writable.string("{\"model\":{\"model\":\"testpack:item/a\"}}"));

        assertEquals(PackValidator.validate(pack), PackValidator.validate(pack));
        assertTrue(PackValidator.validate(pack).get(0).contains("items/a.json"));
    }

    @Test
    void nonJsonFilesAreLeftAlone() {
        ResourcePack pack = pack();
        pack.file("assets/testpack/textures/item/ruby.png", Writable.bytes(new byte[]{1, 2, 3}));

        assertEquals(List.of(), PackValidator.validate(pack));
    }
}

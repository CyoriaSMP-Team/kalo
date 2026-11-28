package io.kalo.model;

import io.kalo.pack.PackFormats;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRegistryTest {

    private static final Key SOURCE = Key.key("testpack", "models/item/cube.bbmodel");

    private static String fixture(String name) throws Exception {
        try (var stream = ModelRegistryTest.class.getResourceAsStream(
                "/blockbench/" + name + ".bbmodel")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @AfterEach
    void clearCache() {
        ModelRegistry.invalidateAll();
    }

    @Test
    void modelAssetPathsStayInsideThePack() {
        assertEquals("assets/testpack/models/item/cube.bbmodel",
                ModelRegistry.assetPath(SOURCE));
        assertThrows(IllegalArgumentException.class,
                () -> ModelRegistry.assetPath(Key.key("testpack", "../cube.bbmodel")));
        assertThrows(IllegalArgumentException.class,
                () -> ModelRegistry.assetPath(Key.key("testpack", "models/cube.json")));
    }

    @Test
    void aChangedFileInvalidatesTheCacheWithoutAServerRestart() throws Exception {
        String first = fixture("simple_cube");
        String second = first.replace("\"simple_cube\"", "\"renamed_cube\"");

        ModelIR one = ModelRegistry.load(first.getBytes(StandardCharsets.UTF_8), SOURCE, "a");
        ModelIR cached = ModelRegistry.load(first.getBytes(StandardCharsets.UTF_8), SOURCE, "b");
        ModelIR changed = ModelRegistry.load(second.getBytes(StandardCharsets.UTF_8), SOURCE, "c");

        assertEquals("simple_cube", one.name());
        assertTrue(one == cached, "identical bytes should reuse the parsed IR");
        assertEquals("renamed_cube", changed.name());
        assertNotEquals(ModelRegistry.cachedFingerprint(SOURCE), null);
    }

    @Test
    void missingModelsFailWithTheExpectedPackPath() {
        ResourcePack pack = new ResourcePackImpl(PackMeta.of(PackFormats.CURRENT, "test"));
        ModelImportException error = assertThrows(ModelImportException.class,
                () -> ModelRegistry.load(pack, SOURCE));
        assertTrue(error.getMessage().contains("assets/testpack/models/item/cube.bbmodel"));
    }

    @Test
    void loadTimePathReadsOnlyInsideThePackRoot(@TempDir Path temp) throws Exception {
        Path pack = Files.createDirectories(temp.resolve("pack"));
        Path model = pack.resolve("assets/testpack/models/item/cube.bbmodel");
        Files.createDirectories(model.getParent());
        Files.writeString(model, fixture("simple_cube"), StandardCharsets.UTF_8);

        ModelIR loaded = ModelRegistry.load(pack, SOURCE);
        assertEquals("simple_cube", loaded.name());

        Path traversal = pack.resolve("assets/testpack/models/item/../../../../escape.bbmodel")
                .normalize();
        assertTrue(traversal.startsWith(pack), "fixture itself stays inside the pack");
        assertThrows(IllegalArgumentException.class, () -> ModelRegistry.load(pack,
                Key.key("testpack", "models/../../escape.bbmodel")));
    }
}

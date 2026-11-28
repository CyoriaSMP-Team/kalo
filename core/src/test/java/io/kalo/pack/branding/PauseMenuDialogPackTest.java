package io.kalo.pack.branding;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PauseMenuDialogPackTest {
    private static PauseMenuDialogPack.Settings example() {
        return new PauseMenuDialogPack.Settings("CyoriaSMP", "เมนู CyoriaSMP",
                "ทางลัดเซิร์ฟเวอร์", 2, List.of(
                new PauseMenuDialogPack.Button("กลับจุดเกิด", "command", "/spawn"),
                new PauseMenuDialogPack.Button("ตลาด", "command", "/shop"),
                new PauseMenuDialogPack.Button("เว็บไซต์", "url", "https://www.cyoriasmp.com"),
                new PauseMenuDialogPack.Button("Discord", "url", "https://discord.cyoriasmp.com")));
    }

    @Test
    void generatesGenuineDialogAndPauseScreenTag(@TempDir Path world) throws Exception {
        assertTrue(PauseMenuDialogPack.install(world, example()));
        Path base = world.resolve("datapacks/kalo-pause-menu");
        assertTrue(Files.exists(base.resolve("pack.mcmeta")));
        JsonObject meta = json(base.resolve("pack.mcmeta"));
        assertEquals(94, meta.getAsJsonObject("pack").getAsJsonArray("min_format").get(0).getAsInt());
        assertEquals(1, meta.getAsJsonObject("pack").getAsJsonArray("min_format").get(1).getAsInt());
        JsonObject dialog = json(base.resolve("data/kalo/dialog/cyoria_menu.json"));
        assertEquals("minecraft:multi_action",dialog.get("type").getAsString());
        assertEquals("เมนู CyoriaSMP",dialog.get("external_title").getAsString());
        assertEquals(4,dialog.getAsJsonArray("actions").size());
        JsonObject first = dialog.getAsJsonArray("actions").get(0).getAsJsonObject();
        assertEquals("minecraft:run_command", first.getAsJsonObject("action").get("type").getAsString());
        assertEquals("/spawn",first.getAsJsonObject("action").get("command").getAsString());
        JsonObject last = dialog.getAsJsonArray("actions").get(3).getAsJsonObject();
        assertEquals("minecraft:open_url",last.getAsJsonObject("action").get("type").getAsString());
        assertEquals("https://discord.cyoriasmp.com",last.getAsJsonObject("action").get("url").getAsString());
        JsonObject tag=json(base.resolve("data/minecraft/tags/dialog/pause_screen_additions.json"));
        assertEquals("kalo:cyoria_menu",tag.getAsJsonArray("values").get(0).getAsString());
        assertFalse(tag.has("replace"));
        assertFalse(PauseMenuDialogPack.install(world, example()),"unchanged config should not rewrite datapacks");
        assertEquals(3, Files.walk(base).filter(Files::isRegularFile).count());
    }

    @Test
    void validatesYmlAndDoesNotAllowArbitraryCommands(@TempDir Path dir) throws Exception {
        var cfg = new YamlConfiguration();
        cfg.set("title", "CyoriaSMP");
        cfg.set("pause-button", "เมนู CyoriaSMP");
        cfg.set("description", "เมนูทางลัด");
        cfg.set("columns", 2);
        cfg.set("buttons", List.of(Map.of("label","ตลาด", "type","command", "value","/shop")));
        assertEquals("/shop", PauseMenuDialogPack.parse(cfg).buttons().get(0).value());
        cfg.set("buttons", List.of(Map.of("label","Danger", "type","command", "value","/op player")));
        assertThrows(IOException.class, () -> PauseMenuDialogPack.parse(cfg));
        cfg.set("buttons", List.of(Map.of("label","Danger", "type","url", "value","http://localhost")));
        assertThrows(IOException.class, () -> PauseMenuDialogPack.parse(cfg));
        cfg.set("buttons", List.of(Map.of("label","Safe", "type","url", "value","https://www.cyoriasmp.com")));
        assertEquals("url",PauseMenuDialogPack.parse(cfg).buttons().get(0).type());
    }

    @Test
    void doesNotModifyOtherDatapacks(@TempDir Path world) throws Exception {
        Path thirdParty = world.resolve("datapacks/terralith/data/terralith/dialog/existing.json");
        Files.createDirectories(thirdParty.getParent());
        Files.writeString(thirdParty,"unchanged");
        PauseMenuDialogPack.install(world,example());
        assertEquals("unchanged",Files.readString(thirdParty));
    }

    private static JsonObject json(Path file) throws Exception {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }
}

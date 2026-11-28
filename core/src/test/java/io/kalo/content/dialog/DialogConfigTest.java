package io.kalo.content.dialog;

import io.kalo.content.dialog.definition.DialogDefinition;
import io.kalo.content.PackContext;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogConfigTest {

    static YamlConfiguration config() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("title", "Welcome");
        config.set("body", "Choose an option");
        config.set("columns", 2);
        config.set("buttons", List.of(
                Map.of("label", "Spawn", "type", "command", "value", "/spawn"),
                Map.of("label", "Website", "type", "url",
                        "value", "https://example.com")));
        return config;
    }

    @Test
    void aDialogParsesItsTitleBodyAndButtons() {
        DialogDefinition definition = DialogType.parse(Key.key("testpack", "welcome"), config());

        assertEquals("Welcome", definition.title());
        assertEquals("Choose an option", definition.body());
        assertEquals(2, definition.columns());
        assertEquals(2, definition.buttons().size());
        assertEquals(DialogDefinition.ButtonType.COMMAND,
                definition.buttons().get(0).type());
        assertEquals("/spawn", definition.buttons().get(0).value());
        assertEquals(DialogDefinition.ButtonType.URL,
                definition.buttons().get(1).type());
    }

    @Test
    void unsafeOrEmptyDialogsAreRejected() {
        YamlConfiguration noButtons = config();
        noButtons.set("buttons", List.of());
        assertThrows(IllegalArgumentException.class,
                () -> DialogType.parse(Key.key("testpack", "welcome"), noButtons));

        YamlConfiguration badUrl = config();
        badUrl.set("buttons", List.of(Map.of(
                "label", "Bad", "type", "url", "value", "http://evil.example")));
        assertThrows(IllegalArgumentException.class,
                () -> DialogType.parse(Key.key("testpack", "welcome"), badUrl));

        YamlConfiguration badCommand = config();
        badCommand.set("buttons", List.of(Map.of(
                "label", "Bad", "type", "command", "value", "spawn\nsay hacked")));
        assertThrows(IllegalArgumentException.class,
                () -> DialogType.parse(Key.key("testpack", "welcome"), badCommand));
    }

    @Test
    void aDialogReadFromRealYamlHasTheSameButtons() {
        String yaml = """
                welcome:
                  type: dialog
                  title: Welcome
                  body:
                    - Choose an option
                    - Pick one
                  columns: 2
                  buttons:
                    - label: Spawn
                      type: command
                      value: "/spawn"
                    - label: Website
                      type: url
                      value: "https://example.com"
                """;
        YamlConfiguration root = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        ConfigurationSection welcome = root.getConfigurationSection("welcome");

        DialogDefinition definition = DialogType.parse(
                Key.key("testpack", "welcome"), welcome);

        assertEquals("Choose an option\nPick one", definition.body());
        assertEquals(2, definition.buttons().size());
        assertEquals(DialogDefinition.ButtonType.COMMAND,
                definition.buttons().get(0).type());
        assertEquals(DialogDefinition.ButtonType.URL,
                definition.buttons().get(1).type());
    }

    @Test
    void consoleMultiCommandsTooltipsAndAfterModesParse() {
        String yaml = """
                welcome:
                  type: dialog
                  title: Welcome
                  after: none
                  can-close-with-escape: false
                  buttons:
                    - label: Buy
                      console:
                        - "warp shop {player}"
                        - "give {player} bread"
                      tooltip: Open the shop
                      width: 220
                    - label: Spawn
                      command: spawn
                """;
        YamlConfiguration root = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        ConfigurationSection welcome = root.getConfigurationSection("welcome");

        DialogDefinition definition = DialogType.parse(
                Key.key("testpack", "welcome"), welcome);

        assertEquals(DialogDefinition.After.NONE, definition.after());
        assertFalse(definition.canCloseWithEscape());
        assertEquals(2, definition.buttons().get(0).actions().size());
        assertEquals(DialogDefinition.ActionType.CONSOLE,
                definition.buttons().get(0).actions().get(0).type());
        assertEquals("warp shop {player}",
                definition.buttons().get(0).actions().get(0).value());
        assertEquals("Open the shop", definition.buttons().get(0).tooltip());
        assertEquals(220, definition.buttons().get(0).width());
        assertEquals(1, definition.buttons().get(1).actions().size());
        assertEquals(2, definition.clickActions().size());
        definition.clickKeys().forEach(key ->
                assertTrue(key.matches("[a-z0-9]{1,32}"), key));
    }

    @Test
    void rawJsonIsImportedWithConsoleCommandKeys(@TempDir Path folder) throws Exception {
        Path dialogs = folder.resolve("dialogs");
        Files.createDirectories(dialogs);
        Files.writeString(dialogs.resolve("raw.json"), """
                {
                  "type": "minecraft:multi_action",
                  "title": {"text": "Raw Shop"},
                  "body": [{"type": "minecraft:plain_message", "contents": "Pick one"}],
                  "actions": [
                    {
                      "label": {"text": "Buy"},
                      "tooltip": "Buy it",
                      "action": {"type": "minecraft:run_command",
                                 "command": "/kalo click abc123"}
                    }
                  ],
                  "after_action": "none",
                  "can_close_with_escape": false,
                  "columns": 2
                }
                """);
        YamlConfiguration config = new YamlConfiguration();
        config.set("title", "Fallback title");
        config.set("json", "dialogs/raw.json");
        config.set("console-commands.abc123", "warp shop {player}");
        PackContext pack = new PackContext("testpack", folder.toFile());

        DialogDefinition definition = DialogType.parse(pack,
                Key.key("testpack", "raw"), config);

        assertNotNull(definition.raw());
        assertEquals("Raw Shop", definition.title());
        assertEquals("Pick one", definition.body());
        assertEquals(2, definition.columns());
        assertFalse(definition.canCloseWithEscape());
        assertEquals(DialogDefinition.After.NONE, definition.after());
        assertEquals("Buy", definition.buttons().get(0).label());
        assertEquals("Buy it", definition.buttons().get(0).tooltip());
        assertEquals("/kalo click abc123", definition.buttons().get(0).value());
        assertEquals(DialogDefinition.ActionType.CONSOLE,
                definition.clickActions().get("abc123").get(0).type());
        assertEquals("warp shop {player}",
                definition.clickActions().get("abc123").get(0).value());
    }

    @Test
    void loadingOneDialogRegistersItAndADuplicateKeepsTheFirst(@TempDir Path folder) {
        DialogType type = new DialogType();
        PackContext pack = new PackContext("testpack", folder.toFile());
        YamlConfiguration root = new YamlConfiguration();
        ConfigurationSection named = root.createSection("welcome");
        config().getValues(false).forEach(named::set);

        assertTrue(type.load(pack, null, named));
        assertEquals(1, type.size());
        assertEquals(List.of(Key.key("testpack", "welcome")),
                type.ordered().stream().map(DialogDefinition::key).toList());

        assertFalse(type.load(pack, null, named), "duplicate must be rejected");
        assertEquals(1, type.size());
    }
}

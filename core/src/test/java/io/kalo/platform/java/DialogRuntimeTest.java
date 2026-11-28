package io.kalo.platform.java;

import io.kalo.content.dialog.definition.DialogDefinition;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogRuntimeTest {

    @Test
    void theJavaDialogCommandTargetsThePlayerUuidNotTheGamertag() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0009-01f2c3d4e5f6");

        assertEquals("dialog show " + playerId + " testpack:welcome",
                DialogRuntime.javaCommand(playerId, Key.key("testpack", "welcome")));
    }

    @Test
    void placeholdersAreResolvedWithoutTouchingUnknownBraces() {
        assertEquals("Hello Steve, you are Alex",
                DialogRuntime.render("Hello {player}, you are {target}",
                        Map.of("player", "Steve", "target", "Alex")));
        assertEquals("Hello {unknown}", DialogRuntime.render("Hello {unknown}", Map.of()));
    }

    @Test
    void inlineCommandResolvesTitleBodyLabelsAndTooltips() throws Exception {
        UUID playerId = UUID.fromString("00000000-0000-0000-0009-01f2c3d4e5f6");
        DialogDefinition definition = new DialogDefinition(Key.key("testpack", "welcome"),
                "Hello {player}", "For {target}", 1, List.of(
                new DialogDefinition.Button("Kit for {player}",
                        DialogDefinition.ButtonType.COMMAND, "give {player} bread",
                        "For {target}", 150)));

        String command = DialogRuntime.inlineCommand(playerId, definition,
                Map.of("player", "Steve", "target", "Alex"));

        assertTrue(command != null && command.startsWith("dialog show " + playerId + " "),
                command);
        String json = command.substring(("dialog show " + playerId + " ").length());
        var dialog = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        assertEquals("Hello Steve", dialog.get("title").getAsString());
        assertEquals("For Alex", dialog.getAsJsonArray("body").get(0)
                .getAsJsonObject().get("contents").getAsString());
        assertEquals("Kit for Steve", dialog.getAsJsonArray("actions").get(0)
                .getAsJsonObject().get("label").getAsString());
        assertEquals("For Alex", dialog.getAsJsonArray("actions").get(0)
                .getAsJsonObject().get("tooltip").getAsString());

        Path artifacts = Path.of("build", "reports", "dialog");
        Files.createDirectories(artifacts);
        Files.writeString(artifacts.resolve("inline-command.txt"), command);
        Files.writeString(artifacts.resolve("resolved-dialog.json"), dialog.toString());
    }

    @Test
    void aDialogWithoutPlaceholdersKeepsUsingItsDataPackId() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0009-01f2c3d4e5f6");
        DialogDefinition definition = new DialogDefinition(Key.key("testpack", "welcome"),
                "Welcome", "Choose", 1, List.of(
                new DialogDefinition.Button("Close",
                        DialogDefinition.ButtonType.CLOSE, "")));

        assertNull(DialogRuntime.inlineCommand(playerId, definition, Map.of()));
    }

    @Test
    void clickKeysAreStableAndBoundToTheCommands() {        DialogDefinition definition = new DialogDefinition(Key.key("testpack", "shop"),
                "Shop", "", 1, true, DialogDefinition.After.NONE, List.of(
                new DialogDefinition.Button("Buy",
                        List.of(new DialogDefinition.Action(
                                DialogDefinition.ActionType.CONSOLE, "give {player} bread")),
                        "", 150),
                new DialogDefinition.Button("Spawn",
                        List.of(new DialogDefinition.Action(
                                DialogDefinition.ActionType.COMMAND, "spawn")),
                        "", 150)));

        String first = definition.clickKeys().get(0);
        assertEquals(first, definition.clickKeys().get(0));
        assertNotEquals(first, definition.clickKeys().get(1));
        assertTrue(first.matches("[a-z0-9]{16}"), first);
        assertEquals("give {player} bread",
                definition.clickActions().get(first).get(0).value());
        assertTrue(DialogRuntime.accepts(definition, first));
        assertTrue(DialogRuntime.accepts(definition, first.toUpperCase()));
        assertFalse(DialogRuntime.accepts(definition, "not-a-shown-key"));

        DialogDefinition changed = new DialogDefinition(Key.key("testpack", "shop"),
                "Shop", "", 1, true, DialogDefinition.After.NONE, List.of(
                new DialogDefinition.Button("Buy",
                        List.of(new DialogDefinition.Action(
                                DialogDefinition.ActionType.CONSOLE, "give {player} torch")),
                        "", 150),
                new DialogDefinition.Button("Spawn",
                        List.of(new DialogDefinition.Action(
                                DialogDefinition.ActionType.COMMAND, "spawn")),
                        "", 150)));
        assertNotEquals(first, changed.clickKeys().get(0));
    }
}

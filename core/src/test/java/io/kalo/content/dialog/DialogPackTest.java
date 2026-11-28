package io.kalo.content.dialog;

import com.google.gson.JsonObject;
import io.kalo.content.dialog.definition.DialogDefinition;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogPackTest {

    private static DialogDefinition definition() {
        return new DialogDefinition(Key.key("testpack", "welcome"),
                "Welcome", "Choose an option", 2, List.of(
                new DialogDefinition.Button("Spawn",
                        DialogDefinition.ButtonType.COMMAND, "/spawn"),
                new DialogDefinition.Button("Website",
                        DialogDefinition.ButtonType.URL, "https://example.com")));
    }

    @Test
    void javaDialogJsonContainsMultiActionsAndCommands() {
        DialogDefinition definition = definition();
        JsonObject dialog = DialogPack.javaDialog(definition);

        assertEquals("minecraft:multi_action", dialog.get("type").getAsString());
        assertEquals("Welcome", dialog.get("title").getAsString());
        assertEquals("none", dialog.get("after_action").getAsString());
        assertEquals(true, dialog.get("can_close_with_escape").getAsBoolean());
        assertEquals(2, dialog.getAsJsonArray("actions").size());
        JsonObject first = dialog.getAsJsonArray("actions").get(0).getAsJsonObject();
        assertEquals("Spawn", first.get("label").getAsString());
        assertEquals("minecraft:run_command",
                first.getAsJsonObject("action").get("type").getAsString());
        String key = definition.clickKeys().get(0);
        assertEquals("/kalo click " + key,
                first.getAsJsonObject("action").get("command").getAsString());
        assertEquals("/spawn",
                definition.clickActions().get(key).get(0).value());
        JsonObject second = dialog.getAsJsonArray("actions").get(1).getAsJsonObject();
        assertEquals("minecraft:open_url",
                second.getAsJsonObject("action").get("type").getAsString());
        assertEquals("https://example.com",
                second.getAsJsonObject("action").get("url").getAsString());
    }

    @Test
    void consoleAndMultiCommandButtonsUseOneAuthorizedKey() throws Exception {
        DialogDefinition definition = new DialogDefinition(Key.key("testpack", "shop"),
                "Shop", "Choose", 1, false, DialogDefinition.After.NONE, List.of(
                new DialogDefinition.Button("Buy",
                        List.of(new DialogDefinition.Action(
                                        DialogDefinition.ActionType.CONSOLE,
                                        "warp shop {player}"),
                                new DialogDefinition.Action(
                                        DialogDefinition.ActionType.CONSOLE,
                                        "give {player} bread")),
                        "Open the shop", 220),
                new DialogDefinition.Button("Kit",
                        List.of(new DialogDefinition.Action(
                                        DialogDefinition.ActionType.COMMAND, "spawn"),
                                new DialogDefinition.Action(
                                        DialogDefinition.ActionType.COMMAND, "say hi")),
                        "", 180)));

        JsonObject dialog = DialogPack.javaDialog(definition);
        assertEquals("none", dialog.get("after_action").getAsString());
        assertFalse(dialog.get("can_close_with_escape").getAsBoolean());

        String consoleKey = definition.clickKeys().get(0);
        String playerKey = definition.clickKeys().get(1);
        JsonObject first = dialog.getAsJsonArray("actions").get(0).getAsJsonObject();
        assertEquals("Open the shop", first.get("tooltip").getAsString());
        assertEquals(220, first.get("width").getAsInt());
        assertEquals("/kalo click " + consoleKey,
                first.getAsJsonObject("action").get("command").getAsString());
        assertEquals(List.of("warp shop {player}", "give {player} bread"),
                definition.clickActions().get(consoleKey).stream()
                        .map(DialogDefinition.Action::value).toList());
        assertEquals(List.of("spawn", "say hi"),
                definition.clickActions().get(playerKey).stream()
                        .map(DialogDefinition.Action::value).toList());

        Path artifacts = Path.of("build", "reports", "dialog");
        Files.createDirectories(artifacts);
        Files.writeString(artifacts.resolve("java-dialog-multicommand.json"),
                dialog.toString());
        Files.writeString(artifacts.resolve("bedrock-form-multicommand.json"),
                DialogPack.bedrockForm(definition).toString());
    }

    @Test
    void afterModesMapToVanillaAfterAction() {
        for (DialogDefinition.After after : DialogDefinition.After.values()) {
            DialogDefinition definition = new DialogDefinition(Key.key("testpack", "mode"),
                    "Mode", "", 1, true, after,
                    List.of(new DialogDefinition.Button("Close",
                            DialogDefinition.ButtonType.CLOSE, "")));
            assertEquals(after.action(), DialogPack.javaDialog(definition)
                    .get("after_action").getAsString());
        }
    }

    @Test
    void rawJavaDialogJsonIsPassedThroughUnchanged() throws Exception {
        JsonObject raw = com.google.gson.JsonParser.parseString("""
                {"type":"minecraft:multi_action","title":"Raw",
                 "inputs":[{"type":"minecraft:text","key":"name","label":"Name"}],
                 "actions":[{"label":"OK"}]}
                """).getAsJsonObject();
        DialogDefinition definition = new DialogDefinition(Key.key("testpack", "raw"),
                "Raw", "", 1, true, DialogDefinition.After.CLOSE,
                List.of(), raw, Map.of());

        assertEquals(raw, DialogPack.javaDialog(definition));
        Path artifacts = Path.of("build", "reports", "dialog");
        Files.createDirectories(artifacts);
        Files.writeString(artifacts.resolve("raw-java-dialog.json"),
                DialogPack.javaDialog(definition).toString());
    }

    @Test
    void bedrockFormJsonAndTheJavaDatapackAreGenerated() throws Exception {
        JsonObject form = DialogPack.bedrockForm(definition());
        assertEquals("Welcome", form.get("title").getAsString());
        assertEquals("Choose an option", form.get("content").getAsString());
        assertEquals(2, form.getAsJsonArray("buttons").size());
        assertEquals("seamless", form.get("after").getAsString());
        assertEquals(true, form.get("can_close_with_escape").getAsBoolean());
        assertEquals("Spawn",
                form.getAsJsonArray("buttons").get(0).getAsJsonObject()
                        .get("text").getAsString());
        assertEquals(definition().clickKeys().get(0),
                form.getAsJsonArray("buttons").get(0).getAsJsonObject()
                        .get("key").getAsString());

        Path artifacts = Path.of("build", "reports", "dialog");
        Files.createDirectories(artifacts);
        Files.writeString(artifacts.resolve("bedrock-form.json"), form.toString());
        Files.writeString(artifacts.resolve("java-dialog.json"),
                DialogPack.javaDialog(definition()).toString());
    }

    @Test
    void copySuggestAndNestedDialogButtonsRenderNatively() {
        DialogDefinition definition = new DialogDefinition(Key.key("testpack", "actions"),
                "Actions", "", 1, true, DialogDefinition.After.CLOSE, List.of(
                new DialogDefinition.Button("Copy",
                        DialogDefinition.ButtonType.COPY, "hello"),
                new DialogDefinition.Button("Suggest",
                        DialogDefinition.ButtonType.SUGGEST, "/spawn"),
                new DialogDefinition.Button("Next",
                        DialogDefinition.ButtonType.DIALOG, "testpack:next")));

        JsonObject dialog = DialogPack.javaDialog(definition);
        var actions = dialog.getAsJsonArray("actions");
        assertEquals("minecraft:copy_to_clipboard",
                actions.get(0).getAsJsonObject().getAsJsonObject("action")
                        .get("type").getAsString());
        assertEquals("hello",
                actions.get(0).getAsJsonObject().getAsJsonObject("action")
                        .get("value").getAsString());
        assertEquals("minecraft:suggest_command",
                actions.get(1).getAsJsonObject().getAsJsonObject("action")
                        .get("type").getAsString());
        String nextKey = definition.clickKeys().get(0);
        assertEquals("minecraft:run_command",
                actions.get(2).getAsJsonObject().getAsJsonObject("action")
                        .get("type").getAsString());
        assertEquals("/kalo click " + nextKey,
                actions.get(2).getAsJsonObject().getAsJsonObject("action")
                        .get("command").getAsString());
        assertEquals(DialogDefinition.ActionType.DIALOG,
                definition.clickActions().get(nextKey).get(0).type());

        JsonObject form = DialogPack.bedrockForm(definition);
        assertEquals("copy",
                form.getAsJsonArray("buttons").get(0).getAsJsonObject()
                        .get("type").getAsString());
        assertEquals("suggest",
                form.getAsJsonArray("buttons").get(1).getAsJsonObject()
                        .get("type").getAsString());
        assertEquals("dialog",
                form.getAsJsonArray("buttons").get(2).getAsJsonObject()
                        .get("type").getAsString());
        assertEquals(nextKey,
                form.getAsJsonArray("buttons").get(2).getAsJsonObject()
                        .get("key").getAsString());
    }

    @Test
    void installingADialogWritesAnOwnedDatapack(@TempDir Path world) throws Exception {
        boolean changed = DialogPack.install(world, List.of(definition()));
        assertTrue(changed);

        Path dialog = world.resolve("datapacks/kalo-dialogs/data/testpack/dialog/welcome.json");
        assertNotNull(Files.readString(dialog));
        assertTrue(Files.readString(dialog).contains("minecraft:multi_action"));
        assertTrue(Files.isRegularFile(
                world.resolve("datapacks/kalo-dialogs/pack.mcmeta")));

        assertTrue(Files.readString(world.resolve("datapacks/kalo-dialogs/pack.mcmeta"))
                .contains("\"min_format\":[80,0]"));
        assertTrue(Files.readString(world.resolve("datapacks/kalo-dialogs/pack.mcmeta"))
                .contains("\"max_format\":[107,1]"), "26.2 data pack format must be supported");
        assertFalse(DialogPack.install(world, List.of(definition())),
                "identical reinstall must not request a reload");

        Path artifactWorld = Path.of("build", "reports", "dialog", "datapack-world");
        DialogPack.install(artifactWorld, List.of(definition()));
        assertTrue(Files.isRegularFile(artifactWorld.resolve(
                "datapacks/kalo-dialogs/data/testpack/dialog/welcome.json")));
    }

    @Test
    void removingADialogDeletesOnlyTheOwnedFile(@TempDir Path world) throws Exception {
        assertTrue(DialogPack.install(world, List.of(definition())));
        Path owned = world.resolve("datapacks/kalo-dialogs");
        Path foreign = owned.resolve("notes.txt");
        Files.writeString(foreign, "server owner notes");

        assertTrue(DialogPack.install(world, List.of()));

        assertFalse(Files.exists(owned.resolve("data/testpack/dialog/welcome.json")));
        assertTrue(Files.isRegularFile(foreign), "install must not delete unrelated files");
    }

    @Test
    void aCloseOnlyDialogStillProducesAValidMultiAction() {
        DialogDefinition closeOnly = new DialogDefinition(Key.key("testpack", "close_only"),
                "Notice", "Nothing to do", 1,
                List.of(new DialogDefinition.Button("Close",
                        DialogDefinition.ButtonType.CLOSE, "")));

        JsonObject dialog = DialogPack.javaDialog(closeOnly);

        assertEquals("minecraft:multi_action", dialog.get("type").getAsString());
        assertEquals(1, dialog.getAsJsonArray("actions").size());
        JsonObject close = dialog.getAsJsonArray("actions").get(0).getAsJsonObject();
        assertEquals("Close", close.get("label").getAsString());
        assertEquals("/kalo click " + closeOnly.clickKeys().get(0),
                close.getAsJsonObject("action").get("command").getAsString());
        assertEquals(DialogDefinition.ActionType.CLOSE,
                closeOnly.clickActions().get(closeOnly.clickKeys().get(0)).get(0).type());
    }
}

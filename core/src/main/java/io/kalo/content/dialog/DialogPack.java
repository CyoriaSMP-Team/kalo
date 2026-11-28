package io.kalo.content.dialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.kalo.content.dialog.definition.DialogDefinition;
import io.kalo.pack.Json;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders dialogs twice: as Java data-pack JSON and as a Bedrock form description.
 *
 * <p>Java dialogs live in a server data pack, never in the resource pack. The data pack
 * is written into an owned directory so installing or updating it can never overwrite a
 * server owner's own data packs. Bedrock has no data-pack dialog registry, so the runtime
 * uses the same definition to build a Cumulus form; {@link #bedrockForm} is the
 * inspectable JSON equivalent.</p>
 */
public final class DialogPack {

    public static final String DIRECTORY_NAME = "kalo-dialogs";
    private static final String META_PATH = "pack.mcmeta";

    private DialogPack() {
    }

    /** The exact Java data-pack representation of one dialog. */
    public static @NotNull JsonObject javaDialog(@NotNull DialogDefinition definition) {
        if (definition.raw() != null) {
            return definition.raw().deepCopy();
        }

        JsonObject dialog = new JsonObject();
        dialog.addProperty("type", "minecraft:multi_action");
        dialog.addProperty("title", definition.title());
        dialog.addProperty("external_title", definition.title());
        dialog.addProperty("pause", false);
        dialog.addProperty("after_action", definition.after().action());
        dialog.addProperty("can_close_with_escape", definition.canCloseWithEscape());
        dialog.addProperty("columns", definition.columns());

        JsonArray body = new JsonArray();
        if (!definition.body().isBlank()) {
            JsonObject message = new JsonObject();
            message.addProperty("type", "minecraft:plain_message");
            message.addProperty("contents", definition.body());
            message.addProperty("width", 300);
            body.add(message);
        }
        dialog.add("body", body);

        JsonArray actions = new JsonArray();
        for (int index = 0; index < definition.buttons().size(); index++) {
            DialogDefinition.Button button = definition.buttons().get(index);
            JsonObject action = new JsonObject();
            action.addProperty("label", button.label());
            if (!button.tooltip().isBlank()) {
                action.addProperty("tooltip", button.tooltip());
            }
            action.addProperty("width", button.width());

            JsonObject click = new JsonObject();
            if (button.actions().isEmpty()) {
                // Route close through the runtime so `after: none` and `seamless`
                // still close when there is no server-side command to answer.
                click.addProperty("type", "minecraft:run_command");
                click.addProperty("command", "/kalo click " + definition.clickKey(index));
                action.add("action", click);
                actions.add(action);
                continue;
            }

            if (button.needsServer()) {
                // Commands never ride in the client payload. The key is checked
                // against the dialog this player was last shown.
                click.addProperty("type", "minecraft:run_command");
                click.addProperty("command", "/kalo click " + definition.clickKey(index));
            } else {
                DialogDefinition.Action first = button.actions().get(0);
                switch (first.type()) {
                    case URL -> {
                        click.addProperty("type", "minecraft:open_url");
                        click.addProperty("url", first.value());
                    }
                    case COPY -> {
                        click.addProperty("type", "minecraft:copy_to_clipboard");
                        click.addProperty("value", first.value());
                    }
                    case SUGGEST -> {
                        click.addProperty("type", "minecraft:suggest_command");
                        click.addProperty("command", first.value());
                    }
                    case DIALOG -> {
                        click.addProperty("type", "minecraft:show_dialog");
                        click.addProperty("dialog", first.value());
                    }
                    case COMMAND, CONSOLE -> throw new IllegalStateException(
                            "server action escaped the click-key route");
                }
            }
            action.add("action", click);
            actions.add(action);
        }

        if (actions.isEmpty()) {
            // multi_action requires at least one action.
            JsonObject acknowledge = new JsonObject();
            acknowledge.addProperty("label", "OK");
            acknowledge.addProperty("width", 150);
            actions.add(acknowledge);
        }
        dialog.add("actions", actions);

        JsonObject exit = new JsonObject();
        exit.addProperty("label", "Close");
        exit.addProperty("width", 200);
        dialog.add("exit_action", exit);
        return dialog;
    }

    /** The inspectable form description used by the Bedrock runtime bridge. */
    public static @NotNull JsonObject bedrockForm(@NotNull DialogDefinition definition) {
        JsonObject form = new JsonObject();
        form.addProperty("type", "form");
        form.addProperty("title", definition.title());
        form.addProperty("content", definition.body());
        form.addProperty("after", definition.after().name().toLowerCase(java.util.Locale.ROOT));
        form.addProperty("can_close_with_escape", definition.canCloseWithEscape());

        JsonArray buttons = new JsonArray();
        for (int index = 0; index < definition.buttons().size(); index++) {
            DialogDefinition.Button button = definition.buttons().get(index);
            JsonObject entry = new JsonObject();
            entry.addProperty("text", button.label());
            if (!button.tooltip().isBlank()) {
                entry.addProperty("tooltip", button.tooltip());
            }
            entry.addProperty("type", button.type().name().toLowerCase(java.util.Locale.ROOT));
            entry.addProperty("value", button.value());
            if (button.needsServer() && definition.raw() == null) {
                entry.addProperty("key", definition.clickKey(index));
            }
            if (!button.actions().isEmpty()) {
                JsonArray actions = new JsonArray();
                for (DialogDefinition.Action action : button.actions()) {
                    JsonObject item = new JsonObject();
                    item.addProperty("type",
                            action.type().name().toLowerCase(java.util.Locale.ROOT));
                    item.addProperty("value", action.value());
                    actions.add(item);
                }
                entry.add("actions", actions);
            }
            buttons.add(entry);
        }
        form.add("buttons", buttons);
        return form;
    }

    /** All files an install would write, keyed by pack-relative path. */
    public static @NotNull Map<String, byte[]> render(
            @NotNull List<DialogDefinition> definitions) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(META_PATH, bytes(metadata()));

        for (DialogDefinition definition : definitions) {
            String path = "data/" + definition.key().namespace()
                    + "/dialog/" + definition.key().value() + ".json";
            files.put(path, bytes(javaDialog(definition)));
        }
        return files;
    }

    /**
     * Writes the Java data pack. Only the owned {@value #DIRECTORY_NAME} directory is
     * modified; a dialog that no longer exists is removed from it, and identical files
     * are left untouched.
     *
     * @return whether a data-pack reload is needed
     */
    public static boolean install(@NotNull Path worldFolder,
                                  @NotNull List<DialogDefinition> definitions)
            throws IOException {
        Path container = worldFolder.resolve("datapacks").resolve(DIRECTORY_NAME);
        Path root = container.toAbsolutePath().normalize();
        Map<String, byte[]> rendered = render(definitions);
        boolean exists = Files.isDirectory(root);
        if (!exists && rendered.size() == 1) {
            // No dialogs and no previous install: do not create an empty data pack.
            return false;
        }

        Files.createDirectories(root);
        boolean changed = false;
        for (Map.Entry<String, byte[]> entry : rendered.entrySet()) {
            Path target = root.resolve(entry.getKey()).normalize();
            if (!target.startsWith(root)) {
                throw new IOException("Unsafe dialog data-pack path: " + entry.getKey());
            }
            if (Files.isRegularFile(target)
                    && java.util.Arrays.equals(Files.readAllBytes(target), entry.getValue())) {
                continue;
            }
            Files.createDirectories(target.getParent());
            writeAtomically(target, entry.getValue());
            changed = true;
        }

        // Remove dialogs this pack used to own. The directory is exclusively Kalo's, so
        // nothing created by the server owner lives here.
        List<Path> stale = new ArrayList<>();
        Path data = root.resolve("data");
        if (Files.isDirectory(data)) {
            try (DirectoryStream<Path> namespaces = Files.newDirectoryStream(data)) {
                for (Path namespace : namespaces) {
                    Path dialogs = namespace.resolve("dialog");
                    if (!Files.isDirectory(dialogs)) {
                        continue;
                    }
                    try (DirectoryStream<Path> files = Files.newDirectoryStream(dialogs, "*.json")) {
                        for (Path file : files) {
                            String relative = root.relativize(file.normalize())
                                    .toString().replace('\\', '/');
                            if (!rendered.containsKey(relative)) {
                                stale.add(file);
                            }
                        }
                    }
                }
            }
        }
        for (Path file : stale) {
            Files.deleteIfExists(file);
            changed = true;
        }
        return changed;
    }

    private static JsonObject metadata() {
        JsonObject root = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Kalo data-driven dialogs");
        // Dialogs exist from Java 1.21.6 (data pack format 80). Versions before 1.21.9
        // read pack_format/supported_formats; newer versions read min_format/max_format.
        // Both are written so the same owned pack loads on the supported range.
        pack.addProperty("pack_format", 80);
        JsonObject supported = new JsonObject();
        supported.addProperty("min_inclusive", 80);
        supported.addProperty("max_inclusive", 81);
        pack.add("supported_formats", supported);
        JsonArray minimum = new JsonArray();
        minimum.add(80);
        minimum.add(0);
        pack.add("min_format", minimum);
        JsonArray maximum = new JsonArray();
        maximum.add(107);
        maximum.add(1);
        pack.add("max_format", maximum);
        root.add("pack", pack);
        return root;
    }

    private static void writeAtomically(Path target, byte[] value) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), ".kalo-", ".tmp");
        try {
            Files.write(temp, value);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static byte[] bytes(JsonObject object) {
        return (Json.write(object) + "\n").getBytes(StandardCharsets.UTF_8);
    }
}

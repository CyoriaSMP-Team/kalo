package io.kalo.content.dialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.dialog.definition.DialogDefinition;
import io.kalo.content.item.Item;
import io.kalo.pack.ResourcePack;
import io.kalo.registry.Registries;
import io.kalo.utils.Constants;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads dialogs from pack YAML and installs their Java datapack representation.
 *
 * <pre>
 * welcome:
 *   type: dialog
 *   title: "Welcome"
 *   body: "Choose an option"
 *   columns: 2
 *   after: seamless
 *   buttons:
 *     - { label: Spawn, command: spawn, tooltip: "Back to the middle" }
 *     - label: Shop
 *       console:
 *         - "warp shop {player}"
 *         - "give {player} bread"
 *     - { label: Website, url: "https://example.com" }
 * </pre>
 */
public final class DialogType implements ContentType<Item> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "dialog");

    private static final Logger LOGGER = Logger.getLogger(DialogType.class.getName());
    private static final long MAX_JSON_BYTES = 256L * 1024L;
    private static final List<String> ACTION_KEYS = List.of(
            "command", "console", "url", "copy", "suggest", "dialog");

    private final Map<Key, DialogDefinition> dialogs = new ConcurrentHashMap<>();

    @Override
    public @NotNull String id() {
        return "dialog";
    }

    @Override
    public @NotNull Class<Item> clazz() {
        return Item.class;
    }

    @Override
    public @NotNull Iterable<Item> contents(@NotNull Registries registries) {
        return List.of();
    }

    @Override
    public boolean load(@NotNull PackContext pack, @NotNull Registries registries,
                        @NotNull ConfigurationSection config) {
        Key key = pack.key(config.getName());
        try {
            DialogDefinition definition = parse(pack, key, config);
            if (dialogs.putIfAbsent(key, definition) != null) {
                LOGGER.warning("Duplicate dialog '" + key.asString()
                        + "'; keeping the first definition");
                return false;
            }
            return true;
        } catch (Exception error) {
            LOGGER.log(Level.WARNING,
                    "Failed to load dialog '" + key.asString() + "': " + error.getMessage(),
                    error);
            return false;
        }
    }

    /** Parses authored YAML. Raw JSON needs a pack context and uses the other overload. */
    public static @NotNull DialogDefinition parse(@NotNull Key key,
                                                  @NotNull ConfigurationSection config) {
        return parse(null, key, config);
    }

    public static @NotNull DialogDefinition parse(@Nullable PackContext pack,
                                                  @NotNull Key key,
                                                  @NotNull ConfigurationSection config) {
        boolean canClose = config.getBoolean("can-close-with-escape",
                config.getBoolean("can_close_with_escape", true));
        DialogDefinition.After after = config.contains("after")
                ? DialogDefinition.After.parse(config.getString("after", "seamless"))
                : DialogDefinition.After.SEAMLESS;
        int columns = config.getInt("columns", 1);

        String jsonPath = config.getString("json");
        if (jsonPath != null && !jsonPath.isBlank()) {
            if (pack == null) {
                throw new IllegalArgumentException(
                        "raw dialog JSON requires the owning pack folder");
            }
            JsonObject raw = readJson(pack, jsonPath);
            String title = firstNonBlank(textOf(raw.get("title")),
                    config.getString("title"), key.value());
            String body = textOfBody(raw.get("body"));
            if (body.isBlank()) {
                body = bodyFromConfig(config);
            }
            if (raw.has("columns")) {
                columns = raw.get("columns").getAsInt();
            } else {
                columns = 2;
            }
            if (raw.has("can_close_with_escape")) {
                canClose = raw.get("can_close_with_escape").getAsBoolean();
            }
            after = raw.has("after_action")
                    ? DialogDefinition.After.parse(raw.get("after_action").getAsString())
                    : DialogDefinition.After.CLOSE;
            return new DialogDefinition(key, title, body, columns, canClose, after,
                    rawButtons(raw), raw, rawClicks(config));
        }

        String title = config.getString("title");
        if (title == null) {
            throw new IllegalArgumentException("dialog has no title");
        }
        List<DialogDefinition.Button> buttons = parseButtons(config.getList("buttons"));
        return new DialogDefinition(key, title, bodyFromConfig(config), columns,
                canClose, after, buttons, null, Map.of());
    }

    private static @NotNull String bodyFromConfig(@NotNull ConfigurationSection config) {
        String body = config.getString("body", "");
        Object rawBody = config.get("body");
        if (rawBody instanceof List<?> lines) {
            body = lines.stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining("\n"));
        }
        return body == null ? "" : body;
    }

    private static @NotNull List<DialogDefinition.Button> parseButtons(@Nullable List<?> raw) {
        if (raw == null) {
            throw new IllegalArgumentException("dialog has no buttons");
        }
        List<DialogDefinition.Button> buttons = new ArrayList<>();
        for (Object value : raw) {
            if (!(value instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("dialog buttons must be YAML maps");
            }
            String label = map.get("label") == null ? "" : map.get("label").toString();
            String tooltip = map.get("tooltip") == null
                    ? "" : map.get("tooltip").toString();
            int width = map.get("width") instanceof Number number
                    ? number.intValue() : 150;

            List<DialogDefinition.Action> actions = new ArrayList<>();
            if (map.get("type") != null) {
                DialogDefinition.ButtonType type = DialogDefinition.ButtonType.parse(
                        map.get("type").toString());
                if (type != DialogDefinition.ButtonType.CLOSE) {
                    Object actionValue = map.get("value") == null ? "" : map.get("value");
                    if (type == DialogDefinition.ButtonType.COMMAND
                            || type == DialogDefinition.ButtonType.CONSOLE) {
                        addActions(actions, type, actionValue);
                    } else {
                        actions.add(new DialogDefinition.Action(type.actionType(),
                                String.valueOf(actionValue)));
                    }
                }
            } else {
                List<String> present = ACTION_KEYS.stream()
                        .filter(key -> map.get(key) != null)
                        .toList();
                if (present.size() > 1) {
                    throw new IllegalArgumentException(
                            "a dialog button has more than one action kind: " + present);
                }
                if (!present.isEmpty()) {
                    String actionKey = present.get(0);
                    DialogDefinition.ButtonType type =
                            DialogDefinition.ButtonType.parse(actionKey);
                    addActions(actions, type, map.get(actionKey));
                }
            }
            buttons.add(new DialogDefinition.Button(label, actions, tooltip, width));
        }
        return List.copyOf(buttons);
    }

    private static void addActions(@NotNull List<DialogDefinition.Action> actions,
                                   @NotNull DialogDefinition.ButtonType type,
                                   @NotNull Object raw) {
        if (raw instanceof List<?> list) {
            if (type != DialogDefinition.ButtonType.COMMAND
                    && type != DialogDefinition.ButtonType.CONSOLE) {
                throw new IllegalArgumentException(
                        "only command and console buttons accept several commands");
            }
            if (list.isEmpty()) {
                throw new IllegalArgumentException("a command list cannot be empty");
            }
            for (Object value : list) {
                actions.add(new DialogDefinition.Action(type.actionType(),
                        String.valueOf(value)));
            }
            return;
        }
        actions.add(new DialogDefinition.Action(type.actionType(), String.valueOf(raw)));
    }

    private static @NotNull Map<String, List<DialogDefinition.Action>> rawClicks(
            @NotNull ConfigurationSection config) {
        ConfigurationSection section = config.getConfigurationSection("console-commands");
        if (section == null) {
            return Map.of();
        }
        Map<String, List<DialogDefinition.Action>> clicks = new LinkedHashMap<>();
        for (String rawKey : section.getKeys(false)) {
            String key = rawKey.toLowerCase(Locale.ROOT);
            List<String> lines = new ArrayList<>();
            if (section.isList(rawKey)) {
                for (Object value : section.getList(rawKey, List.of())) {
                    lines.add(String.valueOf(value));
                }
            } else {
                String value = section.getString(rawKey);
                if (value != null) {
                    lines.addAll(Arrays.asList(value.split("\\R")));
                }
            }
            List<DialogDefinition.Action> actions = new ArrayList<>();
            for (String line : lines) {
                if (!line.isBlank()) {
                    actions.add(new DialogDefinition.Action(
                            DialogDefinition.ActionType.CONSOLE, line));
                }
            }
            if (actions.isEmpty()) {
                throw new IllegalArgumentException(
                        "dialog console-commands key '" + key + "' has no command");
            }
            if (clicks.putIfAbsent(key, List.copyOf(actions)) != null) {
                throw new IllegalArgumentException(
                        "duplicate dialog console-commands key: " + key);
            }
        }
        return Map.copyOf(clicks);
    }

    private static @NotNull JsonObject readJson(@NotNull PackContext pack,
                                                @NotNull String relativePath) {
        Path root = pack.folder().toPath().toAbsolutePath().normalize();
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException(
                    "dialog JSON must stay inside the content pack: " + relativePath);
        }
        try {
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("dialog JSON not found: " + relativePath);
            }
            if (!file.toRealPath().startsWith(root.toRealPath())) {
                throw new IllegalArgumentException(
                        "dialog JSON resolves outside the content pack: " + relativePath);
            }
            if (Files.size(file) > MAX_JSON_BYTES) {
                throw new IllegalArgumentException(
                        "dialog JSON is larger than " + MAX_JSON_BYTES + " bytes");
            }
            JsonElement parsed = JsonParser.parseString(
                    Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("dialog JSON must be one JSON object");
            }
            return parsed.getAsJsonObject();
        } catch (IOException error) {
            throw new IllegalArgumentException(
                    "could not read dialog JSON " + relativePath, error);
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException(
                    "invalid dialog JSON " + relativePath + ": " + malformed.getMessage(),
                    malformed);
        }
    }

    private static @NotNull List<DialogDefinition.Button> rawButtons(@NotNull JsonObject raw) {
        JsonElement element = raw.get("actions");
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        JsonArray actions = element.getAsJsonArray();
        List<DialogDefinition.Button> buttons = new ArrayList<>();
        for (JsonElement entry : actions) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject button = entry.getAsJsonObject();
            String label = firstNonBlank(textOf(button.get("label")), "Button");
            String tooltip = textOf(button.get("tooltip"));
            int width = button.has("width") ? button.get("width").getAsInt() : 150;

            List<DialogDefinition.Action> commands = new ArrayList<>();
            JsonElement clickElement = button.get("action");
            if (clickElement != null && clickElement.isJsonObject()) {
                JsonObject click = clickElement.getAsJsonObject();
                String type = click.has("type")
                        ? click.get("type").getAsString().replace("minecraft:", "")
                        : "";
                String value = switch (type) {
                    case "run_command" -> textOf(click.get("command"));
                    case "open_url" -> textOf(click.get("url"));
                    case "copy_to_clipboard" -> textOf(click.get("value"));
                    case "suggest_command" -> textOf(click.get("command"));
                    case "show_dialog" -> textOf(click.get("dialog"));
                    default -> "";
                };
                if (!value.isBlank()) {
                    DialogDefinition.ButtonType buttonType =
                            DialogDefinition.ButtonType.parse(type);
                    commands.add(new DialogDefinition.Action(
                            buttonType.actionType(), value));
                }
            }
            buttons.add(new DialogDefinition.Button(label, commands, tooltip, width));
        }
        return List.copyOf(buttons);
    }

    private static @NotNull String textOfBody(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonArray()) {
            List<String> lines = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                String line = textOfBody(child);
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
            return String.join("\n", lines);
        }
        if (element.isJsonObject() && element.getAsJsonObject().has("contents")) {
            return textOf(element.getAsJsonObject().get("contents"));
        }
        return textOf(element);
    }

    private static @NotNull String textOf(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                String part = textOf(child);
                if (!part.isBlank()) {
                    parts.add(part);
                }
            }
            return String.join("\n", parts);
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("text")) {
                return textOf(object.get("text"));
            }
            if (object.has("contents")) {
                return textOf(object.get("contents"));
            }
            if (object.has("translate")) {
                return object.get("translate").getAsString();
            }
            if (object.has("extra")) {
                return textOf(object.get("extra"));
            }
        }
        return "";
    }

    private static @NotNull String firstNonBlank(@NotNull String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    /** Exposed for tests and programmatic registration. */
    public void put(@NotNull DialogDefinition definition) {
        dialogs.put(definition.key(), definition);
    }

    public @NotNull Map<Key, DialogDefinition> dialogs() {
        return Map.copyOf(dialogs);
    }

    /** Deterministic install order; the data pack must not churn between reloads. */
    public @NotNull List<DialogDefinition> ordered() {
        return dialogs.values().stream()
                .sorted(java.util.Comparator.comparing(definition ->
                        definition.key().asString()))
                .toList();
    }

    public int size() {
        return dialogs.size();
    }

    public void clear() {
        dialogs.clear();
    }

    @Override
    public void compilePack(@NotNull ResourcePack resourcePack,
                            @NotNull Iterable<Item> contents) {
        // Dialogs live in a world datapack, not a resource pack.
    }
}

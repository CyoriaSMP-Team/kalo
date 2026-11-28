package io.kalo.content.dialog.definition;

import com.google.gson.JsonObject;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A server-side dialog with a Java datapack representation and a Bedrock form.
 *
 * <p>Buttons run as the player, run as the console, open an HTTPS URL, copy text,
 * suggest a command, open another dialog, or simply close. Commands never travel in the
 * client payload: the dialog sends {@code /kalo click <key>}, and the runtime only
 * executes that key while it belongs to the dialog the player was last shown. A dialog
 * written as raw Java JSON is passed through unchanged; its explicit
 * {@code console-commands} keys use the same authorization path.</p>
 */
public record DialogDefinition(
        @NotNull Key key,
        @NotNull String title,
        @NotNull String body,
        int columns,
        boolean canCloseWithEscape,
        @NotNull After after,
        @NotNull List<Button> buttons,
        @Nullable JsonObject raw,
        @NotNull Map<String, List<Action>> rawClicks
) {

    private static final Pattern CLICK_KEY = Pattern.compile("[a-z0-9]{1,32}");

    public enum After {
        SEAMLESS,
        CLOSE,
        NONE,
        WAIT_FOR_RESPONSE;

        public static @NotNull After parse(@NotNull String value) {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "seamless", "smooth" -> SEAMLESS;
                case "close" -> CLOSE;
                case "none", "stay" -> NONE;
                case "wait_for_response", "wait", "waiting" -> WAIT_FOR_RESPONSE;
                default -> throw new IllegalArgumentException(
                        "dialog after must be seamless, close, none or wait_for_response");
            };
        }

        /** The vanilla {@code after_action} value. Seamless is closed by the runtime. */
        public @NotNull String action() {
            return switch (this) {
                case SEAMLESS, NONE -> "none";
                case CLOSE -> "close";
                case WAIT_FOR_RESPONSE -> "wait_for_response";
            };
        }
    }

    public enum ButtonType {
        COMMAND,
        CONSOLE,
        URL,
        COPY,
        SUGGEST,
        DIALOG,
        CLOSE;

        public static @NotNull ButtonType parse(@NotNull String value) {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "command", "run", "run_command", "player" -> COMMAND;
                case "console", "op" -> CONSOLE;
                case "url", "open_url" -> URL;
                case "copy", "copy_to_clipboard" -> COPY;
                case "suggest", "suggest_command" -> SUGGEST;
                case "dialog", "show_dialog" -> DIALOG;
                case "close", "exit", "none" -> CLOSE;
                default -> throw new IllegalArgumentException(
                        "dialog button type must be command, console, url, copy,"
                                + " suggest, dialog or close");
            };
        }

        public @NotNull ActionType actionType() {
            return switch (this) {
                case COMMAND -> ActionType.COMMAND;
                case CONSOLE -> ActionType.CONSOLE;
                case URL -> ActionType.URL;
                case COPY -> ActionType.COPY;
                case SUGGEST -> ActionType.SUGGEST;
                case DIALOG -> ActionType.DIALOG;
                case CLOSE -> ActionType.CLOSE;
            };
        }

        static @NotNull ButtonType of(@NotNull ActionType type) {
            return switch (type) {
                case COMMAND -> COMMAND;
                case CONSOLE -> CONSOLE;
                case URL -> URL;
                case COPY -> COPY;
                case SUGGEST -> SUGGEST;
                case DIALOG -> DIALOG;
                case CLOSE -> CLOSE;
            };
        }
    }

    public enum ActionType {
        COMMAND,
        CONSOLE,
        URL,
        COPY,
        SUGGEST,
        DIALOG,
        CLOSE
    }

    public record Action(@NotNull ActionType type, @NotNull String value) {
        public Action {
            value = value == null ? "" : value.strip();
            switch (type) {
                case COMMAND, CONSOLE -> {
                    if (value.isBlank() || value.length() > 256
                            || value.chars().anyMatch(Character::isISOControl)) {
                        throw new IllegalArgumentException(
                                "dialog command must be one line of at most 256 characters");
                    }
                }
                case URL -> {
                    URI uri;
                    try {
                        uri = URI.create(value);
                    } catch (RuntimeException malformed) {
                        throw new IllegalArgumentException("Malformed dialog URL", malformed);
                    }
                    if (!"https".equalsIgnoreCase(uri.getScheme())
                            || uri.getHost() == null || uri.getUserInfo() != null
                            || value.length() > 1024) {
                        throw new IllegalArgumentException(
                                "dialog URL must be HTTPS with no credentials");
                    }
                }
                case COPY -> {
                    if (value.isBlank() || value.length() > 1024
                            || value.chars().anyMatch(character ->
                            Character.isISOControl(character) && character != '\n')) {
                        throw new IllegalArgumentException("dialog copy text is invalid");
                    }
                }
                case SUGGEST -> {
                    if (value.isBlank() || value.length() > 256
                            || value.chars().anyMatch(Character::isISOControl)) {
                        throw new IllegalArgumentException("dialog suggestion is invalid");
                    }
                }
                case DIALOG -> {
                    if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                        throw new IllegalArgumentException("dialog target must be a namespace:key");
                    }
                }
                case CLOSE -> value = "";
            }
        }
    }

    public record Button(
            @NotNull String label,
            @NotNull List<Action> actions,
            @NotNull String tooltip,
            int width
    ) {
        public Button {
            label = text(label, "button label", 120);
            tooltip = tooltip == null ? "" : tooltip.strip();
            if (tooltip.length() > 256
                    || tooltip.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("dialog tooltip is invalid");
            }
            if (width < 1 || width > 1024) {
                throw new IllegalArgumentException("dialog button width must be within 1..1024");
            }
            actions = List.copyOf(actions);
            if (actions.size() > 16) {
                throw new IllegalArgumentException("dialog button has more than 16 actions");
            }
            if (actions.stream().anyMatch(action -> action.type() == ActionType.CLOSE)) {
                throw new IllegalArgumentException(
                        "a closing button must have no action; leave the action kind empty");
            }
            if (!actions.isEmpty()) {
                ActionType first = actions.get(0).type();
                if (actions.stream().anyMatch(action -> action.type() != first)) {
                    throw new IllegalArgumentException(
                            "dialog button actions must all be the same kind");
                }
            }
        }

        public Button(@NotNull String label, @NotNull ButtonType type, @NotNull String value) {
            this(label, type, value, "", 150);
        }

        public Button(@NotNull String label, @NotNull ButtonType type, @NotNull String value,
                      @NotNull String tooltip, int width) {
            this(label, actions(type, value), tooltip, width);
        }

        public @NotNull ButtonType type() {
            return actions.isEmpty() ? ButtonType.CLOSE : ButtonType.of(actions.get(0).type());
        }

        public @NotNull String value() {
            return actions.isEmpty() ? "" : actions.get(0).value();
        }

        public boolean needsServer() {
            return actions.stream().anyMatch(action ->
                    action.type() == ActionType.COMMAND
                            || action.type() == ActionType.CONSOLE
                            || action.type() == ActionType.DIALOG);
        }

        private static @NotNull List<Action> actions(@NotNull ButtonType type,
                                                     @NotNull String value) {
            if (type == ButtonType.CLOSE) {
                return List.of();
            }
            return List.of(new Action(type.actionType(), value));
        }
    }

    public DialogDefinition {
        title = text(title, "title", 120);
        body = body == null ? "" : body;
        if (body.length() > 4096
                || body.chars().anyMatch(character -> Character.isISOControl(character)
                && character != '\n')) {
            throw new IllegalArgumentException("dialog body is invalid");
        }
        if (columns < 1 || columns > 4) {
            throw new IllegalArgumentException("dialog columns must be within 1..4");
        }
        buttons = List.copyOf(buttons);
        int maximumButtons = raw == null ? 12 : 64;
        if ((raw == null && buttons.isEmpty()) || buttons.size() > maximumButtons) {
            throw new IllegalArgumentException("dialog buttons must be within 1.."
                    + maximumButtons);
        }
        raw = raw == null ? null : raw.deepCopy();
        rawClicks = copyClicks(rawClicks);
    }

    /** Backwards-compatible constructor for authored YAML with the default after mode. */
    public DialogDefinition(@NotNull Key key, @NotNull String title, @NotNull String body,
                            int columns, @NotNull List<Button> buttons) {
        this(key, title, body, columns, true, After.SEAMLESS, buttons, null, Map.of());
    }

    public DialogDefinition(@NotNull Key key, @NotNull String title, @NotNull String body,
                            int columns, boolean canCloseWithEscape, @NotNull After after,
                            @NotNull List<Button> buttons) {
        this(key, title, body, columns, canCloseWithEscape, after, buttons, null, Map.of());
    }

    /**
     * The stable key a button sends through {@code /kalo click}. It is derived from the
     * dialog, the button index and every command on the button, so an edited dialog
     * cannot reuse the old key for a different command.
     */
    public @NotNull String clickKey(int buttonIndex) {
        Button button = buttons.get(buttonIndex);
        if (!button.needsServer() && !button.actions().isEmpty()) {
            throw new IllegalArgumentException("button does not use a server click");
        }
        StringBuilder material = new StringBuilder(key.asString())
                .append('|').append(buttonIndex);
        if (button.actions().isEmpty()) {
            material.append("|close");
        } else {
            for (Action action : button.actions()) {
                material.append('|').append(action.type()).append(':').append(action.value());
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime",
                    impossible);
        }
    }

    /** Every command key a currently shown dialog will accept. */
    public @NotNull Map<String, List<Action>> clickActions() {
        Map<String, List<Action>> clicks = new LinkedHashMap<>();
        if (raw == null) {
            for (int index = 0; index < buttons.size(); index++) {
                Button button = buttons.get(index);
                if (button.needsServer()) {
                    clicks.put(clickKey(index), button.actions());
                } else if (button.actions().isEmpty()) {
                    clicks.put(clickKey(index), List.of(new Action(ActionType.CLOSE, "")));
                }
            }
        }
        clicks.putAll(rawClicks);
        return Map.copyOf(clicks);
    }

    private static @NotNull Map<String, List<Action>> copyClicks(
            @NotNull Map<String, List<Action>> clicks) {
        if (clicks.size() > 64) {
            throw new IllegalArgumentException("dialog has more than 64 click keys");
        }
        Map<String, List<Action>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<Action>> entry : clicks.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (!CLICK_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException(
                        "dialog click key must be 1..32 lower-case letters or digits");
            }
            List<Action> actions = List.copyOf(entry.getValue());
            if (actions.isEmpty() || actions.size() > 16) {
                throw new IllegalArgumentException(
                        "dialog click key must hold 1..16 commands");
            }
            if (actions.stream().anyMatch(action -> action.type() != ActionType.CONSOLE
                    && action.type() != ActionType.COMMAND)) {
                throw new IllegalArgumentException(
                        "dialog click key may only hold player or console commands");
            }
            if (copy.putIfAbsent(key, actions) != null) {
                throw new IllegalArgumentException("duplicate dialog click key: " + key);
            }
        }
        return Map.copyOf(copy);
    }

    private static @NotNull String text(@NotNull String value, @NotNull String label,
                                        int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("invalid dialog " + label);
        }
        return value.strip();
    }

    /** All generated click keys for tests and diagnostics. */
    public @NotNull List<String> clickKeys() {
        if (raw != null) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (int index = 0; index < buttons.size(); index++) {
            if (buttons.get(index).needsServer() || buttons.get(index).actions().isEmpty()) {
                keys.add(clickKey(index));
            }
        }
        return List.copyOf(keys);
    }
}

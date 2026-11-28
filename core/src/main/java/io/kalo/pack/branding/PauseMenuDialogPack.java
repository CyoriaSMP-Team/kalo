package io.kalo.pack.branding;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.kalo.pack.Json;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * First-party, data-driven pause-screen dialog for vanilla Java >= 1.21.6.
 *
 * Minecraft's pause button is populated by the dialog registry tag
 * minecraft:pause_screen_additions. Dialogs live in SERVER data packs,
 * not in the client resource pack. No client mod or fake bitmap buttons.
 *
 * Written to an isolated owned datapack directory; other datapacks are never
 * touched. Pack installation/update requires a world datapack reload/restart.
 */
public final class PauseMenuDialogPack {
    public static final String DIRECTORY_NAME = "kalo-pause-menu";
    private static final String DIALOG_PATH = "data/kalo/dialog/cyoria_menu.json";
    private static final String TAG_PATH = "data/minecraft/tags/dialog/pause_screen_additions.json";
    private static final String META_PATH = "pack.mcmeta";

    private PauseMenuDialogPack() { }

    public record Button(String label, String type, String value) { }
    public record Settings(
            String title, String pauseButton, String description, int columns,
            List<Button> buttons) { }

    public static Settings parse(@NotNull ConfigurationSection cfg) throws IOException {
        String title = text(cfg.getString("title", "CyoriaSMP"), "title");
        String label = text(cfg.getString("pause-button", "เมนู CyoriaSMP"), "pause-button");
        String body = text(cfg.getString("description", "เมนูทางลัดของเซิร์ฟเวอร์"), "description");
        int columns = cfg.getInt("columns", 2);
        if (columns < 1 || columns > 4) {
            throw new IOException("pause-menu.dialog.columns must be 1-4");
        }
        List<?> raw = cfg.getList("buttons");
        if (raw == null || raw.isEmpty() || raw.size() > 12) {
            throw new IOException("pause-menu.dialog.buttons must contain 1-12 buttons");
        }
        java.util.ArrayList<Button> buttons = new java.util.ArrayList<>();
        for (Object value : raw) {
            if (!(value instanceof Map<?, ?> m)) {
                throw new IOException("Each pause menu button must be a YAML map");
            }
            String text = text(asString(m.get("label")), "buttons.label");
            String type = asString(m.get("type"));
            String target = asString(m.get("value"));
            switch (type) {
                case "command" -> {
                    // Command button executes AS THE PLAYER, never the console. No
                    // elevated permission or ability to inject multiple commands.
                    if (!target.matches("/[a-zA-Z0-9_:][a-zA-Z0-9_:\\-]{0,70}")) {
                        throw new IOException("pause-menu button commands must be a single command without arguments");
                    }
                }
                case "url" -> {
                    java.net.URI u;
                    try {
                        u = java.net.URI.create(target);
                    } catch (IllegalArgumentException ex) {
                        throw new IOException("Malformed pause-menu URL", ex);
                    }
                    if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null
                            || u.getUserInfo() != null || target.length() > 1024) {
                        throw new IOException("pause-menu link must be an HTTPS URL with no credentials");
                    }
                }
                default -> throw new IOException("pause-menu button type must be command or url");
            }
            buttons.add(new Button(text, type, target));
        }
        return new Settings(title, label, body, columns, List.copyOf(buttons));
    }

    private static String asString(Object value) {
        return value instanceof String s ? s.strip() : "";
    }

    private static String text(String value, String key) throws IOException {
        if (value == null || value.isBlank() || value.length() > 120
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IOException("Invalid pause-menu.dialog." + key);
        }
        return value.strip();
    }

    public static Map<String, byte[]> render(@NotNull Settings cfg) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        JsonObject metadata = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Kalo Native CyoriaSMP Pause Menu");
        // Minecraft Java 1.21.11: Data Pack format 94.1
        JsonArray version = new JsonArray();
        version.add(94); version.add(1);
        pack.add("min_format", version);
        pack.add("max_format", version.deepCopy());
        metadata.add("pack", pack);
        result.put(META_PATH, bytes(metadata));

        JsonObject dialog = new JsonObject();
        dialog.addProperty("type", "minecraft:multi_action");
        dialog.addProperty("title", cfg.title());
        dialog.addProperty("external_title", cfg.pauseButton());
        dialog.addProperty("pause", false);
        dialog.addProperty("columns", cfg.columns());

        JsonArray body = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("type", "minecraft:plain_message");
        message.addProperty("contents", cfg.description());
        message.addProperty("width", 300);
        body.add(message);
        dialog.add("body", body);

        JsonArray actions = new JsonArray();
        for (Button b : cfg.buttons()) {
            JsonObject action = new JsonObject();
            action.addProperty("label", b.label());
            action.addProperty("width", 150);
            JsonObject click = new JsonObject();
            if ("command".equals(b.type())) {
                click.addProperty("type", "minecraft:run_command");
                click.addProperty("command", b.value());
            } else {
                click.addProperty("type", "minecraft:open_url");
                click.addProperty("url", b.value());
            }
            action.add("action", click);
            actions.add(action);
        }
        dialog.add("actions", actions);
        JsonObject back = new JsonObject();
        back.addProperty("label", "กลับ");
        back.addProperty("width", 200);
        dialog.add("exit_action", back);
        result.put(DIALOG_PATH, bytes(dialog));

        JsonObject tag = new JsonObject();
        JsonArray values = new JsonArray();
        values.add("kalo:cyoria_menu");
        tag.add("values", values); // no "replace": preserves other plugins' dialogs
        result.put(TAG_PATH, bytes(tag));
        return result;
    }

    private static byte[] bytes(JsonObject o) {
        return (Json.write(o) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Atomically write owned files only. Existing files are left untouched if
     * identical; returns true when a datapack reload is needed.
     */
    public static boolean install(Path worldFolder, Settings cfg) throws IOException {
        Path container = worldFolder.resolve("datapacks").resolve(DIRECTORY_NAME);
        Path root = container.toAbsolutePath().normalize();
        Files.createDirectories(root);
        boolean changed = false;
        for (var entry : render(cfg).entrySet()) {
            Path target = root.resolve(entry.getKey()).normalize();
            if (!target.startsWith(root)) throw new IOException("Unsafe datapack path");
            if (Files.isRegularFile(target)
                    && java.util.Arrays.equals(Files.readAllBytes(target), entry.getValue())) {
                continue;
            }
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".kalo-", ".tmp");
            try {
                Files.write(temp, entry.getValue());
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
            changed = true;
        }
        return changed;
    }
}

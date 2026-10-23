package io.kalo.converter;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.geyser.api.GeyserApi;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Kalo's Bedrock Forms adapter for existing DeluxeMenus Java menus.
 *
 * Only translates menus when the exact original Java click semantics can be
 * preserved. Anything with dynamic requirements, admin/console actions,
 * purchases or unsupported commands stays a native DeluxeMenus GUI. Never
 * execute a privileged action merely because a Bedrock form was clicked.
 */
public final class DeluxeMenuFormBridge implements Listener {
    private static final int MAX_BUTTONS = 48;
    private static final int MAX_MENU_SIZE = 108;
    private static final int MAX_COMMAND_LENGTH = 256;
    private static final Set<String> SAFE_ACTIONS = Set.of("player", "close", "sound", "message", "openguimenu");
    private final JavaPlugin plugin;
    private final Map<String, Menu> commands;
    private final Map<String, Menu> menuIds;

    record Action(String type, String argument) {}
    record Button(int slot, String text, String imageUrl, List<Action> actions) {}
    record Menu(String id, String title, String permission, List<Button> buttons) {}

    private DeluxeMenuFormBridge(JavaPlugin plugin, Map<String, Menu> commands,
            Map<String, Menu> menuIds) {
        this.plugin = plugin;
        this.commands = Map.copyOf(commands);
        this.menuIds = Map.copyOf(menuIds);
    }

    public static @NotNull DeluxeMenuFormBridge create(@NotNull JavaPlugin plugin) {
        File directory = new File(plugin.getDataFolder().getParentFile(), "DeluxeMenus");
        File configFile = new File(directory, "config.yml");
        Map<String, Menu> mapping = new HashMap<>();
        Map<String, Menu> safeMenuIds = new HashMap<>();
        Set<String> ambiguous = new java.util.HashSet<>();
        int skipped = 0;
        if (configFile.isFile()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            ConfigurationSection section = config.getConfigurationSection("gui_menus");
            if (section != null) {
                for (String id : section.getKeys(false)) {
                    ConfigurationSection record = section.getConfigurationSection(id);
                    if (record == null || record.getString("file") == null) continue;
                    File menuFile = new File(directory, "gui_menus/" + record.getString("file"));
                    try {
                        String root = new File(directory, "gui_menus").getCanonicalPath() + File.separator;
                        if (!menuFile.getCanonicalPath().startsWith(root) || !menuFile.isFile()
                                || menuFile.length() > 2L * 1024 * 1024) {
                            skipped++;
                            continue;
                        }
                        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(menuFile);
                        Menu menu = parse(id, yaml);
                        if (menu == null) { skipped++; continue; }
                        safeMenuIds.put(id.toLowerCase(Locale.ROOT), menu);
                        List<String> aliases = record.isSet("command")
                                ? strings(record, "command") : strings(yaml, "open_command");
                        for (String alias : aliases) {
                            String command = alias.trim().toLowerCase(Locale.ROOT);
                            if (!command.matches("[a-z0-9_:-]{1,64}")) continue;
                            // Duplicates are ambiguous; never pick a menu at random.
                            if (ambiguous.contains(command)) continue;
                            if (mapping.containsKey(command)) {
                                mapping.remove(command);
                                ambiguous.add(command);
                            } else mapping.put(command, menu);
                        }
                    } catch (Exception error) {
                        plugin.getLogger().log(Level.WARNING,
                                "Kalo could not read DeluxeMenus menu " + id + "; using original Java GUI", error);
                        skipped++;
                    }
                }
            }
        }
        plugin.getLogger().info("Kalo Bedrock Forms ready: " + mapping.size()
                + " safe DeluxeMenus open command(s), " + skipped
                + " complex menu(s) delegated to original DeluxeMenus");
        return new DeluxeMenuFormBridge(plugin, mapping, safeMenuIds);
    }

    static Menu parse(String id, YamlConfiguration yaml) {
        // Do not bypass dynamic open, view, click or purchase restrictions.
        if (yaml.isSet("open_requirement") || yaml.isSet("open_requirements")
                || yaml.isSet("open_actions") || yaml.isSet("click_requirement")) return null;
        int size = yaml.getInt("size", 54);
        if (size < 9 || size > MAX_MENU_SIZE) return null;
        ConfigurationSection items = yaml.getConfigurationSection("items");
        if (items == null) return null;
        List<Button> buttons = new ArrayList<>();
        for (String key : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(key);
            if (item == null) continue;
            if (item.isSet("view_requirement") || item.isSet("click_requirement")
                    || item.isSet("left_click_requirement") || item.isSet("right_click_requirement")
                    || item.isSet("priority") || item.isSet("update")) return null;
            if (item.isSet("left_click_commands") || item.isSet("right_click_commands")
                    || item.isSet("middle_click_commands") || item.isSet("shift_left_click_commands")
                    || item.isSet("shift_right_click_commands")) return null;
            if (!item.isSet("click_commands")) continue; // filler/decorations
            List<Action> actions = parseActions(strings(item, "click_commands"));
            if (actions == null) return null;
            if (actions.isEmpty()) continue;
            int slot = item.getInt("slot", 0);
            if (item.isList("slots") && !item.getList("slots").isEmpty()) return null;
            if (slot < 0 || slot >= size) return null;
            String title = strip(item.getString("display_name", key));
            if (title.isBlank()) title = key;
            String description = item.getStringList("lore").stream()
                    .map(DeluxeMenuFormBridge::strip).filter(s -> !s.isBlank()).findFirst().orElse("");
            String label = title + (description.isBlank() ? "" : "\n" + description);
            if (label.length() > 200) label = label.substring(0, 200);
            String image = GuiCustomSkullScanner.decodeTextureUrl(item.getString("material", ""));
            buttons.add(new Button(slot, label, image, actions));
            if (buttons.size() > MAX_BUTTONS) return null;
        }
        if (buttons.isEmpty()) return null;
        buttons.sort(Comparator.comparingInt(Button::slot));
        return new Menu(id, strip(yaml.getString("menu_title", id)),
                yaml.getString("open_permission", ""), List.copyOf(buttons));
    }

    static List<Action> parseActions(List<String> lines) {
        List<Action> result = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.length() > MAX_COMMAND_LENGTH || !line.startsWith("[")) return null;
            int pos = line.indexOf(']');
            if (pos < 1) return null;
            String type = line.substring(1, pos).toLowerCase(Locale.ROOT);
            String value = line.substring(pos + 1).trim();
            if (!SAFE_ACTIONS.contains(type)) return null;
            if ((type.equals("player") || type.equals("openguimenu"))
                    && (value.isEmpty() || value.contains("%") || value.contains("\n") || value.startsWith("/")
                        || value.contains("..")== true || value.contains(";") || value.contains("&&"))) return null;
            if ((type.equals("close") && !value.isEmpty()) || value.contains("\n")) return null;
            result.add(new Action(type, value));
        }
        return List.copyOf(result);
    }

    private static List<String> strings(ConfigurationSection section, String path) {
        if (section.isList(path)) return section.getStringList(path);
        String value = section.getString(path);
        return value == null ? List.of() : List.of(value);
    }

    private static String strip(String value) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', value));
    }

    /**
     * Intercept real DeluxeMenus inventories as a second route. Many plugins
     * navigate using Player.performCommand() or DeluxeMenus.openMenu() directly,
     * neither of which necessarily emits PlayerCommandPreprocessEvent. A Bedrock
     * player must get the same form regardless of which route opened the menu.
     *
     * Check the *actual* inventory holder and registered menu ID. Never match
     * only the GUI title, which other plugins can freely reuse.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!plugin.getConfig().getBoolean("bedrock-forms.enabled", false)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        String menuName = menuNameFromDeluxeHolder(event.getInventory().getHolder());
        if (menuName == null) return;
        Menu menu = menuIds.get(menuName.toLowerCase(Locale.ROOT));
        if (menu == null || !allowed(player, menu)) return;
        try {
            if (!isBedrockPlayer(player)) return;
            // Do not suppress the Java fallback until Bedrock confirms the
            // Form was accepted. InventoryOpenEvent is raised before the Java
            // inventory opens, so cancellation keeps that chest window off-wire.
            if (sendForm(player, menu)) event.setCancelled(true);
        } catch (Exception error) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not translate DeluxeMenus inventory " + menu.id(), error);
        }
    }

    /** Isolated reflection avoids a direct Paper plugin-classloader dependency. */
    static String menuNameFromDeluxeHolder(InventoryHolder holder) {
        if (holder == null || !holder.getClass().getName()
                .equals("com.extendedclip.deluxemenus.menu.MenuHolder")) return null;
        try {
            Object name = holder.getClass().getMethod("getMenuName").invoke(holder);
            return name instanceof String s && !s.isBlank() ? s : null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private boolean allowed(Player player, Menu menu) {
        return menu.permission().isBlank() || player.hasPermission(menu.permission());
    }

    private boolean isBedrockPlayer(Player player) {
        try {
            GeyserApi api = GeyserApi.api();
            if (api != null && (api.isBedrockPlayer(player.getUniqueId())
                    || api.connectionByUuid(player.getUniqueId()) != null)) return true;
        } catch (RuntimeException ignored) {
            // Optional API may still be initializing; Floodgate can confirm.
        }
        // On Floodgate setups the backend Java UUID can differ from the UUID
        // Geyser indexes. Ask Floodgate itself, never infer from a name prefix.
        Plugin floodgate = plugin.getServer().getPluginManager().getPlugin("floodgate");
        if (floodgate == null || !floodgate.isEnabled()) return false;
        try {
            Class<?> api = floodgate.getClass().getClassLoader().loadClass(
                    "org.geysermc.floodgate.api.FloodgateApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return Boolean.TRUE.equals(api.getMethod("isFloodgatePlayer", java.util.UUID.class)
                    .invoke(instance, player.getUniqueId()));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("bedrock-forms.enabled", false)) return;
        String message = event.getMessage().trim();
        if (!message.startsWith("/") || message.indexOf(' ') >= 0) return;
        Menu menu = commands.get(message.substring(1).toLowerCase(Locale.ROOT));
        if (menu == null) return;
        Player player = event.getPlayer();
        if (!allowed(player, menu)) return;
        try {
            if (!isBedrockPlayer(player)) return;
            if (sendForm(player, menu)) event.setCancelled(true);
        } catch (Exception failure) {
            plugin.getLogger().log(Level.WARNING, "Could not show Bedrock Form; keeping original GUI", failure);
        }
    }

    private boolean sendForm(Player player, Menu menu) {
        SimpleForm.Builder form = SimpleForm.builder().title(menu.title())
                .content("CyoriaSMP · Bedrock");
        for (Button button : menu.buttons()) {
            if (button.imageUrl() != null) {
                form.button(button.text(), FormImage.Type.URL, button.imageUrl());
            } else form.button(button.text());
        }
        form.validResultHandler(response -> {
            int index = response.clickedButtonId();
            if (index < 0 || index >= menu.buttons().size()) return;
            player.getScheduler().run(plugin, task -> {
                if (player.isOnline() && allowed(player, menu)) {
                    execute(player, menu.buttons().get(index));
                }
            }, () -> {});
        });
        org.geysermc.cumulus.form.SimpleForm ready = form.build();
        boolean sent = false;
        // A Floodgate player uses an injected Java UUID (0...9-xuid).
        // Geyser connection lookup may not index that injected UUID. Prefer the
        // authoritative Floodgate forms transport for Floodgate players.
        Plugin floodgate = plugin.getServer().getPluginManager().getPlugin("floodgate");
        if (floodgate != null && floodgate.isEnabled()) {
            try {
                Class<?> api = floodgate.getClass().getClassLoader().loadClass(
                        "org.geysermc.floodgate.api.FloodgateApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                boolean floodgatePlayer = Boolean.TRUE.equals(api.getMethod(
                        "isFloodgatePlayer", java.util.UUID.class).invoke(instance, player.getUniqueId()));
                if (floodgatePlayer) {
                    Object accepted = api.getMethod("sendForm", java.util.UUID.class,
                            org.geysermc.cumulus.form.Form.class)
                            .invoke(instance, player.getUniqueId(), ready);
                    sent = Boolean.TRUE.equals(accepted);
                }
            } catch (ReflectiveOperationException | LinkageError error) {
                plugin.getLogger().log(Level.FINE,
                        "Floodgate form API unavailable, trying Geyser API", error);
            }
        }
        if (!sent) {
            GeyserApi api = GeyserApi.api();
            if (api != null) {
                var connection = api.connectionByUuid(player.getUniqueId());
                if (connection != null) sent = connection.sendForm(ready);
                else sent = api.sendForm(player.getUniqueId(), ready);
            }
        }
        if (sent) plugin.getLogger().info("Kalo opened Bedrock Form " + menu.id()
                + " for " + player.getName());
        else plugin.getLogger().warning("No Bedrock Form transport for "
                + player.getName() + " (" + menu.id() + "); using original GUI");
        return sent;
    }

    private void execute(Player player, Button button) {
        for (Action action : button.actions()) {
            switch (action.type()) {
                case "close" -> { /* Closing a Bedrock form is implicit. */ }
                case "player" -> player.performCommand(action.argument());
                case "openguimenu" -> player.performCommand("dm open " + action.argument());
                case "message" -> player.sendMessage(strip(action.argument()));
                case "sound" -> {
                    try {
                        Sound sound = Sound.valueOf(action.argument().toUpperCase(Locale.ROOT));
                        player.playSound(player.getLocation(), sound, 1f, 1f);
                    } catch (IllegalArgumentException ignored) {
                        // An invalid cosmetic sound cannot override a menu action.
                    }
                }
                default -> throw new IllegalStateException("Unvalidated action");
            }
        }
    }
}

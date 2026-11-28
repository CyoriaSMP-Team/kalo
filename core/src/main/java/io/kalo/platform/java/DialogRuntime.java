package io.kalo.platform.java;

import io.kalo.Kalo;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kalo.content.dialog.DialogPack;
import io.kalo.content.dialog.DialogTextResolver;
import io.kalo.content.dialog.definition.DialogDefinition;
import io.kalo.integration.GeyserIntegration;
import io.kalo.pack.Json;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Opens a Kalo dialog and authorizes its clicks.
 *
 * <p>Java clients use vanilla's {@code /dialog show}; the dialog itself was written to
 * {@code datapacks/kalo-dialogs} by {@code DialogPack}. Command buttons send
 * {@code /kalo click <key>} instead of a command, and the key is accepted only while the
 * player still has the dialog that produced it. Bedrock clients use the optional Cumulus
 * bridge with the same click model.</p>
 */
public final class DialogRuntime implements Listener {

    private static final String BRIDGE = "io.kalo.converter.KaloDialogFormBridge";
    private static final Map<UUID, Shown> SHOWN = new ConcurrentHashMap<>();

    public DialogRuntime() {
    }

    private record Shown(@NotNull DialogDefinition definition,
                         @NotNull Map<String, String> placeholders) {
    }

    public static boolean show(@NotNull Player player,
                               @NotNull DialogDefinition definition) {
        return show(player, definition, Map.of());
    }

    public static boolean show(@NotNull Player player,
                               @NotNull DialogDefinition definition,
                               @NotNull Map<String, String> placeholders) {
        if (!ContentEntitlements.allows(player, definition.key())) {
            return false;
        }
        Map<String, String> context = new LinkedHashMap<>();
        context.put("player", player.getName());
        context.put("target", player.getName());
        context.put("uuid", player.getUniqueId().toString());
        context.put("world", player.getWorld().getName());
        context.putAll(placeholders);

        Shown shown = new Shown(definition, Map.copyOf(context));
        SHOWN.put(player.getUniqueId(), shown);

        if (isBedrock(player)) {
            try {
                Class<?> bridge = Class.forName(BRIDGE, true,
                        DialogRuntime.class.getClassLoader());
                Object opened = bridge.getMethod("show", Player.class,
                                DialogDefinition.class, Map.class)
                        .invoke(null, player, definition, context);
                if (Boolean.TRUE.equals(opened)) {
                    return true;
                }
            } catch (Throwable unavailable) {
                // Cumulus/Floodgate are optional. A missing or moved API must fall back
                // to the Java path rather than break the command.
                Plugins.logger().log(Level.FINE,
                        "Bedrock dialog form unavailable; trying the Java dialog command",
                        unavailable);
            }
        }

        if (openJava(player, definition, context)) {
            return true;
        }
        if (SHOWN.get(player.getUniqueId()) == shown) {
            SHOWN.remove(player.getUniqueId());
        }
        return false;
    }

    /**
     * Runs a click key sent by a Java dialog button.
     *
     * @return whether the key belonged to the dialog this player was last shown
     */
    public static boolean click(@NotNull Player player, @NotNull String key) {
        Shown shown = SHOWN.get(player.getUniqueId());
        if (shown == null || !accepts(shown.definition(), key)) {
            return false;
        }
        List<DialogDefinition.Action> actions = shown.definition().clickActions()
                .get(key.toLowerCase(Locale.ROOT));
        execute(player, actions, shown.placeholders());
        finish(player, shown);
        return true;
    }

    /** Pure authorization check used by the click route and its tests. */
    static boolean accepts(@NotNull DialogDefinition definition, @NotNull String key) {
        return definition.clickActions().containsKey(key.toLowerCase(Locale.ROOT));
    }

    /** Whether a Bedrock form still belongs to the dialog the player is on. */
    public static boolean stillShown(@NotNull Player player,
                                     @NotNull DialogDefinition definition) {
        Shown shown = SHOWN.get(player.getUniqueId());
        return shown != null && shown.definition() == definition;
    }

    /** Runs a Bedrock form button by its position in the shown definition. */
    public static boolean clickButton(@NotNull Player player, int index) {
        Shown shown = SHOWN.get(player.getUniqueId());
        if (shown == null || index < 0 || index >= shown.definition().buttons().size()) {
            return false;
        }
        DialogDefinition.Button button = shown.definition().buttons().get(index);
        if (button.actions().isEmpty()) {
            close(player);
            return true;
        }
        execute(player, button.actions(), shown.placeholders());
        finish(player, shown);
        return true;
    }

    /** Resolves the placeholders Kalo owns; unknown braces are left alone. */
    public static @NotNull String render(@NotNull String text,
                                         @NotNull Map<String, String> placeholders) {
        return DialogTextResolver.render(text, placeholders);
    }

    public static void close(@NotNull Player player) {
        if (SHOWN.remove(player.getUniqueId()) != null) {
            clearJavaDialog(player);
        }
    }

    public static void clear() {
        for (UUID playerId : List.copyOf(SHOWN.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                close(player);
            } else {
                SHOWN.remove(playerId);
            }
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        SHOWN.remove(event.getPlayer().getUniqueId());
    }

    private static boolean execute(@NotNull Player player,
                                   @NotNull List<DialogDefinition.Action> actions,
                                   @NotNull Map<String, String> placeholders) {
        for (DialogDefinition.Action action : actions) {
            String value = render(action.value(), placeholders);
            boolean success = switch (action.type()) {
                case COMMAND -> player.performCommand(withoutSlash(value));
                case CONSOLE -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        withoutSlash(value));
                case URL -> {
                    player.sendMessage(Component.text(value)
                            .clickEvent(ClickEvent.openUrl(value)));
                    yield true;
                }
                case SUGGEST -> {
                    player.sendMessage(Component.text(value)
                            .clickEvent(ClickEvent.suggestCommand(value)));
                    yield true;
                }
                case COPY -> {
                    player.sendMessage(Component.text(value)
                            .clickEvent(ClickEvent.copyToClipboard(value)));
                    yield true;
                }
                case DIALOG -> openNested(player, value);
                case CLOSE -> {
                    close(player);
                    yield true;
                }
            };
            if (!success) {
                return false;
            }
        }
        return true;
    }

    private static boolean openNested(@NotNull Player player, @NotNull String target) {
        if (!(Kalo.plugin() instanceof io.kalo.KaloPluginImpl impl)) {
            return false;
        }
        try {
            DialogDefinition next = impl.registryManager().dialogType().dialogs()
                    .get(Key.key(target));
            return next != null && show(player, next);
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    private static void finish(@NotNull Player player, @NotNull Shown shown) {
        UUID playerId = player.getUniqueId();
        if (SHOWN.get(playerId) != shown) {
            // A command opened a new dialog; leave that one alone.
            return;
        }
        switch (shown.definition().after()) {
            case SEAMLESS, WAIT_FOR_RESPONSE -> {
                SHOWN.remove(playerId);
                clearJavaDialog(player);
            }
            case CLOSE -> SHOWN.remove(playerId);
            case NONE -> {
                // Stays open, so keep the shown definition for the next click.
            }
        }
    }

    private static @NotNull String withoutSlash(@NotNull String command) {
        String value = command.trim();
        return value.startsWith("/") ? value.substring(1) : value;
    }

    /** The console command that opens a data-pack dialog for exactly one player. */
    static @NotNull String javaCommand(@NotNull Player player,
                                       @NotNull DialogDefinition definition) {
        return javaCommand(player.getUniqueId(), definition.key());
    }

    static @NotNull String javaCommand(@NotNull UUID playerId,
                                       @NotNull Key key) {
        return "dialog show " + playerId + " " + key.asString();
    }

    /**
     * The inline dialog command when open-time placeholders changed the static data-pack
     * representation. Null means use the registered data-pack id.
     */
    static @Nullable String inlineCommand(@NotNull UUID playerId,
                                          @NotNull DialogDefinition definition,
                                          @NotNull Map<String, String> placeholders) {
        JsonObject staticJson = DialogPack.javaDialog(definition);
        JsonElement resolved = DialogTextResolver.resolve(staticJson, placeholders);
        if (resolved.equals(staticJson)) {
            return null;
        }
        String json = Json.write(resolved);
        if (json.length() > 30000) {
            Plugins.logger().warning("Dialog " + definition.key().asString()
                    + " is too large for an inline placeholder command;"
                    + " using the static data-pack id");
            return null;
        }
        return "dialog show " + playerId + " " + json;
    }

    private static boolean openJava(@NotNull Player player,
                                    @NotNull DialogDefinition definition,
                                    @NotNull Map<String, String> placeholders) {
        try {
            // /dialog requires permission level 2, so a player cannot run it for
            // themselves. The console sender supplies the permission while the UUID
            // target keeps the selector independent of Bedrock gamertag characters.
            String inline = inlineCommand(player.getUniqueId(), definition, placeholders);
            if (inline != null && Bukkit.dispatchCommand(Bukkit.getConsoleSender(), inline)) {
                return true;
            }
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    javaCommand(player, definition));
        } catch (RuntimeException unsupported) {
            Plugins.logger().log(Level.FINE,
                    "This server does not support the vanilla dialog command", unsupported);
            return false;
        }
    }

    private static void clearJavaDialog(@NotNull Player player) {
        if (isBedrock(player)) {
            return;
        }
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "dialog clear " + player.getUniqueId());
        } catch (RuntimeException unsupported) {
            Plugins.logger().log(Level.FINE,
                    "This server does not support the vanilla dialog command", unsupported);
        }
    }

    static boolean isBedrock(@NotNull Player player) {
        if (GeyserIntegration.support().isBedrock(player.getUniqueId())) {
            return true;
        }
        var floodgate = Bukkit.getPluginManager().getPlugin("floodgate");
        if (floodgate == null || !floodgate.isEnabled()) {
            return false;
        }
        try {
            Class<?> api = floodgate.getClass().getClassLoader()
                    .loadClass("org.geysermc.floodgate.api.FloodgateApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return Boolean.TRUE.equals(api.getMethod("isFloodgatePlayer", java.util.UUID.class)
                    .invoke(instance, player.getUniqueId()));
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return false;
        }
    }
}

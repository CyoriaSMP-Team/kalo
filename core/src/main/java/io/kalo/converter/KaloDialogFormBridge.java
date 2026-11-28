package io.kalo.converter;

import io.kalo.content.dialog.definition.DialogDefinition;
import io.kalo.platform.java.DialogRuntime;
import io.kalo.utils.Plugins;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.geysermc.cumulus.form.SimpleForm;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.logging.Level;

/**
 * Bedrock transport for Kalo's own dialog definitions.
 *
 * <p>This class names Cumulus/Geyser types and is therefore only loaded reflectively by
 * {@code DialogRuntime} on a server where at least one Bedrock stack is present.</p>
 */
public final class KaloDialogFormBridge {

    private KaloDialogFormBridge() {
    }

    public static boolean show(@NotNull Player player, @NotNull DialogDefinition definition,
                               @NotNull Map<String, String> placeholders) {
        SimpleForm.Builder form = SimpleForm.builder()
                .title(DialogRuntime.render(definition.title(), placeholders))
                .content(DialogRuntime.render(definition.body(), placeholders));
        if (definition.buttons().isEmpty()) {
            // Raw Java dialogs such as `notice` have no `actions` array. A form still
            // needs at least one way out.
            form.button("Close");
            form.validResultHandler(response -> player.getScheduler().run(Plugins.plugin(),
                    ignored -> {
                        if (player.isOnline()) {
                            DialogRuntime.close(player);
                        }
                    }, null));
        } else {
            for (DialogDefinition.Button button : definition.buttons()) {
                String label = DialogRuntime.render(button.label(), placeholders);
                if (!button.tooltip().isBlank()) {
                    label += "\n" + DialogRuntime.render(button.tooltip(), placeholders);
                }
                form.button(label);
            }
            form.validResultHandler(response -> {
                int index = response.clickedButtonId();
                if (index < 0 || index >= definition.buttons().size()) {
                    return;
                }
                player.getScheduler().run(Plugins.plugin(), ignored -> {
                    if (player.isOnline()) {
                        boolean handled = DialogRuntime.clickButton(player, index);
                        if (handled && definition.after() == DialogDefinition.After.NONE
                                && DialogRuntime.stillShown(player, definition)) {
                            // Cumulus closes the form after a response. `after: none` means
                            // the screen should stay, so send the same definition again.
                            show(player, definition, placeholders);
                        }
                    }
                }, null);
            });
        }

        SimpleForm ready = form.build();
        boolean sent = false;
        Plugin floodgate = player.getServer().getPluginManager().getPlugin("floodgate");
        if (floodgate != null && floodgate.isEnabled()) {
            try {
                Class<?> api = floodgate.getClass().getClassLoader()
                        .loadClass("org.geysermc.floodgate.api.FloodgateApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                boolean floodgatePlayer = Boolean.TRUE.equals(api.getMethod(
                        "isFloodgatePlayer", java.util.UUID.class)
                        .invoke(instance, player.getUniqueId()));
                if (floodgatePlayer) {
                    Object accepted = api.getMethod("sendForm", java.util.UUID.class,
                            org.geysermc.cumulus.form.Form.class)
                            .invoke(instance, player.getUniqueId(), ready);
                    sent = Boolean.TRUE.equals(accepted);
                }
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                Plugins.logger().log(Level.FINE,
                        "Floodgate form API unavailable; trying Geyser", unavailable);
            }
        }
        if (!sent) {
            Plugin geyser = player.getServer().getPluginManager().getPlugin("Geyser-Spigot");
            if (geyser != null && geyser.isEnabled()) {
                sent = io.kalo.integration.GeyserAccess.sendForm(
                        geyser.getClass().getClassLoader(), player.getUniqueId(), ready);
            }
        }
        return sent;
    }
}

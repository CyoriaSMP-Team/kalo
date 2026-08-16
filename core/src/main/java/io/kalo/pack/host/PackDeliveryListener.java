package io.kalo.pack.host;

import io.kalo.utils.Plugins;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Sends the generated pack to players as they join.
 *
 * <p>The SHA-1 is the load-bearing part. Without it the client cannot tell one version of
 * a pack from another and re-downloads on every join; with it, an unchanged pack is served
 * from cache — which is also why {@code ZipPackWriter} is deterministic.</p>
 */
public final class PackDeliveryListener implements Listener {

    private final PackHost host;
    private final boolean required;

    public PackDeliveryListener(@NotNull PackHost host, boolean required) {
        this.host = host;
        this.required = required;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    public void send(@NotNull Player player) {
        if (!host.available()) {
            return;
        }
        try {
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo()
                    // Derived from the URL so the same pack keeps the same id across
                    // joins, and a regenerated one gets a new id.
                    .id(UUID.nameUUIDFromBytes(host.url().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .uri(URI.create(host.url()))
                    .hash(host.sha1())
                    .build();

            player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    .required(required)
                    .prompt(Component.text("This server uses custom content.", NamedTextColor.GRAY))
                    .build());
        } catch (Exception e) {
            // One player failing to receive a pack must not disturb their join.
            Plugins.logger().log(Level.WARNING, "Could not send the resource pack to " + player.getName(), e);
        }
    }
}

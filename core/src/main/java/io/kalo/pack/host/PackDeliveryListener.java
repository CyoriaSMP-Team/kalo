package io.kalo.pack.host;

import io.kalo.pack.ResourcePack;
import io.kalo.pack.ZipPackWriter;
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
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;
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
        PackHost.Snapshot snapshot = host.snapshot();
        if (snapshot == null) {
            return;
        }
        try {
            player.sendResourcePacks(request(List.of(info(snapshot))));
        } catch (Exception e) {
            // One player failing to receive a pack must not disturb their join.
            Plugins.logger().log(Level.WARNING, "Could not send the resource pack to " + player.getName(), e);
        }
    }

    /**
     * Sends the global pack plus a per-player overlay in one request, so the overlay
     * cannot replace the global pack. The overlay keeps the caller's stable pack id.
     */
    public boolean sendOverlay(@NotNull Player player, @NotNull UUID overlayId,
                               @NotNull ResourcePack overlay) {
        try {
            byte[] bytes = ZipPackWriter.toBytes(overlay);
            ResourcePackRequest request = requestWithOverlay(bytes, overlayId);
            if (request == null) {
                return false;
            }
            player.sendResourcePacks(request);
            return true;
        } catch (Exception e) {
            Plugins.logger().log(Level.WARNING,
                    "Could not send the emote skin overlay to " + player.getName(), e);
            return false;
        }
    }

    /**
     * Visible for tests: only the overlay pack is sent, with {@code replace(false)} so the
     * client stacks it on top of the global pack and any packs from other plugins.
     */
    public @Nullable ResourcePackRequest requestWithOverlay(byte @NotNull [] overlayBytes,
                                                            @NotNull UUID overlayId) {
        PackHost.Snapshot overlay = host.publishOverlay(overlayBytes);
        if (overlay == null) {
            return null;
        }
        return request(List.of(info(overlay, overlayId)), false, false);
    }

    private @NotNull ResourcePackRequest request(@NotNull List<ResourcePackInfo> packs) {
        return request(packs, true, required);
    }

    private @NotNull ResourcePackRequest request(@NotNull List<ResourcePackInfo> packs,
                                                 boolean replace, boolean required) {
        return ResourcePackRequest.resourcePackRequest()
                .packs(packs)
                .required(required)
                .replace(replace)
                .prompt(Component.text("This server uses custom content.",
                        NamedTextColor.GRAY))
                .build();
    }

    private static @NotNull ResourcePackInfo info(@NotNull PackHost.Snapshot snapshot) {
        return info(snapshot, UUID.nameUUIDFromBytes(
                snapshot.url().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static @NotNull ResourcePackInfo info(@NotNull PackHost.Snapshot snapshot,
                                                  @NotNull UUID id) {
        return ResourcePackInfo.resourcePackInfo()
                .id(id)
                .uri(URI.create(snapshot.url()))
                .hash(snapshot.sha1())
                .build();
    }
}

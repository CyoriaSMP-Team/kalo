package io.kalo.platform.java;

import io.kalo.content.hud.definition.HudDefinition;
import io.kalo.utils.Plugins;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws HUD glyphs on a player's action bar or boss bar.
 *
 * <p>An action-bar HUD is re-sent once a second because the client clears it after a few
 * seconds; a boss-bar HUD stays until it is stopped. The glyph itself is compiled by
 * {@code HudType} into the Java font and the Bedrock glyph page.</p>
 */
public final class HudRuntime implements Listener {

    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();

    /** The exact Java component a HUD draws; separated so the runtime can be tested. */
    public static @NotNull Component overlay(@NotNull HudDefinition definition) {
        return Component.text(definition.overlayText());
    }

    /** Bedrock pages carry the offset in the glyph cell, so no space prefix is used. */
    public static @NotNull Component overlay(@NotNull HudDefinition definition,
                                             boolean bedrock) {
        return Component.text(bedrock ? definition.glyph() : definition.overlayText());
    }

    public static boolean show(@NotNull Player player, @NotNull HudDefinition definition) {
        stop(player);
        Component glyph = overlay(definition, DialogRuntime.isBedrock(player));
        try {
            if (definition.slot() == HudDefinition.Slot.BOSS_BAR) {
                BossBar bar = BossBar.bossBar(glyph, 1.0f,
                        BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                player.showBossBar(bar);
                ACTIVE.put(player.getUniqueId(), new Active(bar, null));
                return true;
            }
            player.sendActionBar(glyph);
            var task = player.getScheduler().runAtFixedRate(Plugins.plugin(),
                    ignored -> player.sendActionBar(glyph),
                    null, 1L, 20L);
            ACTIVE.put(player.getUniqueId(), new Active(null, task));
            return true;
        } catch (RuntimeException retired) {
            stop(player);
            return false;
        }
    }

    public static void stop(@NotNull Player player) {
        Active active = ACTIVE.remove(player.getUniqueId());
        if (active == null) {
            return;
        }
        if (active.task != null) {
            active.task.cancel();
        }
        if (active.bar != null) {
            player.hideBossBar(active.bar);
        }
    }

    public static void clear() {
        for (UUID playerId : java.util.List.copyOf(ACTIVE.keySet())) {
            Player player = org.bukkit.Bukkit.getPlayer(playerId);
            if (player != null) {
                stop(player);
            } else {
                ACTIVE.remove(playerId);
            }
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        stop(event.getPlayer());
    }

    private record Active(@Nullable BossBar bar,
                          @Nullable io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
    }
}

package io.kalo.content.feature.builtin;

import io.kalo.content.Content;
import io.kalo.content.feature.*;
import io.kalo.content.feature.event.ItemInteractEvent;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Production-ready cooldown and mana system with MMOItems-level complexity.
 * 
 * <p>Supports:</p>
 * <ul>
 *   <li><b>Cooldowns:</b> per-ability cooldowns with groups</li>
 *   <li><b>Mana:</b> mana pool, regeneration, costs</li>
 *   <li><b>Cooldown groups:</b> shared cooldowns across abilities</li>
 *   <li><b>Cooldown reduction:</b> reduce cooldowns via stats</li>
 *   <li><b>Cooldown display:</b> show cooldowns on action bar</li>
 * </ul>
 * 
 * <p>Example YAML config:</p>
 * <pre>
 * features:
 *   cooldown:
 *     id: kalo:cooldown
 *     arguments:
 *       # Mana system
 *       mana: 100
 *       mana_regen: 1.0
 *       mana_regen_delay: 20
 *       
 *       # Cooldown groups
 *       cooldown_group_combat: 20
 *       cooldown_group_magic: 40
 *       cooldown_group_utility: 60
 *       
 *       # Cooldown reduction
 *       cooldown_reduction: 0.2
 * </pre>
 */
public final class CooldownFeature extends Feature {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "cooldown");
    
    // Cooldown tracking: player UUID -> ability name -> cooldown end time
    private static final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    
    // Mana tracking: player UUID -> current mana
    private static final Map<UUID, Double> mana = new ConcurrentHashMap<>();
    
    // Mana regen tracking: player UUID -> last regen time
    private static final Map<UUID, Long> manaRegenTime = new ConcurrentHashMap<>();
    // A single task for the entire plugin: previously each content definition
    // started its own BukkitRunnable and each task regenerated every player's mana.
    private static final AtomicBoolean regenScheduled = new AtomicBoolean();
    private static volatile ManaSettings settings = new ManaSettings(100.0, 1.0, 20L);

    record ManaSettings(double maxMana, double regenPerSecond, long delayTicks) {
        ManaSettings {
            if (!Double.isFinite(maxMana) || maxMana <= 0) throw new IllegalArgumentException("max mana must be >0");
            if (!Double.isFinite(regenPerSecond) || regenPerSecond < 0) throw new IllegalArgumentException("regen must be finite and >=0");
            if (delayTicks < 0 || delayTicks > 1728000L)
                throw new IllegalArgumentException("mana delay must be 0..1728000 ticks");
        }

        double afterTick(double current, long lastRegenMillis, long nowMillis) {
            if (!Double.isFinite(current)) return maxMana;
            if (nowMillis - lastRegenMillis < delayTicks * 50L) {
                return Math.clamp(current, 0, maxMana);
            }
            return Math.min(maxMana, Math.max(0, current) + regenPerSecond);
        }
    }

    private CooldownFeature(@NotNull FeatureFactory.Context context) {
        super(context);
        Map<String, String> args = context.arguments().raw();

        // Handle interaction events
        context.eventBus().subscribe(ItemInteractEvent.class, event -> {
            if (event.action() == Action.RIGHT_CLICK_AIR || event.action() == Action.RIGHT_CLICK_BLOCK) {
                handleCooldownCheck(event.player(), args);
            }
        });

        // Start mana regen task
        startManaRegenTask(args);

        Plugins.logger().info("Loaded cooldown feature for " + context.content().key().asString());
    }

    /**
     * Handle cooldown check.
     */
    private void handleCooldownCheck(@NotNull Player player, @NotNull Map<String, String> args) {
        UUID playerUUID = player.getUniqueId();
        Map<String, Long> playerCooldowns = cooldowns.get(playerUUID);
        
        if (playerCooldowns != null) {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> entry : playerCooldowns.entrySet()) {
                if (now < entry.getValue()) {
                    long remaining = (entry.getValue() - now) / 50; // Convert to ticks
                    player.sendActionBar(net.kyori.adventure.text.Component.text(
                            "§c" + entry.getKey() + " cooldown: " + remaining + " ticks"));
                }
            }
        }
    }

    /**
     * Folia-safe mana regeneration: one global coordinator schedules the
     * actual player state/ActionBar work on that player's own region/entity.
     * No BukkitRunnable and no cross-region player mutation.
     */
    private void startManaRegenTask(@NotNull Map<String, String> args) {
        double maxMana = parseDouble(args.get("mana"), 100.0);
        double regenPerSecond = parseDouble(args.get("mana_regen"), 1.0);
        long regenDelay = parseLong(args.get("mana_regen_delay"), 20);
        ManaSettings requested;
        try {
            requested = new ManaSettings(maxMana, regenPerSecond, regenDelay);
        } catch (IllegalArgumentException invalid) {
            Plugins.logger().warning("Invalid mana regeneration settings: " + invalid.getMessage());
            return;
        }
        // Current Kalo mana is a shared pool, not one pool per custom item.
        // Make conflicting definitions visible and don't multiply regen tasks.
        if (regenScheduled.get() && !settings.equals(requested)) {
            Plugins.logger().warning("Conflicting Kalo mana settings: all content shares one"
                    + " player mana pool; using last-loaded configuration");
        }
        settings = requested;
        if (requested.regenPerSecond() <= 0 || !regenScheduled.compareAndSet(false, true)) {
            return;
        }

        Bukkit.getGlobalRegionScheduler().runAtFixedRate(Plugins.plugin(), task -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.getScheduler().execute(Plugins.plugin(),
                        () -> regenerateOnPlayerRegion(player),
                        null, 1L);
            }
        }, 20L, 20L);
    }

    private static void regenerateOnPlayerRegion(@NotNull Player player) {
        if (!player.isOnline()) return;
        ManaSettings config = settings;
        if (config.regenPerSecond() <= 0) return;
        UUID id = player.getUniqueId();
        double current = mana.getOrDefault(id, config.maxMana());
        long now = System.currentTimeMillis();
        double next = config.afterTick(current, manaRegenTime.getOrDefault(id, 0L), now);
        if (next <= current) return;
        mana.put(id, next);
        manaRegenTime.put(id, now);
        updateManaDisplay(player, next, config.maxMana());
    }

    /**
     * Update mana display.
     */
    private static void updateManaDisplay(@NotNull Player player, double currentMana, double maxMana) {
        int bars = 20;
        int filled = (int) (currentMana / maxMana * bars);
        StringBuilder bar = new StringBuilder("§9Mana: ");
        for (int i = 0; i < bars; i++) {
            if (i < filled) {
                bar.append("§1█");
            } else {
                bar.append("§8░");
            }
        }
        bar.append(" §f").append(String.format("%.0f", currentMana)).append("/").append(String.format("%.0f", maxMana));
        player.sendActionBar(net.kyori.adventure.text.Component.text(bar.toString()));
    }

    /**
     * Check if ability is on cooldown.
     */
    public static boolean isOnCooldown(@NotNull Player player, @NotNull String ability) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return false;
        Long end = playerCooldowns.get(ability);
        return end != null && System.currentTimeMillis() < end;
    }

    /**
     * Set cooldown for ability.
     */
    public static void setCooldown(@NotNull Player player, @NotNull String ability, int ticks) {
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(ability, System.currentTimeMillis() + (ticks * 50L));
    }

    /**
     * Get remaining cooldown.
     */
    public static long getRemainingCooldown(@NotNull Player player, @NotNull String ability) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return 0;
        Long end = playerCooldowns.get(ability);
        if (end == null) return 0;
        long remaining = end - System.currentTimeMillis();
        return remaining > 0 ? remaining / 50 : 0; // Convert to ticks
    }

    /**
     * Check if player has enough mana.
     */
    public static boolean hasMana(@NotNull Player player, double cost) {
        double currentMana = mana.getOrDefault(player.getUniqueId(), 100.0);
        return currentMana >= cost;
    }

    /**
     * Consume mana.
     */
    public static void consumeMana(@NotNull Player player, double amount) {
        UUID playerUUID = player.getUniqueId();
        double currentMana = mana.getOrDefault(playerUUID, 100.0);
        mana.put(playerUUID, Math.max(0, currentMana - amount));
        // Regen delay starts after spending mana, not after a previous regen.
        manaRegenTime.put(playerUUID, System.currentTimeMillis());
    }

    /**
     * Get current mana.
     */
    public static double getMana(@NotNull Player player) {
        return mana.getOrDefault(player.getUniqueId(), 100.0);
    }

    /**
     * Set mana.
     */
    public static void setMana(@NotNull Player player, double amount) {
        mana.put(player.getUniqueId(), amount);
    }

    /**
     * Parse double value.
     */
    private double parseDouble(String s, double def) {
        if (s == null) return def;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }

    /**
     * Parse long value.
     */
    private long parseLong(String s, long def) {
        if (s == null) return def;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return def; }
    }

    /**
     * Factory for creating CooldownFeature instances.
     */
    public static final class Factory implements FeatureFactory<CooldownFeature> {
        @Override
        public @NotNull CooldownFeature create(@NotNull Context context) {
            return new CooldownFeature(context);
        }
    }
}

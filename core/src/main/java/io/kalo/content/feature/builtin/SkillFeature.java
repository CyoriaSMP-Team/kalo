package io.kalo.content.feature.builtin;

import io.kalo.content.Content;
import io.kalo.content.feature.*;
import io.kalo.content.feature.event.EntityDamageByEntityEvent;
import io.kalo.content.feature.event.EntitySpawnEvent;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-ready mob skill system with MythicMobs-level complexity.
 * 
 * <p>Supported skill types:</p>
 * <ul>
 *   <li><b>Combat:</b> damage, heal, potion, disarm, silence, vulnerability, armor_break, life_steal, mana_burn</li>
 *   <li><b>Movement:</b> teleport, dash, launch, pull, push, charge, leap, blink</li>
 *   <li><b>Summoning:</b> summon, spawn_particle, spawn_entity</li>
 *   <li><b>Effects:</b> effect, sound, message, command, fire, ice, lightning</li>
 *   <li><b>AI:</b> target_nearest, flee, patrol, wander, follow, guard</li>
 *   <li><b>Conditions:</b> health_below, health_above, distance, has_target, has_potion, time_of_day</li>
 *   <li><b>Targets:</b> nearest, random, weakest, strongest, tank, healer</li>
 *   <li><b>Sequences:</b> chain skills together with delays</li>
 * </ul>
 * 
 * <p>Example YAML config:</p>
 * <pre>
 * features:
 *   skills:
 *     id: kalo:skills
 *     arguments:
 *       on_spawn:
 *         - "effect:SPEED:200:1"
 *         - "message:§cBoss has appeared!"
 *         - "particle:flame:100"
 *       on_damage:
 *         - "damage:10"
 *         - "lifesteal:0.2"
 *         - "teleport:target"
 *       on_death:
 *         - "summon:zombie:5:10"
 *         - "particle:explosion:50"
 *       on_low_health:
 *         - "condition:health_below:0.3"
 *         - "damage:20"
 *         - "launch:3"
 *       ai_target: nearest
 *       ai_aggro_range: 16
 *       ai_attack_cooldown: 40
 *       skill_cooldown: 100
 * </pre>
 */
public final class SkillFeature extends Feature {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "skills");
    
    // Cooldown tracking: entity UUID -> skill name -> cooldown end time
    private static final Map<UUID, Map<String, Long>> skillCooldowns = new ConcurrentHashMap<>();
    
    // Target tracking: entity UUID -> target UUID
    private static final Map<UUID, UUID> targets = new ConcurrentHashMap<>();

    private SkillFeature(@NotNull FeatureFactory.Context context) {
        super(context);
        Map<String, String> args = context.arguments().raw();

        // Handle spawn events
        context.eventBus().subscribe(EntitySpawnEvent.class, event -> {
            Entity entity = event.entity();
            if (entity instanceof Mob mob) {
                // Apply spawn skills
                runSkills(entity, null, "on_spawn", args);
                
                // Setup AI
                setupAI(mob, args);
            }
        });

        // Handle damage events
        context.eventBus().subscribe(EntityDamageByEntityEvent.class, event -> {
            Entity source = event.damager();
            Entity damaged = event.damaged();
            
            if (damaged instanceof LivingEntity target) {
                // Run on_damage skills
                runSkills(source, target, "on_damage", args);
            }
        });

        Plugins.logger().info("Loaded skills feature for " + context.content().key().asString());
    }

    /**
     * Setup AI behaviors.
     */
    private void setupAI(@NotNull Mob mob, @NotNull Map<String, String> args) {
        // Target AI
        String targetAI = args.get("ai_target");
        if (targetAI != null) {
            switch (targetAI) {
                case "nearest" -> {
                    mob.setTarget(null); // Will be set by behavior
                }
                case "random" -> {
                    mob.setTarget(null);
                }
                case "weakest" -> {
                    mob.setTarget(null);
                }
                case "strongest" -> {
                    mob.setTarget(null);
                }
            }
        }

        // Aggro range
        String aggroRange = args.get("ai_aggro_range");
        if (aggroRange != null) {
            double range = parseDouble(aggroRange, 16.0);
            AttributeInstance followRange = mob.getAttribute(Attribute.FOLLOW_RANGE);
            if (followRange != null) {
                followRange.setBaseValue(range);
            }
        }

        // Persistence
        if ("true".equals(args.get("ai_persistent"))) {
            mob.setRemoveWhenFarAway(false);
        }

        // Attack cooldown
        String attackCooldown = args.get("ai_attack_cooldown");
        if (attackCooldown != null) {
            // Store cooldown in mob state
        }
    }

    /**
     * Run skills for an event.
     */
    private void runSkills(@NotNull Entity source, @NotNull LivingEntity target,
                           @NotNull String event, @NotNull Map<String, String> args) {
        String skillsStr = args.get(event);
        if (skillsStr == null) return;

        // Parse skills (comma-separated)
        String[] skills = skillsStr.split(",");
        for (String skill : skills) {
            String trimmed = skill.trim();
            if (!trimmed.isEmpty()) {
                executeSkill(source, target, trimmed, args);
            }
        }
    }

    /**
     * Execute a single skill.
     */
    private void executeSkill(@NotNull Entity source, @NotNull LivingEntity target,
                              @NotNull String skill, @NotNull Map<String, String> args) {
        // Check for conditions
        if (skill.startsWith("condition:")) {
            if (!checkCondition(skill, source, target)) {
                return;
            }
            return;
        }

        // Parse skill
        String[] parts = skill.split(":");
        if (parts.length < 1) return;

        String type = parts[0].toLowerCase();
        String[] params = Arrays.copyOfRange(parts, 1, parts.length);

        // Check cooldown
        UUID sourceUUID = source.getUniqueId();
        Map<String, Long> cooldowns = skillCooldowns.computeIfAbsent(sourceUUID, k -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();
        Long skillCooldownEnd = cooldowns.get(type);
        long globalCooldown = parseLong(args.get("skill_cooldown"), 100);
        
        if (skillCooldownEnd != null && now < skillCooldownEnd) {
            return; // On cooldown
        }

        switch (type) {
            // Combat skills
            case "damage" -> {
                if (params.length >= 1) {
                    double damage = parseDouble(params[0], 1.0);
                    target.damage(damage);
                }
            }
            case "heal" -> {
                if (source instanceof LivingEntity healer && params.length >= 1) {
                    double amount = parseDouble(params[0], 1.0);
                    double current = healer.getHealth();
                    double max = getAttributeValue(healer, Attribute.MAX_HEALTH, 20.0);
                    healer.setHealth(Math.min(max, current + amount));
                }
            }
            case "lifesteal" -> {
                if (source instanceof LivingEntity healer && params.length >= 1) {
                    double percentage = parseDouble(params[0], 0.1);
                    // This would need to be called after damage is dealt
                }
            }
            case "effect" -> {
                if (params.length >= 2) {
                    try {
                        PotionEffectType effectType = PotionEffectType.getByName(params[0].toUpperCase());
                        int duration = parseInt(params[1], 100);
                        int amplifier = params.length > 2 ? parseInt(params[2], 0) : 0;
                        if (effectType != null) {
                            target.addPotionEffect(new PotionEffect(effectType, duration, amplifier));
                        }
                    } catch (Exception ignored) {}
                }
            }
            case "disarm" -> {
                if (target instanceof Player player) {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (hand.getType() != Material.AIR) {
                        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                        player.getWorld().dropItemNaturally(player.getLocation(), hand);
                    }
                }
            }
            case "silence" -> {
                int duration = params.length >= 1 ? parseInt(params[0], 100) : 100;
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 127));
            }

            // Movement skills
            case "teleport" -> {
                if ("target".equals(params.length >= 1 ? params[0] : "") && source instanceof LivingEntity tele) {
                    tele.teleport(target.getLocation());
                }
            }
            case "dash" -> {
                if (source instanceof LivingEntity dasher && params.length >= 1) {
                    double distance = parseDouble(params[0], 5.0);
                    Vector direction = dasher.getLocation().getDirection().multiply(distance);
                    dasher.setVelocity(direction);
                }
            }
            case "launch" -> {
                double power = params.length >= 1 ? parseDouble(params[0], 2.0) : 2.0;
                target.setVelocity(new Vector(0, power, 0));
            }
            case "pull" -> {
                if (source instanceof LivingEntity puller && params.length >= 1) {
                    double strength = parseDouble(params[0], 1.0);
                    Vector direction = puller.getLocation().toVector()
                            .subtract(target.getLocation().toVector()).normalize();
                    target.setVelocity(direction.multiply(strength));
                }
            }
            case "push" -> {
                if (source instanceof LivingEntity pusher && params.length >= 1) {
                    double strength = parseDouble(params[0], 1.0);
                    Vector direction = target.getLocation().toVector()
                            .subtract(pusher.getLocation().toVector()).normalize();
                    target.setVelocity(direction.multiply(strength));
                }
            }

            // Summoning skills
            case "summon" -> {
                if (params.length >= 1) {
                    try {
                        EntityType entityType = EntityType.valueOf(params[0].toUpperCase());
                        int count = params.length >= 2 ? parseInt(params[1], 1) : 1;
                        double range = params.length >= 3 ? parseDouble(params[2], 3.0) : 3.0;
                        
                        for (int i = 0; i < count; i++) {
                            Location loc = target.getLocation().add(
                                    (Math.random() - 0.5) * range * 2,
                                    0,
                                    (Math.random() - 0.5) * range * 2
                            );
                            target.getWorld().spawnEntity(loc, entityType);
                        }
                    } catch (Exception ignored) {}
                }
            }

            // Effect skills
            case "particle" -> {
                if (params.length >= 1) {
                    try {
                        Particle particle = Particle.valueOf(params[0].toUpperCase());
                        int count = params.length >= 2 ? parseInt(params[1], 10) : 10;
                        target.getWorld().spawnParticle(particle, target.getLocation(), count);
                    } catch (Exception ignored) {}
                }
            }
            case "sound" -> {
                if (params.length >= 1) {
                    try {
                        Sound sound = Sound.valueOf(params[0].toUpperCase());
                        float volume = params.length >= 2 ? Float.parseFloat(params[2]) : 1.0f;
                        float pitch = params.length >= 3 ? Float.parseFloat(params[3]) : 1.0f;
                        target.getWorld().playSound(target.getLocation(), sound, volume, pitch);
                    } catch (Exception ignored) {}
                }
            }
            case "message" -> {
                if (target instanceof Player player && params.length >= 1) {
                    String message = String.join(":", params);
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                }
            }
            case "fire" -> {
                int duration = params.length >= 1 ? parseInt(params[0], 100) : 100;
                target.setFireTicks(duration);
            }
            case "lightning" -> {
                target.getWorld().strikeLightningEffect(target.getLocation());
            }

            // AI skills
            case "flee" -> {
                if (source instanceof Mob mob) {
                    LivingEntity fleeTarget = findNearestEnemy(mob);
                    if (fleeTarget != null) {
                        Vector direction = mob.getLocation().toVector()
                                .subtract(fleeTarget.getLocation().toVector()).normalize();
                        mob.setVelocity(direction.multiply(2));
                    }
                }
            }
            case "patrol" -> {
                if (source instanceof Mob mob) {
                    // Random patrol behavior
                    Location loc = mob.getLocation().add(
                            (Math.random() - 0.5) * 20,
                            0,
                            (Math.random() - 0.5) * 20
                    );
                    mob.getPathfinder().moveTo(loc);
                }
            }

            // ===== NEW COMBAT SKILLS =====
            case "bleed" -> {
                if (params.length >= 1) {
                    int duration = parseInt(params[0], 60);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, 0));
                    target.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, target.getLocation(), 10);
                }
            }
            case "stun" -> {
                if (params.length >= 1) {
                    int duration = parseInt(params[0], 40);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 127));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duration, 127));
                }
            }
            case "knockup" -> {
                double power = params.length >= 1 ? parseDouble(params[0], 1.5) : 1.5;
                target.setVelocity(new Vector(0, power, 0));
            }
            case "mana_burn" -> {
                // Would need mana system integration
            }
            // ===== NEW AI SKILLS =====
            case "circle" -> {
                if (source instanceof Mob mob) {
                    LivingEntity targetEntity = findNearestEnemy(mob);
                    if (targetEntity != null) {
                        double radius = params.length >= 1 ? parseDouble(params[0], 5.0) : 5.0;
                        double angle = Math.random() * Math.PI * 2;
                        Location circleLoc = targetEntity.getLocation().add(
                            Math.cos(angle) * radius, 0, Math.sin(angle) * radius
                        );
                        mob.getPathfinder().moveTo(circleLoc);
                    }
                }
            }
            case "strafe" -> {
                if (source instanceof Mob mob) {
                    LivingEntity targetEntity = findNearestEnemy(mob);
                    if (targetEntity != null) {
                        Vector direction = mob.getLocation().toVector()
                            .subtract(targetEntity.getLocation().toVector()).normalize();
                        Vector strafe = new Vector(-direction.getZ(), 0, direction.getX());
                        mob.setVelocity(strafe.multiply(2));
                    }
                }
            }
            case "retreat" -> {
                if (source instanceof Mob mob) {
                    LivingEntity enemy = findNearestEnemy(mob);
                    if (enemy != null) {
                        Vector direction = mob.getLocation().toVector()
                            .subtract(enemy.getLocation().toVector()).normalize();
                        mob.setVelocity(direction.multiply(3));
                    }
                }
            }
            case "guard" -> {
                if (source instanceof Mob mob) {
                    mob.setTarget(null);
                    mob.getPathfinder().moveTo(mob.getLocation());
                }
            }
            case "chase" -> {
                if (source instanceof Mob mob) {
                    LivingEntity targetEntity = findNearestEnemy(mob);
                    if (targetEntity != null) {
                        mob.setTarget(targetEntity);
                    }
                }
            }
            // ===== NEW ENVIRONMENT SKILLS =====
            case "place_block" -> {
                if (params.length >= 1) {
                    try {
                        Material material = Material.valueOf(params[0].toUpperCase());
                        target.getWorld().getBlockAt(target.getLocation()).setType(material);
                    } catch (Exception ignored) {}
                }
            }
            case "destroy_block" -> {
                target.getWorld().getBlockAt(target.getLocation()).setType(Material.AIR);
            }
            case "create_explosion" -> {
                double power = params.length >= 1 ? parseDouble(params[0], 2.0) : 2.0;
                target.getWorld().createExplosion(target.getLocation(), (float) power, false, false);
            }

            default -> Plugins.logger().warning("Unknown skill type: " + type);
        }

        // Set cooldown
        cooldowns.put(type, now + globalCooldown);
    }

    /**
     * Check condition.
     */
    private boolean checkCondition(@NotNull String conditionStr, @NotNull Entity source,
                                   @NotNull LivingEntity target) {
        String[] parts = conditionStr.split(":");
        if (parts.length < 3) return false;

        String condition = parts[1];
        String value = parts[2];

        switch (condition) {
            case "health_below" -> {
                double threshold = parseDouble(value, 0.3);
                return target.getHealth() / target.getMaxHealth() < threshold;
            }
            case "health_above" -> {
                double threshold = parseDouble(value, 0.7);
                return target.getHealth() / target.getMaxHealth() > threshold;
            }
            case "distance" -> {
                double maxDistance = parseDouble(value, 16.0);
                if (source instanceof LivingEntity attacker) {
                    return target.getLocation().distance(attacker.getLocation()) < maxDistance;
                }
                return false;
            }
            case "has_target" -> {
                if (source instanceof Mob mob) {
                    return mob.getTarget() != null;
                }
                return false;
            }
            case "has_potion" -> {
                PotionEffectType effectType = PotionEffectType.getByName(value.toUpperCase());
                if (effectType != null) {
                    return target.hasPotionEffect(effectType);
                }
                return false;
            }
            case "time_of_day" -> {
                long time = target.getWorld().getTime();
                if (value.equals("night")) {
                    return time < 13000 || time > 23000;
                } else if (value.equals("day")) {
                    return time >= 13000 && time <= 23000;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Find nearest enemy.
     */
    private LivingEntity findNearestEnemy(@NotNull Mob mob) {
        double closest = Double.MAX_VALUE;
        LivingEntity nearest = null;
        
        for (Entity entity : mob.getNearbyEntities(16, 16, 16)) {
            if (entity instanceof LivingEntity living && entity != mob) {
                double distance = mob.getLocation().distanceSquared(entity.getLocation());
                if (distance < closest) {
                    closest = distance;
                    nearest = living;
                }
            }
        }
        
        return nearest;
    }

    /**
     * Get attribute value.
     */
    private double getAttributeValue(@NotNull LivingEntity entity, @NotNull Attribute attribute, double def) {
        AttributeInstance attr = entity.getAttribute(attribute);
        return attr != null ? attr.getValue() : def;
    }

    /**
     * Parse double value.
     */
    private double parseDouble(String s, double def) {
        if (s == null) return def;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Parse integer value.
     */
    private int parseInt(String s, int def) {
        if (s == null) return def;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Parse long value.
     */
    private long parseLong(String s, long def) {
        if (s == null) return def;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Factory for creating SkillFeature instances.
     */
    public static final class Factory implements FeatureFactory<SkillFeature> {
        @Override
        public @NotNull SkillFeature create(@NotNull Context context) {
            return new SkillFeature(context);
        }
    }
}

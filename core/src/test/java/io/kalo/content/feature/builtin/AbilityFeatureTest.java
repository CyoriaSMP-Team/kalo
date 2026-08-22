package io.kalo.content.feature.builtin;

import io.kalo.content.feature.FeatureArguments;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AbilityFeature - tests argument parsing and configuration.
 */
class AbilityFeatureTest {
    
    @Test
    @DisplayName("Test ability arguments are correctly parsed")
    void testAbilityArgumentsParsed() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("lifesteal", "2")
                .argument("fire", "100")
                .argument("knockback", "1.5")
                .argument("poison", "60")
                .argument("wither", "40")
                .build();
        
        assertEquals("2", args.get("lifesteal"));
        assertEquals("100", args.get("fire"));
        assertEquals("1.5", args.get("knockback"));
        assertEquals("60", args.get("poison"));
        assertEquals("40", args.get("wither"));
    }
    
    @Test
    @DisplayName("Test combat abilities configuration")
    void testCombatAbilitiesConfiguration() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("lifesteal", "0.1")
                .argument("fire", "100")
                .argument("knockback", "1.5")
                .argument("poison", "60")
                .argument("wither", "40")
                .argument("stun", "20")
                .argument("vulnerability", "30")
                .argument("slowness", "40")
                .argument("blindness", "20")
                .argument("weakness", "30")
                .argument("nausea", "15")
                .argument("disarm_chance", "0.1")
                .argument("critical_chance", "0.2")
                .argument("critical_multiplier", "2.0")
                .build();
        
        // Verify all combat abilities are present
        assertNotNull(args.get("lifesteal"));
        assertNotNull(args.get("fire"));
        assertNotNull(args.get("knockback"));
        assertNotNull(args.get("poison"));
        assertNotNull(args.get("wither"));
        assertNotNull(args.get("stun"));
        assertNotNull(args.get("vulnerability"));
        assertNotNull(args.get("slowness"));
        assertNotNull(args.get("blindness"));
        assertNotNull(args.get("weakness"));
        assertNotNull(args.get("nausea"));
        assertNotNull(args.get("disarm_chance"));
        assertNotNull(args.get("critical_chance"));
        assertNotNull(args.get("critical_multiplier"));
        
        // Verify values
        assertEquals("0.1", args.get("lifesteal"));
        assertEquals("100", args.get("fire"));
        assertEquals("1.5", args.get("knockback"));
        assertEquals("60", args.get("poison"));
        assertEquals("40", args.get("wither"));
        assertEquals("20", args.get("stun"));
        assertEquals("30", args.get("vulnerability"));
        assertEquals("40", args.get("slowness"));
        assertEquals("20", args.get("blindness"));
        assertEquals("30", args.get("weakness"));
        assertEquals("15", args.get("nausea"));
        assertEquals("0.1", args.get("disarm_chance"));
        assertEquals("0.2", args.get("critical_chance"));
        assertEquals("2.0", args.get("critical_multiplier"));
    }
    
    @Test
    @DisplayName("Test movement abilities configuration")
    void testMovementAbilitiesConfiguration() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("speed_boost_duration", "100")
                .argument("speed_boost_amplifier", "1")
                .argument("jump_boost_duration", "60")
                .argument("jump_boost_amplifier", "1")
                .argument("strength_duration", "80")
                .argument("strength_amplifier", "1")
                .argument("dash_distance", "3.0")
                .argument("launch_power", "2.0")
                .argument("fire_resist_duration", "200")
                .argument("invisibility_duration", "100")
                .argument("regeneration_duration", "60")
                .argument("regeneration_amplifier", "1")
                .build();
        
        // Verify all movement abilities are present
        assertNotNull(args.get("speed_boost_duration"));
        assertNotNull(args.get("speed_boost_amplifier"));
        assertNotNull(args.get("jump_boost_duration"));
        assertNotNull(args.get("jump_boost_amplifier"));
        assertNotNull(args.get("strength_duration"));
        assertNotNull(args.get("strength_amplifier"));
        assertNotNull(args.get("dash_distance"));
        assertNotNull(args.get("launch_power"));
        assertNotNull(args.get("fire_resist_duration"));
        assertNotNull(args.get("invisibility_duration"));
        assertNotNull(args.get("regeneration_duration"));
        assertNotNull(args.get("regeneration_amplifier"));
    }
    
    @Test
    @DisplayName("Test defense abilities configuration")
    void testDefenseAbilitiesConfiguration() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("damage_absorb", "5.0")
                .argument("thorns_chance", "0.3")
                .argument("thorns_damage", "2.0")
                .argument("repair_chance", "0.1")
                .argument("auto_heal", "1.0")
                .build();
        
        assertNotNull(args.get("damage_absorb"));
        assertNotNull(args.get("thorns_chance"));
        assertNotNull(args.get("thorns_damage"));
        assertNotNull(args.get("repair_chance"));
        assertNotNull(args.get("auto_heal"));
    }
    
    @Test
    @DisplayName("Test conditional triggers configuration")
    void testConditionalTriggersConfiguration() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("trigger_on_hit", "lifesteal,fire,knockback")
                .argument("trigger_on_kill", "heal:10,speed_boost:100:1")
                .argument("trigger_on_take_damage", "damage_absorb,thorns")
                .argument("trigger_on_block", "repair,auto_heal")
                .argument("trigger_on_sneak", "invisibility:100")
                .argument("trigger_on_sprint", "speed_boost:60:2")
                .build();
        
        assertEquals("lifesteal,fire,knockback", args.get("trigger_on_hit"));
        assertEquals("heal:10,speed_boost:100:1", args.get("trigger_on_kill"));
        assertEquals("damage_absorb,thorns", args.get("trigger_on_take_damage"));
        assertEquals("repair,auto_heal", args.get("trigger_on_block"));
        assertEquals("invisibility:100", args.get("trigger_on_sneak"));
        assertEquals("speed_boost:60:2", args.get("trigger_on_sprint"));
    }
    
    @Test
    @DisplayName("Test ability combinations configuration")
    void testAbilityCombinationsConfiguration() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("combo_fire_knockback", "fire:100,knockback:2.0")
                .argument("combo_poison_wither", "poison:60,wither:40")
                .argument("combo_stun_disarm", "stun:20,disarm_chance:0.5")
                .build();
        
        assertEquals("fire:100,knockback:2.0", args.get("combo_fire_knockback"));
        assertEquals("poison:60,wither:40", args.get("combo_poison_wither"));
        assertEquals("stun:20,disarm_chance:0.5", args.get("combo_stun_disarm"));
    }
}

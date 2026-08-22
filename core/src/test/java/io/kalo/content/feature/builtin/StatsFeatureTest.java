package io.kalo.content.feature.builtin;

import io.kalo.content.feature.FeatureArguments;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StatsFeature - tests argument parsing and configuration.
 */
class StatsFeatureTest {
    
    @Test
    @DisplayName("Test basic stats are correctly parsed")
    void testBasicStatsParsed() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("strength", "10")
                .argument("defense", "8")
                .argument("agility", "6")
                .argument("health", "20")
                .argument("crit_chance", "0.15")
                .argument("crit_damage", "2.0")
                .argument("lifesteal", "0.05")
                .build();
        
        assertEquals("10", args.get("strength"));
        assertEquals("8", args.get("defense"));
        assertEquals("6", args.get("agility"));
        assertEquals("20", args.get("health"));
        assertEquals("0.15", args.get("crit_chance"));
        assertEquals("2.0", args.get("crit_damage"));
        assertEquals("0.05", args.get("lifesteal"));
    }
    
    @Test
    @DisplayName("Test advanced stats configuration")
    void testAdvancedStatsConfiguration() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("mana", "100")
                .argument("mana_regen", "1.0")
                .argument("cooldown_reduction", "0.2")
                .argument("spell_power", "1.5")
                .argument("attack_speed", "1.2")
                .build();
        
        assertEquals("100", args.get("mana"));
        assertEquals("1.0", args.get("mana_regen"));
        assertEquals("0.2", args.get("cooldown_reduction"));
        assertEquals("1.5", args.get("spell_power"));
        assertEquals("1.2", args.get("attack_speed"));
    }
    
    @Test
    @DisplayName("Test elemental stats configuration")
    void testElementalStatsConfiguration() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("fire_damage", "5")
                .argument("ice_damage", "5")
                .argument("lightning_damage", "5")
                .argument("poison_damage", "5")
                .argument("fire_resist", "0.1")
                .argument("ice_resist", "0.1")
                .argument("lightning_resist", "0.1")
                .argument("poison_resist", "0.1")
                .build();
        
        assertEquals("5", args.get("fire_damage"));
        assertEquals("5", args.get("ice_damage"));
        assertEquals("5", args.get("lightning_damage"));
        assertEquals("5", args.get("poison_damage"));
        assertEquals("0.1", args.get("fire_resist"));
        assertEquals("0.1", args.get("ice_resist"));
        assertEquals("0.1", args.get("lightning_resist"));
        assertEquals("0.1", args.get("poison_resist"));
    }
    
    @Test
    @DisplayName("Test level system configuration")
    void testLevelSystemConfiguration() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("level", "1")
                .argument("xp", "0")
                .argument("xp_to_next", "100")
                .argument("xp_multiplier", "1.5")
                .argument("skill_points", "10")
                .build();
        
        assertEquals("1", args.get("level"));
        assertEquals("0", args.get("xp"));
        assertEquals("100", args.get("xp_to_next"));
        assertEquals("1.5", args.get("xp_multiplier"));
        assertEquals("10", args.get("skill_points"));
    }
    
    @Test
    @DisplayName("Test all stats can be combined")
    void testAllStatsCombined() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("strength", "10")
                .argument("defense", "8")
                .argument("agility", "6")
                .argument("health", "20")
                .argument("crit_chance", "0.15")
                .argument("crit_damage", "2.0")
                .argument("lifesteal", "0.05")
                .argument("mana", "100")
                .argument("mana_regen", "1.0")
                .argument("cooldown_reduction", "0.2")
                .argument("spell_power", "1.5")
                .argument("attack_speed", "1.2")
                .argument("fire_damage", "5")
                .argument("ice_damage", "5")
                .argument("lightning_damage", "5")
                .argument("poison_damage", "5")
                .argument("fire_resist", "0.1")
                .argument("ice_resist", "0.1")
                .argument("lightning_resist", "0.1")
                .argument("poison_resist", "0.1")
                .build();
        
        // Verify all stats are present
        assertNotNull(args.get("strength"));
        assertNotNull(args.get("defense"));
        assertNotNull(args.get("agility"));
        assertNotNull(args.get("health"));
        assertNotNull(args.get("crit_chance"));
        assertNotNull(args.get("crit_damage"));
        assertNotNull(args.get("lifesteal"));
        assertNotNull(args.get("mana"));
        assertNotNull(args.get("mana_regen"));
        assertNotNull(args.get("cooldown_reduction"));
        assertNotNull(args.get("spell_power"));
        assertNotNull(args.get("attack_speed"));
        assertNotNull(args.get("fire_damage"));
        assertNotNull(args.get("ice_damage"));
        assertNotNull(args.get("lightning_damage"));
        assertNotNull(args.get("poison_damage"));
        assertNotNull(args.get("fire_resist"));
        assertNotNull(args.get("ice_resist"));
        assertNotNull(args.get("lightning_resist"));
        assertNotNull(args.get("poison_resist"));
    }
}

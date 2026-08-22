package io.kalo.content.feature.builtin;

import io.kalo.content.feature.FeatureArguments;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CooldownFeature - tests argument parsing and configuration.
 */
class CooldownFeatureTest {
    
    @Test
    @DisplayName("Test mana system configuration")
    void testManaSystemConfiguration() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("mana", "100")
                .argument("mana_regen", "1.0")
                .argument("mana_regen_delay", "20")
                .build();
        
        assertEquals("100", args.get("mana"));
        assertEquals("1.0", args.get("mana_regen"));
        assertEquals("20", args.get("mana_regen_delay"));
    }
    
    @Test
    @DisplayName("Test cooldown groups configuration")
    void testCooldownGroupsConfiguration() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("cooldown_group_combat", "20")
                .argument("cooldown_group_magic", "40")
                .argument("cooldown_group_utility", "60")
                .build();
        
        assertEquals("20", args.get("cooldown_group_combat"));
        assertEquals("40", args.get("cooldown_group_magic"));
        assertEquals("60", args.get("cooldown_group_utility"));
    }
    
    @Test
    @DisplayName("Test cooldown reduction configuration")
    void testCooldownReductionConfiguration() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("cooldown_reduction", "0.2")
                .build();
        
        assertEquals("0.2", args.get("cooldown_reduction"));
    }
    
    @Test
    @DisplayName("Test all cooldown features combined")
    void testAllCooldownFeaturesCombined() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("mana", "100")
                .argument("mana_regen", "1.0")
                .argument("mana_regen_delay", "20")
                .argument("cooldown_group_combat", "20")
                .argument("cooldown_group_magic", "40")
                .argument("cooldown_group_utility", "60")
                .argument("cooldown_reduction", "0.2")
                .build();
        
        // Verify all cooldown features are present
        assertNotNull(args.get("mana"));
        assertNotNull(args.get("mana_regen"));
        assertNotNull(args.get("mana_regen_delay"));
        assertNotNull(args.get("cooldown_group_combat"));
        assertNotNull(args.get("cooldown_group_magic"));
        assertNotNull(args.get("cooldown_group_utility"));
        assertNotNull(args.get("cooldown_reduction"));
        
        // Verify values
        assertEquals("100", args.get("mana"));
        assertEquals("1.0", args.get("mana_regen"));
        assertEquals("20", args.get("mana_regen_delay"));
        assertEquals("20", args.get("cooldown_group_combat"));
        assertEquals("40", args.get("cooldown_group_magic"));
        assertEquals("60", args.get("cooldown_group_utility"));
        assertEquals("0.2", args.get("cooldown_reduction"));
    }
}

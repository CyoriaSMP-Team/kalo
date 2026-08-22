package io.kalo.content.feature.builtin;

import io.kalo.content.feature.FeatureArguments;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SkillFeature - tests argument parsing and configuration.
 */
class SkillFeatureTest {
    
    @Test
    @DisplayName("Test skill arguments are correctly parsed")
    void testSkillArgumentsParsed() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("on_spawn", "effect:SPEED:200:1,message:§cBoss has appeared!")
                .argument("on_damage", "damage:10,heal:5,effect:POISON:60:0")
                .argument("on_death", "summon:zombie:5:10,particle:explosion:50")
                .build();
        
        assertEquals("effect:SPEED:200:1,message:§cBoss has appeared!", args.get("on_spawn"));
        assertEquals("damage:10,heal:5,effect:POISON:60:0", args.get("on_damage"));
        assertEquals("summon:zombie:5:10,particle:explosion:50", args.get("on_death"));
    }
    
    @Test
    @DisplayName("Test combat skill configuration")
    void testCombatSkillConfiguration() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("on_damage", "damage:10,heal:5,lifesteal:0.2,effect:SPEED:200:1")
                .build();
        
        String skills = args.get("on_damage");
        assertNotNull(skills);
        
        String[] skillArray = skills.split(",");
        assertEquals(4, skillArray.length);
        assertEquals("damage:10", skillArray[0].trim());
        assertEquals("heal:5", skillArray[1].trim());
        assertEquals("lifesteal:0.2", skillArray[2].trim());
        assertEquals("effect:SPEED:200:1", skillArray[3].trim());
    }
    
    @Test
    @DisplayName("Test movement skill configuration")
    void testMovementSkillConfiguration() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("on_damage", "teleport:target,dash:5.0,launch:2.0,pull:1.5,push:1.5")
                .build();
        
        String skills = args.get("on_damage");
        assertNotNull(skills);
        
        String[] skillArray = skills.split(",");
        assertEquals(5, skillArray.length);
        assertEquals("teleport:target", skillArray[0].trim());
        assertEquals("dash:5.0", skillArray[1].trim());
        assertEquals("launch:2.0", skillArray[2].trim());
        assertEquals("pull:1.5", skillArray[3].trim());
        assertEquals("push:1.5", skillArray[4].trim());
    }
    
    @Test
    @DisplayName("Test summoning skill configuration")
    void testSummoningSkillConfiguration() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("on_death", "summon:zombie:5:10,spawn_particle:explosion:50,spawn_entity:LIGHTNING_BOLT")
                .build();
        
        String skills = args.get("on_death");
        assertNotNull(skills);
        
        String[] skillArray = skills.split(",");
        assertEquals(3, skillArray.length);
        assertEquals("summon:zombie:5:10", skillArray[0].trim());
        assertEquals("spawn_particle:explosion:50", skillArray[1].trim());
        assertEquals("spawn_entity:LIGHTNING_BOLT", skillArray[2].trim());
    }
    
    @Test
    @DisplayName("Test effect skill configuration")
    void testEffectSkillConfiguration() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("on_spawn", "effect:SPEED:200:1,sound:ENTITY_ENDER_DRAGON_GROWL,message:§cBoss appeared!")
                .build();
        
        String skills = args.get("on_spawn");
        assertNotNull(skills);
        
        String[] skillArray = skills.split(",");
        assertEquals(3, skillArray.length);
        assertEquals("effect:SPEED:200:1", skillArray[0].trim());
        assertEquals("sound:ENTITY_ENDER_DRAGON_GROWL", skillArray[1].trim());
        assertEquals("message:§cBoss appeared!", skillArray[2].trim());
    }
    
    @Test
    @DisplayName("Test AI configuration")
    void testAIConfiguration() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("ai_target", "nearest")
                .argument("ai_aggro_range", "16")
                .argument("ai_attack_cooldown", "40")
                .argument("ai_persistent", "true")
                .argument("skill_cooldown", "100")
                .build();
        
        assertEquals("nearest", args.get("ai_target"));
        assertEquals("16", args.get("ai_aggro_range"));
        assertEquals("40", args.get("ai_attack_cooldown"));
        assertEquals("true", args.get("ai_persistent"));
        assertEquals("100", args.get("skill_cooldown"));
    }
    
    @Test
    @DisplayName("Test condition skill configuration")
    void testConditionSkillConfiguration() {
        FeatureArguments args = FeatureArguments.builder()
                .argument("on_damage", "condition:health_below:0.3,damage:20,launch:3")
                .build();
        
        String skills = args.get("on_damage");
        assertNotNull(skills);
        
        String[] skillArray = skills.split(",");
        assertEquals(3, skillArray.length);
        assertEquals("condition:health_below:0.3", skillArray[0].trim());
        assertEquals("damage:20", skillArray[1].trim());
        assertEquals("launch:3", skillArray[2].trim());
    }
}

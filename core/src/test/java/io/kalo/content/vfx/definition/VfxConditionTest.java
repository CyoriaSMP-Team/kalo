package io.kalo.content.vfx.definition;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxConditionTest {

    private static final VfxCondition.Context CONTEXT = new VfxCondition.Context() {
        @Override
        public String value(String name) {
            return Map.of("health", "5", "state", "ended", "flag", "1").get(name);
        }

        @Override
        public boolean permission(String node) {
            return node.equals("mage.aura");
        }
    };

    @Test
    void numericStringAndPermissionConditionsEvaluate() {
        assertTrue(VfxCondition.parse("{health} < 6").evaluate(CONTEXT));
        assertFalse(VfxCondition.parse("{health} >= 6").evaluate(CONTEXT));
        assertTrue(VfxCondition.parse("{state} == \"ended\"").evaluate(CONTEXT));
        assertTrue(VfxCondition.parse("perm:mage.aura").evaluate(CONTEXT));
        assertFalse(VfxCondition.parse("perm:other.node").evaluate(CONTEXT));
        assertTrue(VfxCondition.parse("!perm:other.node").evaluate(CONTEXT));
        assertTrue(VfxCondition.parse("{flag}").evaluate(CONTEXT));
    }

    @Test
    void operatorsAndParenthesesFollowBooleanPrecedence() {
        assertTrue(VfxCondition.parse("{health} < 6 || !perm:mage.aura").evaluate(CONTEXT));
        assertFalse(VfxCondition.parse("{health} < 6 && {health} > 10").evaluate(CONTEXT));
        assertTrue(VfxCondition.parse("({health} < 6 && {state} == \"ended\")")
                .evaluate(CONTEXT));
        assertTrue(VfxCondition.parse("!({health} > 10)").evaluate(CONTEXT));
    }

    @Test
    void malformedConditionsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> VfxCondition.parse("{health} <"));
        assertThrows(IllegalArgumentException.class,
                () -> VfxCondition.parse("{health"));
        assertThrows(IllegalArgumentException.class,
                () -> VfxCondition.parse("true &&"));
        assertThrows(IllegalArgumentException.class,
                () -> VfxCondition.parse("perm:"));
    }
}

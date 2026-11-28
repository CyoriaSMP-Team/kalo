package io.kalo.content.vfx.definition;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VfxConditionContextTest {

    @Test
    void builtInsWinAndUnknownNamesFallBackToTheExternalResolver() {
        AtomicInteger externalCalls = new AtomicInteger();
        VfxCondition.Context context = VfxConditionContext.create(
                Map.of("health", "5"),
                name -> {
                    externalCalls.incrementAndGet();
                    return name.equals("arena_state") ? "running" : null;
                },
                node -> node.equals("mage.aura"));

        assertEquals("5", context.value("health"));
        assertEquals(0, externalCalls.get(), "built-in health must not reach PlaceholderAPI");
        assertEquals("running", context.value("arena_state"));
        assertEquals("", context.value("unknown"));
        assertTrue(context.permission("mage.aura"));
        assertFalse(context.permission("other"));

        assertTrue(VfxCondition.parse("{health} < 6 && {arena_state} == \"running\"")
                .evaluate(context));
        assertTrue(VfxCondition.parse("perm:mage.aura && {arena_state} != \"ended\"")
                .evaluate(context));
    }
}

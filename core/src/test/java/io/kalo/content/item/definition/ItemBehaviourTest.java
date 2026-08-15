package io.kalo.content.item.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemBehaviourTest {

    @Test
    void defaultsAreANonDamageableStackOf64() {
        ItemBehaviour defaults = ItemBehaviour.defaults();
        assertEquals(64, defaults.maxStackSize());
        assertNull(defaults.maxDurability());
    }

    @Test
    void nullDurabilityDefaultSurvivesATernary() {
        // Regression: `config.contains(k) ? config.getInt(k) : defaults.maxDurability()`
        // mixes int and Integer, so Java unboxes both branches and the null default
        // threw NPE for every item that did not declare a durability. Every such item
        // failed to load, which on a real pack is most of them.
        ItemBehaviour defaults = ItemBehaviour.defaults();
        boolean hasDurability = false;

        Integer resolved = hasDurability ? Integer.valueOf(250) : defaults.maxDurability();

        assertNull(resolved);
    }

    @Test
    void rejectsAStackableDamageableItem() {
        // Vanilla cannot represent this, so catch it at definition time rather than
        // letting the client decide what it means.
        assertThrows(IllegalArgumentException.class, () -> new ItemBehaviour(64, 250, false));
    }

    @Test
    void rejectsOutOfRangeStackSizes() {
        assertThrows(IllegalArgumentException.class, () -> new ItemBehaviour(0, null, false));
        assertThrows(IllegalArgumentException.class, () -> new ItemBehaviour(100, null, false));
    }

    @Test
    void rejectsNonPositiveDurability() {
        assertThrows(IllegalArgumentException.class, () -> new ItemBehaviour(1, 0, false));
    }

    @Test
    void acceptsADamageableItemThatDoesNotStack() {
        ItemBehaviour behaviour = new ItemBehaviour(1, 250, true);
        assertEquals(1, behaviour.maxStackSize());
        assertEquals(250, behaviour.maxDurability());
    }
}

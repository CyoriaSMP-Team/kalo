package com.nexomc.nexo.api;

import com.nexomc.nexo.items.ItemBuilder;
import io.kalo.compat.nexo.NexoItemBridge;
import org.bukkit.inventory.ItemStack;

/**
 * The Nexo item API subset that consumer plugins call, backed by Kalo content.
 *
 * <p>Signatures are load-bearing. Consumers were compiled against Nexo and link on the
 * exact JVM descriptors, so the return types here cannot change — see
 * {@code NexoDescriptorTest}.</p>
 */
public final class NexoItems {

    private NexoItems() {
    }

    /** A fresh stack for a Nexo id, or null when no Kalo item carries it. */
    public static ItemBuilder itemFromId(String id) {
        ItemStack stack = NexoItemBridge.resolve(id);
        return stack == null ? null : new ItemBuilder(stack);
    }

    /** The Nexo id of a stack, or null when the stack is not custom content. */
    public static String idFromItem(ItemStack stack) {
        return NexoItemBridge.idOf(stack);
    }

    public static boolean exists(String id) {
        return NexoItemBridge.resolve(id) != null;
    }

    public static boolean exists(ItemStack stack) {
        return NexoItemBridge.idOf(stack) != null;
    }
}

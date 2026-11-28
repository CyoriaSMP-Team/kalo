package io.kalo.compat.nexo;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.glyphs.GlyphTag;
import com.nexomc.nexo.items.ItemBuilder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the JVM descriptors consumer plugins were compiled against.
 *
 * <p>This is the regression test for the first shim: it returned a nested builder type,
 * so {@code itemFromId} linked as
 * {@code (Ljava/lang/String;)Lcom/nexomc/nexo/api/NexoItems$NexoItemBuilder;} and every
 * directly compiled consumer would have thrown {@code NoSuchMethodError} at runtime. A
 * reflective {@code getMethod} check catches that class of mistake without a server.</p>
 */
class NexoDescriptorTest {

    @Test
    void itemFromIdReturnsNexoItemBuilder() throws Exception {
        assertEquals(ItemBuilder.class,
                NexoItems.class.getMethod("itemFromId", String.class).getReturnType());
    }

    @Test
    void idFromItemReturnsString() throws Exception {
        assertEquals(String.class,
                NexoItems.class.getMethod("idFromItem", ItemStack.class).getReturnType());
    }

    @Test
    void existsOverloadsReturnBoolean() throws Exception {
        assertEquals(boolean.class,
                NexoItems.class.getMethod("exists", String.class).getReturnType());
        assertEquals(boolean.class,
                NexoItems.class.getMethod("exists", ItemStack.class).getReturnType());
    }

    @Test
    void itemBuilderBuildReturnsItemStack() throws Exception {
        assertEquals(ItemStack.class, ItemBuilder.class.getMethod("build").getReturnType());
    }

    @Test
    void glyphTagMatchesTheZMenuHook() throws Exception {
        assertNotNull(GlyphTag.class.getField("INSTANCE").get(null));
        assertEquals(TagResolver.class,
                GlyphTag.class.getMethod("getRESOLVER").getReturnType());
    }

    @Test
    void itemsLoadedEventIsABukkitEvent() throws Exception {
        // zMenu, NOrder and ODailyQuests all register an @EventHandler method taking this
        // type. If it does not extend Event, Bukkit refuses to register their listeners
        // and the consumers never learn that items exist.
        assertEquals(org.bukkit.event.Event.class,
                com.nexomc.nexo.api.events.NexoItemsLoadedEvent.class.getSuperclass());
        assertNotNull(com.nexomc.nexo.api.events.NexoItemsLoadedEvent.class
                .getMethod("getHandlerList").invoke(null));
    }
}

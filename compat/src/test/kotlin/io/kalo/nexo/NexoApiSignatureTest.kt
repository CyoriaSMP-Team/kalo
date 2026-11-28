package io.kalo.nexo

import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.api.events.NexoItemsLoadedEvent
import com.nexomc.nexo.items.ItemBuilder
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.io.File
import java.lang.reflect.Modifier
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the shim to the signatures in Nexo's published Javadoc (jd.nexomc.com, API 1.8).
 * A plugin compiled against Nexo links by exact name and descriptor, so a drifted parameter
 * or return type is a NoSuchMethodError in someone else's plugin, not a compile error here.
 */
class NexoApiSignatureTest {

    private fun staticMethod(cls: Class<*>, name: String, returns: Class<*>, vararg params: Class<*>) {
        val m = cls.getMethod(name, *params)
        assertTrue(Modifier.isStatic(m.modifiers), "$name must be static")
        assertEquals(returns, m.returnType, "$name return type")
    }

    @Test
    fun nexoItemsStaticSurfaceMatchesJavadoc() {
        val c = NexoItems::class.java
        staticMethod(c, "loadItems", Void.TYPE)
        staticMethod(c, "idFromItem", String::class.java, ItemBuilder::class.java)
        staticMethod(c, "idFromItem", String::class.java, ItemStack::class.java)
        staticMethod(c, "exists", java.lang.Boolean.TYPE, String::class.java)
        staticMethod(c, "exists", java.lang.Boolean.TYPE, ItemStack::class.java)
        staticMethod(c, "itemFromId", ItemBuilder::class.java, String::class.java)
        staticMethod(c, "optionalItemFromId", Optional::class.java, String::class.java)
        staticMethod(c, "builderFromItem", ItemBuilder::class.java, ItemStack::class.java)
        staticMethod(c, "unexcludedItems", List::class.java, File::class.java)
        staticMethod(c, "hasMechanic", java.lang.Boolean.TYPE, String::class.java, String::class.java)
        staticMethod(c, "itemMap", Map::class.java)
        staticMethod(c, "entries", Map::class.java)
        staticMethod(c, "items", Set::class.java)
        staticMethod(c, "itemNames", Set::class.java)
        staticMethod(c, "unexcludedItemNames", Array<String>::class.java)
    }

    @Test
    fun nexoItemsKotlinEntryPointsExist() {
        // Kotlin callers go through INSTANCE; reloadItem is an instance method in Nexo too.
        assertNotNull(NexoItems::class.java.getField("INSTANCE"))
        NexoItems::class.java.getMethod("reloadItem", String::class.java)
    }

    @Test
    fun itemBuilderConstructorsAndCoreMethods() {
        val c = ItemBuilder::class.java
        assertTrue(Modifier.isFinal(c.modifiers), "Nexo's ItemBuilder is final")
        c.getConstructor(Material::class.java)
        c.getConstructor(ItemStack::class.java)
        c.getConstructor(ItemBuilder::class.java)
        assertEquals(ItemStack::class.java, c.getMethod("build").returnType)
        assertEquals(ItemBuilder::class.java, c.getMethod("setAmount", Integer.TYPE).returnType)
        assertEquals(ItemBuilder::class.java, c.getMethod("clone").returnType)
        assertEquals(Array<ItemStack>::class.java, c.getMethod("buildArray", Integer.TYPE).returnType)
    }

    @Test
    fun loadedEventHasHandlerList() {
        val m = NexoItemsLoadedEvent::class.java.getMethod("getHandlerList")
        assertTrue(Modifier.isStatic(m.modifiers))
    }
}

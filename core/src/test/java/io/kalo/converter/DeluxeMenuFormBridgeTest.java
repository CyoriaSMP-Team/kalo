package io.kalo.converter;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Java users keep DeluxeMenus; only safe equivalent menus become Bedrock Forms. */
class DeluxeMenuFormBridgeTest {
    private static YamlConfiguration menu(String yaml) {
        YamlConfiguration result = new YamlConfiguration();
        try { result.load(new StringReader(yaml)); }
        catch (Exception problem) { throw new AssertionError(problem); }
        return result;
    }

    private static final String RTP = """
            menu_title: '&8RTP'
            size: 54
            open_command:
             - rtp
            items:
              decoration:
                material: BLACK_STAINED_GLASS_PANE
                slots: [0-53]
                display_name: ' &r '
              wild:
                slot: 20
                display_name: '&aSurvival'
                material: PAPER
                lore: ['&7Play survival']
                click_commands:
                 - '[close]'
                 - '[player] betterrtp world world'
              nether:
                slot: 22
                display_name: '&cNether'
                material: NETHERRACK
                click_commands:
                 - '[player] betterrtp world world_nether'
              back:
                slot: 45
                display_name: '&bBack'
                material: ARROW
                click_commands:
                 - '[player] warps'
            """;

    @Test
    void convertsRtpWithThreeRealButtonsAndKeepsJavaPlayerCommands() {
        var converted = DeluxeMenuFormBridge.parse("Warp-RTP", menu(RTP));
        assertNotNull(converted);
        assertEquals("RTP", converted.title());
        assertEquals(3, converted.buttons().size());
        assertEquals(List.of(20, 22, 45), converted.buttons().stream().map(DeluxeMenuFormBridge.Button::slot).toList());
        assertEquals("betterrtp world world", converted.buttons().getFirst().actions().get(1).argument());
        assertTrue(converted.buttons().getFirst().text().contains("Play survival"));
    }

    @Test
    void usesMeaningfulLoreInsteadOfTheLiteralInfoHeading() {
        var result = DeluxeMenuFormBridge.parse("rtp",menu(RTP.replace(
                "lore: ['&7Play survival']",
                "lore: ['&aข้อมูล', '&7ขนาด: 50,000 x 50,000', '&7CLICK TO RTP']")));
        assertNotNull(result);
        assertTrue(result.buttons().getFirst().text().contains("50,000 x 50,000"));
        assertFalse(result.buttons().getFirst().text().contains("ข้อมูล"));
    }

    @Test
    void blocksConsolePurchasesAndPlayerRequirementBypasses() {
        String console = RTP.replace("[player] betterrtp world world", "[console] eco give %player_name% 100");
        assertNull(DeluxeMenuFormBridge.parse("unsafe", menu(console)));
        String check = RTP.replace("    click_commands:\n     - '[close]'", "    click_requirement:\n      requirements:\n        coins:\n          type: has money\n          amount: 100\n    click_commands:\n     - '[close]'");
        assertNull(DeluxeMenuFormBridge.parse("requiresCoins", menu(check)));
        assertNull(DeluxeMenuFormBridge.parse("limited", menu(RTP + "open_requirement:\n  requirements:\n    perm:\n      type: has permission\n")));
    }

    @Test
    void refusesConflictingMouseActionsAndUnknownActions() {
        assertNull(DeluxeMenuFormBridge.parse("mouse", menu(RTP.replace("    click_commands:\n     - '[close]'", "    left_click_commands:\n     - '[player] spawn'\n    click_commands:\n     - '[close]'"))));
        assertNull(DeluxeMenuFormBridge.parse("forged", menu(RTP.replace("[close]", "[openguimenu] ../../admin"))));
        assertNull(DeluxeMenuFormBridge.parse("funds", menu(RTP.replace("[close]", "[console] eco give player 100"))));
    }
    @Test
    void recognizesActualDeluxeMenusHolderEvenWithoutSlashCommandEvent() {
        org.bukkit.inventory.InventoryHolder holder =
                new com.extendedclip.deluxemenus.menu.MenuHolder("Warp-RTP");
        assertEquals("Warp-RTP", DeluxeMenuFormBridge.menuNameFromDeluxeHolder(holder));
        assertNull(DeluxeMenuFormBridge.menuNameFromDeluxeHolder(
                new org.bukkit.inventory.InventoryHolder() {
                    @Override public org.bukkit.inventory.Inventory getInventory() { return null; }
                }), "Never convert a chest GUI owned by another plugin");
        assertNull(DeluxeMenuFormBridge.menuNameFromDeluxeHolder(
                new com.extendedclip.deluxemenus.menu.MenuHolder("")));
    }

}

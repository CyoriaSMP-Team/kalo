package com.extendedclip.deluxemenus.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Minimal standalone fixture of the real DeluxeMenus Folia MenuHolder contract. */
public final class MenuHolder implements InventoryHolder {
    private final String name;
    public MenuHolder(String name) { this.name = name; }
    public String getMenuName() { return name; }
    @Override public Inventory getInventory() { return null; }
}

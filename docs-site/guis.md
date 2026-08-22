---
title: Custom GUIs
---

# 🖥️ Custom GUIs Guide

Server-side inventory menus with configurable slots and actions.

## Basic GUI

```yaml
main_menu:
  type: gui
  title: "<gold>Server Menu</gold>"
  rows: 3
  items:
    4:
      material: NETHER_STAR
      display_name: "<gold>Teleport</gold>"
      lore:
        - "<gray>Teleport to areas</gray>"
      actions:
        - "gui:myitem:teleport_menu"
```

## Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `title` | string | required | Menu title (MiniMessage) |
| `rows` | int | 6 | Inventory rows (1-6) |
| `items` | map | {} | Slot configurations |
| `close_actions` | list | [] | Actions on close |

## Slot Configuration

```yaml
items:
  <slot_number>:
    material: STONE              # Bukkit material
    display_name: "<gold>Name</gold>"
    lore:
      - "<gray>Description</gray>"
    amount: 1
    custom_model_data: 0
    actions:
      - "command:/cmd %player%"
      - "message:<green>Text"
      - "gui:myitem:other_menu"
      - "close"
    conditions:
      permission: "myitem.admin"
```

## Action Types

| Action | Format | Example |
|--------|--------|---------|
| Command | `command:/cmd` | `command:kit pvp %player%` |
| Message | `message:<text>` | `message:<green>Welcome!` |
| Close | `close` | `close` |
| Open GUI | `gui:<key>` | `gui:myitem:submenu` |

## Variables

- `%player%` — Player name

## Examples

### Server Menu

```yaml
server_menu:
  type: gui
  title: "<gradient:#ff5f6d:#ffc371>Server Menu</gradient>"
  rows: 3
  items:
    10:
      material: DIAMOND_SWORD
      display_name: "<red>Kit PvP</red>"
      actions:
        - "command:kit pvp %player%"
        - "message:<green>You received the PvP kit!"
    12:
      material: CHEST
      display_name: "<gold>Shop</gold>"
      actions:
        - "command:shop open %player%"
    14:
      material: BOOK
      display_name: "<blue>Rules</blue>"
      actions:
        - "command:rules %player%"
    22:
      material: BARRIER
      display_name: "<red>Close</red>"
      actions:
        - "close"
```

### Submenu with Back Button

```yaml
teleport_menu:
  type: gui
  title: "<gold>Teleport</gold>"
  rows: 3
  items:
    10:
      material: GRASS_BLOCK
      display_name: "<green>Spawn</green>"
      actions:
        - "command:spawn %player%"
        - "close"
    22:
      material: ARROW
      display_name: "<yellow>Back</yellow>"
      actions:
        - "gui:myitem:server_menu"
```

## Opening GUIs

GUIs can be opened via:
1. Commands (add a command in your plugin)
2. Other GUI actions
3. Signs or other triggers

Example command handler:
```java
if (command.equals("menu")) {
    guiListener.openGui(player, Key.key("myitem:server_menu"));
}
```

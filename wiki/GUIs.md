# 🖥️ Custom GUIs

Server-side inventory menus.

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
      actions:
        - "gui:myitem:teleport_menu"
```

## Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `title` | string | required | Menu title |
| `rows` | int | 6 | Inventory rows (1-6) |
| `items` | map | {} | Slot configs |
| `close_actions` | list | [] | Actions on close |

## Slot Configuration

```yaml
items:
  <slot>:
    material: STONE
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
```

## Action Types

| Action | Format | Example |
|--------|--------|---------|
| Command | `command:/cmd` | `command:kit pvp %player%` |
| Message | `message:<text>` | `message:<green>Welcome!` |
| Close | `close` | `close` |
| Open GUI | `gui:<key>` | `gui:myitem:submenu` |

## Examples

### Server Menu

```yaml
server_menu:
  type: gui
  title: "<gold>Server Menu</gold>"
  rows: 3
  items:
    10:
      material: DIAMOND_SWORD
      display_name: "<red>Kit PvP</red>"
      actions:
        - "command:kit pvp %player%"
    12:
      material: CHEST
      display_name: "<gold>Shop</gold>"
      actions:
        - "command:shop open %player%"
    22:
      material: BARRIER
      display_name: "<red>Close</red>"
      actions:
        - "close"
```

---

## See Also

- [[Items]] — Custom items
- [[Configuration]] — All config options

---

## 📜 Footer

**Kalo** — Open Custom Content Engine for Minecraft

[GitHub](https://github.com/CyoriaSMP-Team/kalo) • [Issues](https://github.com/CyoriaSMP-Team/kalo/issues) • [Discord](https://discord.gg/kalo) (coming soon)

> **Version:** 0.1.0 • **License:** MIT • **Java:** 21+ / 25

*Build once. Play everywhere.*

---
layout: default
title: "🖥️ Custom GUIs"
nav_order: 9
---

# 🖥️ Custom GUIs

Create server-side inventory menus and HUDs.

---

## Basic GUI

```yaml
main_menu:
  type: gui
  title: "<gradient:#ff5f6d:#ffc371>Server Menu</gradient>"
  rows: 3
  items:
    4:
      material: NETHER_STAR
      display_name: "<gold>Teleport</gold>"
      lore:
        - "<gray>Teleport to different areas</gray>"
      actions:
        - "gui:testpack:teleport_menu"
    10:
      material: DIAMOND_SWORD
      display_name: "<red>Kit PvP</red>"
      lore:
        - "<gray>Get PvP gear</gray>"
      actions:
        - "command:kit pvp %player%"
        - "message:<green>You received the PvP kit!"
    22:
      material: BARRIER
      display_name: "<red>Close</red>"
      lore:
        - "<gray>Close this menu</gray>"
      actions:
        - "close"
  close_actions:
    - "message:<gray>Menu closed</gray>"
```

## GUI Options

| Option | Type | Description |
|--------|------|-------------|
| `type` | string | Must be `gui` |
| `title` | string | GUI title with colors |
| `rows` | int | Number of rows (1-6) |
| `items` | map | Slot → item configuration |
| `close_actions` | list | Actions when GUI closes |

## Item Configuration

| Option | Type | Description |
|--------|------|-------------|
| `material` | string | Minecraft material |
| `display_name` | string | Item display name |
| `lore` | list | Item lore lines |
| `actions` | list | Actions to execute |
| `amount` | int | Stack size (default: 1) |
| `glow` | bool | Enchantment glow effect |

## Action Types

| Action | Format | Example |
|--------|--------|---------|
| **Command** | `command:/cmd` | `command:kit pvp %player%` |
| **Message** | `message:<text>` | `message:<green>Welcome!` |
| **Close** | `close` | `close` |
| **Open GUI** | `gui:<key>` | `gui:testpack:teleport_menu` |
| **Sound** | `sound:<key>` | `sound:testpack:click` |
| **Placeholder** | `%player%` | `%player_name%` |

## Slot Layout

```
 1  2  3  4  5  6  7  8  9
10 11 12 13 14 15 16 17 18
19 20 21 22 23 24 25 26 27
28 29 30 31 32 33 34 35 36
37 38 39 40 41 42 43 44 45
46 47 48 49 50 51 52 53 54
```

## PlaceholderAPI Support

Use any PlaceholderAPI placeholder in commands and messages:

- `%player_name%` — Player name
- `%player_health%` — Player health
- `%server_time%` — Server time
- Custom placeholders from other plugins

## Bedrock Support

Custom GUIs work on Bedrock through Geyser's inventory translation. No extra configuration needed.

---

**Next:** [Sounds]({% link sounds.md %})

**Previous:** [Music Discs]({% link music-discs.md %})

# 📋 Commands

## Available Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/kalo reload` | Reload all packs | `kalo.command.reload` |
| `/kalo give <player> <item>` | Give a custom item | `kalo.command.give` |
| `/kalo hud <hud>` | Show a HUD overlay | `kalo.command.hud` |
| `/kalo hud stop` | Stop your HUD overlay | `kalo.command.hud` |
| `/kalo dialog <dialog> [target]` | Open a dialog, optionally about a target | `kalo.command.dialog` |
| `/kalo dialog open <player> <dialog> [target]` | Open a dialog for another player | `kalo.command.dialog` |
| `/kalo vfx list` | List loaded VFX | `kalo.command.vfx` |
| `/kalo vfx <vfx> [options]` | Cast a VFX; `loop`, `loop:<n>x`, `quiet`, `volume:<n>`, `tag:<name>`, `end:cut` | `kalo.command.vfx` |
| `/kalo vfx stop [tag:<name>\|all]` | Stop your VFX, a tagged cast, or all casts | `kalo.command.vfx` |
| `/kalo liquid corner` | Set the first corner for a liquid volume | `kalo.command.liquid` |
| `/kalo liquid fill <liquid>` | Mark a volume between the corner and you | `kalo.command.liquid` |
| `/kalo liquid clear` | Remove the volume at your feet | `kalo.command.liquid` |
| `/kalo click <key>` | Internal dialog click route; only works for the last Kalo dialog shown to you | none |
| `/kalo import <plugin>` | Import from another plugin | `kalo.command.import` |
| `/kalo import file <path>` | Import a specific file | `kalo.command.import` |
| `/kalo doctor` | Check for issues | `kalo.command.doctor` |
| `/kalo migrate-world` | Check world block states | `kalo.command.migrate` |

## Examples

### Give Item

```bash
/kalo give PlayerName myitem:ruby_sword
/kalo give PlayerName myitem:ruby_sword 64
```

### Reload

```bash
/kalo reload
```

### Import

```bash
/kalo import Oraxen
/kalo import ItemsAdder
/kalo import file plugins/Oraxen/items/weapons.yml
```

### Diagnostics

```bash
/kalo doctor
```

### Migrate World

```bash
/kalo migrate-world
```

---

## See Also

- [[Configuration]] — Config options
- [[Permissions]] — Permission nodes

---

## 📜 Footer

**Kalo** — Open Custom Content Engine for Minecraft

[GitHub](https://github.com/CyoriaSMP-Team/kalo) • [Issues](https://github.com/CyoriaSMP-Team/kalo/issues) • [Discord](https://discord.gg/kalo) (coming soon)

> **Version:** 0.2.1-prealpha.16 • **License:** MIT • **Java:** 21 bytecode / 25 for 26.x runtime

*Build once. Play everywhere.*

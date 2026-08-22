# 📋 Commands

## Available Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/kalo reload` | Reload all packs | `kalo.command.reload` |
| `/kalo give <player> <item>` | Give a custom item | `kalo.command.give` |
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

> **Version:** 0.1.0 • **License:** MIT • **Java:** 21+ / 25

*Build once. Play everywhere.*

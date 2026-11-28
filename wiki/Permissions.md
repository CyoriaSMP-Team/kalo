# 🔐 Permissions

## Permission Nodes

| Permission | Description | Default |
|------------|-------------|---------|
| `kalo.command.reload` | Reload packs | `op` |
| `kalo.command.give` | Give items | `op` |
| `kalo.command.hud` | Show or stop HUD overlays | `op` |
| `kalo.command.dialog` | Open a dialog for yourself or another player | `op` |
| `kalo.command.vfx` | Cast or stop a VFX | `op` |
| `kalo.command.liquid` | Mark or clear liquid volumes | `op` |
| `kalo.command.import` | Import from plugins | `op` |
| `kalo.command.doctor` | Run diagnostics | `op` |
| `kalo.command.migrate` | Migrate world blocks | `op` |

## Permission Setup

### LuckPerms

```bash
/lp group default permission set kalo.command.reload true
/lp group default permission set kalo.command.give true
```

### PermissionsEx

```yaml
groups:
  default:
    permissions:
      - kalo.command.reload
      - kalo.command.give
```

### Built-in Permissions

Kalo registers permissions automatically. Players with `op` have all permissions by default.

---

## See Also

- [[Commands]] — Available commands
- [[Configuration]] — Config options

---

## 📜 Footer

**Kalo** — Open Custom Content Engine for Minecraft

[GitHub](https://github.com/CyoriaSMP-Team/kalo) • [Issues](https://github.com/CyoriaSMP-Team/kalo/issues) • [Discord](https://discord.gg/kalo) (coming soon)

> **Version:** 0.2.1-prealpha.16 • **License:** MIT • **Java:** 21 bytecode / 25 for 26.x runtime

*Build once. Play everywhere.*

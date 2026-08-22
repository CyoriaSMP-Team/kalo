# 🔄 Migration Guide

Import content from other plugins.

## Supported Plugins

| Plugin | Items | Blocks | Furniture | Recipes |
|--------|-------|--------|-----------|---------|
| Oraxen | ✅ | ✅ | ✅ | ✅ |
| ItemsAdder | ✅ | ✅ | ✅ | ✅ |
| CraftEngine | ✅ | ✅ | ❌ | ❌ |
| Neko | ✅ | ❌ | ❌ | ❌ |

## Quick Migration

### From Oraxen

```bash
/kalo import Oraxen
```

### From ItemsAdder

```bash
/kalo import ItemsAdder
```

### From CraftEngine

```bash
/kalo import CraftEngine
```

### From Neko

```bash
/kalo import Neko
```

## Single File Import

```bash
/kalo import file plugins/Oraxen/items/weapons.yml
```

## What Gets Imported

- ✅ Items with models and textures
- ✅ Blocks with states
- ✅ Furniture (downgrade - loses entity behaviors)
- ✅ Recipes

## What Doesn't Get Imported

- ❌ Plugin-specific mechanics
- ❌ Already-placed blocks in worlds
- ❌ Furniture rotation, seats, hitboxes

## After Import

1. Copy textures to Kalo's pack
2. Review configs in `plugins/Kalo/packs/<plugin>/`
3. Run `/kalo reload`
4. Test in-game

---

## See Also

- [[Getting-Started]] — Installation guide
- [[Configuration]] — All config options

---

## 📜 Footer

**Kalo** — Open Custom Content Engine for Minecraft

[GitHub](https://github.com/CyoriaSMP-Team/kalo) • [Issues](https://github.com/CyoriaSMP-Team/kalo/issues) • [Discord](https://discord.gg/kalo) (coming soon)

> **Version:** 0.1.0 • **License:** MIT • **Java:** 21+ / 25

*Build once. Play everywhere.*

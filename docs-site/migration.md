---
title: Migration Guide
---

# 🔄 Migration Guide

Kalo can import content from other plugins.

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

Import a specific file:

```bash
/kalo import file plugins/Oraxen/items/weapons.yml
```

## What Gets Imported

- ✅ Items with models and textures
- ✅ Blocks with states
- ✅ Furniture (downgrade - loses entity behaviors)
- ✅ Recipes

## What Doesn't Get Imported

- ❌ Plugin-specific mechanics (Oraxen Mechanics, ItemsAdder behaviours)
- ❌ Already-placed blocks in worlds
- ❌ Furniture rotation, seats, hitboxes (need manual reconfiguration)

## After Import

1. Copy textures/assets from the source plugin to Kalo's pack
2. Review converted configs in `plugins/Kalo/packs/<plugin>/`
3. Run `/kalo reload`
4. Test items in-game

## Manual Conversion

If auto-import doesn't work, convert manually:

1. Create a new pack: `plugins/Kalo/packs/myitem/`
2. Add `pack.yml` with id, version, author
3. Create `configs/items.yml` with item definitions
4. Copy textures to `assets/myitem/textures/`
5. Run `/kalo reload`

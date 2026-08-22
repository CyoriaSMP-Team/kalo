---
layout: default
title: "🔄 Migration"
nav_order: 10
---

# 🔄 Migration Guide

Import content from other plugins to Kalo.

---

## Supported Plugins

| Plugin | Items | Blocks | Furniture | Armor |
|--------|-------|--------|-----------|-------|
| Oraxen | ✅ | ✅ | ✅ | ✅ |
| ItemsAdder | ✅ | ✅ | ✅ | ✅ |
| CraftEngine | ✅ | ✅ | ❌ | ❌ |
| Neko | ✅ | ✅ | ❌ | ❌ |

## Command

```bash
/kalo migrate <plugin> [pack_name]
```

### Examples

```bash
# Migrate from Oraxen
/kalo migrate oraxen

# Migrate from ItemsAdder with custom pack name
/kalo migrate itemsarader mypack

# Migrate from CraftEngine
/kalo migrate craftengine

# Migrate from Neko
/kalo migrate neko
```

## What Gets Migrated

### Items
- Custom model data → Kalo model references
- Display properties (name, lore, enchantments)
- Item behavior (damage, durability)
- Recipes (crafting, smelting)

### Blocks
- Custom block states → Kalo block types
- Hardness, resistance
- Tool requirements
- Drop tables

### Furniture
- Seat mechanics
- Rotation support
- Storage containers
- Interaction behaviors

### Armor
- Custom armor textures
- Equipment models
- Trim patterns

### Sounds
- Custom sound events
- Sound descriptions
- Volume/pitch settings

## Migration Process

1. **Backup** your existing content pack
2. Run `/kalo migrate <plugin>`
3. Review the migrated content in `plugins/Kalo/packs/migrated/`
4. Test each item/block in-game
5. Fix any issues in the YAML files

## Common Issues

### Items Not Showing

Check that model references are correct:

```yaml
model:
  sprite: "item/my_item"  # Should match texture path
```

### Blocks Not Working

Verify block states are valid:

```yaml
block:
  states:
    - default
    - powered
```

### Furniture Not Rotating

Ensure rotation is configured:

```yaml
furniture:
  rotatable: true
  restricted_rotation: strict  # 8 directions
```

## Post-Migration Checklist

- [ ] All items display correctly
- [ ] Blocks have correct textures
- [ ] Furniture rotates and seats work
- [ ] Armor shows on player
- [ ] Sounds play correctly
- [ ] Recipes work in crafting table
- [ ] Bedrock players can see items

---

**Next:** [Configuration]({% link configuration.md %})

**Previous:** [GUIs]({% link guis.md %})

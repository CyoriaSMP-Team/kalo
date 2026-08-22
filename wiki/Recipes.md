# 📋 Recipes

All recipe types supported.

## Crafting Recipe

```yaml
ruby_sword_recipe:
  type: recipe
  result: ruby_sword
  pattern:
    - " R "
    - " R "
    - " S "
  ingredients:
    R: myitem:ruby
    S: minecraft:stick
```

## Shapeless Recipe

```yaml
ruby_dust_recipe:
  type: recipe
  result: 4x myitem:ruby_dust
  ingredients:
    R: myitem:ruby_block
```

## Smelting

```yaml
ruby_smelting:
  type: recipe
  station: furnace
  input: myitem:ruby_ore
  result: myitem:ruby
  experience: 0.7
  cooking_time: 200
```

## Stations

| Station | Station Value |
|---------|---------------|
| Furnace | `furnace` |
| Blast Furnace | `blast_furnace` |
| Smoker | `smoker` |
| Campfire | `campfire` |
| Stonecutter | `stonecutter` |
| Smithing | `smithing` |

## Stonecutter

```ruby_cut:
  type: recipe
  station: stonecutter
  input: myitem:ruby_block
  result: 4x myitem:ruby
```

## Smithing

```yaml
ruby_upgrade:
  type: recipe
  station: smithing
  base: minecraft:netherite_sword
  addition: myitem:ruby
  result: myitem:ruby_sword
```

---

## See Also

- [[Items]] — Custom items
- [[Blocks]] — Custom blocks

---

## 📜 Footer

**Kalo** — Open Custom Content Engine for Minecraft

[GitHub](https://github.com/CyoriaSMP-Team/kalo) • [Issues](https://github.com/CyoriaSMP-Team/kalo/issues) • [Discord](https://discord.gg/kalo) (coming soon)

> **Version:** 0.1.0 • **License:** MIT • **Java:** 21+ / 25

*Build once. Play everywhere.*

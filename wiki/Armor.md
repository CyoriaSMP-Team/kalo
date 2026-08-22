# 🛡️ Armor

Custom armor with equipment textures.

## Basic Armor

```yaml
ruby_helmet:
  type: armor
  slot: head
  model:
    sprite: "item/ruby_helmet"
  equipment:
    humanoid: "ruby"
  java:
    base_material: NETHERITE_HELMET
```

## Slots

| Slot | Description |
|------|-------------|
| `head` | Helmet |
| `chest` | Chestplate |
| `legs` | Leggings |
| `feet` | Boots |

## Equipment Textures

```yaml
equipment:
  humanoid: "texture_name"           # Main texture
  humanoid_leggings: "texture_name"  # Leggings texture (optional)
```

**Texture Locations:**
- `assets/<pack>/textures/entity/equipment/humanoid/texture.png`
- `assets/<pack>/textures/entity/equipment/humanoid_leggings/texture.png`

## Disable Custom Appearance

Keep vanilla armor texture:

```yaml
equipment:
  enabled: false
```

---

## Full Example

```yaml
ruby_helmet:
  type: armor
  slot: head
  display:
    name: "<gold>Ruby Helmet</gold>"
    lore:
      - "<gray>Protects your head</gray>"
  model:
    sprite: "item/ruby_helmet"
  equipment:
    humanoid: "ruby"
  java:
    base_material: NETHERITE_HELMET
  bedrock:
    enabled: true
```

---

## See Also

- [[Items]] — Custom items
- [[Bedrock]] — Bedrock support

---

## 📜 Footer

**Kalo** — Open Custom Content Engine for Minecraft

[GitHub](https://github.com/CyoriaSMP-Team/kalo) • [Issues](https://github.com/CyoriaSMP-Team/kalo/issues) • [Discord](https://discord.gg/kalo) (coming soon)

> **Version:** 0.1.0 • **License:** MIT • **Java:** 21+ / 25

*Build once. Play everywhere.*

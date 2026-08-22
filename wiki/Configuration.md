# ⚙️ Configuration

## Main Config

`plugins/Kalo/config.yml`

```yaml
# Base resource pack to merge with
base-pack: ""

# Bedrock support
bedrock: auto  # auto, always, never

# Pack hosting
pack-host:
  enabled: false
  port: 8163
  public-address: "play.example.com"
  required: false
```

## Content Packs

Packs live in `plugins/Kalo/packs/<pack>/`

### pack.yml

```yaml
id: myitem
version: 1.0
author: YourName
```

### Item Config

```yaml
item_name:
  type: item
  display:
    name: "<gold>Name</gold>"
    lore:
      - "<gray>Description</gray>"
    glint: false
  model:
    sprite: "item/texture"
  behaviour:
    max_stack_size: 64
    durability: null
    fire_resistant: false
  java:
    base_material: PAPER
  bedrock:
    enabled: true
```

### Block Config

```yaml
block_name:
  type: block
  display:
    name: "<gold>Block</gold>"
  model:
    cube_all: "block/texture"
  behaviour:
    hardness: 1.5
    requires_tool: false
  java:
    mode: native
    carrier: NOTE_BLOCK
  bedrock:
    enabled: true
```

### Furniture Config

```yaml
furniture_name:
  type: furniture
  display:
    name: "<gold>Furniture</gold>"
  model:
    custom: "furniture/model"
  behaviour:
    hardness: 2.0
  furniture:
    rotatable: true
    restricted_rotation: strict
    seat:
      height: 0.5
      offset: [0.0, 0.5, 0.0]
    hitbox:
      barriers: [[0,0,0]]
    storage:
      type: STORAGE
      rows: 5
    jukebox:
      volume: 1.0
    light: 0
    waterloggable: false
  java:
    mode: native
```

## Permissions

| Permission | Description |
|------------|-------------|
| `kalo.command.reload` | Reload packs |
| `kalo.command.give` | Give items |
| `kalo.command.import` | Import from plugins |
| `kalo.command.doctor` | Run diagnostics |
| `kalo.command.migrate` | Migrate world blocks |

## Asset Structure

```
assets/<pack>/
├── textures/
│   ├── item/
│   ├── block/
│   └── painting/
├── models/
│   ├── item/
│   └── block/
├── sounds/
└── lang/
    └── en_us.json
```

---

## See Also

- [[Items]] — Items config
- [[Blocks]] — Blocks config
- [[Furniture]] — Furniture config

---

## 📜 Footer

**Kalo** — Open Custom Content Engine for Minecraft

[GitHub](https://github.com/CyoriaSMP-Team/kalo) • [Issues](https://github.com/CyoriaSMP-Team/kalo/issues) • [Discord](https://discord.gg/kalo) (coming soon)

> **Version:** 0.1.0 • **License:** MIT • **Java:** 21+ / 25

*Build once. Play everywhere.*

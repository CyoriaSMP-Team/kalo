---
title: Configuration
---

# ⚙️ Configuration

## Main Config

`plugins/Kalo/config.yml`

```yaml
# Base resource pack to merge with
# Kalo's generated files win on collision
base-pack: ""

# Bedrock support
# auto: build only if Geyser is present
# always: always build
# never: never build
bedrock: auto

# Pack hosting
pack-host:
  enabled: false
  port: 8163
  public-address: "play.example.com"
  required: false  # true = kick players who decline
```

## Content Packs

Packs live in `plugins/Kalo/packs/<pack>/`

### pack.yml

```yaml
id: myitem           # Pack identifier (namespace)
version: 1.0         # Pack version
author: YourName     # Pack author
```

### configs/items.yml

```yaml
item_name:
  type: item
  display:
    name: "<gold>Item Name</gold>"
    lore:
      - "<gray>Description</gray>"
    glint: false
  model:
    sprite: "item/texture_name"  # or vanilla/custom
  behaviour:
    max_stack_size: 64
    durability: null
    fire_resistant: false
  java:
    base_material: PAPER
  bedrock:
    enabled: true
    icon: custom_icon_name
```

### configs/blocks.yml

```yaml
block_name:
  type: block
  display:
    name: "<gold>Block Name</gold>"
  model:
    cube_all: "block/texture"  # or cube/custom
  behaviour:
    hardness: 1.5
    requires_tool: false
  java:
    mode: native  # native or virtual
    carrier: NOTE_BLOCK  # NOTE_BLOCK, TRIPWIRE, SCAFFOLDING
  bedrock:
    enabled: true
```

### configs/furniture.yml

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
  display_transform:
    brightness:
      block_light: 0
      sky_light: 15
```

### configs/armor.yml

```yaml
armor_name:
  type: armor
  slot: head  # head, chest, legs, feet
  display:
    name: "<gold>Armor</gold>"
  model:
    sprite: "item/armor_texture"
  equipment:
    humanoid: "texture_name"
    humanoid_leggings: "texture_name"  # optional
  java:
    base_material: NETHERITE_HELMET
```

### configs/paintings.yml

```yaml
painting_name:
  type: painting
  width: 1
  height: 1
  asset_id: "myitem:painting"
  author: "Author"
  title: "Title"
  animated: false
  frame_duration: 20
```

### configs/music_discs.yml

```yaml
disc_name:
  type: music_disc
  sound: "myitem:music.track"
  description: "Track Description"
  duration: 60
  comparator_output: 7
  model:
    sprite: "item/disc_texture"
```

### configs/guis.yml

```yaml
menu_name:
  type: gui
  title: "<gold>Menu</gold>"
  rows: 3
  items:
    0:
      material: STONE
      display_name: "<gold>Item</gold>"
      lore:
        - "<gray>Description</gray>"
      actions:
        - "command:/cmd %player%"
  close_actions:
    - "message:<gray>Closed</gray>"
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
│   ├── item/          # Item textures (PNG)
│   ├── block/         # Block textures (PNG)
│   └── painting/      # Painting textures (PNG)
├── models/
│   ├── item/          # Item models (JSON)
│   └── block/         # Block models (JSON)
├── sounds/            # Sound files (OGG)
└── lang/
    └── en_us.json     # Translations
```

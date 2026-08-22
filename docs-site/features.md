---
layout: default
title: 📦 Features
nav_order: 3
---

# 📦 Features

Kalo supports all major content types for Minecraft servers.

## Content Types

### 🗡️ Items

Custom items with models, textures, and behaviors.

```yaml
ruby_sword:
  type: item
  display:
    name: "<gold>Ruby Sword</gold>"
  model:
    sprite: "item/ruby_sword"
  behaviour:
    durability: 250
  java:
    base_material: NETHERITE_SWORD
```

**Model Types:**
- `sprite` — Flat texture (auto-generated model)
- `vanilla` — Reuse a vanilla item's appearance
- `custom` — Hand-authored model

### 🧱 Custom Blocks

Placeable blocks using vanilla block states.

**Modes:**
- `native` — Borrows a real vanilla block state (893 available)
- `virtual` — Uses ItemDisplay entity (unlimited, but no Bedrock)

```yaml
ruby_block:
  type: block
  model:
    cube_all: "block/ruby_block"
  behaviour:
    hardness: 3.0
  java:
    mode: native
```

### 🪑 Furniture

Rotatable furniture with seats, hitboxes, storage, and more.

```yaml
ruby_chair:
  type: furniture
  model:
    custom: "furniture/ruby_chair"
  furniture:
    rotatable: true
    restricted_rotation: strict  # 8 facings
    seat:
      height: 0.5
      offset: [0.0, 0.5, 0.0]
    hitbox:
      barriers: [[0,0,0]]
```

**See:** [Furniture Guide](furniture)

### 🛡️ Armor

Custom armor with equipment textures.

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

### 🎨 Paintings

Custom paintings with configurable dimensions.

```yaml
ruby_painting:
  type: painting
  width: 1
  height: 1
  asset_id: "myitem:ruby_painting"
  author: "Kalo"
  title: "Ruby Portrait"
```

### 🎵 Music Discs

Custom jukebox-playable discs.

```yaml
ruby_disc:
  type: music_disc
  sound: "myitem:music.ruby_theme"
  description: "Ruby Theme"
  duration: 180
  comparator_output: 12
  model:
    sprite: "item/ruby_disc"
```

### 🖥️ Custom GUIs

Server-side inventory menus.

```yaml
main_menu:
  type: gui
  title: "<gold>Server Menu</gold>"
  rows: 3
  items:
    4:
      material: NETHER_STAR
      display_name: "<gold>Teleport</gold>"
      actions:
        - "gui:myitem:teleport_menu"
```

### 🔊 Sounds

Custom sound events.

```yaml
cave_wind:
  type: sound
  category: ambient
  subtitle: "subtitles.myitem.cave_wind"
  sounds:
    - "ambient/cave_wind"
```

### ✍️ Glyphs

Custom font characters for icons.

```yaml
coin:
  type: glyph
  texture: "font/coin"
  character: "U+E000"
  ascent: 8
  height: 9
```

### 📋 Recipes

All recipe types supported.

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

## Comparison with Nexo

| Feature | Kalo | Nexo |
|---------|------|------|
| Items | ✅ | ✅ |
| Blocks | ✅ 893 native | ✅ |
| Furniture | ✅ | ✅ |
| Armor | ✅ | ✅ |
| Paintings | ✅ | ✅ |
| Music Discs | ✅ | ✅ |
| GUIs | ✅ | ✅ |
| Bedrock | ✅ Free | €40 addon |
| **Price** | **FREE** | €67.98 |

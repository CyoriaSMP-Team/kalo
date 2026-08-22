---
layout: default
title: 🪑 Furniture
nav_order: 4
---

# 🪑 Furniture Guide

Furniture pieces are rotatable, sittable, and can have storage, hitboxes, and jukebox support.

## Basic Furniture

```yaml
ruby_chair:
  type: furniture
  display:
    name: "<gold>Ruby Chair</gold>"
  model:
    custom: "furniture/ruby_chair"
  behaviour:
    hardness: 2.0
  furniture:
    hitbox:
      barriers: [[0,0,0]]
```

## Rotation

Furniture can be rotated by players (sneak + right-click).

```yaml
furniture:
  rotatable: true
  restricted_rotation: strict  # Options: strict (8), very_strict (4), or none (16)
```

**Rotation Options:**
- `strict` — 8 facings (N, NE, E, SE, S, SW, W, NW)
- `very_strict` — 4 facings (N, E, S, W)
- Default — 16 facings

## Seating

Make furniture sittable.

```yaml
furniture:
  seat:
    height: 0.5      # Seat height above block
    offset: [0.0, 0.5, 0.0]  # [x, y, z] offset
```

## Hitbox

Custom collision boxes using barriers.

```yaml
furniture:
  hitbox:
    barriers:
      - [0,0,0]      # Single block
      - [1,0,0]      # Extends 1 block in X
```

## Storage

Persistent inventory storage.

```yaml
furniture:
  storage:
    type: STORAGE           # STORAGE | PERSONAL | ENDERCHEST | DISPOSAL
    rows: 5                 # 1-6 rows
    title: "<red>Chest</red>"
    open_sound: entity.chest.open
    close_sound: entity.chest.close
```

**Storage Types:**
- `STORAGE` — Shared inventory (everyone can access)
- `PERSONAL` — Per-player inventory
- `ENDERCHEST` — Player's ender chest
- `DISPOSAL` — Items deleted when closed

## Jukebox

Play custom music discs.

```yaml
furniture:
  jukebox:
    volume: 1.0
    pitch: 1.0
    permission: "kalo.jukebox.play"
```

## Light Emission

```yaml
furniture:
  light: 15  # 0-15 light level
```

## Waterlogging

```yaml
furniture:
  waterloggable: true
```

## Display Transform

Control how furniture renders.

```yaml
display_transform:
  display_transform: NONE
  tracking_rotation: FIXED
  translation: [0, 0.5, 0]
  scale: [1.0, 1.0, 1.0]
  brightness:
    block_light: 15
    sky_light: 0
  shadow_radius: 0.5
  shadow_strength: 0.8
```

## Full Example

```yaml
ruby_chair:
  type: furniture
  display:
    name: "<gold>Ruby Chair</gold>"
    lore:
      - "<gray>A comfortable chair</gray>"
  model:
    custom: "furniture/ruby_chair"
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
    waterloggable: false
  java:
    mode: native
```

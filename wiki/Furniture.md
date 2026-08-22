# 🪑 Furniture

Furniture pieces are rotatable, sittable, and can have storage, hitboxes, and jukebox support.

## Basic Furniture

```yaml
ruby_chair:
  type: furniture
  display:
    name: "<gold>Ruby Chair</gold>"
  model:
    custom: "furniture/ruby_chair"
  furniture:
    hitbox:
      barriers: [[0,0,0]]
```

## Rotation

Furniture can be rotated by players (sneak + right-click).

```yaml
furniture:
  rotatable: true
  restricted_rotation: strict
```

**Rotation Options:**

| Option | Facings | Description |
|--------|---------|-------------|
| `strict` | 8 | N, NE, E, SE, S, SW, W, NW |
| `very_strict` | 4 | N, E, S, W |
| Default | 16 | All directions |

## Seating

Make furniture sittable.

```yaml
furniture:
  seat:
    height: 0.5
    offset: [0.0, 0.5, 0.0]
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `height` | double | 0.5 | Seat height above block |
| `offset` | list | [0,0.5,0] | [x, y, z] offset from center |

## Hitbox

Custom collision boxes using barriers.

```yaml
furniture:
  hitbox:
    barriers:
      - [0,0,0]
      - [1,0,0]
```

Each entry is `[x, y, z]` offset from the block position.

## Storage

Persistent inventory storage.

```yaml
furniture:
  storage:
    type: STORAGE
    rows: 5
    title: "<red>Chest</red>"
    open_sound: entity.chest.open
    close_sound: entity.chest.close
```

**Storage Types:**

| Type | Description |
|------|-------------|
| `STORAGE` | Shared inventory (everyone can access) |
| `PERSONAL` | Per-player inventory |
| `ENDERCHEST` | Player's ender chest |
| `DISPOSAL` | Items deleted when closed |

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

## Limited Placing

Restrict where furniture can be placed.

```yaml
furniture:
  limited_placing:
    roof: false
    floor: true
    wall: false
    type: ALLOW
    block_types:
      - GRASS_BLOCK
      - DIRT
    block_tags:
      - "#base_stone"
    nexo_blocks:
      - "myitem:floor_block"
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
  view_range: 1.0
  display_width: 1.0
  display_height: 1.0
```

---

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

---

## See Also

- [[Items]] — Custom items
- [[Blocks]] — Custom blocks
- [[Configuration]] — All config options

---

## 📜 Footer

**Kalo** — Open Custom Content Engine for Minecraft

[GitHub](https://github.com/CyoriaSMP-Team/kalo) • [Issues](https://github.com/CyoriaSMP-Team/kalo/issues) • [Discord](https://discord.gg/kalo) (coming soon)

> **Version:** 0.1.0 • **License:** MIT • **Java:** 21+ / 25

*Build once. Play everywhere.*

---
layout: default
title: "🎨 Paintings"
nav_order: 7
---

# 🎨 Custom Paintings

Add custom paintings to your Minecraft server with Kalo.

---

## Basic Painting

```yaml
ruby_painting:
  type: painting
  width: 1
  height: 1
  asset_id: "testpack:ruby_painting"
  author: "Kalo"
  title: "Ruby Portrait"
```

## Multi-Block Painting

```yaml
large_painting:
  type: painting
  width: 4
  height: 3
  asset_id: "testpack:large_painting"
  author: "Kalo"
  title: "Epic Landscape"
```

## Supported Sizes

| Width | Height | Blocks |
|-------|--------|--------|
| 1 | 1 | 1×1 |
| 1 | 2 | 1×2 |
| 2 | 1 | 2×1 |
| 2 | 2 | 2×2 |
| 4 | 2 | 4×2 |
| 2 | 4 | 2×4 |
| 4 | 3 | 4×3 |
| 4 | 4 | 4×4 |

## Configuration Options

| Option | Type | Description |
|--------|------|-------------|
| `type` | string | Must be `painting` |
| `width` | int | Width in blocks (1-4) |
| `height` | int | Height in blocks (1-4) |
| `asset_id` | string | Namespace:path to texture |
| `author` | string | Artist name |
| `title` | string | Painting name |

## Texture Requirements

- Format: PNG
- Size: Must match width × height (e.g., 1×1 = 16×16px, 4×3 = 64×48px)
- Path: `assets/<namespace>/paintings/<path>.png`

## Bedrock Support

Custom paintings automatically render on Bedrock through the existing painting replacement system. No extra configuration needed.

---

**Next:** [Music Discs]({% link music-discs.md %})

**Previous:** [Furniture]({% link furniture.md %})

---
title: Paintings
---

# 🎨 Paintings Guide

Custom paintings with configurable dimensions and metadata.

## Basic Painting

```yaml
ruby_painting:
  type: painting
  width: 1
  height: 1
  asset_id: "myitem:ruby_painting"
```

## Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `width` | int | 1 | Width in blocks (1-4) |
| `height` | int | 1 | Height in blocks (1-4) |
| `asset_id` | string | required | Texture asset identifier |
| `author` | string | null | Painting author |
| `title` | string | null | Painting title |
| `animated` | boolean | false | Has frame animation |
| `frame_duration` | int | 20 | Ticks per frame |

## Examples

### Small Painting (1x1)

```yaml
ruby_small:
  type: painting
  width: 1
  height: 1
  asset_id: "myitem:ruby_small"
  author: "Kalo"
  title: "Ruby Portrait"
```

### Large Painting (4x4)

```yaml
ruby_large:
  type: painting
  width: 4
  height: 4
  asset_id: "myitem:ruby_large"
  author: "Kalo"
  title: "Ruby Masterpiece"
```

### Animated Painting

```yaml
ruby_animated:
  type: painting
  width: 2
  height: 2
  asset_id: "myitem:ruby_animated"
  animated: true
  frame_duration: 10
```

## Textures

Place painting textures in:
```
assets/<pack>/textures/painting/<asset_id>.png
```

## Placement

Paintings are placed using the vanilla painting mechanic. Players can place them on walls like normal paintings.

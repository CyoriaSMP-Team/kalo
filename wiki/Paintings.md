# 🎨 Paintings

Custom paintings with configurable dimensions.

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
| `asset_id` | string | required | Texture identifier |
| `author` | string | null | Painting author |
| `title` | string | null | Painting title |
| `animated` | boolean | false | Has animation |
| `frame_duration` | int | 20 | Ticks per frame |

## Examples

### Small (1x1)

```yaml
ruby_small:
  type: painting
  width: 1
  height: 1
  asset_id: "myitem:ruby_small"
  author: "Kalo"
  title: "Ruby Portrait"
```

### Large (4x4)

```yaml
ruby_large:
  type: painting
  width: 4
  height: 4
  asset_id: "myitem:ruby_large"
  author: "Kalo"
  title: "Ruby Masterpiece"
```

### Animated

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

Place in: `assets/<pack>/textures/painting/<asset_id>.png`

---

## See Also

- [[Items]] — Custom items
- [[Configuration]] — All config options

---

## 📜 Footer

**Kalo** — Open Custom Content Engine for Minecraft

[GitHub](https://github.com/CyoriaSMP-Team/kalo) • [Issues](https://github.com/CyoriaSMP-Team/kalo/issues) • [Discord](https://discord.gg/kalo) (coming soon)

> **Version:** 0.1.0 • **License:** MIT • **Java:** 21+ / 25

*Build once. Play everywhere.*

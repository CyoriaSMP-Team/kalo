# 🔊 Sounds

Custom sound events.

## Basic Sound

```yaml
cave_wind:
  type: sound
  category: ambient
  subtitle: "subtitles.myitem.cave_wind"
  sounds:
    - "ambient/cave_wind"
```

## Options

| Option | Type | Description |
|--------|------|-------------|
| `category` | string | Sound category |
| `subtitle` | string | Subtitle translation key |
| `sounds` | list | Sound file paths |

## Categories

| Category | Description |
|----------|-------------|
| `master` | Master category |
| `music` | Music |
| `record` | Music discs |
| `weather` | Weather effects |
| `block` | Block sounds |
| `hostile` | Hostile mobs |
| `neutral` | Neutral mobs |
| `player` | Player sounds |
| `ambient` | Ambient sounds |
| `voice` | Voice/commands |

## Examples

### Ambient Sound

```yaml
cave_wind:
  type: sound
  category: ambient
  subtitle: "subtitles.myitem.cave_wind"
  sounds:
    - "ambient/cave_wind"
```

### Block Sound

```yaml
ruby_place:
  type: sound
  category: block
  subtitle: "subtitles.myitem.ruby_place"
  sounds:
    - "block/ruby_place"
```

### Multiple Variations

```yaml
ruby_hit:
  type: sound
  category: block
  sounds:
    - "block/ruby_hit1"
    - "block/ruby_hit2"
    - "block/ruby_hit3"
```

## Files

Place in: `assets/<pack>/sounds/<path>.ogg`

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

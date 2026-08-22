---
layout: default
title: "🎵 Music Discs"
nav_order: 8
---

# 🎵 Custom Music Discs

Add custom music discs that players can play in jukeboxes.

---

## Basic Music Disc

```yaml
ruby_disc:
  type: music_disc
  sound: "testpack:music.ruby_theme"
  description: "Ruby Theme - A majestic melody"
  duration: 180
  comparator_output: 12
  display:
    name: "<gradient:#ff5f6d:#ffc371>Ruby Disc</gradient>"
  model:
    sprite: "item/ruby_disc"
```

## Configuration Options

| Option | Type | Description |
|--------|------|-------------|
| `type` | string | Must be `music_disc` |
| `sound` | string | Sound event to play |
| `description` | string | Description shown in tooltip |
| `duration` | int | Duration in seconds |
| `comparator_output` | int | Redstone signal (1-15) |
| `display.name` | string | Display name with colors |
| `display.lore` | list | Description lines |
| `model.sprite` | string | Sprite texture path |

## Sound Requirements

- Format: OGG Vorbis
- Path: `assets/<namespace>/sounds/<path>.ogg`
- Reference in `sounds.json`:

```json
{
  "music.ruby_theme": {
    "sounds": [
      {
        "name": "testpack:music/ruby_theme",
        "stream": true
      }
    ]
  }
}
```

## Comparator Output

The comparator output determines the redstone signal when a disc is in a jukebox:

| Signal | Use Case |
|--------|----------|
| 1 | Background music |
| 6 | Standard tracks |
| 12 | Featured tracks |
| 15 | Special tracks |

## Bedrock Support

Custom music discs automatically work on Bedrock through the existing sound replacement system.

---

**Next:** [GUIs]({% link guis.md %})

**Previous:** [Paintings]({% link paintings.md %})

---
title: Music Discs
---

# 🎵 Music Discs Guide

Custom jukebox-playable music discs.

## Basic Music Disc

```yaml
ruby_disc:
  type: music_disc
  sound: "myitem:music.ruby_theme"
  description: "Ruby Theme - A majestic melody"
  duration: 180
  comparator_output: 12
  model:
    sprite: "item/ruby_disc"
```

## Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `sound` | key | required | Sound event to play |
| `description` | string | required | Tooltip text |
| `duration` | int | 60 | Duration in seconds |
| `comparator_output` | int | 7 | Redstone signal (1-15) |

## Sound Files

Place sound files in:
```
assets/<pack>/sounds/music/<sound_name>.ogg
```

## Examples

### Short Jingle

```yaml
jingle_disc:
  type: music_disc
  sound: "myitem:music.jingle"
  description: "Holiday Jingle"
  duration: 30
  comparator_output: 5
  model:
    sprite: "item/jingle_disc"
```

### Full Album Disc

```yaml
album_disc:
  type: music_disc
  sound: "myitem:music.album_track"
  description: "Full Album Track"
  duration: 240
  comparator_output: 14
  display:
    name: "<gold>Album Disc</gold>"
    lore:
      - "<gray>Plays the full album</gray>"
  model:
    sprite: "item/album_disc"
```

## Usage

Players can:
1. Hold the disc and right-click a jukebox
2. The disc plays and shows in the jukebox tooltip
3. Comparator outputs signal strength based on `comparator_output`

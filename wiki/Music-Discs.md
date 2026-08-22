# 🎵 Music Discs

Custom jukebox-playable music discs.

## Basic Music Disc

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

## Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `sound` | key | required | Sound event |
| `description` | string | required | Tooltip text |
| `duration` | int | 60 | Duration in seconds |
| `comparator_output` | int | 7 | Redstone signal (1-15) |

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

### Full Track

```yaml
album_disc:
  type: music_disc
  sound: "myitem:music.album_track"
  description: "Full Album Track"
  duration: 240
  comparator_output: 14
  display:
    name: "<gold>Album Disc</gold>"
  model:
    sprite: "item/album_disc"
```

## Sounds

Place in: `assets/<pack>/sounds/music/<sound_name>.ogg`

---

## See Also

- [[Items]] — Custom items
- [[Furniture]] — Jukebox furniture

---

## 📜 Footer

**Kalo** — Open Custom Content Engine for Minecraft

[GitHub](https://github.com/CyoriaSMP-Team/kalo) • [Issues](https://github.com/CyoriaSMP-Team/kalo/issues) • [Discord](https://discord.gg/kalo) (coming soon)

> **Version:** 0.1.0 • **License:** MIT • **Java:** 21+ / 25

*Build once. Play everywhere.*

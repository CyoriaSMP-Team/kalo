# ✍️ Glyphs

Custom font characters for icons in chat, names, and menus.

## Basic Glyph

```yaml
coin:
  type: glyph
  texture: "font/coin"
  character: "U+E000"
  ascent: 8
  height: 9
```

## Options

| Option | Type | Description |
|--------|------|-------------|
| `texture` | string | Texture path |
| `character` | string | Unicode codepoint |
| `ascent` | int | Ascent height |
| `height` | int | Character height |

## Character Codes

Use Private Use Area (PUA) to avoid conflicts:

| Range | Description |
|-------|-------------|
| `U+E000 - U+F8FF` | Private Use Area |
| `U+F0000 - U+FFFFF` | Supplementary PUA-A |
| `U+100000 - U+10FFFF` | Supplementary PUA-B |

## Examples

### Simple Icon

```yaml
coin:
  type: glyph
  texture: "font/coin"
  character: "U+E000"
  ascent: 8
  height: 9
```

### With Literal Character

```yaml
star:
  type: glyph
  texture: "font/star"
  character: "★"
  ascent: 8
  height: 9
```

## Usage

In MiniMessage:
```yaml
name: "<gold>Coin: <custom:coin> 100</gold>"
```

In chat:
```
/chat <custom:coin> 100 coins
```

## Files

Place in: `assets/<pack>/textures/font/<texture>.png`

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

# 🖼️ HUDs

HUD overlays are images bound to a Private Use Area character and drawn as text. The same
glyph compiles into the Java font and a Bedrock glyph page.

## Basic HUD

```yaml
mana:
  type: hud
  texture: "hud/mana"
  character: "U+E100"
  height: 32
  ascent: 24
  offset: 0          # pixels; negative shifts left
  slot: action_bar   # action_bar | boss_bar
```

The image lives at `assets/<pack>/textures/hud/mana.png` (or another path matching
`texture:`).

## Options

| Option | Type | Description |
|--------|------|-------------|
| `texture` | string | Texture path, relative to the pack namespace |
| `character` | string / int | Private Use Area codepoint, e.g. `"U+E100"` |
| `height` | int | Rendered glyph height in pixels |
| `ascent` | int | Pixels above the baseline; must not exceed `height` |
| `offset` | int | Horizontal pixels, -512 to 512; negative shifts left |
| `slot` | string | `action_bar` (re-sent every second) or `boss_bar` |
| `font` | string | Font key; defaults to `minecraft:default` |

## Commands

```bash
/kalo hud testpack:mana
/kalo hud stop
```

Permission: `kalo.command.hud`. A `session-content.permissions` entry for the HUD key can
also gate it.

## Limits

- Java uses a supplementary Private Use Area `space` provider for the offset. Bedrock
  shifts the glyph inside its page cell by the scaled offset; an offset larger than the
  cell is clipped rather than wrapped.
- Bedrock reads the glyph from a generated 16×16-page atlas, so very large HUD artwork is
  scaled into a page cell.
- A boss-bar HUD stays until `/kalo hud stop`; an action-bar HUD is refreshed once a second.

---
layout: default
title: 🌏 Bedrock
nav_order: 5
---

# 🌏 Bedrock Support

One YAML definition compiles to a Java pack and to Bedrock output. Kalo writes a `.mcpack` and registers it with Geyser.

**Pre-alpha:** only the rows marked tested have been seen on a real Bedrock client.

| Feature | Status | Notes |
|---|---|---|
| Sprite item icons | ✅ Tested on a Bedrock client | Derived from your Java pack |
| Native blocks | ✅ Tested on a Bedrock client | Placed and mined |
| Worn armor | ✅ Tested on a Bedrock client | Attachable with the vanilla layer hidden |
| 3D item models | ⚠️ Experimental, not client-verified | Static cuboid models, including multi-sheet textures |
| Held 3D item position | ⚠️ Experimental, not client-verified | Generated from the model `display` transforms |
| Virtual ItemDisplay blocks | ❌ Not supported | Geyser cannot show an `ItemDisplay` to Bedrock |
| Furniture, paintings, sounds | ⚠️ Verify with your content pack | Block-backed or basic support |
| DeluxeMenus as Forms | ⚠️ Experimental | Opt-in, needs Floodgate |

## Setup

**Same server (recommended):** install Geyser, optionally Floodgate, then Kalo. Kalo registers everything with Geyser directly.

**Separate Geyser process:** set `bedrock: always`, then copy the generated files or sync them:

```yaml
bedrock: always
bedrock-standalone:
  directory: "/path/to/geyser"   # must contain Geyser's config.yml and packs/
```

## Configuration

| Key | Default | Meaning |
|---|---|---|
| `bedrock` | `auto` | `auto` only when Geyser is on this server, `always`, or `never` |
| `bedrock-standalone.directory` | empty | Standalone Geyser directory to sync into |
| `bedrock-forms.enabled` | `false` | Convert eligible DeluxeMenus menus to Bedrock Forms (needs Floodgate) |

## Held 3D items

Kalo turns the Java model's `firstperson_righthand` and `thirdperson_righthand` transforms into per-item hold animations. The conversion is an approximation not yet measured on a client. Vanilla parent models are not read, and only the main hand is posed.

## Troubleshooting

- Blocks not rendering: check the Kalo and Geyser logs, restart after the first install.
- Missing texture: verify `assets/<pack>/textures/`, run `/kalo doctor`, then `/kalo reload`.
- Virtual blocks invisible: structural; use native mode for content Bedrock players need.

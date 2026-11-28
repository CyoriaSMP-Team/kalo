---
layout: default
title: 🌏 Bedrock
nav_order: 5
---

# 🌏 Bedrock Support

Kalo has built-in Bedrock support through Geyser. No additional plugins or paid addons required.

## Requirements

- Geyser running either as a server plugin or as a standalone/proxy process
- Floodgate for Bedrock authentication and Cumulus Forms support (optional depending on features)
- For direct native registration, Geyser must share the backend JVM; standalone/proxy deployments use exported mappings and the generated `.mcpack`

## Setup

### Geyser on the backend

If Geyser runs as a plugin on the same backend server, Kalo can use Geyser's API for native registration:

1. Install Geyser and Floodgate
2. Install Kalo
3. Start the server

Kalo will log:
```
[Kalo] Registering Kalo items, blocks and resource pack with Geyser directly
[Kalo] Registered and mapped 2 block(s) with Geyser natively
[Kalo] Registered 8 item(s) with Geyser natively
[Kalo] Kalo Bedrock packs are staged per session
```

### Geyser on Velocity / standalone

If Geyser runs in a proxy or separate process, Kalo cannot call its in-process API. Export the generated files from the backend and install them on the Geyser host:

1. Copy `plugins/Kalo/geyser/kalo-items.json` to Geyser's `custom_mappings/`
2. Copy `plugins/Kalo/geyser/kalo-blocks.json` to Geyser's `custom_mappings/`
3. Copy `plugins/Kalo/generated.mcpack` to Geyser's `packs/`

## Configuration

```yaml
# plugins/Kalo/config.yml
bedrock: auto  # Options: auto, always, never
bedrock-packs:
  per-player: true  # one staged .mcpack per Bedrock session
  transfer: true    # reconnect online sessions when the pack changes
```

- `auto` — Build Bedrock output when in-process Geyser is present or `bedrock-standalone.directory` is configured
- `always` — Always build Bedrock pack (required for a Velocity-proxy Geyser Kalo cannot see)
- `never` — Never build Bedrock pack

## Supported Content

| Content | Bedrock Status |
|---------|----------------|
| Items (2D sprites) | ✅ Works |
| Items (static 3D models) | ⚠️ Restricted static Java cuboid conversion and experimental Blockbench `.bbmodel` import; held-item animation wired, placed-model client entities experimental |
| Custom Blocks (native) | ✅ Works |
| Custom Blocks (virtual) | ❌ Not supported |
| Furniture (native) | ✅ Works |
| Armor | ✅ Works |
| Paintings | ⚠️ Basic support |
| Music Discs | ⚠️ Basic support |
| GUIs / Forms | ⚠️ Java menus are supported separately; Bedrock Cumulus forms require a reachable Floodgate/Geyser Cumulus API |
| Glyphs | ✅ Works |

## Blockbench `.bbmodel`

Blockbench format 3.2–5.0 cube models can be used through the shared model IR. The
importer preserves outliner hierarchy, pivots and per-face UVs; Bedrock receives real
parented bones instead of one flattened bone. Java receives compiled model JSON.

```yaml
model:
  blockbench: models/item/ruby_sword.bbmodel
  textures:
    "0": item/ruby_sword
```

Limits are explicit: box-UV and mesh models are unsupported, rotated bones are refused on
Java, held-item animation keyframes are compiled into Bedrock attachables, and a `place:`
section creates a Java display entity plus a Bedrock client entity with authored
animation. Seats work as a temporary marker; storage and light/solid/model states are implemented.
Animated blocks/furniture play loop animations on Java through per-bone display rigs and on
Bedrock through a client entity with a transparent block surface. Custom mobs, 3D worn
armor, rideable vehicles and emote props compile to Bedrock entities/attachables and to Java
display rigs; vehicles handle land/water/air motion and solid-block collision; mobs and 3D armor also animate per bone on Java, with parent-bone motion
composed through the chain, and play a configured interaction clip once. Blockbench
animation controllers compile to Bedrock and select Java clips through a state machine
with variables, remap curves, math functions and blend weights; player-body emotes bake a
skin and render per-bone on Java and as a Bedrock client entity/attachable, with
multi-performer casts and per-player cached or auto-fetched skin overlays; Java receives a
small per-player overlay for the emote's unique texture path when the local pack host is
available. Each Bedrock session
receives its own staged pack, optionally filtered by a per-XUID content namespace/key policy,
and replaced and re-served on a content update. Placed-model
placed models, animated blocks/furniture, custom mobs and 3D worn armor play a configured
interaction clip once. Placed models, blocks and furniture use per-bone display rigs for
authored loops. See the
full matrix in `docs/BLOCKBENCH.md` in the repository.

## Virtual Blocks on Bedrock

Virtual blocks use `ItemDisplay` entities, which Geyser cannot translate to Bedrock clients. On servers with Bedrock players, use native mode for content they need to see.

## Troubleshooting

### Blocks not rendering

1. Check Kalo and Geyser logs for registration messages
2. Ensure Geyser is loaded before Kalo registers
3. Restart server after first installation

### Items show as missing texture

1. Verify textures exist in `assets/<pack>/textures/`
2. Run `/kalo doctor` to check for broken references
3. Regenerate pack with `/kalo reload`

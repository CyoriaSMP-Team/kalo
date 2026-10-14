# 🌏 Bedrock Support

Kalo has built-in Bedrock support through Geyser. No additional plugins or paid addons required.

## Requirements

- [Geyser](https://geysermc.org/) plugin installed on the same server
- [Floodgate](https://geysermc.org/) (optional, for Bedrock player authentication)

## Setup

### Same Server (Recommended)

If Geyser runs as a plugin on the same server, Kalo automatically registers everything:

1. Install Geyser and Floodgate
2. Install Kalo
3. Start the server

Kalo will log:
```
[Kalo] Registering Kalo items, blocks and resource pack with Geyser directly
[Kalo] Registered and mapped 2 block(s) with Geyser natively
[Kalo] Registered 8 item(s) with Geyser natively
[Kalo] Registered generated.mcpack with Geyser
```

### Standalone Geyser

If Geyser runs separately:

1. Copy `plugins/Kalo/geyser/kalo-items.json` to Geyser's `custom_mappings/`
2. Copy `plugins/Kalo/geyser/kalo-blocks.json` to Geyser's `custom_mappings/`
3. Copy `plugins/Kalo/generated.mcpack` to Geyser's `packs/`

## Standalone Geyser auto-sync (opt-in)

When Geyser runs separately on the same machine or shared filesystem,
Kalo can copy its generated Bedrock pack and both mapping files there.
This is **off by default**; it never restarts Geyser.

Configure the existing Geyser root, with `config.yml` and `packs/`:

~~~yaml
bedrock: always
bedrock-standalone:
  directory: "/srv/geyser"
~~~

Kalo updates only `packs/kalo-generated.mcpack` and
`custom_mappings/kalo-items.json` / `kalo-blocks.json`. Other mappings
and packs are untouched. Staged writes replace individual files atomically
where the filesystem permits. Existing symlink targets are rejected.

**Restart standalone Geyser** after modifying mappings or packs.
This is not a Geyser cross-process hot-reload API.

## Configuration

```yaml
# plugins/Kalo/config.yml
bedrock: auto  # Options: auto, always, never
```

| Option | Description |
|--------|-------------|
| `auto` | Build if in-process Geyser is present or standalone sync directory is configured |
| `always` | Always build Bedrock pack |
| `never` | Never build Bedrock pack |

## Supported Content

| Content | Bedrock Status |
|---------|----------------|
| Items (2D sprites) | ✅ Works |
| Items (3D models, one texture sheet) | Experimental static geometry + held-item attachables |
| Items (3D models, multiple texture sheets) | Experimental UV atlas (same-size sheets, 16 max) |
| Item animations and Java display transforms | Not yet supported |
| Custom Blocks (native) | ✅ Works |
| Custom Blocks (virtual) | ❌ Not supported |
| Furniture (native) | ✅ Works |
| Armor | ✅ Works |
| Paintings | ⚠️ Basic support |
| Music Discs | ⚠️ Basic support |
| GUIs | ❌ Server-side only |
| Glyphs | ✅ Works |

## Static custom 3D items (experimental)

Kalo now compiles hand-authored cuboid Java item models (for example those built
in Blockbench) into a Bedrock geometry file, a held-item attachable, an inventory
icon and a Geyser v2 mapping. The model must contain convertible cuboid elements either directly or through
an available authored parent model, and its textured faces must resolve to
supported PNG sheets. Texture aliases from model JSON and Kalo YAML resolve.

Example YAML definition (under configs/items.yml):

~~~yaml
wrench:
  type: item
  display:
    name: Wrench
  model:
    custom: item/wrench
    textures:
      "0": item/wrench
  java:
    base_material: PAPER
~~~

Place the source JSON model at assets/<namespace>/models/item/wrench.json and
its PNG at assets/<namespace>/textures/item/wrench.png.

Generated output:
- models/entity/<namespace>_wrench.geo.json — Bedrock geometry
- attachables/<namespace>_wrench.json — bound to the held item slot
- textures/items/<namespace>_wrench.png and its atlas entry — provisional icon
- Geyser v2 item mapping matching the Java item model key

Parent model inheritance for geometry and texture overrides is resolved up to
16 levels with cycle detection. Generated attachables include the standard
Bedrock first-person and third-person hold animations.

**Current limitations:** static cuboid models only. For multi-sheet items,
Kalo combines up to 16 same-size PNGs into a deterministic horizontal UV atlas
(maximum width 4096 px) and rewrites the cuboid face UV origins. Mixed sheet
dimensions, parent-only geometry, transforms and animations remain unsupported. The source texture sheet is the
current inventory icon; rendering a dedicated 3D icon is future work.
Live Bedrock player testing is still required before production deployment.

This is content rendering, not a Forge login or client-side mod compatibility
bridge. Mod-specific gameplay needs separate integration.

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

---

## See Also

- [[Getting-Started]] — Installation guide
- [[Configuration]] — All config options

---

## 📜 Footer

**Kalo** — Open Custom Content Engine for Minecraft

[GitHub](https://github.com/CyoriaSMP-Team/kalo) • [Issues](https://github.com/CyoriaSMP-Team/kalo/issues) • [Discord](https://discord.gg/kalo) (coming soon)

> **Version:** 0.1.0 • **License:** MIT • **Java:** 21+ / 25

*Build once. Play everywhere.*

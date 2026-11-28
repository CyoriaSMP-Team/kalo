# 🌏 Bedrock Support

Kalo has built-in Bedrock support through Geyser. No additional plugins or paid addons required.

## Requirements

- [Geyser](https://geysermc.org/) plugin installed on the same backend server, or a
  standalone/proxy Geyser reachable through exported files
- [Floodgate](https://geysermc.org/) (optional, for Bedrock authentication and Cumulus
  forms)

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
[Kalo] Kalo Bedrock packs are staged per session
```

### Geyser on a Velocity proxy

Geyser lives in the proxy JVM; Floodgate lives on the backend. The backend cannot call
Geyser's JVM API, so Kalo exports files and never attempts an in-process call:

1. Set `bedrock: always` on the backend (or configure `bedrock-standalone.directory`)
2. Copy `plugins/Kalo/generated.mcpack` to the proxy Geyser's `packs/`
3. Copy `plugins/Kalo/geyser/kalo-items.json` and `kalo-blocks.json` to the proxy
   Geyser's `custom_mappings/`

Geyser-Spigot is not required on the backend for this topology. Floodgate is optional for
Kalo's Java features; its absence does not break menus or startup.

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
bedrock-packs:
  per-player: true  # one staged .mcpack per Bedrock session
  transfer: true    # reconnect online sessions when the pack changes
  session-content:
    enabled: false
    permissions:
      "testpack:ruby_sword": "kalo.content.ruby_sword"
      "testpack:*": "kalo.content.testpack"
    sessions:
      "2535412345678901":
        - testpack          # namespace
        - other:rare_chair  # or one exact content key
```

`session-content` filters generated assets and gameplay grants per XUID/player. The
compiler writes `kalo-content.json`, which names the content key that owns each model,
animation, attachable and texture. At staging time a listed session receives only files
whose owner is allowed; an unlisted session receives everything. An empty list sends only
shared pack files, and `*` sends everything. The `permissions` map requires the listed node
before that content is staged or granted: give, pickup, use, placement, crafting,
vehicle/mob spawning and emotes all use the same policy. Shared atlases and manifests stay
common, existing placed/spawned state is not retroactively removed, and Geyser registration
itself remains global. A changed allowlist or permission outcome is part of the session
digest, so that session is rebuilt and, with `transfer`, asked to reconnect.

| Option | Description |
|--------|-------------|
| `auto` | Build if in-process Geyser is present or standalone sync directory is configured |
| `always` | Always build Bedrock pack |
| `never` | Never build Bedrock pack |

## Supported Content

| Content | Bedrock Status |
|---------|----------------|
| Items (2D sprites) | ✅ Works |
| Items (Blockbench `.bbmodel`, static) | ⚠️ Experimental; outliner bones/pivots preserved; held-item animations wired to the attachable, placed/block animation not supported |
| Placed models (`place:`) | ⚠️ Java display entity + hitbox; Bedrock client entity, geometry, animation and Geyser registration. Seats, storage and light/solid/model states work; per-session packs are staged |
| Custom mobs (`type: mob`) | ⚠️ Bedrock client entity registered/spawned/moved; Java invisible base mob + per-bone loop rig with parent-bone motion. Real client unverified |
| Worn 3D armor (`armor_model:`) | ⚠️ Bedrock attachable bound to armor bones; Java hides the flat layer and follows with a per-bone loop rig |
| Vehicles (`type: vehicle`) | ⚠️ Bedrock client entity follows an invisible real carrier; per-seat marker stands, `PlayerInput` driving and land/water/air medium motion. Real client unverified |
| Emotes (`type: emote`) | ⚠️ Bedrock client entity/attachable per performer (prop or player-body rig); per-player cached or auto-fetched skin overlay. Multi-performer live cast unverified |
| Items (3D Java model JSON, one texture sheet) | Experimental static geometry + held-item attachables |
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

Kalo supports two authored-model inputs:

- **Blockbench `.bbmodel`** (format 3.2–5.0 cube models) — imported into a shared,
  platform-neutral model IR. Bedrock receives real parented bones and pivots from the
  outliner; Java receives compiled model JSON. See `docs/BLOCKBENCH.md` for the exact
  support matrix.
- **Java model JSON** — a hand-authored cuboid model with optional parent inheritance.

Both produce a Bedrock geometry file, a held-item attachable, an inventory icon and a
Geyser v2 mapping. Parent inheritance for Java JSON is resolved up to 16 levels with cycle
detection.

### Blockbench example

~~~yaml
ruby_sword:
  type: item
  display:
    name: Ruby Sword
  model:
    blockbench: models/item/ruby_sword.bbmodel
    textures:
      "0": item/ruby_sword
  java:
    base_material: PAPER
~~~

Place the source at `assets/<namespace>/models/item/ruby_sword.bbmodel` and its PNG at
`assets/<namespace>/textures/item/ruby_sword.png`. The `.bbmodel` itself is stripped from
the generated client pack.

Generated output:
- `models/entity/<namespace>_<name>.geo.json` — Bedrock geometry
- `attachables/<namespace>_<name>.json` — bound to the held item slot
- `textures/items/<namespace>_<name>.png` and its atlas entry — inventory icon
- Geyser v2 item mapping matching the Java item model key

### Limitations

- Held-item animation is wired to attachables. `place:` adds a Java display entity/hitbox
  and a Bedrock client entity with authored animation; seats, storage and states work, while
  placed models, blocks and furniture use per-bone Java display rigs for authored loops.
  Placed models, animated blocks/furniture, custom mobs and 3D worn armor play their
  configured interaction clip once and resume idle. Animated blocks/furniture loop on Java
  through per-bone display rigs and on Bedrock through a client entity whose block surface
  is transparent. Each Bedrock session receives its own staged pack. Blockbench animation
  controllers compile to Bedrock and run through a Java state machine with variables,
  remaps, math functions, arrays, deterministic random, external query resolvers and
  blend weights; loops/`for_each`, structs, strings, `->` through an entity resolver,
  `??`, multi-statement expressions and conditional `break`/`continue` are evaluated.
  Provider-specific query catalogs and entity references remain host supplied.
- Box-UV and mesh elements are unsupported. Rotated outliner bones, multi-axis element
  rotation and inflation are refused on Java because vanilla Java model JSON cannot
  represent them; Bedrock can carry multi-axis rotation and inflation.
- Mixed per-texture UV spaces are rejected; static models use one shared UV space.
- Textures and rendering still require real Bedrock-client verification.

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

> **Version:** 0.2.1-prealpha.16 • **License:** MIT • **Java:** 21 bytecode / 25 for 26.x runtime

*Build once. Play everywhere.*

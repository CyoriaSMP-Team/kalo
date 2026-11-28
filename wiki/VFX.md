# ✨ VFX

A VFX is a spell or ability effect drawn from glowing sprites. Kalo plays it with Java
item displays and paints the sprite textures into the generated Java pack. A recipe can
also anchor sprites to the caster's view or place a real item model.

## Basic VFX

```yaml
frost:
  type: vfx
  preset: frost_nova
```

```bash
/kalo vfx list
/kalo vfx testpack:frost
/kalo vfx testpack:frost loop
/kalo vfx testpack:frost loop:3x quiet volume:0.5 tag:arena
/kalo vfx testpack:frost speed:0.5 scale:1.5 follow offset:0,1,3 turn:15
/kalo vfx testpack:frost delay:1s seed:42 only:caster
/kalo vfx testpack:frost loop:30s check:5t until:{health} < 6
/kalo vfx testpack:frost loop while:{state} == "running"
/kalo vfx stop
/kalo vfx stop tag:arena
/kalo vfx stop all
```

Permission: `kalo.command.vfx`. A `session-content.permissions` entry for the VFX key
also gates it.

## Three ways to define one

### Preset

```yaml
frost:
  type: vfx
  preset: frost_nova
  name: Frost Nova      # optional display name
```

The ten preset names are:

```text
void_slash  frost_nova  thunderstorm  pillar_of_light  black_hole
starfall  sanctuary_bloom  rift_eye  tornado  aegis_dome
```

The Kalo presets are independent recipes with the same public names; they are not
Studio recipe data.

### Studio-style JSON file

```yaml
burst:
  type: vfx
  file: vfx/burst.json
```

The file is the recipe object (`duration`, `layers`, optional `loop`), or an object with
a `recipe` wrapper. It must be one JSON object, at most 256 KiB, inside the content pack.
A leading `<pack>/` in the path is accepted.

### Inline recipe

```yaml
spark:
  type: vfx
  recipe:
    duration: 30
    layers:
      - type: motes
        kind: streak
        start: 0
        end: 20
        burst: 40
        at: [0, 1, 0]
        shape: sphere
        speed: [0.3, 0.6]
        size: 0.3
        lifetime: 20
        color: "#9FE8FF"
      - type: camera
        kind: spark
        burst: 12
        distance: 2
        size: 0.15
        color: "#E8FFFF"
      - type: model
        model: testpack:ruby
        at: [0, 1, 0]
        scale: 1.5
        spin: 20
```

## Recipe fields

| Option | Type | Description |
|--------|------|-------------|
| `duration` | int | Ticks, clamped to 1–1200 |
| `loop` | `[start, end]` or object | Build-up, repeated loop section, and fade after `end` |
| `sounds` / `sound` | list / object | Sound events at timeline ticks |
| `layers` | list | At least one drawable layer |

Each sprite layer (`motes` or `camera`) supports:

| Option | Type | Description |
|--------|------|-------------|
| `type` | string | `motes` (world-space) or `camera` (anchored to the caster's view) |
| `kind` | string | `glow`, `streak`, `spark`, or another short texture style |
| `start` / `end` | int | Spawn window in ticks |
| `burst` / `count` | int | Number of sprites, capped by the effect budget |
| `at` | `[x,y,z]` | Offset from the cast origin, clamped to ±32 |
| `shape` | string | `sphere`, `ring`, `disc`, `line` or `cone` |
| `radius` | number | Shape radius, 0–32 |
| `speed` | number or `[min,max]` | Outward speed, 0–10 |
| `size` | number | Sprite scale, 0.01–4 |
| `lifetime` / `life` | int | Ticks each sprite lives, 1–200 |
| `gravity` | number | Vertical acceleration, -1–1 |
| `color` | hex or dye name | `#3FBF4A`, `0x3FBF4A`, `3FBF4A`, or a dye name |

A `camera` layer uses the same fields and adds:

| Option | Type | Description |
|--------|------|-------------|
| `distance` | number | Blocks in front of the eyes beyond `at.z`, 0.25–16 (default 1.5) |
| `shake` | number | Deterministic view wobble in blocks, 0–1 (default 0) |
| `fov` | number | Requested vertical field of view, 30–110 (default 70) |
| `yaw` / `pitch` | number | View offsets in degrees, -180–180 / -90–90 |

For a camera layer `at` is camera-space: X is right, Y is up and Z is forward. The
sprites are ordinary world entities recomputed from the caster's eye position and look
direction every tick. On Java, an active camera layer also attaches the client camera to
a temporary invisible marker at the caster's eyes through `ClientboundSetCameraPacket`;
the marker follows the layer's position, view offsets and shake, and the camera is
restored when the window ends or the cast stops. If that packet seam is unavailable, the
view-anchored sprites still play and the camera stays normal.

A `model` layer places a real item model for its window and ignores the sprite fields:

| Option | Type | Description |
|--------|------|-------------|
| `type` | string | `model` |
| `model` | key | An item-model key the pack contains, e.g. `mypack:ruby` |
| `start` / `end` | int | Visible window in ticks |
| `at` | `[x,y,z]` | Offset from the cast origin, clamped to ±32 |
| `scale` | number | Model scale, 0.05–10 (default 1) |
| `yaw` / `pitch` / `roll` | number | Degrees relative to the cast direction |
| `spin` | number | Degrees around the vertical axis per recipe tick, -180–180 |

A sound event supports `tick` (or `time`/`at`), `sound`/`id`, `volume` 0–4 and `pitch`
0.5–2. `time` accepts ticks or `0.5s`. A bare string sound plays at tick 0.

```yaml
sounds:
  - tick: 0
    sound: entity.generic.explode
    volume: 1.0
    pitch: 0.8
  - time: 1.5s
    id: block.note_block.chime
```

An effect is capped at **240 sprites** and a namespace at **48 effects**. Out-of-range
values are clamped; unknown layer types and shapes are reported at load and either skipped
or replaced by a sphere.

## Runtime

- Each sprite is a non-persistent `ItemDisplay` with a generated 32×32 glowing texture.
- A fixed cast stays at the origin; `follow` moves and turns it with the caster. Camera
  sprites always move with the caster's eyes and view direction.
- Camera layers drive a packet-based Java camera while their window is active. `fov` is
  converted into a dolly/zoom offset because vanilla Java has no FOV packet; the true
  value is carried in the camera frame for a transport that can set it. Bedrock players
  keep their normal camera.
- A model layer spawns a non-persistent `ItemDisplay` with the referenced item model. It
  is visible only inside its `start`/`end` window and honors `scale`, rotation and `spin`.
  Bedrock gets a compiled client entity for the same model; spawn/move/remove use the
  existing VFX rig seam.
- It is simulated deterministically; each tick spawns, moves or removes sprites.
- `speed` changes playback ticks per server tick; `scale` grows distances and sprite size;
  `offset` moves the cast relative to the player's facing; `turn` rotates it.
- `follow` recomputes origin/yaw from the caster every tick. `delay` waits before the first
  frame, and `seed` makes the distribution repeatable.
- `loop:30s`, `loop:5m` and `loop:100t` stop after real playback time; `loop:<n>x` stops
  after a number of loop cycles.
- `until:<condition>` and `while:<condition>` are evaluated every `check:<time>` (default
  20t). Conditions support `{placeholders}`, `perm:<node>`, quoted strings, comparisons,
  `&&`, `||`, `!` and parentheses. Built-ins are `player`, `target`, `world`, `health`,
  `max_health`, `food`, `level`, `xp`, `x`, `y`, `z` and `gamemode`. Unknown names fall
  back to PlaceholderAPI when it is installed; unknown placeholders are empty.
- `only:caster` hides the item displays from everyone else and sends sounds only to the
  caster.
- One effect per player. `/kalo vfx stop` requests a graceful stop; a loop with a fade
  section keeps running until the fade finishes. `end:cut` removes it immediately.
- `vfx.max-sprites` (default 4000, 0 disables) is a planned-sprite budget. A new cast that
  would exceed it stops the oldest active casts first.
- It uses the origin region scheduler for fixed casts and the caster scheduler for
  `follow` or camera layers, so it stays region-safe on Folia while the origin is loaded.

## Limits

- Java uses item displays; Bedrock uses generated client entities with textured geometry.
  Both share the same sprite PNG, layer size and cast `scale`: Java scales the display
  transformation, and Bedrock receives the same multiplier as `SCALE` entity metadata.
  The Bedrock geometry itself is baked from the layer size, so the multiplier is applied
  once at runtime.
- Java camera control is a reflective packet seam with a no-op fallback. It needs the
  server's `ClientboundSetCameraPacket`; if that class or packet send path is unavailable,
  camera layers degrade to view-anchored sprites. `fov` is a dolly approximation on Java,
  not a client FOV change.
- Camera sprites are ordinary world entities, so terrain can occlude them. Bedrock players
  do not get the packet camera.
- Model layers compile to Bedrock client entities when the key resolves to a Kalo item
  with a sprite, custom Java model or Blockbench model, or to an item-model JSON the pack
  carries. A vanilla item model the pack does not carry is skipped on Bedrock. Yaw, pitch
  and model scale are delivered through the entity; `roll` is baked into a wrapper bone in
  the Bedrock geometry, matching Java's display rotation.
- A `model` key must name a model present in the generated pack (for example a Kalo item
  key). Java shows the client's missing-model placeholder when it is absent; Bedrock
  skips that layer.
- Other Studio layer types are skipped with a diagnostic.
- Cast options include `loop:<time>`, `until`, `while` and `check`. Built-ins keep
  priority; unknown names resolve through PlaceholderAPI when it is installed.
- `vfx.max-sprites` uses planned sprites, not currently alive sprites; a single cast can
  exceed the cap when no older cast remains.
- Bedrock entity registration is wired through Geyser, but live Bedrock rendering and
  movement are not verified in CI.
- PlaceholderAPI condition values are resolved at check time; an absent or unknown
  placeholder becomes empty.
- The Java client rendering and sound playback are not verified in CI.

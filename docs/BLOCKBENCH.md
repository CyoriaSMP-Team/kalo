# Blockbench `.bbmodel` support

Kalo imports Blockbench project files into a platform-neutral model IR and compiles that
IR independently to Java model JSON and Bedrock geometry. The importer is an independent
implementation written from the published Blockbench format notes; no GPL-3.0-or-later
ResourcePack AI RP Engine source was copied into Kalo.

> **Status:** implemented and unit-tested; **not verified against a real Bedrock client**
> and not tested against a corpus of real Blockbench projects. The fixtures are synthetic
> and pin Kalo's own parser/compiler assumptions.

## Source layout

The `.bbmodel` file is compile input, not a client asset. Place it under the content pack's
namespace asset tree:

```
mypack/
├── assets/
│   ├── models/item/ruby_sword.bbmodel
│   └── textures/item/ruby_sword.png
└── configs/items.yml
```

```yaml
ruby_sword:
  type: item
  display:
    name: Ruby Sword
  model:
    blockbench: models/item/ruby_sword.bbmodel
    textures:
      "0": item/ruby_sword       # optional; maps a Blockbench texture id/name/UUID/path
  java:
    base_material: NETHERITE_SWORD
```

The `blockbench:` path is relative to `assets/<pack-id>/`. It must end in `.bbmodel`.
Blocks and furniture use the same key inside their `model:` section.

Generated Java files (for item `mypack:ruby_sword`):

```
assets/mypack/items/ruby_sword.json                 item definition -> model
assets/mypack/models/item/ruby_sword.json           compiled Java model
```

Generated Bedrock files:

```
models/entity/mypack_ruby_sword.geo.json            geometry, with bones
attachables/mypack_ruby_sword.json                  held-item attachable
animations/mypack_ruby_sword.animation.json         authored keyframes (when present)
textures/items/mypack_ruby_sword.png                rendered 64×64 inventory icon
textures/items/mypack_ruby_sword_t0.png             model material t0
textures/items/mypack_ruby_sword_t1.png             additional material sheets, if any
textures/item_texture.json                          atlas entry
```

The original `.bbmodel` is stripped from the generated Java pack after compilation. It is
never copied into the Bedrock pack.

## Imported coverage

| Blockbench construct | Support |
|---|---|
| Format versions | 3.2 through 5.0, including the 5.0 split between `groups` and `outliner` |
| Cube elements | `from`, `to`, `origin`/pivot, per-axis `rotation`, `visibility`, `export`, `inflate` |
| Per-face UVs | `uv` corners and `rotation` (0/90/180/270) for all six faces |
| Texture references | Numeric id, texture name, or texture UUID; Kalo overrides by id/name/UUID/path |
| Textures | Embedded PNG data URIs, safe pack-relative paths, or explicit Kalo overrides |
| Outliner hierarchy | Groups become IR bones with parent ids, pivots and rotations |
| Animations | Animation, animator and keyframe data are retained in the IR; loops are compiled to Bedrock animation JSON and wired to held-item attachables |
| Display transforms | `gui`, `ground`, `fixed`, `head`, first/third person; emitted to Java models |
| Model metadata | name, format version, model format, resolution and per-texture UV space |

Limits are enforced before parsing: 8 MiB model files, 4096 elements, 1024 bones, 256
textures, 16 MiB embedded textures, and 8192 keyframes. Duplicate element/group/texture
ids, invalid dimensions, non-finite coordinates, malformed JSON and outliner cycles are
rejected. Unsafe texture paths (`..` segments, backslashes, absolute relative paths) are
rejected; an absolute Blockbench `path` without a safe `relative_path` is reported and
never read.

## Java output coverage

- Cube bounds, pivots, single-axis element rotation, face UVs and UV rotation.
- One texture alias per resolved Blockbench texture (`#t0`, `#t1`, ...).
- `texture_size` when the model is not 16x16.
- `display` transforms.
- Embedded textures are extracted to deterministic `assets/<ns>/textures/item/...` names.

**Java limitations**

- Java item/block model JSON has no bone hierarchy. Zero-rotation groups are flattened;
  the outliner parent relationship itself is not represented.
- A rotated outliner bone is **rejected**, not baked. Baking it would silently change the
  model and its UVs.
- Multi-axis element rotation is rejected; Java elements support one axis.
- Non-zero `inflate` is rejected; vanilla Java model JSON has no inflation field.
- Animations are not played on Java.
- Hidden/`export: false` cubes are omitted and reported.
- If an item cannot compile on Java, the Bedrock compiler skips it too, so both platforms
  show the same item set.

## Bedrock output coverage

- Outliner hierarchy is preserved as real Bedrock bones with `parent`, `pivot` and rotation.
- Cubes stay attached to their authored bone.
- Bedrock's mirrored X and centred X/Z coordinate convention is applied to cube bounds,
  pivots and rotation. X and Y rotations are negated and Z keeps its authored sign, which
  is Blockbench's Java-to-Bedrock rule.
- Per-face UVs, UV rotation, inflation and multiple material instances are preserved.
- Each distinct texture sheet becomes its own material instance (`t0`, `t1`, ...) and an
  attachable texture entry. There is no forced single-sheet atlas for Blockbench imports.
- Blockbench animations are emitted as `animations/<name>.animation.json` in Bedrock
  `format_version` 1.8.0. Bone keyframes use the same X/Y-negated rotation rule, loop and
  hold modes are preserved, and loop animations are auto-started from the held-item
  attachable's `scripts.animate`.
- The flat Bedrock inventory icon is a 64×64 isometric render of the authored cubes, not
  the first texture. The model's material textures are emitted separately under
  `textures/items/<name>_t0`, `<name>_t1`, and so on.
- Bound geometry gets generated `hold_first_person` / `hold_third_person` animations.
  Authored Blockbench/Java display transforms become the hold rotation, translation and
  scale; when absent, a half-scale fallback is used. Set the display transforms in
  Blockbench (or the Java model's `display` block) for exact per-model placement.
  The vanilla `animation.steve_head.hold_*` pair is kept only as a fallback for models
  without a custom hold.

### Placed models

An item with a Blockbench model can also be put down:

```yaml
chair:
  type: item
  model:
    blockbench: models/furniture/chair.bbmodel
  place:
    scale: 1.0
    width: 1.0
    height: 1.0
    idle: spin
    interact: swing
    storage:
      rows: 3
      title: Cabinet
    states:
      - scale: 2.0
        light: 15
        solid: true
        model: lamp_on
        sound: minecraft:block.lever.click
```

Java placement spawns a chunk-saved `ItemDisplay` plus an `Interaction` hitbox. The Bedrock
compiler writes `models/entity/<name>_placed.geo.json`,
`animations/<name>.animation.json` and `entity/<name>.entity.json`, registers
`kalo:model_<name>` with Geyser on its entity lifecycle event, and the bridge spawns that
client entity for nearby Bedrock sessions. Right-click plays the configured `interact:` clip once and returns to the idle loop;
sneak-right-click or damage removes the piece. The Geyser session/packet calls are
reflective and fail closed on an incompatible Geyser build.

`storage:` opens a real container; its contents are serialized onto the hitbox's persistent
data when the inventory closes, so they survive a restart. `states:` cycles with each
right-click: each entry can change scale, light, solid (barrier), model (another Kalo item
with a Blockbench model) and play a sound. The state index and its light/barrier are
restored on chunk load.

**Placed-model limits:** seats, storage and light/solid/model state cycling are
implemented; hitbox-per-bone is not. Placed models, blocks and furniture all use the
per-bone Java display rig for authored loop animations. A placed model's configured
`interact:` clip plays once on both platforms and then the idle loop resumes; block/furniture
interaction playback uses the configured clip. Animation controllers are compiled and run
on both platforms; parent-bone motion is composed through each bone's chain on Java. Each Bedrock session
receives its own staged pack (see the Bedrock setup section in the main README). The Java display is one entity, not one display per bone,
so Java players see the rest pose while Bedrock can play the authored animation.

**Block and furniture animation**

A Blockbench block/furniture model with loop animations is animated on both platforms:

```yaml
fan:
  type: block
  model:
    blockbench: models/block/fan.bbmodel
  # java.mode is optional; animated models default to virtual display mode.
```

Because Java blockstates cannot animate, an animated model is automatically placed in
virtual display mode unless `java.mode` is explicitly set. Java then renders one
`ItemDisplay` per bone (each bone's cubes compiled to their own model) and applies sampled
loop poses every two ticks, rotating and scaling around the authored pivot. Bedrock cannot
animate block geometry either: Kalo writes `animations/<name>_block.animation.json` and
`entity/<name>_block.entity.json`, registers `kalo:block_<name>` with Geyser, and registers
a fully transparent terrain texture for the block so the client entity is the only visible
surface. Explicit `java.mode: native` keeps the block static on both platforms and logs a
warning that Java cannot play the animation.

Loop animations are auto-played, matching the Bedrock entity's `scripts.animate` list.
Non-loop animations are not auto-played and Java applies each bone's local pose (child
bones do not inherit a parent's animated motion). Bezier keys are sampled as cubic curves
on Java and baked into intermediate linear keys for Bedrock.

**Migration:** a block placed as a native carrier state before this feature existed is not
converted automatically. Break and replace those blocks once after upgrading; new
placements use the animated path.

**Custom mobs**

```yaml
ruby_golem:
  type: mob
  base: zombie          # AI, hitbox, damage and drops come from this vanilla mob
  name: "<red>Ruby Golem"
  health: 40
  width: 0.7
  height: 2.0
  model:
    blockbench: models/mob/ruby_golem.bbmodel
  animation:
    idle: walk
    interact: Open
```

`/kalo spawn <mob>` spawns the real base mob, names it and applies its health. The base
body is set invisible and a display renders the model on Java; Bedrock gets
`models/entity/<name>_mob.geo.json`, `animations/<name>_mob.animation.json`,
`entity/<name>_mob.entity.json` and a registered `kalo:mob_<name>` client entity that is
spawned, moved and removed with the mob. Mob models must be authored in Bedrock entity
space (feet at y=0, X/Z centred, which is Blockbench's Bedrock Entity format);
`BedrockModelCompiler.compileEntity` keeps those coordinates instead of applying the
Java-block mirror/centre rule. Java renders one display per bone and samples authored
loops; parent-bone motion is composed through the bone chain. Right-clicking the mob plays
`animation.interact` once and resumes the idle loops; Bedrock receives the matching
animation trigger.

**Worn 3D armor**

```yaml
ruby_helmet:
  type: armor
  slot: head
  model:
    sprite: item/ruby_helmet
  armor_model:
    blockbench: models/armor/ruby_helmet.bbmodel
    textures:
      "0": entity/ruby_helmet
    animation:
      idle: idle
      interact: Open
```

The model is authored in Blockbench's player-template frame (feet at y=0, 16 units per
block) and must keep the vanilla armor bone names (`head`, `body`, `rightArm`, `leftArm`,
`rightLeg`, `leftLeg`) so `controller.render.armor` can bind it. Java compiles it to `assets/<ns>/models/armor/<name>.json`, points the flat
equipment layer at a transparent texture, and follows the wearer with a display at their
feet. Bedrock compiles the same model to
`models/armor/<name>.geo.json` plus an attachable whose geometry replaces the vanilla
armor geometry and whose `parent_setup` hides the layer underneath. Java uses a per-bone
display rig for authored loops. Right-clicking with an empty hand while wearing the piece
plays `animation.interact` once and resumes the idle loop.

**Animation controllers**

A Blockbench project's `animation_controllers` block is imported into the model IR and
compiled into Bedrock `animation_controllers/<name>.animation_controllers.json`. The
controller id is added to the entity/attachable `scripts.animate` list, so Bedrock runs it
without a manual trigger. Java builds the same state machine and drives the per-bone rig:

```json
"animation_controllers": {
  "controller.animation.fan": {
    "initial_state": "idle",
    "states": {
      "idle": {
        "animations": ["spin"],
        "transitions": [{"open": "query.is_moving"}]
      },
      "open": {
        "animations": ["Open"],
        "transitions": [{"idle": "query.all_animations_finished"}]
      }
    }
  }
}
```

Java evaluates `query.is_moving`, `is_on_ground`, `is_in_water`, `is_sneaking`,
`is_sprinting`, `is_jumping`, `life_time`, `modified_move_speed`,
`any_animation_finished`, `all_animations_finished`, `!`, `&&`, `||`, parentheses,
`true`/`false`, arithmetic, ternaries and numeric comparisons. State `variables` (with
linear `remap_curve`s), common `math.*` functions and animation `blend` weights are
evaluated too; blended Java poses use component-wise linear weights. Arrays and indexing
(`[0.2, 0.8][query.is_moving]`), `query.count`, deterministic
`math.random`/`math.random_integer` and argument-aware external `query.*` resolvers are
also supported. `loop`/`for_each` with `break`/`continue`, mutable structs, single-quoted
strings, `->` through an `EntityResolver`, `??`, multi-statement expressions with
`return`, and conditional `break`/`continue` run too. Provider-specific query catalogs
and entity references remain host supplied.

**Vehicles**

```yaml
speeder:
  type: vehicle
  name: "Speeder"
  base: armor_stand
  speed: 0.4       # blocks per tick
  turn-speed: 9    # degrees per tick
  scale: 1.5
  y-offset: 0.0
  seats: 3          # or an explicit list of {x, y, z} offsets
  medium: land      # land | water | air
  buoyancy: 0.06    # water rise per tick
  gravity: 0.02     # air fall per tick
  climb-speed: 0.25 # air climb per tick while jump is held
  collision: true   # stop at solid blocks
  model:
    blockbench: models/vehicle/speeder.bbmodel
```

`/kalo vehicle <name>` spawns an invisible real carrier (an armour stand by default) and
one invisible marker stand per seat. Water vehicles rise while in water and sink outside;
air vehicles climb while jump is held and descend otherwise; land vehicles stop at solid
blocks when `collision` is enabled. Seat zero is the driver; right-clicking the vehicle
fills the next free seat and sneak ejects. Seat offsets are in the carrier frame (X right,
Z forward) and rotate with its yaw. W/S throttle, A/D turn and sneak dismounts, read through
Paper 1.21.4's `PlayerInput`. Java follows the carrier with a display; Bedrock writes
entity-space geometry and a `kalo:vehicle_<name>_vehicle` client entity and moves it with
the carrier.

```yaml
seats:
  - {x: 0.0, y: 0.3, z: 0.0}   # driver
  - {x: 0.8, y: 0.4, z: -1.0}  # passenger
```

**Emotes**

```yaml
wave:
  type: emote
  name: "Wave"
  duration: 2.0     # 0 = until sneak
  loop: true
  scale: 1.0
  y-offset: 0.0
  rig: prop         # prop | player
  skin: entity/emote_skin   # optional baked player skin for rig: player
  performers:
    - name: lead
      offset: {x: 0, y: 0, z: 0}
    - name: partner
      offset: {x: 0.8, y: 0, z: 1.0}
  model:
    blockbench: models/emote/wave.bbmodel
```

`/kalo emote <name> <partner>` seats each cast member at its offset in the lead's facing
frame. A PNG at `plugins/Kalo/skins/<uuid>.png` is validated and layered into that XUID's
staged Bedrock pack. When no cached PNG exists, Kalo reads the player's signed `textures`
profile property and fetches the skin from an allowlisted host, capped at 1 MiB and
validated as a PNG before it is cached atomically. A changed profile URL invalidates the
cached PNG, so a player's new skin is fetched on the next emote. Without either source, the
model's baked skin is used. Set `emotes.skin-fetch: false` to stay offline; `emotes.skin-hosts` is the host
allowlist and `emotes.skin-timeout-seconds` bounds the download. At Bedrock login, a cached
skin is written to every body-emote texture path, so the first discovery appears on the
player's next pack negotiation. On Java, each body emote compiles its base texture to a
unique `emote/<name>_skin` path and Kalo sends a tiny, optional per-player overlay pack
that replaces only that path; the global Java pack still carries the authored `skin`. The overlay needs
the local pack host and is skipped in Relay/no-host mode, where the authored skin remains.
The unique path is what keeps two players' overlays from colliding.

`/kalo emote <name>` plays the prop around the player: a Java display follows them and a
Bedrock `kalo:emote_<name>_emote` client entity is spawned, moved and removed. `duration: 0`
keeps it until the player sneaks or starts another emote.

`rig: player` treats the model as a player body: Java hides the vanilla body and spawns one
display per bone, and Bedrock gets both a client entity and a player-emote attachable with
the same baked skin and animations. `skin` is copied into both packs and used as the model's
base texture when the model does not already embed one.

**Bedrock limitations**

- Placed-model animation is wired for the client entity; held-item animation is wired for
  attachables; block/furniture loop animation is wired through an animated client entity.
- Java item and placed-model animation is not played; Java block/furniture loop animation
  is.
- Animation interpolation: linear and step are emitted directly; Bezier segments are
  sampled on Java and baked into intermediate linear keys for Bedrock because Bedrock has
  no cubic keyframe interpolation. Catmull-Rom emits `lerp_mode: catmullrom` and Java
  samples the same uniform spline using the immediate neighbouring keys; the left key
  chooses the segment mode, matching where Bedrock writes `lerp_mode`, and a missing
  neighbour duplicates the endpoint. Discontinuous multi-value keyframes are reported and
  use the first value.
- Bedrock bones have no visibility flag. Hidden/`export: false` bones retain their
  transform for children but their cubes are omitted; hidden cubes are omitted.
- Mesh elements, texture meshes, box-UV models and layered/emissive
  texture modes are unsupported. They are reported, not silently converted.
- A Bedrock attachable consumes static geometry only. Morphs, particle effects and Molang
  animation expressions are not imported.
- Static rendering and material-instance choices still require real-client verification.

## Texture resolution order

1. Explicit Kalo override from `model.textures` (keyed by Blockbench texture id, name,
   path, path without extension, or file name).
2. Embedded PNG in the `.bbmodel`.
3. Safe pack-relative path (`textures/item/x.png` or `item/x.png`).

Blockbench 4.10 stores `relative_path` relative to the `.bbmodel` directory. Kalo treats
it as relative to `assets/<pack-id>/` instead, because the generated pack may not preserve
the author's host layout. If Blockbench writes paths like `../textures/item/x.png`, use a
`model.textures` override rather than relying on that path.

A model whose declared textures do not all resolve is rejected before either compiler
writes a model or publishes a Geyser mapping.

Texture bytes are deduplicated within one model and one material list. Cross-content
sharing still emits a deterministic per-content copy instead of a single shared asset;
that is a packaging optimisation, not a correctness issue.

## Cache and registry

`io.kalo.model.ModelRegistry` is namespaced by model source key and content-addressed by
SHA-256. Re-saving a `.bbmodel` changes its fingerprint, so a reload parses the new bytes
without a server restart. The Java and Bedrock compilers share the same parsed IR within
one generation. Malformed models are never cached.

## Bedrock topology note

When Geyser runs on a Velocity proxy, the backend cannot call Geyser's JVM API. Kalo then
works through files: configure `bedrock: always` (or `bedrock-standalone.directory`) on the
backend, then copy `plugins/Kalo/generated.mcpack` and the two `plugins/Kalo/geyser/*.json`
mapping files into the proxy's Geyser installation. Floodgate on the backend is independent
of this export. No in-process cross-JVM call is attempted.

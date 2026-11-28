# 🗡️ Items

Custom items with models, textures, and behaviors.

## Basic Item

```yaml
ruby_sword:
  type: item
  display:
    name: "<gold>Ruby Sword</gold>"
  model:
    sprite: "item/ruby_sword"
```

## Options

### Display

```yaml
display:
  name: "<gradient:#ff5f6d:#ffc371>Ruby Sword</gradient>"
  lore:
    - "<gray>A powerful blade</gray>"
    - "<dark_gray>Durability: 250</dark_gray>"
  glint: true
```

| Option | Type | Description |
|--------|------|-------------|
| `name` | string | Item name (MiniMessage) |
| `lore` | list | Lore lines |
| `glint` | boolean | Enchantment glint |

### Model

```yaml
model:
  sprite: "item/texture_name"
```

**Model Types:**

| Type | Description |
|------|-------------|
| `sprite` | Flat texture (auto-generated model) |
| `vanilla` | Reuse a vanilla item's appearance |
| `custom` | Hand-authored Java model |
| `blockbench` | Blockbench `.bbmodel` imported into the shared model IR |

#### Sprite Model

```yaml
model:
  sprite: "item/ruby_sword"
```

#### Vanilla Model

```yaml
model:
  vanilla: "minecraft:diamond_sword"
```

#### Custom Model

```yaml
model:
  custom: "item/ruby_sword"
  textures:
    layer0: "item/ruby_sword"
```

#### Blockbench Model

```yaml
model:
  blockbench: "models/item/ruby_sword.bbmodel"
  textures:
    "0": "item/ruby_sword"     # optional override by id/name/UUID/path
```

`blockbench:` is relative to `assets/<pack-id>/` and must end in `.bbmodel`. Format
3.2–5.0 cube models are supported; outliner bones are preserved for Bedrock. Rotated
bones, inflation and multi-axis element rotation are refused on Java because Java model
JSON cannot represent them. Held-item animations are compiled into Bedrock animation JSON
and wired to the attachable; Java does not play imported item animations. Animated blocks
and furniture are different: they play authored loop animations on Java through a per-bone
display rig, and on Bedrock through a client entity with a transparent block surface.

#### Vehicle

```yaml
speeder:
  type: vehicle
  base: armor_stand
  speed: 0.4
  turn-speed: 9
  model:
    blockbench: models/vehicle/speeder.bbmodel
```

Animated blocks/furniture can add `animation: {idle: spin, interact: Open}` to select the
idle loop and a one-shot right-click clip.

`/kalo vehicle testpack:speeder` spawns and seats the player. W/S throttle, A/D turn, sneak
dismounts. `seats: 3` creates a centred row; an explicit `seats:` list sets each offset.
Right-click fills the next free seat. See `docs/BLOCKBENCH.md` for the authoring frame and
limits.

#### Emote prop

```yaml
wave:
  type: emote
  duration: 2.0
  model:
    blockbench: models/emote/wave.bbmodel
```

`/kalo emote testpack:wave` plays a prop around the player. Add `rig: player` to treat the
model as a player body; Java hides the vanilla body and uses the player's skin through a
small per-player overlay when the local pack host is available.

#### Custom mob

```yaml
ruby_golem:
  type: mob
  base: zombie
  name: "Ruby Golem"
  health: 40
  model:
    blockbench: models/mob/ruby_golem.bbmodel
```

`/kalo spawn testpack:ruby_golem` spawns the real base mob with a custom model. Java draws
a per-bone display rig over the invisible body; Bedrock registers and follows a client
entity.
See `docs/BLOCKBENCH.md` for the authoring frame and limits.

#### Worn 3D armor

```yaml
ruby_helmet:
  type: armor
  slot: head
  armor_model:
    blockbench: models/armor/ruby_helmet.bbmodel
```

The model must use Blockbench's player-template frame. Bedrock binds it to the armor
bones; Java hides the flat equipment layer and follows the wearer with a display.

#### Placed model

```yaml
model:
  blockbench: "models/furniture/chair.bbmodel"
place:
  scale: 1.0
  width: 1.0
  height: 1.0
  idle: spin
  interact: swing
```

Right-click places a chunk-saved Java display entity and hitbox; Bedrock gets a client
entity, geometry, animation and Geyser entity registration. Seats work as a temporary
marker. Storage and light/solid/model state cycling are implemented; each Bedrock session
gets its own staged pack.
See `docs/BLOCKBENCH.md` for the exact matrix.

### Behaviour

```yaml
behaviour:
  max_stack_size: 1
  durability: 250
  fire_resistant: true
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `max_stack_size` | int | 64 | Max stack size |
| `durability` | int | null | Max durability |
| `fire_resistant` | boolean | false | Fire resistant |

### Java Options

```yaml
java:
  base_material: NETHERITE_SWORD
  components: {}
```

| Option | Type | Description |
|--------|------|-------------|
| `base_material` | Material | Base vanilla material |
| `components` | map | Additional components |

### Bedrock Options

```yaml
bedrock:
  enabled: true
  icon: custom_icon
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | boolean | true | Enable on Bedrock |
| `icon` | string | null | Custom icon name |

---

## Examples

### Simple Item

```yaml
ruby_dust:
  type: item
  display:
    name: "<gold>Ruby Dust</gold>"
  model:
    sprite: "item/ruby_dust"
```

### Tool with Durability

```yaml
ruby_pickaxe:
  type: item
  display:
    name: "<gold>Ruby Pickaxe</gold>"
    lore:
      - "<gray>Mines quickly</gray>"
  model:
    sprite: "item/ruby_pickaxe"
  behaviour:
    durability: 500
  java:
    base_material: NETHERITE_PICKAXE
```

### Food

```yaml
ruby_apple:
  type: item
  display:
    name: "<gold>Ruby Apple</gold>"
  model:
    sprite: "item/ruby_apple"
  behaviour:
    food:
      nutrition: 4
      saturation: 1.2
      always_food: false
```

### Liquid Bucket

```yaml
acid_bucket:
  type: item
  liquid: acid
  java:
    base_material: BUCKET
  model:
    sprite: "item/acid_bucket"
```

The `liquid:` field points at a `type: liquid` definition in the same pack. See
[[Liquids]].

---

## See Also

- [[Blocks]] — Custom blocks
- [[Furniture]] — Rotatable furniture
- [[Liquids]] — Water or lava with rules and saved volumes
- [[Configuration]] — All config options

---

## 📜 Footer

**Kalo** — Open Custom Content Engine for Minecraft

[GitHub](https://github.com/CyoriaSMP-Team/kalo) • [Issues](https://github.com/CyoriaSMP-Team/kalo/issues) • [Discord](https://discord.gg/kalo) (coming soon)

> **Version:** 0.2.1-prealpha.16 • **License:** MIT • **Java:** 21 bytecode / 25 for 26.x runtime

*Build once. Play everywhere.*

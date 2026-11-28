# 🌊 Liquids

Minecraft cannot add a third fluid, so a Kalo liquid is **real water or lava with rules
attached to a saved volume**. It flows, swims and floats boats like vanilla; what makes it
acid rather than an ocean is the marked box around it.

## Basic Liquid

```yaml
acid:
  type: liquid
  base: water
  color: "#3FBF4A"
  effect: POISON
  amplifier: 1
  damage: 1.0
  fireproof: false
```

| Option | Type | Description |
|--------|------|-------------|
| `base` | string | `water` or `lava` (default `water`) |
| `color` | string | Optional water tint: `#3FBF4A`, `0x3FBF4A`, `3FBF4A`, or a dye name |
| `effect` | string | Optional potion effect applied while inside |
| `amplifier` | int | Effect level, 0–255 |
| `damage` | number | Damage per second, 0–100 |
| `fireproof` | bool | Clear fire ticks while inside |

## Bucket

Create a normal Kalo item with a bucket base and point it at the liquid:

```yaml
acid_bucket:
  type: item
  liquid: acid
  java:
    base_material: BUCKET
  model:
    sprite: item/acid_bucket
```

Right-clicking a block places one source block of the base fluid and marks it as the
liquid. A placement next to an existing pool of the same liquid expands that volume instead
of creating a separate box.

Right-clicking a marked source block with an empty vanilla bucket removes one source block,
trims the saved volume (splitting it if the block was in the middle), and returns the
registered custom bucket for that liquid. A liquid with no bucket item cannot be picked up.
The volume expands again when water or lava flows into an adjacent block.

## Marking a Volume

```bash
/kalo liquid corner
# walk to the opposite corner
/kalo liquid fill testpack:acid
/kalo liquid clear
```

Permission: `kalo.command.liquid`. `fill` marks the box between the saved corner and your
current block. It does **not** replace blocks with fluid; fill the pond with water or lava
first (or use the bucket). `clear` removes the volume containing your feet and restores its
recorded biome.

Volumes are saved to `plugins/Kalo/liquids.json` in creation order. The first volume
containing a point wins, so a small pool inside a large one only counts if it was made
first.

## Colour

For a `color:`, Kalo writes one biome per liquid into:

```text
<world>/datapacks/kalo-liquids/
└── data/<pack>/worldgen/biome/liquid/<name>.json
```

The biome is registered when the world starts, so **a new or changed colour needs a
restart**. The runtime applies the biome to the marked columns; until the restart, the
rules work but the water keeps its vanilla colour. Minecraft tints water by biome on a
coarse grid and blends at the edges, so a small pool never reaches the full colour.

Kalo's Bukkit path sets biome per column. A volume therefore tints the whole vertical
column at its X/Z coordinates, not only its Y range.

## Contact Rules

Once per second, a player whose feet are inside a marked volume receives:

- the configured potion effect, refreshed for two seconds;
- the configured damage;
- fire ticks cleared when `fireproof: true`.

## Limits

- Only water and lava bases exist; there is no custom fluid block.
- Flow expands a volume through `BlockFromToEvent`. Sponge absorption trims the absorbed
  region; placing or breaking a block inside a marked volume trims that cell; block
  physics trims cells that are no longer the base fluid. Breaking a bank block outside
  the volume leaves the pool marked and lets flow expand it into the new space.
- Sponge/place/break/physics handlers run at `MONITOR` after cancellation checks. Their
  trim order is unit-tested for idempotence and independence, and water/lava cells are
  never trimmed by those handlers; live server event dispatch is not verified in CI.
- A saved coloured volume is applied on its owning region at startup, so Folia/Canvas does
  not reject `setBiome` from the server thread. Live Bedrock volume creation/persistence
  was observed; contact damage was not isolated from hostile-mob damage.
- Colour is Java-only, requires Minecraft 1.21.6+ for the biome format, and needs a
  restart.
- Bedrock clients have no colour or contact tint path yet.
- Live client rendering and a real multi-world restart test are not verified in CI.

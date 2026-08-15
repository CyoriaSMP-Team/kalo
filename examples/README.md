# Example content packs

`run/` is gitignored, so the packs used for local testing live here and are copied in.

```bash
cp -r examples/testpack run/plugins/Kalo/packs/
./gradlew runServer
```

## `testpack`

Exercises every content path currently implemented.

### Items — `configs/items.yml`

| Item | Covers |
|---|---|
| `ruby_sword` | sprite model, generated item definition + model, MiniMessage name and lore, glint, durability, non-default base material |
| `plain_apple` | no `model:` section — falls back to the base material's vanilla appearance instead of a missing texture |
| `greeting_paper` | the feature system (`kalo:hello_world`) and a custom stack size |

### Blocks and furniture — `configs/blocks.yml`, `configs/furniture.yml`

| Content | Covers |
|---|---|
| `ruby_block` | `cube_all` model, hardness, tool requirement |
| `oak_chair`, `ruby_pedestal` | furniture, which shares the block carrier — proves both types coexist in the one shared `note_block.json` |

Both borrow note block states. Placed blocks are stored as nothing but their borrowed
vanilla state, so `plugins/Kalo/block-states.json` records the assignment and is written
through on every new one.

### Armor — `configs/armor.yml`

| Piece | Covers |
|---|---|
| `ruby_helmet`, `ruby_chestplate`, `ruby_boots` | worn appearance on the `humanoid` layer |
| `ruby_leggings` | the `humanoid_leggings` layer, which vanilla draws on a different model |
| `plain_helmet` | `equipment: {enabled: false}` — a distinct item that keeps netherite's own armor texture |

The whole set shares one equipment texture, which is how vanilla armor works too: the
layer texture is per-material, not per-piece.

## Expected output

After the server starts, the generated pack is at `run/plugins/Kalo/generated.zip`:

```
pack.mcmeta                                              pack_format 88
assets/minecraft/blockstates/note_block.json             all 800 states
assets/testpack/items/*.json                             one per item, block and armor piece
assets/testpack/models/item/*.json                       generated sprites
assets/testpack/models/block/*.json                      cube models
assets/testpack/equipment/*.json                         worn armor appearance
assets/testpack/lang/en_us.json                          items and blocks merged
assets/testpack/textures/**                              copied from this pack
```

Two absences are deliberate and worth checking:

- `plain_apple` produces **no** item definition — it points `item_model` straight at
  `minecraft:apple`.
- `plain_helmet` produces **no** equipment asset — it has opted out of a custom worn
  appearance.

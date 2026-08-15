# Example content packs

`run/` is gitignored, so the packs used for local testing live here and are copied in.

```bash
mkdir -p run/plugins/Kalo/packs
cp -r examples/testpack run/plugins/Kalo/packs/
./gradlew runServer
```

## `testpack`

Exercises every item path currently implemented:

| Item | Covers |
|---|---|
| `testpack:ruby_sword` | sprite model, generated item definition + model, MiniMessage name and lore, glint, durability, non-default base material |
| `testpack:plain_apple` | no `model:` section — falls back to the base material's vanilla appearance instead of a missing texture |
| `testpack:greeting_paper` | the feature system (`kalo:hello_world`) and a custom stack size |

After the server starts, the generated pack is at `run/plugins/Kalo/generated.zip`. It
should contain:

```
pack.mcmeta
assets/testpack/items/ruby_sword.json
assets/testpack/models/item/ruby_sword.json
assets/testpack/textures/item/ruby_sword.png
assets/testpack/lang/en_us.json
```

`plain_apple` deliberately produces no item definition — it points `item_model` straight
at `minecraft:apple`, so there is nothing to emit.

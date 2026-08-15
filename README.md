<div align="center">

# 🐈 Kalo

**Open Custom Content Engine for Minecraft**

*Build once. Play everywhere.*

[![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![Paper](https://img.shields.io/badge/Paper-26.2-green?style=flat-square&logo=papermc)](https://papermc.io/)
[![Folia](https://img.shields.io/badge/Folia-supported-success?style=flat-square)](https://papermc.io/software/folia)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

</div>

Kalo is a free and open-source engine for building custom items, blocks, furniture and
armor on Paper and Folia servers — no client mods, no player limits, no feature paywalls.

**Bedrock is not a DLC.** One YAML definition compiles to both a Java resource pack and
Bedrock output, because the content model is platform-neutral by design rather than by
translation. That is the thing Kalo exists to do.

> ⚠️ **Pre-alpha.** Items, blocks, furniture and armor work on Java and are verified on a
> live server. The Bedrock path — `.mcpack`, Geyser mappings, geometry conversion, and an
> extension that registers blocks — is written and unit-tested but has **not** been run
> against a live Geyser instance yet, so treat it as unproven. Furniture is static
> (block-backed) rather than entity-backed. See [the roadmap](#roadmap) for what is real
> and what is not.

## Four pillars

| | |
|---|---|
| **Open Source** | MIT, no paid tier gating features |
| **Community First** | No player caps, item caps, or premium converters |
| **Paper / Folia** | Modern server API, current Minecraft |
| **Java + Bedrock** | Cross-platform designed in, not bolted on |

## Content packs

Packs live in `plugins/Kalo/packs/<pack>/`:

```
mypack/
├── pack.yml                          id, version, author
├── configs/
│   └── items.yml                     content definitions
└── assets/
    └── textures/item/ruby_sword.png  copied into the generated pack
```

```yaml
# configs/items.yml
ruby_sword:
  type: item
  display:
    name: "<gradient:#ff5f6d:#ffc371>Ruby Sword</gradient>"
    lore:
      - "<gray>A blade cut from a single ruby.</gray>"
    glint: true
  model:
    sprite: "item/ruby_sword"
  behaviour:
    durability: 250
  java:
    base_material: NETHERITE_SWORD
```

That compiles to a Java resource pack containing the item definition, the generated
model, the texture, and a lang entry — and the same definition is what the Bedrock
compiler consumes to produce the `.mcpack` and Geyser mappings.

Content keys are namespaced by the pack that defines them, so the item above is
`mypack:ruby_sword` and cannot collide with another pack's.

### Blocks and armor

```yaml
ruby_block:
  type: block
  model:
    cube_all: "block/ruby_block"     # or `cube:` per face, or `custom:`
  behaviour:
    hardness: 3.0
    requires_tool: true

ruby_helmet:
  type: armor
  slot: head                          # head | chest | legs | feet
  model:
    sprite: "item/ruby_helmet"        # the hotbar icon
  equipment:
    humanoid: "ruby"                  # what is painted onto the player
  java:
    base_material: NETHERITE_HELMET
```

Blocks and furniture borrow note block states, so they need no client mod. Assignments
are persisted in `plugins/Kalo/block-states.json` and never reused — a placed block is
stored as only its borrowed vanilla state, so a shifting assignment would silently turn
every already-placed block into something else.

Armor needs two textures, and they are different things: the `model:` sprite is the icon
in the hotbar, while `equipment:` is the sheet painted onto the player model. Leave
`equipment:` out and the piece is named after itself; set `equipment: {enabled: false}`
to keep the base material's vanilla armor texture.

### Model sources

```yaml
model:
  sprite: "item/ruby_sword"        # flat sprite, model generated for you
model:
  vanilla: "minecraft:apple"       # reuse a vanilla item's appearance
model:
  custom: "item/ruby_sword"        # a model you authored, shipped in assets/
  textures:
    layer0: "item/ruby_sword"
```

## Commands

| Command | Permission |
|---|---|
| `/kalo reload` | `kalo.command.reload` |
| `/kalo give <player> <item>` | `kalo.command.give` |
| `/kalo import oraxen <file>` | `kalo.command.import` |

## Architecture

```
packs/*/configs/*.yml
        │
        ▼
  ItemDefinition            platform-neutral (no Bukkit, no Geyser)
        │
   ┌────┴────┐
   ▼         ▼
JavaCompiler  BedrockCompiler
   │             │
   ▼             ▼
resourcepack   .mcpack + Geyser mappings
+ ItemStack
```

The definition layer describes *intent*; each platform compiler owns every
platform-specific decision. `Material` appears only in `JavaOptions`, never in the
definition itself. See [`docs/IR_DESIGN.md`](docs/IR_DESIGN.md) for why this is the
load-bearing decision of the whole project.

Kalo writes its own resource packs rather than depending on a third-party pack library —
pack compilation is the product, and being blocked on someone else's release schedule
every Minecraft version is not an option. See [`docs/PHASE0_AUDIT.md`](docs/PHASE0_AUDIT.md) §2.4.

## Roadmap

| Phase | Scope | State |
|---|---|---|
| **0 — Resurrection** | Audit, modern baseline, build green | ✅ done |
| **1 — Alpha** | Items → Blocks → Furniture → Armor, pack compiler, hot reload, API | 🚧 all four types work; furniture is static, entity-backed mode pending |
| **2 — Bedrock** | Geyser extension, Bedrock pack compiler, mappings | 🚧 items, cube blocks and custom-model blocks all compile, including Java→Bedrock geometry conversion; not yet verified against a live Geyser |
| **3 — Migration** | Nexo / ItemsAdder / Oraxen importers | 🚧 Oraxen/Nexo items import, reporting what did not carry over; ItemsAdder and blocks pending |
| **4 — Ecosystem** | Add-on API, MythicMobs, ModelEngine, PlaceholderAPI | planned |
| **5 — Cloud** | Optional managed CDN, hosting, builds, dashboard | planned |

Deliberately **not** in v0.1: HUD, custom mobs, a scripting language, web editor,
marketplace. The basics have to be solid first.

Kalo Cloud, when it exists, sells *operations* — hosting, CDN, build workers — never
features. The Community Edition is standalone forever.

## Development

Requires **Java 25** (Minecraft 26.x will not run on less). The Gradle toolchain
provisions it automatically if you do not have it.

```bash
./gradlew build          # build + test
./gradlew runServer      # test Paper server
./gradlew runFolia       # test Folia server
```

Output: `build/libs/Kalo-<version>.jar`

### Modules

- **`api`** — public API: content model, definitions, registries, features, pack model
- **`core`** — implementation: managers, compilers, pack writer, commands
- **`geyser-extension`** — runs inside Geyser, not Paper; registers Kalo's custom blocks
  so Bedrock renders them

## Migrating from Oraxen or Nexo

```
/kalo import oraxen plugins/Oraxen/items/weapons.yml
```

Writes `weapons.yml.kalo.yml` next to the source, never overwriting, and prints
everything it could **not** carry over. Read that list before adopting the result:
Oraxen's `Mechanics` drive its own behaviour system, which Kalo expresses through
features instead, so those are reported rather than guessed at.

The importer is written against the documented format rather than validated against a
corpus of real packs, so treat a first import as a draft to review.

## Bedrock setup

Two processes are involved, so there are two artifacts. The Paper plugin generates the
Bedrock pack and mappings; the Geyser extension registers them.

1. Run the server once so `plugins/Kalo/` contains `generated.mcpack` and
   `bedrock-mappings.json`.
2. Put `geyser-extension-<version>.jar` in Geyser's `extensions/` folder.
3. Copy `bedrock-mappings.json` into `extensions/kalo/`.
4. Serve `generated.mcpack` to Bedrock clients through Geyser's `packs/` folder.

The extension does not fail when the mapping file is missing — Geyser often starts before
the Paper side has generated one — it simply has nothing to register and says so.

## License

MIT. Kalo is derived from [Neko](https://github.com/bindglam/Neko) by Woobeen Jeon, whose
copyright notice is retained in [LICENSE](LICENSE) as that license requires.

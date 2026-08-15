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

> ⚠️ **Pre-alpha.** Items are the only content type implemented today, and the Bedrock
> compiler lands in Phase 2. See [the roadmap](#roadmap) for what is real and what is not.

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
model, the texture, and a lang entry — and the same definition is what a Bedrock compiler
will consume in Phase 2.

Content keys are namespaced by the pack that defines them, so the item above is
`mypack:ruby_sword` and cannot collide with another pack's.

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

## Architecture

```
packs/*/configs/*.yml
        │
        ▼
  ItemDefinition            platform-neutral (no Bukkit, no Geyser)
        │
   ┌────┴────┐
   ▼         ▼
JavaCompiler  BedrockCompiler (Phase 2)
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
| **1 — Alpha** | Items → Blocks → Furniture → Armor, pack compiler, hot reload, API | 🚧 items done |
| **2 — Bedrock** | Geyser extension, Bedrock pack compiler, mappings | planned |
| **3 — Migration** | Nexo / ItemsAdder / Oraxen importers | planned |
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

## License

MIT. Kalo is derived from [Neko](https://github.com/bindglam/Neko) by Woobeen Jeon, whose
copyright notice is retained in [LICENSE](LICENSE) as that license requires.

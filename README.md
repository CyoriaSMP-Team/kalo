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

> ⚠️ **Pre-alpha.** Items, blocks, furniture, armor and recipes work on Java, verified on a
> live Paper 26.2 server. The Bedrock path is verified as far as Geyser: the extension
> loads into Geyser 2.11.1 and its blocks reach Geyser's block palettes. No Bedrock client
> has connected yet, so the last mile is unproven. Furniture is static (block-backed)
> rather than entity-backed. See [the roadmap](#roadmap) for what is real and what is not.

## Four pillars

| | |
|---|---|
| **Open Source** | MIT, no paid tier gating features |
| **Community First** | No player caps, item caps, or premium converters |
| **Paper / Folia** | Modern server API, current Minecraft — both verified on 26.2 |
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

**There are 799 of those states**, which is the hard ceiling on blocks and furniture
combined. Past it, Kalo names each block it could not place and the reason, and skips it
on Bedrock too — a block on one platform and not the other is worse than a block on
neither. Items have no such limit; they use the item-model system.

Armor needs two textures, and they are different things: the `model:` sprite is the icon
in the hotbar, while `equipment:` is the sheet painted onto the player model. Leave
`equipment:` out and the piece is named after itself; set `equipment: {enabled: false}`
to keep the base material's vanilla armor texture.

### Sounds and glyphs

```yaml
cave_wind:
  type: sound
  category: ambient
  subtitle: "subtitles.mypack.cave_wind"
  sounds:
    - "ambient/cave_wind"        # assets/sounds/ambient/cave_wind.ogg

coin:
  type: glyph
  texture: "font/coin"
  character: "U+E000"            # or a literal character, or a decimal codepoint
  ascent: 8
  height: 9
```

A glyph binds an image to a character, so writing that character draws the image — icons
in chat, item names and menus without a client mod. Use the **Private Use Area**
(`U+E000` and up): those codepoints have no meaning of their own, so nothing in ordinary
text can collide with them. Kalo warns if you pick one outside it, and if two glyphs claim
the same character.

Glyphs are **appended** to the font rather than replacing it. Overwriting
`minecraft:default` outright would drop the providers the game uses for ordinary text and
leave a server where nothing but the icons is legible.

### Recipes

```yaml
ruby_sword_recipe:
  type: recipe
  result: ruby_sword          # or "4x ruby_dust", or "otherpack:thing"
  pattern:
    - " R "
    - " R "
    - " S "
  ingredients:
    R: mypack:ruby            # Kalo content
    S: minecraft:stick        # vanilla
```

Leave `pattern` out for a shapeless recipe. **The namespace decides** whether an
ingredient is vanilla or Kalo content — a pack is free to define `mypack:diamond`, and it
must not silently resolve to the vanilla one, so an unqualified name means "in this pack".

Kalo ingredients match on the id stamped into the item rather than on the whole stack, so
a player who renamed one on an anvil can still craft with it.

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
| `/kalo import <file>` | `kalo.command.import` |

## Asset validation

After generating, Kalo follows every texture and model reference in the pack and reports
the ones that go nowhere:

```
The generated pack has 1 broken reference(s) — these will render as missing textures:
  assets/testpack/models/item/ruby_sword.json references texture
  'testpack:item/ruby_sword_typo' (expected assets/testpack/textures/item/ruby_sword_typo.png)
```

A mistyped path used to produce no error anywhere — the pack built, the server started, and
the first sign of trouble was a player looking at a magenta cube. It reports rather than
refuses: a pack that is 95% right still loads, with the other 5% named.

## Building on an existing pack

Most servers already ship a pack — a font, a HUD, retextured vanilla. Kalo used to replace
it, forcing a choice between custom content and everything already built.

```yaml
# plugins/Kalo/config.yml
base-pack: "base.zip"    # relative to plugins/Kalo/, or absolute
```

Kalo's generated files win a collision, because they are the half that has to agree with
what the server sends clients — an item definition that disagrees with the `item_model`
component renders as missing texture. Language files and block states merge **entry by
entry** instead, since both packs' contents belong there: your translations survive, and a
base pack cannot take over a note block state Kalo allocated to a custom block.

## Serving the pack

Generating a pack is only half the job — without somewhere to fetch it from, the file sits
in the data folder and no player sees the content. Kalo can serve it itself:

```yaml
# plugins/Kalo/config.yml
pack-host:
  enabled: true
  port: 8163
  public-address: "play.example.com"   # what players actually connect to
  required: false                       # true kicks players who decline
```

Off by default deliberately: it opens a port, and the right public address is something
only you know — a wrong value hands out a URL nobody can reach.

The URL carries a token that rotates whenever the pack is regenerated. Minecraft caches a
pack by URL, so reusing one after a content change would strand every player on the old
pack with nothing to indicate anything was wrong. The SHA-1 is sent with it, which is what
lets an *unchanged* pack come from cache — the reason `ZipPackWriter` is deterministic.

This is the piece Kalo Cloud would later replace with a managed CDN. Self-hosting stays
free and fully functional.

## Add-ons

A third-party plugin can add its own content type — Kalo's own five are registered the
same way, through the same registry:

```java
@EventHandler
public void onRegistryInitialize(RegistryInitializeEvent event) {
    event.getRegistries().types().register(MyType.KEY, new MyType());
    event.getRegistries().features().register(MyFeature.KEY, new MyFeature.Factory());
}
```

Use the default event priority. Kalo reads content packs at `HIGHEST`, which runs last, so
a type registered at normal priority is in place before any pack is parsed.

A `ContentType` parses its own YAML, owns its registry, and contributes its own resource
pack assets — see `docs/IR_DESIGN.md` for the rule its definitions have to follow.

## PlaceholderAPI

Registered automatically when PlaceholderAPI is installed; nothing to configure, and
nothing happens on servers without it.

| Placeholder | Gives |
|---|---|
| `%kalo_held_id%` | the Kalo id of the held item, empty if it is not Kalo content |
| `%kalo_held_name%` | its display name as plain text |
| `%kalo_is_held_<key>%` | `true` / `false` |
| `%kalo_count_<key>%` | how many are in the player's inventory |
| `%kalo_items%`, `%kalo_blocks%` | how many are registered |

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
| **1 — Alpha** | Items → Blocks → Furniture → Armor, pack compiler, hot reload, API | ✅ five content types, hot reload and pack serving, verified on Paper **and Folia** 26.2; furniture is static, entity-backed mode pending |
| **2 — Bedrock** | Geyser extension, Bedrock pack compiler, mappings | ✅ verified against Geyser 2.11.1: the extension loads and registers blocks into Geyser's palettes. A Bedrock client has not connected yet |
| **3 — Migration** | Nexo / ItemsAdder / Oraxen importers | 🚧 items, blocks, furniture and crafting recipes from both, reporting what did not carry over; non-crafting stations and placed-world migration pending |
| **4 — Ecosystem** | Add-on API, MythicMobs, ModelEngine, PlaceholderAPI | 🚧 PlaceholderAPI done; the others planned |
| **5 — Cloud** | Optional managed CDN, hosting, builds, dashboard | 🚧 self-hosted pack serving works; the managed side is planned |

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

## Migrating from another plugin

| From | Items | Blocks | Furniture | Recipes |
|---|---|---|---|---|
| **Oraxen** | ✅ | ✅ | ✅ | ✅ |
| **Nexo** | ✅ | ✅ | ✅ | ✅ |
| **ItemsAdder** | ✅ | ✅ | ✅ | ✅ |
| **Neko** | ✅ | — | — | — |
| **CraftEngine** | ✅ | ✅ | — | — |

```
/kalo import plugins/Oraxen/items/weapons.yml
/kalo import plugins/ItemsAdder/contents/mypack/items.yml
/kalo import plugins/Nexo/items/weapons.yml
```

The format is detected, not asked for, and detection is **scored** rather than
first-match — these formats overlap (Nexo is an Oraxen fork; several are plain YAML maps
of content keys) so the most confident reader wins. A file nothing recognises is refused
rather than guessed at, because plausible nonsense is worse than a clear no.

The result is written as `<file>.kalo.yml` next to the source, never overwriting.

An add-on can support a format Kalo does not ship with:
`Importers.register(new MyVendorImporter())`.

What matters more than what converts is what does not. Both plugins drive their own
behaviour systems — Oraxen's `Mechanics`, ItemsAdder's `behaviours` — which Kalo expresses
through features instead. Those are **listed as needing hand-porting rather than mapped to
something plausible**, because a mechanic quietly dropped is something a server owner
learns about from their players. Unrecognised keys are reported by name for the same
reason, and non-item sections say how many entries they held so an empty result is not
mistaken for having nothing to do.

Blocks come across too — Oraxen expresses them as items carrying the `noteblock` mechanic,
ItemsAdder keeps them in a `blocks:` section — but **blocks already placed in a world are
not migrated**. All three plugins store a placed custom block as a note block in some
state and each decides independently which state means what, so existing blocks will read
as the wrong block until they are replaced. The importer says this every time it converts
one, because it is the most expensive thing to discover after going live.

Furniture converts too, but it is a **downgrade in capability, not a format change**. Both
plugins build furniture from entities, which buys rotation, custom hitboxes, seats and
multi-block models. Kalo's furniture is a single static block. The name and shape come
across; none of that behaviour does, and the importer names each lost property
individually — a chair losing its seat and a lamp losing its hitbox need different
follow-up work.

Both importers are written against the documented formats rather than validated against a
corpus of real packs, so treat a first import as a draft to review.

## Bedrock setup

**If Geyser runs as a plugin on the same server — the usual setup — there is nothing to
install and nothing to copy.** Kalo registers its blocks with Geyser directly through
Geyser's own API:

```
[Kalo] Registering Kalo blocks with Geyser directly — no extension needed
[Kalo] Registered 3 block(s) with Geyser natively
```

Serve `plugins/Kalo/generated.mcpack` to Bedrock clients through Geyser's `packs/` folder
and that is the whole setup.

Reading from the live registry rather than a file is the point: there is nothing in
between to go stale, so regenerating content cannot leave Bedrock on an old copy.

### Geyser running standalone

A separate process cannot be reached from inside the server, so that setup still needs the
extension:

1. Put `geyser-extension-<version>.jar` in Geyser's `extensions/` folder.
2. Copy `plugins/Kalo/bedrock-mappings.json` into `extensions/kalo/`, and again whenever
   content changes.

The extension does not fail when the mapping file is missing — Geyser often starts before
the Paper side has generated one — it simply has nothing to register and says so.

## License

MIT. Kalo is derived from [Neko](https://github.com/bindglam/Neko) by Woobeen Jeon, whose
copyright notice is retained in [LICENSE](LICENSE) as that license requires.

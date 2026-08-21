<div align="center">

# 🐈 Kalo

**Open Custom Content Engine for Minecraft**

*Build once. Play everywhere.*

[![Java](https://img.shields.io/badge/Java-21%2B-orange?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![Paper](https://img.shields.io/badge/Paper-1.21.4%20%E2%86%92%2026.2-green?style=flat-square&logo=papermc)](https://papermc.io/)
[![Folia](https://img.shields.io/badge/Folia-supported-success?style=flat-square)](https://papermc.io/software/folia)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

</div>

Kalo is a free and open-source engine for building custom items, blocks, furniture and
armor on Paper and Folia servers — no client mods, no player limits, no feature paywalls.

**Bedrock is not a DLC.** One YAML definition compiles to both a Java resource pack and
Bedrock output, because the content model is platform-neutral by design rather than by
translation. That is the thing Kalo exists to do.

> ⚠️ **Pre-alpha.** Items, blocks, furniture, armor, **all recipe stations** (crafting +
> furnace/blast/smoker/campfire/stonecutting/smithing), sounds and glyphs work on Java,
> verified on Paper 26.2 + Folia. Virtual blocks have a persistent index, chunk
> load/unload, explosion handling and a `/kalo migrate-world` dry-run.
>
> **Bedrock has not been seen by a Bedrock client yet.** The registration path is written
> and tested and the mapping files match Geyser's documented format, but nothing has
> confirmed a Bedrock player sees an item icon or a placed block. Read
> [the roadmap](#roadmap) before you rely on it.

## Four pillars

| | |
|---|---|
| **Open Source** | MIT, no paid tier gating features |
| **Community First** | No player caps, item caps, or premium converters |
| **Paper / Folia** | Modern server API, current Minecraft — both verified on 26.2 |
| **Java + Bedrock** | One definition compiles to both. No converter, no companion plugin, no second jar — where Geyser shares the server, there is nothing to install or copy at all |

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
  # java.mode defaults to `native` — a real vanilla block state. Switch to
  # `virtual` (ItemDisplay + anchor) once the 893 native states run out.

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

The default mode is **native**, and it is the one to prefer. A native block is a real
vanilla block placed in a spare state that the pack tells the client to draw differently —
so the server treats it as a block, it costs no entity, and vanilla mechanics apply to it.

```yaml
java:
  mode: native
  carrier: NOTE_BLOCK               # or TRIPWIRE for non-solid decorative content
```

Assignments are persisted in `plugins/Kalo/block-states.json` and never reused. Note Block
gives **799 usable states**, Tripwire adds 63 and Scaffolding adds 31 — **893** native
blocks and furniture in total, filled in that order.

**Virtual mode is what you reach for when those run out.** It swaps the borrowed state for
an invisible Barrier anchor plus a persistent `ItemDisplay` holding the Kalo key in its
entity PDC, which removes the state ceiling entirely:

```yaml
java:
  mode: virtual
```

It removes the **state-count** ceiling, not every trade-off. A server pays for one
persistent display entity per placed block — storage, tracking bandwidth and client
rendering — and entity-backed content does not inherit redstone, piston, fluid or other
native block behaviour. Either way the item model and block model are generated normally,
so the same YAML drives inventory, placement and the Java resource pack.

A sensible pack at scale mixes the two: mechanically meaningful blocks stay `native`,
bulk decorative content goes `virtual`. Existing native worlds can be moved one key at a
time — a key switched to virtual keeps its old state in the generated pack as a legacy
read path, so already-placed blocks do not change appearance.

Armor needs two textures, and they are different things: the `model:` sprite is the icon
in the hotbar, while `equipment:` is the sheet painted onto the player model. Leave
`equipment:` out and the piece is named after itself; set `equipment: {enabled: false}`
to keep the base material's vanilla armor texture.

The same `equipment:` sheet drives **both platforms**. Java paints it onto the player
through an equipment asset; Bedrock attaches a model and hides the vanilla layer beneath.
Two mechanisms with nothing in common, one line of config — which is the whole reason the
definition layer describes intent rather than either platform's output.

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

Non-crafting stations use `station:` instead of a pattern:

```yaml
ruby_smelting:
  type: recipe
  station: furnace          # furnace | blast_furnace | smoker | campfire
  input: mypack:ruby_ore
  result: mypack:ruby
  experience: 0.7
  cooking_time: 200         # ticks, default 200

ruby_cut:
  type: recipe
  station: stonecutter
  input: mypack:ruby_block
  result: 4x mypack:ruby

ruby_upgrade:
  type: recipe
  station: smithing
  base: minecraft:netherite_sword
  addition: mypack:ruby
  result: mypack:ruby_sword
```

Check what was imported with `/kalo migrate-world` — it reports allocated states in loaded chunks without modifying the world.

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
| `/kalo import <plugin>` | `kalo.command.import` |
| `/kalo import file <path>` | `kalo.command.import` |
| `/kalo doctor` | `kalo.command.doctor` |
| `/kalo migrate-world` | `kalo.command.migrate` |

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

Deliberately **not** in v0.1: HUD, custom mobs, a scripting language, web editor,
marketplace. The basics have to be solid first.

Kalo Cloud, when it exists, sells *operations* — hosting, CDN, build workers — never
features. The Community Edition is standalone forever.

## Development

Requires **Java 21** for Paper 1.21.4, **Java 25** for 26.2 (supports **1.21.4+ → 26.2** with one jar — `pack_format` auto-selects 46 for 1.21.4, 88 for 26.2). The Gradle toolchain provisions the needed JDK automatically.

```bash
./gradlew build          # build + test
./gradlew runServer      # test Paper server
./gradlew runFolia       # test Folia server
```

Output: `build/libs/Kalo-<version>.jar`

### Modules

- **`api`** — public API: content model, definitions, registries, features, pack model
- **`core`** — implementation: managers, compilers, pack writer, commands, Geyser bridge

## Migrating from another plugin

| From | Items | Blocks | Furniture | Recipes |
|---|---|---|---|---|
| **Oraxen** | ✅ | ✅ | ✅ | ✅ |
| **ItemsAdder** | ✅ | ✅ | ✅ | ✅ |
| **Neko** | ✅ | — | — | — |
| **CraftEngine** | ✅ | ✅ | — | — |

```
/kalo import Oraxen
/kalo import ItemsAdder
```

Kalo autocompletes installed plugins that contain a recognised content file. The command
scans the selected plugin's data folder, detects ItemsAdder/Oraxen/CraftEngine/Neko
files, creates `plugins/Kalo/packs/<plugin>/`, writes the converted configs into its
`configs/` folder, and prints the migration report. Copy the source textures/models into
that pack's `assets/` folder, then run `/kalo reload`.

`/kalo impor <plugin>` is accepted as a short alias too.

For a plugin that is not installed, or a single file you want to review first, use the
explicit path form:

```
/kalo import file plugins/Oraxen/items/weapons.yml
```

The format is detected, not asked for, and detection is **scored** rather than
first-match — these formats overlap (several are plain YAML maps
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

Bedrock output is built **only when Geyser is running on this server**. A Java-only server
gets no `.mcpack` and no mapping file, because it has no use for them — at a thousand
items that is thousands of files written for nobody. Override with `bedrock: always` in
the config when Geyser runs as a separate process Kalo cannot see, or `never` to skip it
outright.

**If Geyser runs as a plugin on the same server — the usual setup — there is nothing to
install and nothing to copy.** Kalo registers its blocks with Geyser directly through
Geyser's own API:

```
[Kalo] Registering Kalo items, blocks and resource pack with Geyser directly — no extension needed
[Kalo] Registered and mapped 3 block(s) with Geyser natively
```

Items, blocks **and `generated.mcpack` itself** are handed to Geyser through its own API,
so there is no pack to copy either. That is the whole setup.

Reading from the live registry rather than a file is the point: there is nothing in
between to go stale, so regenerating content cannot leave Bedrock on an old copy.

### Geyser running standalone

A separate process cannot be reached from inside the server, so it reads the same
decisions out of files instead. **There is no Kalo artifact to install inside Geyser** —
these are Geyser's own `custom_mappings` files, which it reads natively:

1. Copy `plugins/Kalo/geyser/kalo-items.json` and `kalo-blocks.json` into Geyser's
   `custom_mappings/` folder.
2. Copy `plugins/Kalo/generated.mcpack` into Geyser's `packs/` folder.

Repeat both whenever content changes. Items and blocks are two files because Geyser
versions the two formats separately (`format_version` 2 and 1).

Every copy step is a chance to serve stale content, and nothing in a separate process can
tell you when it happened. If you can run Geyser as a plugin on the server, do that
instead — then there is nothing to copy at all.

## Roadmap

Kalo is pre-alpha. This table is the honest split between what has been run and what has
only been written — the middle column is the one worth reading before you install it.

### Works, exercised on a running server

| | Notes |
|---|---|
| Items, blocks, furniture, armor | Java side, Paper 26.2 + Folia |
| All recipe stations | crafting, furnace, blast, smoker, campfire, stonecutting, smithing |
| Sounds and glyphs | |
| Virtual blocks | persistent index, chunk load/unload, explosion handling |
| `/kalo migrate-world` | dry-run |
| Resource pack generation | deterministic zip, item-definition format |

### Implemented but **not verified end to end**

| | What is missing |
|---|---|
| **Bedrock, as a player sees it** | Verified on a live server running Geyser: Kalo registers its items, blocks and generated pack with Geyser natively at startup, and the identifiers linking the mapping files to the pack's `blocks.json` and terrain atlas agree. What has **not** happened is a Bedrock client connecting, so nothing has confirmed a *player* sees an item icon, a placed block, or worn armor. That last step is the gap. |
| Bedrock virtual blocks | Placement and `ItemDisplay` rendering unconfirmed — see [docs/VIRTUAL_BLOCKS.md](docs/VIRTUAL_BLOCKS.md) |
| Standalone-Geyser path | The mapping files generate on a real server and match Geyser's documented format, but the copy-the-files setup has not been run against a real standalone Geyser |
| The 1.21.4 end of the version range | One jar spans 1.21.4 → 26.2 and `pack_format` auto-selects per version, but testing has happened on 26.2 only |
| Oraxen / ItemsAdder import | Written against documented formats, never run against a real pack. Treat any import as a draft to review. |

### Not built

| | |
|---|---|
| Verified `pack_format` for 1.21.5 – 1.21.11 | Only 1.21.4 (46) and 26.2 (88) have been read out of a real client jar. Servers in between get 46 and a warning in the console — the number is a guess, and a wrong one can make the client reject the whole pack. Read `pack_version.resource_major` from that version's client jar and add it to `PackFormats` |
| Bedrock geometry for custom **item** models | Only sprite items reach Bedrock; an item with a hand-authored model is skipped and counted in the generation warning. Blocks and furniture are fine — `BedrockGeometry` converts their custom models already |
| Real server-side block registration | Would still need a borrowed visual state on vanilla clients — see the `BlockCarrier` javadoc |

## License

MIT. Kalo is derived from [Neko](https://github.com/bindglam/Neko) by Woobeen Jeon, whose
copyright notice is retained in [LICENSE](LICENSE) as that license requires.

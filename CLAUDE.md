# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Kalo** is a free and open-source custom content engine for Minecraft Paper/Folia
servers. Content creators define custom items, blocks, furniture, armor and recipes in
YAML content packs; Kalo compiles them into a resource pack and runtime objects with no
client mods required.

The product thesis is **cross-platform by design**: one YAML definition compiles to both
Java and Bedrock output. That constraint drives the architecture — see `docs/IR_DESIGN.md`.

Kalo is derived from [Neko](https://github.com/bindglam/Neko) (MIT, Woobeen Jeon). The
original copyright notice stays in `LICENSE`.

## Build System

Gradle with Kotlin DSL. Key properties in `gradle.properties`:

- `plugin_version` — plugin version
- `minecraft_version` — target Minecraft version (`26.2`)
- `paper_api_version` — exact Paper artifact (`26.2.build.112-stable`)
- `paper_plugin_api_version` — `api-version` written into `paper-plugin.yml`

**Minecraft uses calendar versioning now** (26.1, 26.2 — not 1.21.x), and Paper artifacts
are build-pinned rather than `-R0.1-SNAPSHOT`. Bumping the version means updating both
`minecraft_version` and `paper_api_version`.

**Java 25 is the floor.** Minecraft 26.x will not run on less. The foojay resolver in
`settings.gradle.kts` provisions the toolchain automatically.

### Common Commands

```bash
./gradlew build        # compile + test
./gradlew shadowJar    # fat jar
./gradlew runServer    # test Paper server
./gradlew runFolia     # test Folia server
./gradlew test         # tests only
```

Output: `build/libs/Kalo-{version}.jar`

## Module Structure

### `api` — public API, package `io.kalo`

- `Kalo` / `KaloPlugin` — singleton accessor and plugin interface
- `content` — `Content`, `ContentType`, `ContentsPack`, `PackContext`
- **the IR**, one package per content type, none of it naming a platform:
  - `content.item.definition` — `ItemDefinition`, `ModelDefinition`, `DisplayProperties`,
    `ItemBehaviour`, `JavaOptions`, `BedrockOptions`
  - `content.block.definition` — `BlockDefinition`, `BlockModelDefinition`,
    `BlockBehaviour`, `BlockCarrier`, `JavaBlockOptions`
  - `content.armor` — `ArmorDefinition`, `ArmorSlot`, and the worn `EquipmentTexture`
  - `content.recipe.definition` — `RecipeDefinition`, `RecipeIngredient`, `RecipeResult`
- `content.furniture` — `Furniture extends Block`; static, block-backed by design
- `content.feature` — `Feature`, `FeatureFactory`, `FeatureBuilder`, `FeatureEventBus`
- `pack` — `ResourcePack`, `PackMeta`, `Writable`
- `registry` / `manager` — registry and manager interfaces

### `core` — implementation

- `KaloPluginImpl` / `KaloPluginLoader`
- `manager.*Impl` — registry, content, resource pack, command managers
- `pack` — `ResourcePackImpl`, `ZipPackWriter`, `PackFormats`, `Json`
- `platform.java` — `JavaItemCompiler` / `JavaBlockItemCompiler` / `JavaArmorItemCompiler`
  (→ `ItemStack`), `JavaPackCompiler` / `JavaBlockCompiler` / `JavaArmorCompiler`
  (→ pack assets), `JavaRecipeCompiler` (→ Bukkit recipes), `BlockStateAllocator`,
  `JavaBlockListener`
- `platform.bedrock` — `BedrockPackCompiler` (→ `.mcpack` + Geyser mappings),
  `BedrockGeometry` (Java model → Bedrock geometry), `BedrockPackWriter`
- `migration` — `OraxenImporter`, `ItemsAdderImporter`, `ImportReport`
- `registry` — `MappedRegistry`, `DirectScalableRegistry`, `EntryScalableRegistry`

### `geyser-extension` — runs inside Geyser, not Paper

Registers Kalo's custom blocks through Geyser's API. It shares a **file format** with the
plugin (`bedrock-mappings.json`), never classes — the two are different processes.

## Architecture

### The IR is the load-bearing decision

```
YAML → ItemDefinition (platform-neutral) → ┬→ JavaCompiler    → resource pack + ItemStack
                                           └→ BedrockCompiler → .mcpack + Geyser mappings
```

**No `org.bukkit.*`, no Geyser type, and no pack-format constant may appear in the
definition layer.** `Material` lives in `JavaOptions` and nowhere else. If a field can
only be satisfied by naming a Java concept, it belongs in a platform options record.

When adding a content type, add the `*Definition` first, then a case in each compiler.
Never let a platform type leak upward into the definition.
`JavaPackCompilerTest.javaOptionsIsTheOnlyPlaceMaterialAppears` and its block counterpart
exist to fail loudly if that rule is broken.

### Custom blocks borrow vanilla states

Java cannot add a block without a client mod, so a custom block is a note block in a state
the pack tells the client to render differently. `BlockStateAllocator` assigns those
states, **persists them, and never reuses one**: a placed block is stored as only its
borrowed state, so a shifting assignment silently turns every already-placed block into
something else. Assignments are written through on allocation, not just at shutdown.

`JavaBlockListener` suppresses the three ways vanilla fights this: instrument
recomputation on neighbour updates, right-click tuning, and note playing.

### Shared output files must be merged, not replaced

Several content types write the same file — `note_block.json` (blocks and furniture),
`lang/en_us.json` (every type), the Bedrock mapping (every type). Each of those has been a
bug where the type that compiled last erased the others. New compilers must merge.

### Resource pack generation

Kalo writes its own packs. The old Creative dependency was dropped: its last release
(1.7.3, April 2024) predates the 1.21.4 item-definition system and is binary-incompatible
with the Adventure 5 that Paper 26.2 ships. See `docs/PHASE0_AUDIT.md` §2.4.

Target format is the **item definition system** (`assets/<ns>/items/*.json` +
`minecraft:item_model`), *not* legacy CustomModelData overrides.

`PackFormats.CURRENT` must be verified against `version.json` in the vanilla client jar
(`pack_version.resource_major`) on every Minecraft bump — a wrong value makes the client
reject the whole pack.

`ZipPackWriter` writes deterministically (fixed entry timestamps, sorted entries) so an
unchanged pack has a stable hash and clients do not re-download it every restart.

### Manager lifecycle

`Managerial`: `preload(Context)` → `start(Context)` → `end(Context)`. Managers
implementing `Reloadable` participate in `KaloPlugin.reload()`.

### Registries

`GlobalRegistries` (types, features, contentsPacks) plus per-pack `Registries` (item,
block, furniture, armor). Backed by `ConcurrentHashMap`; pack generation reads from a
background thread while the main thread may still be registering.
`RegistryInitializeEvent` fires when global registries are open for registration.

Recipes are the exception: they are not `Content` — no key to hand a player, no item form,
nothing in the pack — so `RecipeType` holds them itself and registers them with the server
only after **every** pack has loaded, since a recipe may reference content from a pack
that had not been read when it was parsed.

### Content packs

Loaded from `plugins/Kalo/packs/`. Each pack needs `pack.yml` (id, version, author);
`configs/**.yml` holds definitions and `assets/**` is copied into the generated pack under
the pack's namespace.

**Content keys are namespaced by the owning pack** via `PackContext`. Anything that
bypasses that lands content in `minecraft:` and collides across packs.

## Conventions

- Java 25, Lombok for accessors on implementation classes
- `@NotNull` / `@Nullable` on API surfaces
- Never swallow exceptions during pack loading — a content creator's typo must produce a
  message naming the file and the problem
- Tests live in `core/src/test/java` and `geyser-extension/src/test/java`;
  `./gradlew build` runs them
- Compilers must stay runnable without a live server. `org.bukkit.Instrument` is
  registry-backed on Paper 26.2 and throws `No RegistryAccess implementation found`
  outside one — that is why `JavaBlockCompiler` holds instrument *ids* and the Bukkit
  table lives in `JavaBlockListener`

## Migration importers

`io.kalo.migration` reads Oraxen/Nexo and ItemsAdder configs. The governing rule is that
**a migration must report what it cannot carry, never guess**: both plugins have their own
behaviour systems with no mechanical equivalent in Kalo's features, and an item that
quietly stops working is discovered from players weeks later. `ImportReport` collects
failures, unsupported keys and warnings; `/kalo import` prints them.

Both are written against documented formats, **not validated against real packs**. The
tests pin the importers' own assumptions and are a regression guard, not proof.

Two notices are raised unconditionally when they apply, because they are the expensive
surprises: `BlockImportNotice` (placed blocks are not migrated — every plugin allocates
note block states independently) and `FurnitureImportNotice` (Kalo furniture is a static
block, so rotation, hitboxes and seats do not survive).

## Examples

`examples/testpack` is the reference pack (`run/` is gitignored). It exercises every
content type. See `examples/README.md`.

## License

MIT — Copyright (c) 2026 Woobeen Jeon and Kalo Contributors.

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Kalo** is a free and open-source custom content engine for Minecraft Paper/Folia
servers. Content creators define custom items (and eventually blocks, furniture, armor)
in YAML content packs; Kalo compiles them into a resource pack and runtime objects with
no client mods required.

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
- `content.item.definition` — **the IR**: `ItemDefinition`, `ModelDefinition`,
  `DisplayProperties`, `ItemBehaviour`, `JavaOptions`, `BedrockOptions`
- `content.feature` — `Feature`, `FeatureFactory`, `FeatureBuilder`, `FeatureEventBus`
- `pack` — `ResourcePack`, `PackMeta`, `Writable`
- `registry` / `manager` — registry and manager interfaces

### `core` — implementation

- `KaloPluginImpl` / `KaloPluginLoader`
- `manager.*Impl` — registry, content, resource pack, command managers
- `pack` — `ResourcePackImpl`, `ZipPackWriter`, `PackFormats`, `Json`
- `platform.java` — `JavaItemCompiler` (→ `ItemStack`), `JavaPackCompiler` (→ pack assets)
- `registry` — `MappedRegistry`, `DirectScalableRegistry`, `EntryScalableRegistry`

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

`GlobalRegistries` (types, features, contentsPacks) plus per-pack `Registries` (items).
Backed by `ConcurrentHashMap`; pack generation reads from a background thread while the
main thread may still be registering. `RegistryInitializeEvent` fires when global
registries are open for registration.

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
- Tests live in `core/src/test/java`; `./gradlew build` runs them

## Examples

`examples/testpack` is the reference pack (`run/` is gitignored). See `examples/README.md`.

## License

MIT — Copyright (c) 2026 Woobeen Jeon and Kalo Contributors.

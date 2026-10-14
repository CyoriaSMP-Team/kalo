# Kalo Mohist Compatibility — DeadZone Minecraft 1.20.1

This is a separate, experimental Java 17-bytecode Bukkit plugin for Mohist
1.20.1. DeadZone currently runs Java 21, which is compatible: Java 21 can
execute Java 17 class files. No change to DeadZone's runtime is needed.

Build from the repo root:

    ./gradlew :mohist:clean :mohist:test :mohist:jar

Output: mohist/build/libs/Kalo-Mohist-<version>.jar
Do not install the Paper/Folia Kalo jar from build/libs/ into Mohist.

## Supported now

- Read-only local resource-pack ZIP discovery (plugins/) and Forge JAR asset
  discovery (mods/), with archive and path safeguards.
- Old-style CustomModelData observation on vanilla Bukkit carrier materials.
- Optional operator-enabled automatic observation from player inventories
  and GUIs, disabled by default.
- Experimental flat 2D Bedrock PNG atlas/pack + Geyser legacy item mapping
  export. Generated files are separate from the active bridge.

Commands (OP permission kalomohist.admin):
- /kalomohist status
- /kalomohist scan
- /kalomohist learn
- /kalomohist export

Output files when conversion succeeds:
- plugins/KaloMohist/exports/kalo-mohist-generated.mcpack
- plugins/KaloMohist/exports/kalo-mohist-items.json

The running DeadZoneBridgeLauncher remains unchanged. All outputs need
manual inspection before use with a compatible external Geyser proxy.

## Not supported

- Full Kalo native YAML content engine/commands on Mohist 1.20.1
- Forge custom registry and packet protocol translation, TaCZ gameplay,
  special entity behaviors and mod item identity mapping
- 3D model conversion, animated textures, custom blocks or composite models
- Direct Geyser/Bedrock player connectivity from a Forge-modded server
- Multi-pack automatic merge when multiple pack candidates exist

Exporting assets is not proof that a Bedrock client can enter the server.

## Compatibility and deployment

The CI must build on Java 21 and Java 25 and confirm class file major 61
(Java 17). Actual testing on Mohist 1.20.1 with DeadZone's Java 21 runtime
and a real Bedrock client remains pending. Production installation and
restart are intentionally deferred until reviewed and authorized.

## Universal content adapters (experimental extension)

The separate Mohist module now provides a **mod-neutral catalog**. It is not
hardcoded to TaCZ or any other mod. On startup, `auto-catalog: true` (default)
inspects local plugin pack ZIPs and the assets inside installed Forge mod JARs
in the background. On the server thread it also reads the **existing Forge
registry identifiers** for items, blocks, entities, fluids and sounds through a
read-only reflection probe when available. This does not register game objects,
change world data, hook network packets, or load new code from mod JARs.

The neutral `ContentProvider` interface supports further trusted adapters:
implementations classify paths into item and block models, textures, animated
texture metadata, entity animations, sound, language, loot tables, recipes,
particles, fonts, geometries and opaque mod assets. Future adapters can be
packaged on the plugin classpath via Java ServiceLoader; nothing is executed
from scanned JARs.

Commands:
- `/kalomohist catalog` rebuilds the read-only index on demand.
- `/kalomohist scan` continues to list candidate input archives.
- `/kalomohist export` remains the operator-only Geyser legacy exporter.

New files are placed only in `plugins/KaloMohist/exports/`:
- `kalo-content-index.json` (`kalo.content-index/1`): provenance-tagged
  source assets and Forge IDs, including candidate item/block model matches.
- `kalo-bridge-handoff.json` (`kalo.bridge-handoff/1`): opt-in format contract
  for downstream integration, with explicit warning that it does NOT translate
  Forge protocols or automatically deploy to a live Geyser proxy.

Handoff consumers (including a future DeadZoneBridgeLauncher adapter) must
validate the schema, review the asset provenance and handle their own lifecycle.
The current launcher and ViaProxy installations are **not modified**.

The legacy exporter additionally supports conservative single-texture-sheet
static cuboid models with Bedrock geometry and attachables; unsupported or
ambiguous multi-sheet/complex models are skipped. Animated textures are
indexed as candidates, but animation rendering is not converted. Java item
identity on Forge is more complicated than an asset name: the asset index is
not proof that a matching registry entry can be rendered or interacted with
on a Bedrock client.

This is extensibility and safe asset interoperability, **not universal
Forge-to-Bedrock compatibility**. Mod-specific game mechanics and network
messages still require protocol adapters and live client testing.

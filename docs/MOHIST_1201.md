# Kalo Mohist Compatibility — DeadZone Minecraft 1.20.1

The Mohist 1.20.1 runtime is embedded in the same `Kalo-<version>.jar` as
Kalo's Paper/Folia runtime. The jar contains both descriptors:

- Paper/Folia selects `paper-plugin.yml` and starts the Java 21 Kalo engine.
- Mohist 1.20.1 selects the legacy `plugin.yml` entry point
  `io.kalo.mohist.MohistPlugin`, which is compiled to Java 17 bytecode.

DeadZone's Java 21 runtime can execute Java 17 class files, so no Java runtime
change is needed. Install only the main `Kalo-<version>.jar`; do not install a
separate Mohist or Nexo compatibility jar.

Build and test from the repository root:

    ./gradlew build

The `mohist` source module remains separate inside the Gradle build so CI can
verify its Java 17 bytecode independently. It is packaged into the main Kalo
artifact and is not released as a second plugin.

## Supported now

- Read-only local resource-pack ZIP discovery (plugins/) and Forge JAR asset
  discovery (mods/), with archive and path safeguards.
- Old-style CustomModelData observation on vanilla Bukkit carrier materials.
- Configurable automatic observation from player inventories and GUIs
  (`mohist.auto-learn` in the shared `plugins/Kalo/config.yml`).
- Experimental flat 2D Bedrock PNG atlas/pack + Geyser legacy item mapping
  export. Generated files are separate from the active bridge.

Commands (OP permission `kalomohist.admin`):
- `/kalo status` (aliases: `/kalomohist`, `/kalom`)
- `/kalo scan`
- `/kalo catalog`
- `/kalo learn`
- `/kalo export`

Output files when conversion succeeds:
- plugins/Kalo/exports/kalo-mohist-generated.mcpack
- plugins/Kalo/exports/kalo-mohist-items.json

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

CI builds on Java 21 and Java 25 and verifies that the Mohist entry point has
class file major 61 (Java 17) inside the unified Kalo jar. Actual testing on
Mohist 1.20.1 with DeadZone's Java 21 runtime and a real Bedrock client remains
pending. The shared config is `plugins/Kalo/config.yml`; Mohist options are
under `mohist:` (`auto-learn`, `auto-catalog`).

When upgrading from the former standalone `Kalo-Mohist` plugin, stop the
server and copy `legacy-observed.properties` and the `exports/` directory from
`plugins/KaloMohist/` into `plugins/Kalo/`. Keep the existing Kalo config and
merge the old `auto-learn` / `auto-catalog` values under the `mohist:` section.

## Universal content adapters (experimental extension)

The embedded Mohist runtime provides a **mod-neutral catalog**. It is not
hardcoded to TaCZ or any other mod. On startup, `mohist.auto-catalog: true` (default)
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
- `/kalo catalog` rebuilds the read-only index on demand.
- `/kalo scan` continues to list candidate input archives.
- `/kalo export` remains the operator-only Geyser legacy exporter.

New files are placed only in `plugins/Kalo/exports/`:
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

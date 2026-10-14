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

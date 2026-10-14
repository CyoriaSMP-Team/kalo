# Changelog

Kalo is distributed through multiple channels, each of which wants its own release notes.
This file is the one place they are written; the release workflow reads the section for
the version being tagged and hands it to every channel.

Versions follow `plugin_version` in `gradle.properties`, and a tag must match it.

## Unreleased

### Added

- Provider-neutral Mohist content inventory with read-only Forge runtime
  registry probing and generic Java pack/Forge mod asset classification.
- Trusted ContentProvider SPI, versioned bridge handoff and content index
  with provenance and failure-safe output writes; optional auto-catalog.
- Conservative Bedrock static cuboid/attachable conversion for single-sheet
  legacy Java item models, reusing Kalo's proven geometry translation rules.
- Unit tests for mixed asset sources, Forge registry metadata, 3D model
  conversion, fallback behavior and bridge contract semantics.

### Limitations

- Forge network packets, custom mod behaviors, animated/multi-sheet 3D models,
  Bedrock login and live Bridge installation remain unsupported. No production
  deployment or real Bedrock client verification has been performed.

## 0.2.1-prealpha.6

### Added

- Independent Java 17-compatible Mohist/Bukkit 1.20.1 adapter, also runnable
  on DeadZone's Java 21. This is a separate jar, not the Paper Kalo plugin.
- Read-only resource-pack and Forge-jar asset discovery; old CustomModelData
  observations, optional opt-in auto-learn, flat 2D Bedrock pack and
  experimental legacy Geyser mapping exports.
- Module-specific archive safety limits and deterministic fixture tests.

### Limitations

- No native Kalo engine on Mohist, Forge/TaCZ protocol conversion, 3D item
  rendering or hot-loaded Geyser custom items. No live Bedrock-client
  validation, production deployment or server restart has been performed.

## 0.2.1-prealpha.5

### Added

- Opt-in automatic Java Resource Pack discovery from installed plugin data
  folders when base-pack is empty or "auto". A single bounded and validated
  archive is merged automatically; multiple candidates are never guessed.
- /kalo discover-packs: asynchronous, read-only report of detected Java pack
  ZIPs and rejected inputs. Explicit base-pack remains authoritative.
- Regression tests for detection, ambiguous packs, malformed archives,
  traversal paths, backups and symbolic links.

### Limitations

- No download of remote server packs, client-only assets, or automatic merging
  of ambiguous plugin pack generations. Unknown pack formats are rejected.
- Auto-learn still requires an explicit opt-in; Geyser palette updates need
  restart and external conversions require Bedrock-client verification.

## 0.2.1-prealpha.4

### Added

- Java item-model selector conversion (condition, range dispatch, select) with
  bounded expansion, mutually exclusive Geyser v2 predicates, stable variant
  Bedrock identifiers and shared JSON/native API registration.
- Legacy CustomModelData observation/persistence plus conservative legacy
  model-override compilation to Bedrock through the existing Kalo compiler.
- Regression tests for selector fallbacks, negative conditions, range intervals,
  duplicate thresholds, legacy source overrides and mapping parity.

### Limitations

- No animated texture conversion, external block override mapping, composite
  rendering, skulls, custom waypoints or automatic client-only resource pack
  collection. Geyser/Bedrock real-client verification is still pending.
- Existing gameplay and native Kalo definitions are unchanged by default;
  these features require external-content.enabled and a matching base pack.

## 0.2.1-prealpha.3

### Added

- Opt-in existing Java item_model mapping into Kalo's Bedrock/Geyser output,
  resolving plain 2D sprites and supported static 3D models from a base Java pack.
  Uses real ItemStack material/model observations rather than inventing mappings
  from every unused resource-pack asset.
- /kalo learn-held records observed item models for the next Geyser startup.
  Optional region-safe auto-learn observes server-visible inventory/GUI items.
- External mapping persistence, validation, native-ID collision protection,
  and regression tests. Native Kalo output stays unchanged by default.

### Limitations

- Newly observed models require a Geyser/server restart; mapping changes
  cannot be hot-reloaded into Geyser's frozen item palette.
- Predicate-driven/legacy-only models, animated items, external custom blocks
  and client-only resource packs are not supported by this increment.
- Real Bedrock client validation of these external mappings remains pending.

## 0.2.1-prealpha.2

### Added

- Hybrid Java resource-pack inspection (phase 1): `/kalo inspect-pack <filename>`
  indexes item-model candidates, blockstates, textures, sounds, and locales from
  a local ZIP without modifying Kalo items or live Geyser mappings.
- Optional MCMall publishing integration in the release workflow; publishing
  requires both MCMALL_API_KEY and MCMALL_PRODUCT_ID.
- Experimental static 3D custom item compilation for Bedrock: geometry,
  held-item attachables, provisional texture icon and Geyser v2 mappings.
  Single- and multi-sheet static items can resolve model and authored texture
  aliases; multi-sheet cuboids use a deterministic UV atlas. Parent model
  geometry/texture overrides and standard held-item view animations are supported.
- Tests for output, mappings, aliases, and unsupported texture combinations.
- GUI references to Kalo content keys, usable with
  `/kalo gui open <player> <gui>`. Repaired InventoryHolder registration.
- `/kalo export kalopack <pack>`: deterministic ZIP with safe output
  handling, no overwrites, and a test suite.
- Validated CustomModelData frame sequences using entity-region scheduling
  instead of silently applying only the first frame.
- Optional standalone Geyser local-filesystem synchronization. Copies a
  generated pack and item/block mapping files to a verified Geyser root,
  preserving unrelated files and refusing symlink targets.
- Regression tests for changed/unchanged output, missing inputs and symlinks.

### Fixed

- Folia-safe mana regeneration: a single global coordinator runs every second
  and schedules player-specific updates on each player's own entity scheduler.
  The old per-definition BukkitRunnable multiplied mana regeneration when
  several custom items declared the cooldown feature.
- Regen delay starts when mana is consumed. Bounds-check mana configuration
  and prevent negative/infinite state from reaching UI updates.

### Still pending

- Live Folia/Paper multi-player validation and broader feature-scheduler audit.
  Mana remains a shared player pool, not one independent pool per item.

### Limitations

- Hybrid inspection does not yet convert external packs into Bedrock mappings,
  import external runtime item identities, or provide Rainbow feature parity.
- Live Bedrock client rendering remains unverified. Full Java display transforms,
  rigged/animated models, mixed-size texture sheets and 3D inventory previews
  are still unsupported or incomplete.
- This does not solve Forge login or mod-specific gameplay protocols.
- Client models and Bedrock animation controllers remain unfinished.
- Third-party exporters remain lossy and have no public CLI command.
- Live Bedrock verification of new behavior is still required.
- This is not cross-process hot reload; standalone Geyser must load
  its updated mappings and resource packs.

## 0.2.1-prealpha.1

### Changes from v0.2.0

- **Pre-alpha status made explicit** in the README and documentation. Tagged
  pre-alpha builds now publish as GitHub prereleases, Modrinth alpha releases
  and Hangar Alpha channel builds rather than silently appearing stable.
- **Correct Java resource pack format values** for Minecraft 1.21.5 (55),
  1.21.6 (63), 1.21.7–1.21.8 (64), 1.21.9–1.21.10 (69), and 26.1 (84).
  These numbers were independently verified from official Minecraft client
  jars, including source SHA-1 checks. Older verified values are unchanged.
- Unit tests cover all verified pack formats and preserve fallbacks when Kalo
  compiles without a running server.

### Pre-alpha limitations

- Kalo is **not stable**. Server operators should back up existing content
  packs and worlds and test first on a staging Paper/Folia server.
- Bedrock 3D **custom item** conversion and generic GUI item/animation/export
  improvements remain in **draft PRs**; they are **not** included in this build.
- Bedrock **virtual blocks are not rendered** by Geyser, and a separate
  standalone-Geyser deployment has not been verified end to end.
- Full real-client verification of the supported Minecraft version range,
  advanced models and third-party conversions remains unfinished.
- Mohist/Forge + required client-mod protocol bridging is not part of Kalo
  Core and is **not supported** by this release.

## 0.2.0

### Added

- **Export system** (`io.kalo.export`) — an API that writes a pack back out in five
  formats: a KaloPack archive for backup and sharing, portable canonical JSON, and
  Oraxen / ItemsAdder / Nexo YAML for moving content the other way. Every conversion
  reports exported / partial / unsupported by name rather than being silently lossy.
  Reachable from add-ons today; no command wraps it yet.
- **Built-in feature set** (`io.kalo.content.feature`) — abilities, skills, stats,
  particles, animations, cooldowns, sounds, models and mobs, registered through the
  same feature registry an add-on uses.
- Docs site restructured: landing page at the root, documentation under `/docs`,
  deployed to Cloudflare Pages with a realtime version badge.

### Removed

- **Vaporware commands and placeholder classes.** `/kalo benchmark` simulated work with
  `Thread.sleep` and then printed an invented performance comparison;
  `/kalo market browse|install` talked to a marketplace service that does not exist;
  `/kalo scan` and `/kalo migrate-live` counted YAML files as "content", flagged every
  renamed vanilla item as custom, and did all of it off the main thread. The classes
  behind them (`TextureGenerator`, `ContentMarketplace`, `MultiServerSync`,
  `PerformanceOptimizer`, `LiveServerScanner`) were placeholders labelled as such in
  their own javadoc. They are gone. The honest paths remain: `/kalo import` for config
  conversion, `/kalo doctor` for diagnosis, and the `performanceBenchmark` Gradle task
  for real numbers.
- Build artifacts that had been committed by mistake: `.idea/`, `.serena/`,
  `.wrangler/`, a stray `org/bukkit/attribute/Attribute.class` at the repository root,
  and a `gradle.properties.bak-*`. All are ignored now.

## 0.1.0

### Fixed

- **Custom blocks never reached Bedrock at all.** The Geyser bridge waited on a
  registration snapshot that production code never published, so it timed out after 30
  seconds on every startup and registered nothing. Items and the resource pack were
  unaffected, which is what made the path look like it worked.
- **Bedrock blocks were named inconsistently.** Blocks registered under the content pack's
  namespace through the API, but a mapping file can only produce Geyser's `geyser_custom`
  namespace. The generated pack is keyed by one of them, so the other rendered untextured.
  Both paths now agree.
- **Blocks past the note block carrier were mapped to the wrong Java state.** The carrier
  was dropped on the way to the Bedrock compiler, so anything on tripwire or scaffolding
  was named as a note block.
- **`/kalo migrate-world` reported "nothing to migrate" when it could not look.** On Folia
  every cross-region block read threw, the error was swallowed per world, and the count
  stayed zero. It now scans through the region scheduler, off the server thread, and
  reports unreachable chunks as unreachable.
- **Virtual blocks placed just before shutdown could be lost.** A debounced save and the
  shutdown flush shared no lock, so an older snapshot could land on top of a newer one.
- `PackFormats` returned a pack_format for 1.21.5 through 1.21.11 as if it had been
  verified. It had not; it now says when it is guessing.
- **The PlaceholderAPI expansion never registered on any server that had PlaceholderAPI.**
  A Paper plugin's classloader is isolated, so the hook's supertype could not resolve and
  the guarded call failed with `NoClassDefFoundError` every time. It now declares the
  classpath edge, the way the Geyser dependency already did.

### Verified

- **A Bedrock player confirmed it.** On a live Paper server running Geyser 2.11.2 and
  Floodgate: custom item icons render, native blocks place and mine and drop, worn armor
  shows on the player. Geyser also confirms the handshake from its own side rather than
  taking Kalo's word for it, and both `custom_mappings` files are written.
- Getting there took five release candidates. Everything each one fixed was green in CI
  beforehand — including an assertion that pinned the wrong armor geometry, and a rule that
  was correct, tested, and called by nothing.

### Fixed after a Bedrock player tested it

- **Custom armor equipped invisibly.** The attachable named
  `geometry.player_armor.helmet`, which Bedrock does not define, while already hiding the
  base material's armor underneath — so the piece drew nothing at all. All four slots were
  wrong the same way, and the test asserted the broken string.
- **Every custom block showed as a note block in the hotbar.** Blocks had no Bedrock item
  definition, so they fell back to their Java base material, which is always `NOTE_BLOCK`.
- **Kalo broke vanilla physics for every note block, tripwire and scaffolding on the
  server.** `onPhysics` cancelled on the carrier *material* rather than on Kalo content, so
  merely installing the plugin stopped vanilla scaffolding collapsing when unsupported and
  stopped tripwire updating — for blocks Kalo had nothing to do with.
- **Native custom blocks could not be broken by anyone.** `onInteract` cancelled the whole
  `PlayerInteractEvent` to stop note-block tuning, which also swallowed
  `LEFT_CLICK_BLOCK` — where mining starts. That is the standard trick for making a block
  indestructible, applied by accident to every native block. `JavaBlockRules.preventsTuning`
  had encoded the right rule, tests and all, and nothing in production called it.
- **The Java resource pack declared the wrong `pack_format` on 1.21.11.** It sent 46, the
  1.21.4 number, because nothing between the ends of the supported range had been checked.
  The real value is 75.

### Known limitations

- **Virtual blocks do not render on Bedrock and cannot.** A virtual block is an
  `ItemDisplay` entity, which Geyser does not translate to a Bedrock client, and it
  overrides no Java block state so it cannot be mapped either. Native blocks are
  unaffected. On a server with Bedrock players, the 893 native states are the real ceiling.
- `pack_format` is verified for 1.21.4 (46), 1.21.11 (75) and 26.2 (88), each read out of
  that version's own client jar. Any other version gets the nearest number in its family
  plus a console warning saying it is a guess. A wrong `pack_format` can make the client
  reject the pack outright, so treat that warning as work to do, not noise.

### Changed

- **The `geyser-extension` jar is gone.** Geyser reads a `custom_mappings` folder natively,
  so Kalo writes Geyser's own format to `plugins/Kalo/geyser/` instead of shipping a second
  artifact to run inside Geyser. A standalone Geyser now needs two files copied and nothing
  installed. Releases carry one jar.
- Blocks default to `native` mode in the documentation as well as in code; `virtual` is the
  answer to exhausting the 893 carrier states, not the starting point.
- The generated pack is no longer reopened and reparsed on every Bedrock connection.

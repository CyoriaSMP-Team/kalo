# Changelog

Kalo is distributed through multiple channels, each of which wants its own release notes.
This file is the one place they are written; the release workflow reads the section for
the version being tagged and hands it to every channel.

Versions follow `plugin_version` in `gradle.properties`, and a tag must match it.

## 0.2.1-prealpha.16

### Added

- Blockbench `.bbmodel` format 3.2–5.0 cube import through a shared, platform-neutral
  model IR. The importer preserves cube bounds, per-face UVs, pivots, element rotations,
  visibility/export flags, texture references, outliner hierarchy, animation keyframes
  and display transforms. Malformed JSON, duplicate ids, invalid dimensions, unsafe
  paths, oversized inputs, box-UV models and outliner cycles are rejected.
- Java model compilation from the IR: flattened zero-rotation groups, single-axis element
  rotation, face UVs, texture size and display transforms. Rotated bones, inflation and
  multi-axis element rotation are refused rather than baked incorrectly, and a Blockbench
  item that fails Java compilation is skipped on Bedrock too.
- Bedrock geometry compilation from the same IR, preserving outliner hierarchy as real
  parented bones with pivots and rotations, plus per-face UVs, UV rotation, inflation and
  multiple material instances. Blockbench imports no longer pass through a flattened Java
  model first.
- Blockbench keyframes compile to Bedrock `format_version` 1.8.0 animation JSON for held
  items. Loop animations are auto-started from the attachable's `scripts.animate`; loop,
  hold and once modes are preserved.
- Blockbench 3D items and block items get a rendered 64×64 isometric inventory preview;
  the model's own material textures stay separate from that flat icon.
- Bound 3D items receive generated first/third-person hold animations from authored
  Blockbench/Java display transforms, with a half-scale fallback. This replaces the vanilla
  player-skeleton hold animations that left custom bound models at the wrong scale and
  orientation.
- `BedrockSupport` seam with a no-op `NONE` implementation; Geyser-backed support is
  constructed only after Geyser-Spigot is confirmed present, so Java-only startup does not
  resolve Geyser classes.
- Private-use-area glyphs now get Bedrock `font/glyph_XX.png` pages, so chat/item/menu
  icons that work on Java are not missing for Bedrock players.
- Placed-model runtime for Blockbench items: `place:` creates a chunk-saved Java
  `ItemDisplay` plus `Interaction` hitbox, writes Bedrock client-entity/geometry/animation
  files, registers the entity with Geyser, and spawns/removes/animates it for nearby
  Bedrock sessions. The Geyser session/packet calls are reflective and fail closed if a
  Geyser build moves them.
- Placed-model storage and state cycling: `place.storage` serializes container contents
  onto the hitbox and survives restart; `place.states` cycles scale/light/solid/model/sound.
  State models compile their own Bedrock client entity; state index and light/barrier are
  restored on chunk load.
- Placed-model Java animation: a placed Blockbench item with authored loop animations now
  gets one `ItemDisplay` per bone, sampled with `ModelAnimations` and rotated/scaled around
  the authored pivots; the rig is rebuilt on chunk load and removed with the hitbox.
- Block/furniture interaction animation: `animation: {idle, interact}` selects the idle
  loop and a one-shot right-click clip. Java plays it once through the per-bone rig and
  resumes idle; Bedrock receives an `AnimateEntityPacket` for the animated client entity.
- Parent-bone motion propagation for Java display rigs: sampled bone rotations, positions and
  scales are composed through the parent chain before becoming display transforms, and the
  same composition is used by blocks, placed models, mobs and worn armor.
- Mob/armor interaction animation: `animation: {idle, interact}` selects the idle loop and
  a one-shot clip. Right-clicking a custom mob (or right-clicking empty-handed while wearing
  3D armor) plays it on Java and sends the matching Bedrock trigger; idle resumes after.
- Per-bone Java animation for custom mobs and 3D worn armor: both compile one `ItemDisplay`
  model per bone and sample authored loop animations with `ModelAnimations`; the rigs follow
  the mob/player and use the entity-space half-block correction.
- Placed-model interaction animation: right-click plays the configured one-shot clip once
  on Java, holds the final sampled frame, then resumes the idle loops; Bedrock receives the
  matching animation trigger. Names resolve by authored or sanitized spelling.
- Multi-performer emotes: `performers:` defines cast slots with offsets, and
  `/kalo emote <emote> <names>` validates and seats each member around the lead. Every
  performer gets an independent Java rig and Bedrock client entity.
- Per-player emote skins: a PNG cached at `plugins/Kalo/skins/<uuid>.png` is validated and
  overlaid into that XUID's staged Bedrock pack; the session digest includes the overlay,
  so changing a skin rebuilds only that session.
- Automatic skin resolution: when no cached PNG exists, Kalo reads the player's signed
  `textures` profile property, fetches the skin from an allowlisted host, caps the response
  at 1 MiB, validates the PNG signature and writes the cache atomically. The cached PNG
  records its source URL, so a changed skin is fetched again instead of serving stale
  bytes. At Bedrock login,
  the cached skin is applied to every body-emote texture path, so the first discovery is
  used from the next pack negotiation. Fetching is disabled with
  `emotes.skin-fetch: false`; the allowlist and timeout live under `emotes`.
- Player-body emotes: `rig: player` and `skin:` compile a Blockbench player rig into
  per-bone Java displays and a Bedrock client entity/attachable with the baked skin. The
  vanilla body is hidden while the emote plays and restored on stop. Java body rigs now
  reference a unique `emote/<name>_skin` texture, and a small per-player resource-pack
  overlay replaces that path with the player's cached/fetched skin. The optional overlay is
  stacked on top of the global pack under a stable per-player pack id; Relay and no-host
  modes keep the authored skin.
- Bezier keyframe interpolation: Blockbench per-axis `bezier_*_time`/`bezier_*_value`
  handles are parsed into the model IR. Java solves the cubic curve for the requested
  time; Bedrock has no cubic keyframes, so each segment is baked into intermediate linear
  keys with an INFO diagnostic. Step keys switch exactly at their key time.
- Catmull-Rom keyframes: Java samples the uniform spline with the immediate neighbouring
  keys, matching the generated Bedrock `lerp_mode: catmullrom`; endpoints duplicate the
  nearest key. Runtime and generated-pack tests cover the neighbour-dependent curve.
- Full controller Molang: state variables with linear remap curves, arithmetic, ternaries,
  comparisons, `math.*` functions and animation blend weights are evaluated on Java and
  written unchanged for Bedrock. Blended Java poses use component-wise linear weights.
- Liquids: `type: liquid` defines water/lava rules (color, potion effect, damage,
  fireproof). Volumes are saved to `plugins/Kalo/liquids.json`; `/kalo liquid corner` +
  `fill` mark one, and a `liquid:` bucket item places a source block. Empty-bucket pickup
  removes and trims a volume and returns the custom bucket; fluid flow expands/trims saved
  volumes. Sponge absorption trims the absorbed region, and placing, breaking or a physics
  update inside a marked volume trims that cell; breaking a bank outside the volume leaves
  the pool marked and flow expands it. Handler priority, trim idempotence and
  order-independence are covered by `LiquidRuntimeTest`. Colored liquids get one biome in an owned
  `datapacks/kalo-liquids` pack, and contact is checked once a second.
- Clean-room access history: a read-only session-store query showed the original
  development session referenced 89 RP Engine Java files and had a full 747-file source tree
  plus a 205-file docs site on disk. Those artifacts are now quarantined with tree digests.
  Re-audits against the actual tree, the 66 docs Java code blocks and docs prose report
  0.062, 0.016 and 0.002 top similarity, with no pair at 0.15. See
  `docs/clean-room-access-history-2026-11-19.md`.
- Clean-room full-tree audit: RP Engine 0.2.90 (commit `60be18c`) was cloned into the
  root-only quarantine and compared against 370 Kalo main sources, 137 test sources and 5
  text resources. Top pairs: main code 0.062, main comments 0.030, test code 0.052, test
  comments 0.074, resources 0.102 — no pair at the 0.15 review threshold. A distribution
  guard (`scripts/clean-room-guard.py`) now runs in CI and before release, failing any jar
  with RP Engine packages, embedded GPL text or a non-`io.kalo` main class. Full MIT remains
  the project direction; legal certification is still an external review item.
- Clean-room rewrite: fresh-context implementers rewrote the reviewed comments/javadoc and
  replaced the NMS camera transport from a behaviour contract without reading the prior
  implementation. The executable code in the comment-only files was token-verified
  unchanged; targeted tests pass and the provenance audit still reports 0 high-risk pairs.
- Clean-room provenance: six RP Engine reference files found under `/tmp` are quarantined
  outside the repository. A reproducible audit reports 2,220 pairs and no high-risk literal
  copy; two independent reviewers, denied access to the reference files, found the top API
  and geometry pairs independently written from public specs and required no rewrite.
  Remaining comment-level, access-history and legal checks are recorded in
  `docs/CLEAN_ROOM.md`.
- Live Bedrock check: a separate Canvas 1.21.11 + Geyser 2.12.1 server and a
  Bedrock 1.26.60 protocol client confirmed Kalo's staged pack identity/size, VFX sprite
  entities, the camera-sprite entity and the model entity. The check also fixed three
  startup regressions: duplicate `kalo vehicle`/`kalo spawn` command chains, colored-liquid
  biome JSON missing fields required by 1.21.11, and `setBiome` being called from the
  server thread during enable on Folia/Canvas. Java camera packets are not translated by
  Geyser. A follow-up mob-free void run isolated acid contact damage as five-point health
  drops (`damage: 5.0`), with natural regeneration between ticks.
- VFX: `type: vfx` loads one of ten named presets, an inline Studio-style recipe or a
  JSON recipe file. Motes layers are cleaned and capped, colored sprite textures and item
  models are written into the generated Java pack, and a deterministic simulation drives
  non-persistent item displays. Recipe sound events play on the timeline; loop casting
  supports build-up/repeat/fade, `loop:<n>x`, `loop:<time>`, graceful stop, tags and
  `end:cut`. Cast options add speed, scale, follow, offset, turn, delay, seed,
  `only:caster`, and `until`/`while`/`check` conditions. Unknown condition names fall back
  to PlaceholderAPI while built-ins keep priority. `camera` layers anchor sprite bursts
  to the caster's eyes and view direction, and `model` layers place a real item model for
  their time window with scale, rotation and spin. The `vfx.max-sprites` planned-sprite
  budget stops the oldest casts first. Bedrock gets generated client-entity geometry,
  entity definitions and textures; spawn/move/remove events are sent through Geyser, with
  `only:caster` filtering the Bedrock viewer and the cast `scale` delivered as `SCALE`
  entity metadata so Bedrock matches Java's display scaling. Camera layers attach the Java
  client camera through a reflective `ClientboundSetCameraPacket` seam with a no-op
  fallback; `fov` is a dolly approximation because vanilla Java has no FOV packet. Model
  layers compile to Bedrock client entities from sprite, custom Java and Blockbench item
  models and spawn/move/remove through the same VFX rig seam; a model layer's `roll` is
  baked into a wrapper bone so Bedrock matches Java's Z rotation. `/kalo vfx <name>` casts
  one; `/kalo vfx stop` ends it.
- Dialogs: `type: dialog` builds a Java multi-action dialog and writes it to an owned
  `datapacks/kalo-dialogs` pack; `/kalo dialog <name>` opens it for a player and
  `/kalo dialog open <player> <name>` targets someone else. Buttons support player or
  console commands (single or up to 16 per button), HTTPS URLs, copy, suggest, nested
  dialogs and close. Commands are sent as authorized `/kalo click <key>` routes, not in
  the client payload. Tooltips, widths, `can-close-with-escape`, `after` modes and raw
  Java dialog JSON (`json:` + `console-commands`) are supported. Titles, bodies, labels
  and tooltips resolve `{player}`/`{target}` at open time through an inline dialog command;
  the generated data pack remains the fallback. The same definition becomes a Cumulus
  Bedrock form when Floodgate/Geyser shares the server.
- HUD overlays: `type: hud` binds an image to a Private Use Area glyph, compiles a Java
  font provider and a Bedrock glyph page, and `/kalo hud <name>` draws it on the action
  bar or boss bar. `offset` adds a positive or negative pixel shift through a Java space
  provider and a scaled shift inside the Bedrock glyph cell. Boss-bar HUDs persist until
  `/kalo hud stop`; action-bar HUDs refresh once a second. The HUD key can also be
  permission-gated through `session-content`.
- Extended Molang statements: assignments and mutable structs, single-quoted strings and
  equality, `loop`/`for_each` with `break`/`continue`, `??`, the `->` entity operator
  through `EntityResolver`, and multi-statement expressions with `return`. Controller JSON
  preserves the full expressions, including loop/struct bodies.
- Arrays in Molang: array literals and environment arrays support Molang indexing
  (C-style cast, negative clamp, large-index wrap), and `query.count` counts array elements.
  `math.random`/`math.random_integer` use a caller-supplied or expression-seeded
  deterministic source. External `query.*` functions, including argument-aware ones, use a
  `QueryResolver`; controller JSON preserves every expression unchanged.
- Blockbench animation controllers: `animation_controllers` from a project compile into
  Bedrock controller JSON and auto-start on the entity/attachable. Java runs a state machine
  over the same transitions and selects the active state's clips through the per-bone rigs;
  conditions support query flags, `!`, `&&`, `||`, parentheses and numeric comparisons.
- Vehicle medium handling: `medium: land|water|air` with configurable buoyancy, gravity,
  climb-speed and collision. Water vehicles rise at the surface, air vehicles climb while
  jump is held and otherwise descend, and land vehicles stop at solid blocks instead of
  driving through them.
- Vehicles: `type: vehicle` spawns an invisible real carrier (armour stand by default),
  one marker stand per declared seat, a Java display and a Bedrock client entity. Seat zero
  drives through Paper 1.21.4 `PlayerInput` (W/S throttle, A/D turn, sneak dismount);
  right-click fills the next free seat. `seats: 3` makes a centred row and a YAML seat list
  sets each offset explicitly. Medium handling is basic; real client verification remains.
- Emote props: `type: emote` plus `/kalo emote <name>` spawns a Blockbench prop that follows
  the player for its duration (or until sneaking), with a Bedrock client entity and Java
  display. This is a prop emote, not a re-skinned player body.
- Custom mobs: `type: mob` loads a real base `LivingEntity`, applies name/health and
  compiles the Blockbench model to Bedrock geometry/entity/animation plus a Geyser client
  entity. Java hides the base body and follows it with a display rig; Bedrock receives
  spawn/move/remove packets. `/kalo spawn <mob>` creates one.
- Worn 3D armor: `armor_model:` compiles a Blockbench player-frame model to a Java model
  and a Bedrock attachable bound to the vanilla armor bones, hiding the flat equipment
  layer on both platforms. Java follows the wearer with a display rig.
- Block/furniture animation runtime: an animated Blockbench block or furniture model is
  automatically placed through virtual display entities (unless `java.mode: native` is
  explicit). Java renders one `ItemDisplay` per bone and samples authored loop keyframes at
  two-tick intervals; Bedrock gets animation/entity JSON, a client-entity registration and a
  transparent terrain texture so the static block does not double-draw. The block surface
  is restored for explicitly native blocks. Older native placements are not migrated;
  break and replace them once after upgrading.
- Per-session Bedrock packs: each XUID is served its own deterministic
  `bedrock-packs/<xuid>.mcpack` containing a `kalo-session.json` marker with the source
  SHA-256. Staging is idempotent, rebuilds only when `generated.mcpack` changes, and after
  a rebuild online sessions holding the old revision are transferred through their join
  address so the next login reads the new copy. `bedrock-packs.per-player` and
  `bedrock-packs.transfer` control both behaviours.
- Session-scoped Bedrock contents: the compiler writes `kalo-content.json`, mapping every
  generated asset to its owning content key. `bedrock-packs.session-content` can then give
  an XUID only selected namespaces or full keys; unlisted sessions still receive
  everything. Filtering is applied at staging and included in the session digest, so two
  sessions cannot receive each other's filtered model, animation or texture files.
  Shared atlases and manifests remain common.
- Gameplay entitlements: `session-content.permissions` maps a content key (or namespace
  wildcard) to a permission node. The node is checked when staging that session's pack and
  by Java gates for give, pickup, use, placement, crafting, vehicle/mob spawning and
  emotes. The permission outcome is part of the session digest; existing placed/spawned
  state is not retroactively removed.
- `model.blockbench` YAML support for items, blocks and furniture, with optional texture
  overrides keyed by Blockbench texture id, name, UUID or path.
- Content-addressed `ModelRegistry` cache (SHA-256 fingerprint) shared by both compilers;
  changed model bytes invalidate on the next generation without a restart.
- Classloader-safe Bedrock Forms guard: Floodgate/Geyser are detected before the Cumulus
  bridge class is loaded, so Java-only and Velocity-proxy backends keep working.
- DeluxeMenus Forms bridge: with Geyser present, DeluxeMenus inventory opens can be
  rendered as Cumulus forms instead of Java chest menus.
- GUI head icons: custom skull textures from DeluxeMenus menus are scanned and compiled
  into Bedrock form images, sharing one resolver with the Java menu path.
- Tests for the importer, both compilers, registry cache/path safety, block/item pipeline
  integration, YAML parsing and optional-form provider selection.

### Changed

- Align the site version metadata with the actual pre-alpha build version.
- Correct Bedrock deployment guidance for Geyser on Velocity or a standalone process,
  including the file-export path and the fact that no proxy JVM API is called.
- Document the exact static Java-model and Blockbench conversion limits.
- Clarify that the Paper API baseline is 1.21.4 and that the 26.2 run-server target is not
  the minimum supported API version.

### Fixed

- Bedrock cube and bone rest rotations now follow Blockbench's Java-to-Bedrock rule:
  X and Y are negated and Z keeps its authored sign. The previous Y sign was wrong for
  rotated geometry.
- A player-skin fetch removed its in-flight marker before invoking the completion
  callback, so a changed profile URL requested from that callback could fetch again
  instead of being silently dropped and timing out.

### Limitations

- Placed-model parity is a first slice: Java has a chunk-saved display entity and hitbox,
  Bedrock has a spawned client entity and authored animations. Per-bone Java animation and
  hitbox-per-bone remain unimplemented; seats, storage and light/solid/model state cycling
  are implemented. Reflection against Geyser's internal session/packet API is guarded but
  requires real-client verification.
- Held Blockbench item animations are wired to Bedrock attachables; animated
  blocks/furniture and placed models play loop animations on both platforms, and both play
  a configured one-shot interaction clip, including mobs and worn armor. Provider-specific
  Molang query catalogs and entity references remain host supplied as described above.
- Box-UV models, mesh elements, texture meshes and layered/emissive texture modes remain
  unsupported; variables, remaps, arithmetic, ternaries, comparisons, arrays, structs,
  strings, loops, deterministic random, external queries, the `->` operator, `??`,
  multi-statement expressions, common `math.*` functions and blend weights are evaluated
  on Java.
- Blockbench imports are unit-tested against synthetic fixtures but not a corpus of real
  projects, and static/animated Bedrock rendering still requires real-client verification.
- The 1.21.4 end of the supported range is compiled against but remains runtime-untested.

## 0.2.1-prealpha.7

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

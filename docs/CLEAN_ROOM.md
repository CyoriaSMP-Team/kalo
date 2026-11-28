# Clean-room implementation policy

Kalo is MIT and derives from Neko (MIT, Woobeen Jeon). [RP Engine](https://github.com/
resourcepack-ai/rp-engine) is GPL-3.0-or-later and is used only as an external behavioural
reference. No RP Engine source file is part of this repository. RP Engine reference
material — six individual files plus a full source tree, its docs site and repository
metadata — was found under `/tmp` during development and quarantined; a reproducible audit
found no high-risk literal copy, but a strict clean-room wall was not maintained. The
audit, the access-history reconstruction and their limits are in
[`clean-room-audit.md`](clean-room-audit.md) and
[`clean-room-access-history-2026-11-19.md`](clean-room-access-history-2026-11-19.md).

## What clean-room means here

1. **Public behaviour and format documentation are the specification.** Blockbench's
   published `.bbmodel` notes, Bedrock geometry/item/attachable formats, Geyser's public
   API, and RP Engine's public README/FORMAT/API descriptions are read for behaviour.
2. **Implementation is written against Kalo's own architecture.** Kalo's `ModelIR`,
   `ModelRegistry`, Java model compiler and Bedrock compiler were designed around Kalo's
   existing definition layer and test fixtures.
3. **No source-to-source port.** Algorithms are expressed from the format rules (centre
   X/Z, mirror X, X/Y rotation signs, 16×16 UV space, Bedrock glyph pages) and verified by
   Kalo's own tests. Where another project has a useful idea, the idea is reimplemented,
   not pasted. The 2026-11-19 provenance audit found no high-risk literal copy in the six
   known reference files, but the reference files were present during development; see
   [`clean-room-audit.md`](clean-room-audit.md) for the caveat and top review pairs.
4. **Fixtures are synthetic or authored for Kalo.** The `.bbmodel` fixtures in
   `core/src/test/resources/blockbench/` were written for this repository; they do not
   contain RP Engine assets.
5. **A GPL dependency is not linked into the Kalo jar.** Geyser's public API is a
   compile-only optional dependency; RP Engine is not a dependency at all.

## Parity implemented

| Capability | RP Engine reference | Kalo implementation | Evidence |
|---|---|---|---|
| `.bbmodel` project import | Source-folder models and Blockbench `.bbmodel` | `BlockbenchImporter`, shared `ModelIR` | `BlockbenchImporterTest` |
| Outliner/bone hierarchy | Bedrock bones and pivots | `BedrockModelCompiler` keeps parented bones | `BedrockModelCompilerTest` |
| Blockbench animation keys | Bedrock animation JSON | `BedrockModelCompiler` + `BedrockHoldAnimations` | `BedrockModelCompilerTest`, `BlockbenchPipelineTest` |
| Held-item animation wiring | Attachable animation table | Attachable `animations` + `scripts.animate` | `BlockbenchPipelineTest` |
| 3D item inventory icon | Rendered model icon | `BedrockModelIcon` (64×64 isometric) | `BedrockModelIconTest` |
| Hold placement | Custom attachable holds | Display-transform-derived holds with fallback | `BedrockPackCompilerTest` |
| Texture atlas / multiple materials | Baked model texture | Per-material terrain/item atlases | `BedrockPackCompilerTest` |
| Bedrock glyph pages | `font/glyph_XX.png` | `BedrockGlyphPages` | `BedrockGlyphPagesTest` |
| Placed model | Java display rig + Bedrock client entity | `PlacementDefinition`, `PlacedModelListener`, client-entity JSON + registration + reflective spawn | `BlockbenchPipelineTest`, `BedrockRegistrationSnapshotTest` |
| Block/furniture animation | Authored loop animations on both platforms | `ModelAnimations` sampler, per-bone Java `ItemDisplay` rig, Bedrock animated client entity + transparent block surface | `ModelAnimationsTest`, `BlockbenchRigTransformsTest`, `BlockbenchPipelineTest`, `GeyserPlacedEntityRegistrationTest` |
| Animation controllers | Bedrock `animation_controllers` state machines | `BlockbenchImporter`, `BedrockModelCompiler`, `ControllerRuntime`, `RigControllers`, auto-start wiring | `BlockbenchImporterTest`, `ControllerRuntimeTest`, `BlockbenchPipelineTest` |
| Custom mobs | Real mob wearing a rig | `MobType`, `MobListener`, Bedrock entity registration/spawn/move, Java per-bone display rig | `MobConfigTest`, `BlockbenchPipelineTest`, `BedrockRegistrationSnapshotTest`, `GeyserPlacedEntityRegistrationTest` |
| Worn 3D armor | Blockbench armor models | `ArmorDefinition.ArmorModel`, `JavaWornModelCompiler`, Bedrock attachable with authored geometry, transparent flat layer | `JavaArmorCompilerTest`, `BlockbenchPipelineTest` |
| Vehicles | Rideable multi-seat model with medium physics | `VehicleType`, `VehicleListener` (real carrier + per-seat marker stands + `PlayerInput`), `VehicleSeats`, `VehicleMotion`, Bedrock rig entity | `VehicleConfigTest`, `VehicleSeatsTest`, `VehicleMotionTest`, `BlockbenchPipelineTest`, `BedrockRegistrationSnapshotTest`, `GeyserPlacedEntityRegistrationTest` |
| Emotes | Playable model around a player | `EmoteType`, `EmoteListener` prop display, Bedrock rig entity | `EmoteConfigTest`, `BlockbenchPipelineTest` |
| Per-session pack delivery | `bedrock-packs/<xuid>.mcpack` + transfer/reconnect, optionally different contents per player | `BedrockPackStager`, generated `kalo-content.json` ownership manifest, `SessionContentPolicy` namespace/key allowlist, digest-marked serving, reconnect on changed revision | `BedrockPackStagerTest`, `SessionContentPolicyTest`, `GeyserPackTransferTest`, `BlockbenchPipelineTest` |
| HUD overlays | Screens and HUDs | `HudType` compiles a bitmap HUD into a Java font provider and a Bedrock glyph page; `HudRuntime` draws it on the action bar or boss bar; `offset` uses a Java space provider and a Bedrock page-cell shift | `HudConfigTest`, `HudPackTest`, `HudRuntimeTest` |
| Dialogs | Dialogs | `DialogType` parses YAML and raw Java JSON; `DialogPack` writes an owned Java data pack and renders Bedrock form JSON; `DialogRuntime` authorizes click keys, resolves open-time text and command placeholders, enforces after/escape modes and routes Bedrock players through `KaloDialogFormBridge` | `DialogConfigTest`, `DialogPackTest`, `DialogTextResolverTest`, `DialogRuntimeTest` |
| VFX | VFX | `VfxType` parses presets, inline recipes, Studio-style JSON and sound events; `VfxSpritePainter` writes colored Java textures/models for world and camera layers; `VfxPlayback` handles speed, time/cycle loops and build-up/repeat/fade; `VfxCondition` evaluates `until`/`while`; `VfxRuntime` drives follow/offset/turn/delay/only-caster casts, view-anchored camera sprites, packet-based camera control through a reflective no-op-fallback seam, Java model displays plus compiled Bedrock model client entities, Bedrock client entities with cast scale and the sprite budget | `VfxConfigTest`, `VfxSimulationTest`, `VfxLayerTypesTest`, `VfxCameraPlanTest`, `VfxPackTest`, `VfxPlaybackTest`, `VfxCastOptionsTest`, `VfxConditionTest`, `VfxPlacementTest`, `VfxSpriteBudgetTest`, `BedrockVfxCompilerTest`, `BedrockPackCompilerTest`, `VfxBedrockDeliveryTest`, `VfxCameraDeliveryTest` |
| Liquids | Liquids | `LiquidType` parses rules; `LiquidStore` persists ordered boxes and splits them for box/scattered removal; `LiquidBiomePack` writes colored biomes; `LiquidRuntime` handles bucket placement, flow, sponge/place/break/physics trimming with MONITOR-order and idempotence tests, contact effects and biome application | `LiquidConfigTest`, `LiquidStoreTest`, `LiquidBiomePackTest`, `LiquidRuntimeTest` |
| Native Bedrock custom blocks | Not present in the inspected Bedrock path | Geyser `CustomBlockData`, `blocks.json`, terrain atlas | `BedrockPackCompilerTest` |
| Standalone/proxy export | Same-JVM Geyser bridge | `GeyserStandaloneSync` + file copy | `GeyserStandaloneSyncTest` |
| Optional Geyser absence | `BedrockSupport.NONE` seam | `BedrockSupport` + guarded `GeyserBedrockSupport` | `BedrockSupportTest` |

## Live verification

A separate Canvas 1.21.11 + Geyser-Spigot 2.12.1 + Floodgate + Via test server was run
with a `bedrock-protocol` client (Bedrock 1.26.60). The full method and observations are in
[`live-check-2026-11-19.md`](live-check-2026-11-19.md). Summary:

- The Bedrock client was offered Kalo's pack (`content_identity`
  `81909e58-8b86-32f0-bcee-7d447bd4a7e1`, size 11575); the staged file matches that
  identity, size and manifest UUID.
- `/kalo vfx livecheck:spark` produced `kalo:vfx_..._vfx_0` sprite entities, a
  `..._vfx_camera_0` camera-sprite entity and a `..._vfx_model_0` model entity on the
  Bedrock client. The model pack contains the baked 30-degree roll wrapper.
- No Bedrock camera packet was observed; Geyser does not translate the Java packet camera.
- `/kalo liquid corner` and `/kalo liquid fill livecheck:acid` marked and persisted a
  volume. In a mob-free void world with `damage: 5.0`, the Bedrock player's health fell in
  five-point steps after the fill (20→15, then 13, 11, 9, 7), isolating the contact tick;
  natural regeneration raised health between ticks.
- The check found and fixed three startup regressions: duplicate `vehicle`/`spawn` command
  chains, incomplete biome JSON required by 1.21.11, and Folia/Canvas `setBiome` being
  called from the server thread during enable.

This is protocol-level delivery evidence, not a rendered-client visual check.

## Known gaps and partial parity

These are real gaps, not silently claimed as done:

- **Placed-model parity is partial**: Java has a chunk-saved display entity and hitbox;
  Bedrock has client-entity assets, registration and reflective spawn/remove/animation.
  Seats and storage work as temporary/persistent markers; light/solid/model states are
  implemented. Placed models, blocks and furniture all use the per-bone Java rig for
  authored loop animations, and a placed model plays its configured interaction clip once
  before the idle loop resumes.
- **Session-scoped content**: Kalo filters content-owned files per XUID through a
  namespace/key allowlist and an optional content-key → permission-node map, backed by a
  generated ownership manifest. The same policy gates server-side give, pickup, use,
  placement, crafting, vehicle/mob spawning and emotes. Existing placed blocks/entities
  and shared atlas entries are not retroactively removed, and Geyser registration itself
  remains global.
- **HUD**: image-backed overlays compile into a Java font glyph and a Bedrock glyph page;
  `/kalo hud` draws them on the action bar or boss bar. Horizontal offsets use a Java
  space provider and a scaled Bedrock page-cell shift; offsets beyond a cell are clipped,
  and large artwork is scaled into a Bedrock glyph cell.
- **Dialogs**: `type: dialog` writes an owned Java data pack
  (`datapacks/kalo-dialogs`), `/kalo dialog` opens it on Java 1.21.6+, and the same
  definition becomes a Cumulus form when a Bedrock forms transport is available. Buttons
  support player/console commands, multi-command lists, HTTPS URLs, copy, suggest, nested
  dialogs and close, behind authorized click keys. Tooltips, widths, escape and after
  modes are implemented. Titles, bodies, labels and tooltips resolve `{player}`/`{target}`
  at open time through an inline dialog command; the generated data pack is the fallback.
  Rich Java input controls require raw `json:` and are passed through to Java only.
  Bedrock form delivery/live behavior is unverified.
- **VFX**: presets, inline recipes and Studio-style JSON are parsed into a cleaned recipe;
  Java writes colored sprite textures/models, and a deterministic simulation drives
  non-persistent item displays. Bedrock generates client-entity geometry/entity assets for
  each layer and the runtime sends spawn/move/remove events through Geyser. Sound events,
  loop build-up/repeat/fade, graceful stop, tags, speed/scale/follow/offset/turn/delay/seed/
  only-caster, loop:<time>, `until`/`while`/`check` conditions and the `vfx.max-sprites`
  planned-sprite budget are implemented. Bedrock client-entity geometry/entity/texture
  assets and spawn/move/remove delivery through Geyser are implemented. Conditions fall
  back to PlaceholderAPI for unknown names. `camera` layers anchor sprite bursts to the
  caster's eyes and view direction; `model` layers place a real item model with scale,
  rotation and spin. Active camera layers attach the Java client camera through a
  reflective `ClientboundSetCameraPacket` seam with a no-op fallback; `fov` is a dolly
  approximation because vanilla Java has no FOV packet, and the true value is carried in
  the frame. Camera sprites are ordinary world entities (so terrain can occlude them) and
  Bedrock players keep their normal camera. Model layers compile to Bedrock client
  entities from a Kalo item's sprite/custom/Blockbench model or a pack item-model key;
  vanilla models the pack does not carry are skipped, yaw/pitch/scale are delivered, and
  `roll` is baked into a wrapper bone in the Bedrock geometry. Bedrock VFX entities receive
  the cast `scale` as `SCALE` metadata, matching Java's display transformation; the
  geometry stays baked at the layer size. A protocol-level live Bedrock check observed the
  staged pack, sprite entities, camera-sprite entity and model entity; rendered pixels,
  sound playback and Java camera packets on Bedrock remain unverified. See
  `docs/live-check-2026-11-19.md`.
- **Liquids**: rules, persisted boxes, `liquid:` buckets, empty-bucket pickup/refill,
  flow-driven volume expansion/trimming, sponge absorption, deliberate place/break and
  physics-driven cell trimming, contact effects and the color-biome data pack are
  implemented. Sponge/place/break/physics handlers are pinned to MONITOR and their trim
  order, idempotence and fluid guards are unit-tested; live server event dispatch is not.
  Biome color applies per X/Z column, needs a restart, is Java-only, and unusual
  fluid-removal paths are event-driven rather than a full fluid simulation. A live Bedrock
  check confirmed volume creation/persistence and, in a mob-free void world, isolated the
  contact tick as five-point health drops. Live client rendering is unverified.
- **Vehicle depth**: sounds and damage response. Land/water/air motion, buoyancy, air
  climb/gravity and solid-block collision are implemented and unit-tested; multi-seat
  marker stands are unit-tested for positions, not live-tested with riders.
- **Emote depth**: looping stances remain. Multi-performer casts, player-body rigs, a
  per-player cached skin overlay and automatic skin-URL resolution from the signed
  profile (allowlisted hosts, bounded download, PNG validation) are implemented. The
  resolved skin is applied to every body-emote texture at the next Bedrock pack
  negotiation. Java body emotes use a unique per-emote texture path and receive a small
  per-player overlay pack, so two players' skins do not collide; Relay and no-host modes
  fall back to the authored skin.
- **Bedrock invisibility forwarding for custom mobs** is not verified.
- **Animation controller Molang breadth**: variables, remap curves, arithmetic,
  ternaries, comparisons, common `math.*` functions, arrays and indexing, `query.count`,
  deterministic `math.random`, argument-aware external query resolvers and blend weights
  run on Java. `loop`/`for_each`, structs, strings, the `->` operator via an
  `EntityResolver`, `??` and multi-statement expressions with `return` also run on Java.
  Conditional `break`/`continue` work inside loops. Provider-specific query catalogs and
  entity references remain host-supplied. Parent-bone motion is composed on Java.
- **Bezier interpolation** is implemented: Blockbench per-axis handles are parsed into the
  IR, Java samples the cubic curve, and Bedrock receives baked intermediate linear keys.
  **Catmull-Rom** is sampled on Java with the immediate neighbouring keys, matching the
  generated Bedrock `lerp_mode: catmullrom`; endpoints duplicate the nearest key.
- Protocol-level Bedrock delivery was checked live on Geyser/Canvas 1.21.11 (pack,
  sprite/model entities; see `docs/live-check-2026-11-19.md`). A rendered Bedrock client is
  not available here: the connected Android device has no Minecraft/Play Store package, the
  host has no Wine/mcpelauncher/Bedrock APK, and only a 1.21.11 server jar is present.
  Real rendered visuals, sound playback, a real Blockbench project corpus, and runtime
  testing on the 1.21.4 floor therefore remain unverified; the blocker and required user
  action are recorded in the live report.

## Provenance audit

On 2026-11-19, six RP Engine source files found under `/tmp` were moved to
`/tmp/opencode/clean-room-quarantine/reference/` and hashed. `scripts/clean-room-audit.py`
compared them with all 370 Kalo Java sources:

```text
pairs reported: 2220
high-risk pairs: 0
```

The highest review pair was `BedrockSupport`/`VfxCameraTransport` (2 meaningful lines,
13.3% meaningful overlap: the `NONE`/`available()` interface pattern); the rest were
Bedrock schema keys and public coordinate rules. This is low literal-copy risk, not a legal
clean-room certification: the same agent saw the reference files, so independent review was
still required. Full method and hashes are in
[`clean-room-audit.md`](clean-room-audit.md).

### Independent reviews (2026-11-19)

Two fresh-context reviewer sessions reviewed the audit's top pairs using only public
Bedrock/Blockbench/Geyser/Paper documentation, Kalo code and Kalo tests. The quarantined
reference directory was moved to root-only `/root/clean-room-quarantine/reference/`; the
reviewers could not read it and were instructed not to look for RP Engine source.

| Scope | Verdict | Report |
|---|---|---|
| `BedrockSupport`, `VfxCameraTransport`, `VfxCameraTransports`, `VfxCameraDelivery`, `NmsVfxCameraTransport`, tests | `BedrockSupport` and `VfxCameraTransport` are generic public API shape (`NONE`/`available()` null-object idiom); the factory, delivery helper, NMS transport and tests are independent. **No rewrite.** | [`clean-room-review-api-2026-11-19.md`](clean-room-review-api-2026-11-19.md) |
| `BedrockGeometry`, `BedrockModelCompiler`, `BedrockAttachable`, tests | Public format rules independently expressed/assembled; the shared formulas are Microsoft/Bedrock schema and public Blockbench-codec conventions. **No rewrite.** | [`clean-room-review-geometry-2026-11-19.md`](clean-room-review-geometry-2026-11-19.md) |

Targeted evidence from the reviewers:

```text
BedrockSupportTest        4 tests, 0 failures
VfxCameraDeliveryTest     2 tests, 0 failures
BedrockGeometryTest      13 tests, 0 failures
BedrockModelCompilerTest 10 tests, 0 failures
BlockbenchPipelineTest   17 tests, 0 failures
```

The audit was rerun against the quarantined reference as root: **2,220 pairs, 0 high-risk
pairs**. The reviews lower the technical copy risk; they are not a legal certification.

### Strict separated rewrite (2026-11-19)

Two further fresh-context implementers, denied access to the root-only quarantine, reduced
the remaining comment and non-literal risks without touching the reviewed logic:

- **Comments/javadoc** in `BedrockSupport`, `VfxCameraTransport`, `VfxCameraTransports`,
  `VfxCameraDelivery`, `BedrockGeometry`, `BedrockModelCompiler` and `BedrockAttachable`
  were rewritten from the code and public format rules. A comment-stripped token comparison
  confirms the executable code is unchanged.
- **`NmsVfxCameraTransport`** was replaced by a new implementation written from a behaviour
  contract; the implementer did not read the previous file. It uses a single
  `ClientboundSetCameraPacket` attachment per `Marker` anchor, caches the reflective
  constructor/connection/send route, and keeps the fail-closed `tryCreate()` contract.

Targeted tests: `VfxCameraDeliveryTest` 2/2 and `BedrockSupportTest` 4/4. The clean build
below passed 753 tests. The audit was rerun after the rewrite: **0 high-risk pairs**.

Reports:

```text
docs/clean-room-rewrite-comments-2026-11-19.md
docs/clean-room-rewrite-nms-2026-11-19.md
```

The remaining legal checks are no longer comment-level copy in these files, but
Blockbench's own GPL-3.0 status, the documented access history, non-literal design choices
outside the reviewed files, and an independent legal review with lawful reference access.
Those are legal/documentary questions, not another code pass.

### Full MIT decision and full-tree audit

The project direction is **Full MIT**: no GPL-derived expression may remain. The audit was
expanded beyond the six initial files to the full RP Engine checkout (`0.2.90`, commit
`60be18cdaf90bf37c4a7db88c6b5040a00a32fe2`): 673 reference Java files against 370 Kalo
main sources, 137 test sources, and 5 text resources. Code and comment shingles are scored
separately.

```text
main code        top 0.062   pairs >= 0.15: 0
main comments    top 0.030   pairs >= 0.15: 0
test code        top 0.052   pairs >= 0.15: 0
test comments    top 0.074   pairs >= 0.15: 0
resources        top 0.102   pairs >= 0.15: 0
```

Script: `scripts/clean-room-full-audit.py`; report:
`build/reports/clean-room-full-audit.txt`. No pair crossed the review threshold. The top
pairs are ordinary Java/record/enum vocabulary and standard event/feature shapes, not
copied implementation. The single resource pair (`studio-mob.json -> simple_cube.geo.json`,
0.102) is Bedrock geometry JSON: Kalo's golden uses only the 25 published schema keys
(`format_version`, `minecraft:geometry`, `bones`, `cubes`, `origin`, `uv_size`, …).

Tests and resources were the reviewer's "outside the audit" gap; they are now included.
`scripts/clean-room-guard.py` enforces the distribution side of Full MIT in CI: LICENSE is
MIT with the Neko notice, no RP Engine dependency, no `ai/resourcepack`/`rpengine` jar
entries, no embedded GPL text, `io.kalo` plugin main classes, and no GPL headers in the 507
Java sources. It is wired into `.github/workflows/build.yml` and `release.yml` before any
artifact is uploaded or published.

### Access-history reconstruction (2026-11-19)

The initial six-file scope was too narrow. A read-only query of the local session store
showed that the original development session (`ses_e12800d45ffekoo0347Yti3QH7`,
2026-11-18 15:00 → 2026-11-19 15:24) referenced **89 distinct RP Engine Java files** and
had on disk a full 747-file source tree (`/tmp/rpe-src`), a 205-file docs site
(`/tmp/rpe-docs`, including 66 Java code blocks), repository metadata and archives.

All of it was moved to the root-only
`/root/clean-room-quarantine/access-history/` with SHA-256 and tree digests. The audit was
rerun against the actual artifacts:

```text
rpe-src (earlier revision the agent saw)
  main code 0.062 / comments 0.030 / test code 0.052 / test comments 0.074
  every category: 0 pairs >= 0.15
rpe-docs Java code blocks (66)     top 0.016, 0 pairs >= 0.15
rpe-docs prose vs Kalo docs (111 vs 60)  top 0.002, 0 pairs >= 0.15
```

Access is not copying, and these numbers do not prove clean-room process. They do mean the
expanded audit now covers the material that was actually reachable, not just six files.
Full timeline, artifact hashes and limits:
[`clean-room-access-history-2026-11-19.md`](clean-room-access-history-2026-11-19.md).

### Legal review status (blocked)

A legal-grade clean-room certification cannot be issued by this agent. Full MIT now has
technical evidence covering the full RP Engine tree, but the legal questions remain:
comment-by-comment ownership, non-literal structure at the top pairs, Blockbench GPL-3.0,
the original agent's access history, and any reference beyond RP Engine. The prepared
handoff packet is [`clean-room-legal-review-packet.md`](clean-room-legal-review-packet.md).

Until that review is complete, Kalo is **not certified clean-room** and must not be
published as MIT-only if any GPL-derived expression remains.

## Verification

```bash
# The wrapper jar is absent in this checkout; use the configured/cached Gradle binary.
gradle clean build
```

Latest evidence:

```text
tests: 753 passed (after per-bone/interaction animation, full Molang controllers with arrays/random/queries/loops/structs/strings/->/??, Bezier and Catmull-Rom keyframes, HUD glyph overlays with horizontal offsets, dialogs with open-time text placeholders and authorized console/multi-command clicks, VFX presets/recipes with sounds, loop time/conditions, PlaceholderAPI-backed condition values, movement cast options, view-anchored camera sprites, packet-based camera control with a no-op fallback, model display layers with Bedrock model client entities and baked roll, Bedrock client entities with cast scale and a sprite budget, liquids with persisted volumes, pickup/flow trimming, order-tested sponge/bank/physics trimming and colored biomes, multi-performer emotes with cached/auto-fetched per-player skins, per-player Java overlays, permission-gated session content, parent-bone motion, and vehicles with medium physics)
failures: 0
```

The generated Java and Bedrock archives used by the integration test are written to
`core/build/reports/blockbench-pipeline/` and are not distributed artifacts.

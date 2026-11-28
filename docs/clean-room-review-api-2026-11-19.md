# Independent clean-room review — Bedrock and VFX camera seams

**Date:** 2026-11-19
**Scope:** the two files named by the 2026-11-19 provenance audit as its top review pair,
the camera files around them, and their tests.
**Reviewer:** an independent reviewer session with no access to the quarantined reference
sources. This is a provenance opinion about the files below, not a legal certification.

## 1. Reviewer statement

I did not read `/root`, `/root/clean-room-quarantine`,
`/tmp/opencode/clean-room-quarantine`, any `/tmp/rpe-*` path, or any file whose package is
`ai.resourcepack.engine`. I did not search for, fetch, or clone RP Engine source. I did not
run `scripts/clean-room-audit.py`, because its default reference path is the quarantined
directory and running it would read those files.

Everything below is based on:

- the Kalo sources and tests in scope, read end to end;
- Kalo's own documentation and build output;
- public Geyser, Paper/Bukkit, Java and Minecraft protocol documentation;
- the aggregate metrics already recorded in `build/reports/clean-room-audit.txt` and
  `docs/clean-room-audit.md`, which contain counts and hashes but no reference source text.

I therefore cannot confirm what the reference file actually contains. I can decide whether
the Kalo code in front of me is the kind of expression that a small optional-runtime seam
would independently produce, and whether its overlap is limited to generic API shape.

## 2. Files inspected

| File | Lines | Role |
|---|---:|---|
| `core/src/main/java/io/kalo/integration/BedrockSupport.java` | 211 | Optional-Bedrock port and no-op implementation |
| `core/src/main/java/io/kalo/platform/java/VfxCameraTransport.java` | 42 | Camera packet strategy and no-op implementation |
| `core/src/main/java/io/kalo/platform/java/VfxCameraTransports.java` | 22 | Class-load-time transport factory |
| `core/src/main/java/io/kalo/platform/java/VfxCameraDelivery.java` | 22 | Delegation helper between VFX runtime and transport |
| `core/src/main/java/io/kalo/platform/java/NmsVfxCameraTransport.java` | 143 | Reflective `ClientboundSetCameraPacket` transport |
| `core/src/test/java/io/kalo/integration/BedrockSupportTest.java` | 108 | No-op and overload-fallback tests |
| `core/src/test/java/io/kalo/platform/java/VfxCameraDeliveryTest.java` | 66 | Delegation, no-op and factory tests |

Supporting Kalo files read for context (not reviewed as targets):
`GeyserIntegration.java`, `GeyserBedrockSupport.java`, `VfxBedrockDelivery.java`,
`VfxBedrockDeliveryTest.java`, `VfxCameraFrame.java`, `VfxRuntime.java` call sites,
`CLAUDE.md`, `README.md`, `CHANGELOG.md`, `docs/CLEAN_ROOM.md`,
`docs/clean-room-audit.md`, `scripts/clean-room-audit.py`, and
`build/reports/clean-room-audit.txt`.

### Public documentation consulted

1. **GeyserMC wiki, "Geyser API"** —
   <https://geysermc.org/wiki/geyser/api/>. Confirms that the documented public surface is
   `GeyserApi#isBedrockPlayer(UUID)`, `GeyserApi#connectionByUuid(UUID)`,
   `GeyserConnection`, and `GeyserApi#onlineConnections()`. There is no Geyser API method
   named `available()` or constant named `NONE`; those are Kalo's seam vocabulary.
2. **Paper 1.21.4 Javadoc, `org.bukkit.entity.Marker`** —
   <https://jd.papermc.io/paper/1.21.4/org/bukkit/entity/Marker.html>. Confirms `Marker`
   is a server-side marker entity and that `Entity#setVisibleByDefault(boolean)` and the
   other entity calls used by the transport are documented Bukkit API.
3. **Minecraft Wiki, "Java Edition protocol/Packets", § Set Camera** —
   <https://minecraft.wiki/w/Java_Edition_protocol/Packets>. Confirms the Java protocol has
   a clientbound Set Camera packet carrying an entity id. `ClientboundSetCameraPacket` and
   its `Entity`-taking constructor are Mojang-mapped names, not creative naming.
4. **Java language rules** for interfaces, default methods, anonymous classes,
   `@Override`, and the null-object pattern — general language knowledge, no external
   source copied.
5. **Kalo documentation**: `docs/CLEAN_ROOM.md` states the design constraints (Geyser as a
   compile-only optional dependency; no Geyser type in platform-neutral code), and
   `CLAUDE.md` § Architecture states the same rule independently.

## 3. Method

For each file I:

- read the whole file, not just the flagged region;
- traced every non-private method to a Kalo caller or to documented Geyser/Bukkit/
  Minecraft API;
- compared the file with sibling Kalo files to see whether a pattern is a repository
  convention or an isolated import;
- looked for authorship texture that a wholesale copy would not normally have (mixed
  import styles, incremental overloads, local design commentary, repo-wide vocabulary);
- treated the audit metric only as a pointer, because it strips comments, compares only
  `src/main/java`, and rewards tiny files for sharing braces and `@Override`.

## 4. Findings per file

### 4.1 `BedrockSupport.java` — the audit's top review pair

**What the file is.** A port (hexagonal-style seam) that lets platform-neutral Kalo code
ask about Bedrock players and announce content events without linking Geyser. The no-op
implementation is `NONE` at lines 19-29; the capability predicate is `available()` at
lines 31-32; `isBedrock(UUID)` is at lines 34-35. The remaining 26 default hooks are Kalo
domain members: placed rigs (`rigPlaced`, `rigRemoved`, `rigStateChanged`), animated blocks
(`blockPlaced`, `blockRemoved`, `blockAnimationStarted`), worn armor
(`armorAnimationStarted`), custom mobs (`mobSpawned`, `mobMoved`, `mobAnimationStarted`,
`mobRemoved`), generic/VFX rigs (`clientRigSpawned`, `clientRigMoved`,
`clientRigRemoved`), plus session lifecycle (`forget`, `xuid`, `refresh`,
`packUpdated`).

**Suspicious-looking region and exact lines.**

| Lines | Content | Assessment |
|---|---|---|
| 18-29 | Javadoc, `BedrockSupport NONE = new BedrockSupport() { ... }` | Standard Java null-object constant. The type name, field name, anonymous subclass and no-op body are the canonical way to express "this capability is absent". |
| 31-32 | `boolean available();` | Generic capability predicate; the audit script itself lists this declaration as boilerplate. |
| 34-35 | `boolean isBedrock(@NotNull UUID playerId);` | A one-line predicate over Geyser's documented `isBedrockPlayer(UUID)` concept. Kalo needs a UUID-keyed answer because its runtime keys players by UUID. |

**Why this is generic API shape, not copied expression.**

- The overlap is two meaningful normalized lines
  (`build/reports/clean-room-audit.txt:15`). The audit script's boilerplate list
  (`scripts/clean-room-audit.py:54-57`) already removes `@Override`, `return false;`,
  `public boolean available() {` and `boolean available();`. The two remaining lines are
  therefore not method/return scaffolding; on Kalo's side they would be structural
  declarations such as the interface declaration or the `NONE` field declaration. I cannot
  enumerate them without reading the reference, which the access rules forbid. Either way,
  two lines out of 211 is not a copied implementation.
- If the reference's method set had been copied, the meaningful count would be dozens of
  lines: the rig/mob/armor/block/pack hooks do not exist in Geyser's public API and are
  shaped by Kalo's own content types. The audit reports 2, not dozens.
- The one name-level coincidence is the type name itself: the reference file is reported
  as `rpe-BedrockSupport.java`, so both are likely called `BedrockSupport`. That is a plain
  description of the capability ("Bedrock" + "Support"), not a distinctive coinage, and
  nothing else in the file follows from the name.
- The interface is a textbook null-object seam. Any implementation of "optional platform
  support" needs a capability query, a do-nothing instance, and a constant to name it.
  There are only so many ways to write that, and the names `NONE` and `available` are the
  obvious ones. That is the kind of idea/expression merger a lawyer should assess;
  technically it is generic shape, not authored logic.
- The file shows incremental local authorship rather than a coherent imported unit:
  `java.util.UUID` is imported at line 6 but fully qualified at lines 60, 65, 71, 75, 81,
  86, 92, 104, 106, 119, 129, 138, 157, 200; `@Nullable` is imported at line 4 but fully
  qualified at line 199; continuation lines are not aligned consistently with their
  opening parentheses (compare lines 45-46 with 104-108). A copy of one external file
  would normally be internally consistent.
- The overload ladder (`rigPlaced` at 37-48, `clientRigSpawned` at 104-164,
  `clientRigMoved` at 166-181) is a local compatibility pattern: each new parameter set
  delegates to the previous hook. The javadoc at lines 143-145 explains the fallback
  choice ("an implementation that cannot do both should still deliver the size the caller
  asked for"), and `BedrockSupportTest` pins it. That is design evolution inside this
  repository, not API surface dictated by Geyser.
- The prose is Kalo-specific: "A placed rig entered the world without position details",
  "A custom block entered the world; animated blocks spawn a client entity", "The
  generated Bedrock pack changed; stage it and refresh online sessions". None of this
  describes a Geyser type or a generic Bedrock library.

**Verdict: generic public API shape.** The `NONE`/`available()` fragment is a generic
idiom; the interface as a whole is independently shaped by Kalo's domain. No evidence of
derivation in the reviewed file.

### 4.2 `VfxCameraTransport.java` — the other half of the flagged pair

**What the file is.** A three-method strategy interface for the Java camera packet seam:
`available()` (34-35), `apply(UUID, VfxCameraFrame)` (37-38), `clear(UUID)` (40-41), plus
the no-op `NONE` at 19-32.

**Suspicious-looking region and exact lines.**

| Lines | Content | Assessment |
|---|---|---|
| 18-32 | `VfxCameraTransport NONE = new VfxCameraTransport() { ... }` | Same null-object constant idiom as above, with two empty method bodies. |
| 34-35 | `boolean available();` | Capability predicate; boilerplate in the audit script. |
| 37-41 | `apply` / `clear` declarations | The minimum verbs for "move the camera for one tick" and "restore it". |

**Why the 40.9% raw line overlap is an artifact.** The audit reports
`jaccard=0.042 lines=0.409 meaningful=0` for
`rpe-BedrockSupport.java -> VfxCameraTransport.java`
(`build/reports/clean-room-audit.txt:14`). The `lines` ratio divides by the reference
file's unique normalized lines. Since the script reports **zero** meaningful lines, the
entire shared set must consist of material it classifies as scaffolding: braces, `};`,
`@Override`, `public boolean available() {`, `return false;`, `boolean available();`,
`return null;` and similarly short lines. The two `NONE` declarations cannot be identical,
because each names its own interface type. A high ratio on a tiny file with a zero
meaningful count is a size artifact, not a copy signal.

**Why the shape is independently determined.** The payload type is Kalo's
`io.kalo.content.vfx.definition.VfxCameraFrame` record (`VfxCameraFrame.java:13-14`), not
a Bedrock or Geyser type. `BedrockSupport` has no `apply`/`clear` methods and no camera
concept; `VfxCameraTransport` has no `isBedrock`, rig, block, mob or pack methods. The
shared vocabulary is limited to `available()`, `NONE`, `@Override`, `return false`. The
interfaces are not structural copies of each other:

| | `BedrockSupport` | `VfxCameraTransport` |
|---|---|---|
| Abstract methods | 2 (`available`, `isBedrock`) | 3 (`available`, `apply`, `clear`) |
| No-op overrides | 2 | 3 |
| Default hooks | 26 | 0 |
| Domain payload | rigs, blocks, mobs, armor, pack | one camera frame record |
| Size | 211 lines | 42 lines |

**Verdict: generic public API shape.** The overlap is the standard no-op strategy
pattern; the file's actual surface is camera-specific and Kalo-specific.

### 4.3 `VfxCameraTransports.java`

**What the file is.** A static factory (`create()`, lines 14-21) that tries
`NmsVfxCameraTransport.tryCreate()` and falls back to `VfxCameraTransport.NONE` on a
`null` return or `Throwable`. Private constructor at 11-12.

**Assessment.** This is the standard Java factory-with-private-constructor idiom. The
`catch (Throwable)` fallback is a repository-wide convention for optional integrations:
`GeyserIntegration.java:38-58` documents and uses the same fail-closed approach for
Geyser, with the reason spelled out (a moved API surfaces as `LinkageError`, and optional
support must not stop startup). The class does not appear in the audit's review pairs and
contains no domain expression that could come from a Bedrock-support file.

**Verdict: independent.** (The factory idiom itself is generic, but there is no flagged
overlap and the fallback policy is Kalo's own.)

### 4.4 `VfxCameraDelivery.java`

**What the file is.** A 22-line, two-method pass-through:
`apply(transport, playerId, frame)` at 14-17 and `clear(transport, playerId)` at 19-21.

**Assessment.** There is no algorithm here to copy. Its javadoc, "The one place a planned
camera pose crosses into the packet seam" (line 8), is the same authorial template as
`VfxBedrockDelivery.java:12` ("The one place a Java VFX sprite crosses into the Bedrock
seam"), and that sibling class uses the same static-helper shape at
`VfxBedrockDelivery.java:19-51`. That is strong evidence of an internal convention:
Kalo routes each VFX concern through one named delivery helper so tests can record the
seam. The corresponding test (`VfxCameraDeliveryTest`) uses a recording transport, and
`VfxBedrockDeliveryTest` uses a recording `BedrockSupport` in the same way.

**Verdict: independent.**

### 4.5 `NmsVfxCameraTransport.java`

**What the file is.** A reflective transport that spawns a non-persistent `Marker`, sends
`ClientboundSetCameraPacket` to attach the player's client camera to it, moves the marker
each tick, and restores the player camera on `clear`.

**Potentially sensitive material and exact lines.**

| Lines | Content | Assessment |
|---|---|---|
| 29-30 | `"net.minecraft.network.protocol.game.ClientboundSetCameraPacket"` | A Mojang-mapped class name. Functional/factual identifier, confirmed by the public protocol's Set Camera packet. Not creative expression. |
| 45-55 | `Class.forName` for the packet, `Packet` and `Entity`, then `getConstructor(entityClass)` | The minimum reflective validation for "can this server build the packet?". |
| 125-135 | `getHandle()` on the target and player, `connection` field, `send(Packet)` | The standard CraftBukkit/Paper reflection route for sending a packet. The sequence is dictated by the server classes, not by a choice of expression. Widely used across the plugin ecosystem; not original to any one project. |

**Kalo-specific choices.** `Marker` rather than an armor stand or a mob (93-104); skip
Bedrock players because Geyser does not translate this Java packet (66-68, 111-113);
`setVisibleByDefault(false)` plus `player.showEntity(...)` so only the caster sees the
anchor (100-102); validity/world checks before reuse (71-72); `Level.FINE` failure logs
that leave the view alone (87-89, 137-139); `available()` returns `true` because the
constructor already proved the classes exist (57-60). These are product decisions, not
API transcription.

The file does not appear in the audit's top-40 report. Its only external material is
public API naming and a public reflection technique.

**Verdict: independent.** Public/factual API names only; implementation choices are
Kalo's.

### 4.6 Tests

`BedrockSupportTest.java`:

- lines 15-40 assert that `NONE` answers no and accepts the default hooks the test calls;
- lines 43-46 assert `GeyserIntegration.support()` is non-null and unavailable without
  Geyser — a Kalo architecture guarantee;
- lines 49-84 assert the scaled `clientRigSpawned`/`clientRigMoved` hooks fall back to the
  unscaled hooks;
- lines 87-107 assert the position-aware `rigPlaced` falls back to the simple hook.

The fixtures (`testpack:chair`, `testpack:fan`, `testpack:golem`,
`testpack:ruby_helmet`, `kalo:vehicle_testpack_speeder_vehicle`) are Kalo content keys.
The test names are full sentences in the repository's established style (compare
`VfxBedrockDeliveryTest.theSeamCarriesCastScaleNotTheAbsoluteSpriteSize` and
`BlockStateAllocatorTest.theSameBlockAlwaysGetsTheSameState`). The anonymous
`BedrockSupport` subclass is a minimal test double, not an imported fixture.

`VfxCameraDeliveryTest.java`:

- lines 17-31 assert the delivery helper passes the exact player id and frame and then
  clears;
- lines 34-43 assert `NONE` is harmless and the factory returns a non-null, unavailable
  transport without server internals;
- lines 45-65 are a standard recording transport test double.

The audit script does not scan tests (`SOURCE_ROOTS` at
`scripts/clean-room-audit.py:27` covers only `src/main/java`), so I inspected these two
files manually. No comment or fixture in them names another Bedrock or resource-pack
project. The recording-double pattern is shared with
`VfxBedrockDeliveryTest.java:63-127`.

**Verdict: independent.**

## 5. Verdict summary

| File | Verdict | Basis |
|---|---|---|
| `BedrockSupport.java` | **Generic public API shape** | `NONE` + `available()` null-object idiom; all domain hooks are Kalo content types; 2 meaningful lines out of 211. |
| `VfxCameraTransport.java` | **Generic public API shape** | No-op strategy interface; 0 meaningful lines; camera-specific payload; not structurally the same as `BedrockSupport`. |
| `VfxCameraTransports.java` | **Independent** | Standard factory plus Kalo's documented fail-closed guard policy. |
| `VfxCameraDelivery.java` | **Independent** | Pure delegation; mirrors the repo's `VfxBedrockDelivery` convention. |
| `NmsVfxCameraTransport.java` | **Independent** | Public Mojang-mapped names and standard CraftBukkit reflection; Kalo-specific marker/Bedrock-skip behavior. |
| `BedrockSupportTest.java` | **Independent** | Kalo fixtures and repository test style. |
| `VfxCameraDeliveryTest.java` | **Independent** | Standard recording double; Kalo factory/no-op behavior. |

No file was rated **derived** or **uncertain**. The only overlap worth naming is the
null-object idiom shared by the two interfaces, and that is an idea/idiom with essentially
one natural expression in Java.

## 6. Rewrite decision

**No rewrite is required.** I found no clear copied expression: no shared multi-line
method body, no comment or javadoc naming another project or carrying a third-party
notice, no shared constants beyond a type name and a field name, no copied selection or
arrangement of domain operations, and no third-party copyright header. Rewriting working
code on the strength of the small structural overlap would add risk without reducing legal
exposure.

I did not modify any source file. The only file written by this review is this report.

### Targeted test evidence

Although no rewrite was made, I ran the two named tests against the current tree to pin
the behavior discussed above:

```bash
~/.gradle/wrapper/dists/gradle-9.8.1-bin/\
a2l6ltp3k1vth6lwipzm5p2jp/gradle-9.8.1/bin/gradle \
  :core:test \
  --tests 'io.kalo.integration.BedrockSupportTest' \
  --tests 'io.kalo.platform.java.VfxCameraDeliveryTest' \
  --offline --console=plain -q
```

Result (from `core/build/test-results/test/`):

```text
BedrockSupportTest       tests=4 failures=0 errors=0 skipped=0
VfxCameraDeliveryTest    tests=2 failures=0 errors=0 skipped=0
```

## 7. Residual risk

This review lowers the risk that these files contain copied protected expression; it does
not eliminate it. An independent legal reviewer should still check:

1. **Comments and javadoc were not compared.** The repository's audit script strips
   comments and block comments before comparison (`scripts/clean-room-audit.py:35-51`), so
   it cannot detect copied prose. I could not compare the prose against the reference
   because reading the reference is prohibited for this review. The javadoc in these files
   reads as Kalo-specific, but a reviewer with lawful access to the quarantined files
   should diff the comment blocks directly.
2. **Only main sources were mechanically compared.** The script scans only the three
   `src/main/java` roots (`core`, `api`, `mohist`). I inspected the two named tests, but
   other tests, docs, examples, and generated assets were outside this review.
3. **Non-literal similarity is not fully measurable.** The metric uses exact normalized
   lines and 8-token shingles. It would miss a paraphrased implementation, a renamed
   method body, or a copied control-flow skeleton. The NMS camera transport is the most
   plausible place for that kind of similarity, because its reflection sequence is
   constrained; a reviewer should compare its structure with any camera code in the
   reference, not only with `rpe-BedrockSupport.java`.
4. **API selection and arrangement can be protectable in some jurisdictions.** Even where
   individual names are unprotectable, the selection/arrangement of an interface can be
   argued about (the Oracle v. Google line of cases). Here the arrangement follows Kalo's
   content taxonomy and its own runtime needs, but that is a legal characterization, not a
   metric.
5. **No version history is available in this checkout.** The workspace is not a Git
   repository (`git status` reports no repository), so I could not check when these files
   were introduced relative to the reference files' presence under `/tmp`. A reviewer with
   the real history should inspect the introducing commits and any co-development
   timeline.
6. **The original implementation was not produced under a strict clean-room wall.**
   `docs/clean-room-audit.md:79-80` records that the same agent that saw the reference
   files also wrote Kalo code. My
   review is independent of the reference, but it reviews the resulting code, not a
   separated spec-to-implementation process. "No detectable copying" is not the same as
   "clean-room provenance."
7. **Dependency hygiene.** Confirm that Geyser remains `compileOnly`/optional
   (`core/build.gradle.kts:25`) and that no RP Engine artifact, source, or asset is in the
   repository or the distributed jar. The quarantine must stay outside the build.
8. **License notices.** Kalo is MIT and derives from Neko (MIT). These files carry no
   third-party header; a reviewer should confirm the repository-level `LICENSE` and Neko
   notice remain intact, independent of this review.

## 8. Conclusion

The audit's top pair is best explained as a metric artifact of two small
optional-runtime seams that share the standard Java null-object idiom and capability
predicate. `BedrockSupport` is a 211-line Kalo domain port whose method set has no
counterpart in Geyser's public API; `VfxCameraTransport` is a 42-line camera strategy
whose payload is a Kalo record. They share `NONE`, `available()`, `@Override` and
`return false`, and nothing else of substance. The camera factory, delivery helper, NMS
transport, and tests are independently written against public API and Kalo's own
conventions.

No code rewrite is required on the evidence available to this review. The remaining work
is legal and documentary: compare comments directly, examine the NMS camera design for
non-literal similarity, and preserve the provenance caveats in `docs/CLEAN_ROOM.md` until
a reviewer with access to the reference files confirms the same conclusion.

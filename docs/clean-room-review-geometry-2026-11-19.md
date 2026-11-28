# Clean-room review — Java-to-Bedrock geometry and attachables

**Date:** 2026-11-19
**Scope:** `BedrockGeometry`, `BedrockModelCompiler`, `BedrockAttachable`, and their
tests.
**Reviewer:** independent subagent, no access to the GPL reference sources.

## 1. Reviewer statement

I reviewed only Kalo repository code, tests, docs and build output, plus public
Minecraft/Bedrock/Blockbench documentation. I did **not** open, read, copy, diff or run
any tool against `/root`, `/root/clean-room-quarantine`,
`/tmp/opencode/clean-room-quarantine`, `/tmp/rpe-*`, or any file in package
`ai.resourcepack.engine`. I did not search for, fetch or clone RP Engine source.

I did read Kalo's own `docs/CLEAN_ROOM.md`, `docs/clean-room-audit.md` and the retained
`build/reports/clean-room-audit.txt`. Those files name the quarantined files and report
aggregate similarity scores; they contain no reference source text. I did not run
`scripts/clean-room-audit.py`, because it reads the quarantine directory. This is a
technical provenance review, not a legal opinion or a clean-room certification.

## 2. Files inspected

### 2.1 In-scope Kalo files

| File | Role |
|---|---|
| `core/src/main/java/io/kalo/platform/bedrock/BedrockGeometry.java` | Java model JSON → Bedrock geometry (legacy/Java-asset path) |
| `core/src/main/java/io/kalo/model/bedrock/BedrockModelCompiler.java` | Kalo `ModelIR` → Bedrock geometry, animations and controllers |
| `core/src/main/java/io/kalo/platform/bedrock/BedrockAttachable.java` | Bedrock attachable JSON for held items, armor and emotes |
| `core/src/test/java/io/kalo/platform/bedrock/BedrockGeometryTest.java` | Coordinate/UV regression tests |
| `core/src/test/java/io/kalo/model/bedrock/BedrockModelCompilerTest.java` | IR compiler tests (the task named the `platform/bedrock` package; the file is actually under `model/bedrock`) |
| `core/src/test/java/io/kalo/platform/bedrock/BlockbenchPipelineTest.java` | End-to-end `.bbmodel` → Java + Bedrock tests |

### 2.2 Supporting Kalo files read

- `CLAUDE.md`, `docs/CLEAN_ROOM.md`, `docs/clean-room-audit.md`, `docs/BLOCKBENCH.md`,
  `docs/HYBRID_CONVERTER.md`, `docs/IR_DESIGN.md`
- `build/reports/clean-room-audit.txt` (aggregate metrics only)
- `core/src/main/java/io/kalo/model/ModelIR.java`
- `core/src/main/java/io/kalo/model/ModelTextureResolver.java`
- `core/src/main/java/io/kalo/model/bedrock/BedrockHoldAnimations.java`
- `core/src/main/java/io/kalo/model/blockbench/BlockbenchImporter.java` (UV and rotation
  parsing around lines 420–533 and 1246–1261)
- `core/src/test/resources/golden/simple_cube.geo.json`
- `core/src/test/resources/blockbench/*.bbmodel` (synthetic fixtures)
- `core/src/test/java/io/kalo/platform/bedrock/BedrockPackCompilerTest.java` (attachable
  hold-animation test, lines 514–556)
- `docs/live-check-2026-11-19.md` (to confirm what was and was not rendered)

### 2.3 Public documentation consulted

- Microsoft Learn, **geometry:1.12.0 schema** — `description`, `texture_width/height`,
  `visible_bounds_*`, bones, cube `origin`/`size`/`pivot`/`rotation`, per-face `uv`,
  `uv_size`, `material_instance`.
  <https://learn.microsoft.com/en-us/minecraft/creator/reference/content/schemasreference/schemas/minecraftschema_geometry_1.12.0>
- Microsoft Learn, **geometry:1.16.0 schema** — adds bone `binding`; no `uv_rotation`.
  <https://learn.microsoft.com/en-us/minecraft/creator/reference/content/schemasreference/schemas/minecraftschema_geometry_1.16.0>
- Microsoft Learn, **geometry:1.21.0 schema** — adds per-face `uv_rotation`.
  <https://learn.microsoft.com/en-us/minecraft/creator/reference/content/schemasreference/schemas/minecraftschema_geometry_1.21.0>
- Microsoft Learn, **minecraft:attachable** — description fields and the armor sample
  (`armor` / `armor_enchanted`, `enchanted_actor_glint`, `geometry.humanoid.armor.*`,
  `parent_setup` layer variables, `controller.render.armor`).
  <https://learn.microsoft.com/en-us/minecraft/creator/reference/content/attachablereference/examples/attachabledefinitions/attachable>
- Bedrock Wiki, **Attachables** — held-item sample with `hold_first_person` /
  `hold_third_person`, `context.is_first_person` conditions, `controller.render.item_default`,
  and the note that bone `binding` requires format 1.16.0.
  <https://wiki.bedrock.dev/items/attachables>
- Bedrock Wiki, **Block Models** — Bedrock block-geometry workflow (background only).
  <https://wiki.bedrock.dev/blocks/block-models>
- Minecraft Wiki, **Model** — Java model `texture_size`, UV `[x1,y1,x2,y2]` semantics,
  optional UVs being inferred from element position, element rotation fields.
  <https://minecraft.wiki/w/Model>
- Blockbench `LICENSE.MD` — **GPL-3.0**.
  <https://github.com/JannisX11/blockbench/blob/master/LICENSE.MD>
- EaseCation **mc-model-geo** README, `SPEC.md`, `src/uv.ts`, `src/autouv.ts`,
  `src/rotation.ts` — a public MIT project that explicitly says its conversion math is
  ported from Blockbench's `bedrock` / `java_block` codecs. Used only to establish that
  the coordinate, UV and rotation rules are publicly documented conventions.
  <https://github.com/EaseCation/mc-model-geo>
- Kaioga5 **BBModel-Unpack** `docs/FORMAT-NOTES.md` — independent format notes derived
  from the Blockbench source and Microsoft schemas; documents the same formulas, the
  up/down UV flip, format-version raising for `binding`/`uv_rotation`, and the animation
  conversion rules.
  <https://github.com/Kaioga5/BBModel-Unpack/blob/main/docs/FORMAT-NOTES.md>

### 2.4 Not inspected

The six quarantined GPL files, any RP Engine source, and any other third-party
implementation beyond the public documents above. The repository is not a git checkout
(`git status` fails), so commit history and blame were not available.

## 3. Method

For every non-trivial line in the six in-scope files I asked whether it is:

1. a public schema key or required identifier;
2. a mathematical/format rule that any correct converter must implement;
3. a Kalo-specific design decision; or
4. an expression (comment, identifier, structure, constant, test datum) that appears
   copied from another project.

I looked for the usual copy markers: distinctive comments or phrasings, non-schema
identifiers, unusual constants, identical control-flow shapes, identical test data or
goldens, mixed formatting that would indicate a pasted block, and attribution strings.
I then compared the formulas with public documentation and with Kalo's own tests.

I also ran the three targeted test classes as a **baseline** (no code was changed):

```text
BedrockGeometryTest       13 tests, 0 failures
BedrockModelCompilerTest  10 tests, 0 failures
BlockbenchPipelineTest    17 tests, 0 failures
```

Command (the project's wrapper jar is absent in this checkout, so the cached Gradle
9.8.1 distribution was used):

```bash
~/.gradle/wrapper/dists/gradle-9.8.1-bin/*/gradle-9.8.1/bin/gradle --offline \
  --console=plain :core:test \
  --tests io.kalo.platform.bedrock.BedrockGeometryTest \
  --tests io.kalo.model.bedrock.BedrockModelCompilerTest \
  --tests io.kalo.platform.bedrock.BlockbenchPipelineTest
```

### Limits

- I cannot prove a negative. Without the comparator I cannot say "the reference does not
  contain this expression"; I can only say I found no copied expression in Kalo.
- Kalo's own audit strips comments before comparing. Comment copying is therefore outside
  that metric. I read the comments, but without the reference I cannot diff them.
- The same agent that saw the reference files also wrote Kalo code (the repository says so
  in `docs/CLEAN_ROOM.md`). Access history is outside this review.

## 4. Findings

### Verdict summary

| File | Verdict |
|---|---|
| `BedrockGeometry.java` | **Public format rules — independently expressed** |
| `BedrockModelCompiler.java` | **Independent** (format-rule fragments are public) |
| `BedrockAttachable.java` | **Public format rules — independently assembled** |
| `BedrockGeometryTest.java` | **Independent** |
| `BedrockModelCompilerTest.java` | **Independent** |
| `BlockbenchPipelineTest.java` | **Independent** |

No file was assessed as *derived* or *uncertain* on the evidence available. The
qualification in §3 (no comparator) still applies: these are technical verdicts, not
legal findings.

### 4.0 The shared rules are public and functionally constrained

The rules in the task context are not unique to any one project. They are documented in
Microsoft's schemas and in public descriptions of Blockbench's Java/Bedrock codecs:

| Rule | Kalo location | Public source |
|---|---|---|
| Java cube `[from,to]` → Bedrock `origin = [8 - to.x, from.y, from.z - 8]`, `size = to - from` | `BedrockGeometry.java:119-120`; `BedrockModelCompiler.java:587-592` | mc-model-geo README ("center X/Z by −8, then mirror X"); BBModel-Unpack §2 (`[-to.x, from.y, from.z]`) |
| Rotation pivot `= [8 - origin.x, origin.y, origin.z - 8]` | `BedrockGeometry.java:155-158`; `BedrockModelCompiler.java:596-601` | mc-model-geo README (`pivot = [-(origin[0] - 8), origin[1], origin[2] - 8]`); BBModel-Unpack §2 |
| Java element rotation X/Y negated, Z kept | `BedrockGeometry.java:160-166`; `BedrockModelCompiler.java:444-449, 655-661` | mc-model-geo README/`rotation.ts`; BBModel-Unpack §2 |
| Java UV `[x1,y1,x2,y2]` → Bedrock `uv` + `uv_size`; up/down faces flipped to the opposite corner | `BedrockGeometry.java:193-203`; `BedrockModelCompiler.java:618-627` | Microsoft geometry schema (`uv`, `uv_size`); mc-model-geo `uv.ts`; BBModel-Unpack §2 |
| Java's inferred UVs when `uv` is omitted | `BedrockGeometry.java:229-240`; `BlockbenchImporter.java:1246-1261` | Minecraft Wiki "Model" (UV inferred from position); mc-model-geo `autouv.ts` |
| `texture_width/height` from `texture_size`, default 16 | `BedrockGeometry.java:249-257`; `BedrockModelCompiler.java:164-170` | Minecraft Wiki "Model" (`texture_size`); Microsoft geometry schema |
| Geometry envelope `format_version: 1.12.0`, `minecraft:geometry`, `description`, `bones` | `BedrockGeometry.java:74-100`; `BedrockModelCompiler.java:164-181` | Microsoft geometry 1.12.0 schema |
| Animation `hold_on_last_frame`, `lerp_mode: catmullrom`, step `pre`/`post`, decimal time keys | `BedrockModelCompiler.java:263-269, 383-398, 452-457` | BBModel-Unpack §3 (public Blockbench animation export notes) |

These formulas are short mathematical facts. There are only so many ways to write
`8 - to.x` or `[x2, y2]` / `[x1 - x2, y1 - y2]`. The relevant question is therefore not
whether the numbers match — they must — but whether the surrounding expression was copied.
The evidence below says it was not.

### 4.1 `BedrockGeometry.java`

**What is Kalo-specific**

- The API is a single stateless `convert(String identifier, JsonObject javaModel)` entry
  point (`:49-101`), returning `null` when the model has no elements (`:50-55`). This is a
  Kalo design choice: a parent-only model has no shape to convert, and the caller decides
  how to report it.
- The parser is tolerant where Java would reject: malformed elements are skipped
  (`:61-68`), missing rotation fields return without rotating (`:148-150`), malformed face
  UVs are skipped (`:182-186`). That is not a transcription of a strict codec.
- `materialName` (`:244-247`) strips `#` and replaces `:`/`/` with `_` — a Kalo naming
  scheme, not a Bedrock rule.
- `identifierFor` (`:260-261`) produces `geometry.kalo.<namespace>_<name>` — Kalo-specific.
- `array` (`:264-276`) formats whole numbers as integers so output resembles hand-written
  Bedrock JSON — Kalo serialization choice.
- The comments are explanatory Kalo prose ("a mirrored model still looks like a model" in
  the test, "nobody goes looking for the cause" in the class doc). They do not read like
  copied API docs.

**Substantive divergence from the public Blockbench codec**

Kalo does **not** scale inferred UVs by `texture_size / 16` and does **not** clamp them
to the texture, both of which the public Blockbench-derived descriptions do (mc-model-geo
`uv.ts`/`autouv.ts`; BBModel-Unpack §2). `defaultUv` (`:229-240`) hardcodes `16` and takes
no texture-size parameter. A direct port of the Blockbench codec would likely carry the
scaling and clamping. This is technical evidence of independent implementation
(and a likely functional gap for non-16×16 textures with inferred UVs — see §6).

**Closest overlaps, and why they are not suspicious**

| Kalo lines | What appears there | Assessment |
|---|---|---|
| `:119-120` | `origin = (8 - toX, fromY, fromZ - 8)`, `size = to - from` | Public mirror/centre rule. The code stores `fromX`/`toX` in locals and passes them to `convertFaces`; the surrounding structure is Kalo's. |
| `:141-142` | "which is Blockbench's own Java-to-Bedrock convention. This is the rule the existing Java-model converter uses." | Explicit attribution to a public convention, not to a source file. Accurate: Blockbench's codec does negate X/Y. |
| `:155-158` | `pivot = (8 - origin.x, origin.y, origin.z - 8)` | Public rule. Written as a `JsonArray` builder, not a ported `pivot[0] *= -1` mutation. |
| `:160-166` | `switch (axis)` returning `(-angle,0,0)`, `(0,-angle,0)`, `(0,0,angle)` | Public rule. A Java `switch` expression, not Blockbench's array mutation. |
| `:193-203` | Up/down `uv = (x2,y2)`, `uv_size = (x1-x2, y1-y2)`; other faces `(x1,y1)` / `(x2-x1, y2-y1)` | Public rule. Kalo algebraically simplifies the flip instead of copying `uv += uv_size; uv_size *= -1`. |
| `:229-240` | The Java inferred-UV table | Public format behaviour; also appears in `BlockbenchImporter` in a different return type/form. Same-author duplication, not third-party. |

**Suspicious overlap found:** none that indicates copied expression.

**Hygiene, not provenance**

- `mirrorFace` (`:223-226`) is a package-visible identity function kept only for
  `BedrockGeometryTest:66-71`. It looks like a vestige of an earlier implementation that
  mirrored east/west; the test now pins that face names must *not* move because the cube
  coordinates already mirror X. This is confusing dead production code, not evidence of
  copying. It should be removed or renamed in a cleanup.
- The class doc at `:21-23` says "`8 - to.x`, not `from.x - 8`". This is a rule statement,
  not copied prose.

**Verdict:** **public format rules, independently expressed.** No rewrite.

### 4.2 `BedrockModelCompiler.java`

**What is Kalo-specific**

- The input is Kalo's `ModelIR` (`io.kalo.model.ModelIR`), not a Java model or a
  Blockbench project. The compiler reads bones, cubes, animations, controllers, display
  transforms and diagnostics (`:53-187`). The IR is documented in `docs/IR_DESIGN.md` and
  is shared with the Java compiler.
- Diagnostics with Kalo codes: `bedrock.missing-parent`, `bedrock.bone-hidden`,
  `bedrock.no-elements`, `bedrock.multiple-roots`, `bedrock.animation-animator`,
  `bedrock.animation-target`, `bedrock.animation-empty`,
  `bedrock.animation-discontinuous`, `bedrock.animation-bezier-baked`,
  `bedrock.controller-animation` (`:110-122, 129-131, 144-146, 153-157, 211-215, 228-233,
  248-253, 355-359, 404-410, 523-527`).
- `compileEntity` and the `entitySpace` branch (`:61-73, 440-449, 584-590, 597-600,
  655-668`) are a Kalo extension for mobs, worn armor, vehicles and emotes. A Java-block
  converter does not need this mode.
- Animation compilation (`:189-431`), controller compilation (`:497-576`),
  `BedrockHoldAnimations` delegation (`:294-338`), `uniqueBoneName` (`:638-653`),
  `sanitize` (`:459-465`), `timeKey` (`:452-457`) and `unique` (`:467-475`) are Kalo
  design, not a generic codec.
- The class only exists because Kalo has a shared IR. `docs/BLOCKBENCH.md` and
  `docs/IR_DESIGN.md` describe this as a Kalo architectural decision, and the file's
  feature set (bones, controllers, Bezier baking, hold fallbacks) is much wider than a
  Java-to-Bedrock translation.

**Closest overlaps, and why they are not suspicious**

| Kalo lines | What appears there | Assessment |
|---|---|---|
| `:38` | "Coordinate translation matches the proven Java-to-Bedrock rules" | A vague claim of external validation. Not copied expression, but "proven" is an unverifiable provenance assertion. See §6. |
| `:587-589` | `origin = (8 - to.x, from.y, from.z - 8)` | Public rule. The comment explicitly says "This is the same rule BedrockGeometry uses for Java models" — internal cross-reference, which is evidence of Kalo authorship, not a third-party port. |
| `:596-601` | `pivot = (8 - pivot.x, pivot.y, pivot.z - 8)`, `rotation = bedrockRotation(...)` | Public rule, delegated to a helper rather than inlined. |
| `:619-627` | UV flip for up/down faces | Public rule. Same algebraic form as `BedrockGeometry`, different surrounding code (`Face` record, material resolution). |
| `:444-449` | Animation rotation `(-x,-y,z)`, position `(-x,y,z)` | Public Blockbench animation convention; the `entitySpace` branch is Kalo-only. |
| `:655-668` | `bedrockRotation` / `bedrockVec` helpers | Public rule factored into two small functions; a port would more likely keep the array mutation inline. |

No Blockbench function names or source paths appear (`compileCube`, `mapAutoUV`,
`java_block.js`, `bedrock.js`, `centered_grid`, `compileGroup` — none are present in the
repository).

**Suspicious overlap found:** none.

**Technical observation (not provenance):** `BEZIER_BAKE_STEPS = 8` (`:48`) is an
arbitrary Kalo constant; the test only requires at least 8 keys
(`BedrockModelCompilerTest:179-180`). Public documentation says Bezier is baked, but not
into how many steps. This is a design choice, not a copied constant.

**Verdict:** **independent.** The JSON fragments that match public format rules are
unavoidable; the architecture, diagnostics, entity-space mode and animation/controller
handling are Kalo's. No rewrite.

### 4.3 `BedrockAttachable.java`

**What is Kalo-specific**

- Four distinct builders: held item (`:90-181`), player emote (`:193-243`), worn 3D armor
  (`:249-305`) and flat armor (`:307-345`). The material/texture setup is repeated across
  them rather than factored into one generic function. That is organic, incremental
  authorship; a copied library would more likely have a single shared builder.
- Kalo-specific paths and identifiers: `attachablePath` (`:82-84`), `texturePath`
  (`:77-80`), `key.namespace() + ":" + key.value()` (`:280, 331`), and the `_emote`
  suffix (`:217`).
- Kalo-specific comments: the `geometry.player_armor.*` bug story (`:33-41`), the
  vanilla-layer explanation (`:52-57`), the leggings sheet split (`:67-75`), the
  held-item animation rationale (`:134-137`).
- `textureLayer` uses Kalo's `ArmorSlot.usesLeggingsLayer()` (`:73-75`), not a raw slot
  switch.
- `hideVanillaLayer` (`:58-65`) and `geometryFor` (`:43-50`) are small mappings; the
  values are vanilla names, but the selection/wiring is Kalo's.

**Closest overlaps, and why they are not suspicious**

The held-item builder is structurally almost identical to the **public Bedrock Wiki
attachable example**. The following strings are from that public sample, not from the GPL
reference:

| Kalo lines | String | Public source |
|---|---|---|
| `:140-147` | `hold_first_person` / `hold_third_person` fallbacks to `animation.steve_head.hold_*` | Bedrock Wiki "Attachables" |
| `:148-151` | `"context.is_first_person == 1.0"` / `"context.is_first_person == 0.0"` | Bedrock Wiki "Attachables" |
| `:163-164` | `controller.render.item_default` | Bedrock Wiki "Attachables" |
| `:117-129` | `materials` / `textures` with `default` and `enchanted` | Microsoft attachable schema; wiki sample |
| `:166-179` | `description` / `minecraft:attachable` envelope, `format_version: 1.10.0` | Microsoft attachable schema |

The worn-model and flat-armor builders match the **Microsoft armor attachable sample**
almost field for field: `materials` `armor`/`armor_enchanted` (`:259-261, 311-313`),
`textures` `..._1` plus `textures/misc/enchanted_actor_glint` (`:262-264, 315-319`),
`geometry.humanoid.armor.*` (`:43-49`), `parent_setup` layer variables (`:58-65,
276-277, 327-328`) and `controller.render.armor` (`:295-297, 324-325`). These are required
schema/documentation values, not copied implementation.

**Suspicious overlap found:** none that indicates copied expression.

**Hygiene, not provenance**

- `:71` says the leggings-sheet split was "arrived at independently." This is a
  self-assertion in a comment, not evidence. It is also unnecessary; the split is a
  public Bedrock/Java convention. A cleanup should rephrase it to cite the convention or
  drop it.
- `:183-192` contains two consecutive Javadoc blocks: one for `attachable` ("Builds the
  attachable for one armor piece", `@param textureName`) and one for `playerEmote`. The
  first is orphaned above `playerEmote`; `attachable` is defined later at `:307`. This is
  an editing artifact, not copied text, but it makes the file look disorganized. Move or
  delete the orphaned block in a cleanup.

**Verdict:** **public format rules, independently assembled.** No rewrite.

### 4.4 Tests

- `BedrockGeometryTest` uses hand-computed values and comments that explain the failure
  each test guards: full block at `-8` (`:39-50`), asymmetric cube (`:53-63`), face names
  staying put (`:66-71`), UV corner + size (`:74-83`), negative UV size (`:86-97`),
  inferred UVs (`:100-109`), rotation sign (`:112-122`), up/down flip (`:125-134`),
  `uv_rotation`/material (`:137-146`), parent-only model (`:149-154`), `texture_size`
  (`:157-166`), identifier (`:169-172`), integer serialization (`:175-183`). The comments
  are in the same voice as the production code. No copied test data.
- `BedrockModelCompilerTest` uses synthetic `.bbmodel` fixtures and asserts Kalo-specific
  ids, diagnostics, animation JSON, controllers, determinism and error behavior
  (`:37-311`). The fixture loader (`:22-30`) and the assertions are tied to Kalo's
  `ModelIR`/`BlockbenchImporter`.
- `BlockbenchPipelineTest` is an end-to-end Kalo pipeline test. Its golden
  `core/src/test/resources/golden/simple_cube.geo.json` contains Kalo-specific output:
  `"identifier": "geometry.kalo.testpack_ruby_hammer"`, a `binding` on the root bone, and
  `"material_instance": "t0"`. It is generated from Kalo's own compiler and asserted
  byte-for-byte (`:126-134`). The fixtures use sequential synthetic UUIDs and names like
  `body`; they are not third-party assets.
- The tests assert Kalo's *assumptions*, not a third-party test suite. `docs/BLOCKBENCH.md`
  is explicit that they are a regression guard, not proof against a real client.

**Verdict:** **independent.** No rewrite.

### 4.5 The retained similarity audit

`build/reports/clean-room-audit.txt` reports 2,220 pairs and 0 high-risk pairs. The
highest pairs involving these files are:

```text
jaccard=0.027 meaningful=16  rpe-BedrockContent.java -> BedrockAttachable.java
jaccard=0.020 meaningful=12  rpe-BedrockGeometry.java -> BedrockModelCompiler.java
jaccard=0.018 meaningful=13  rpe-BedrockGeometry.java -> BedrockGeometry.java
```

I did not inspect the reference lines, so I cannot confirm what those 12–16 lines are. From
the Kalo side, a "meaningful line" count of that size is exactly what schema-heavy files
produce: `JsonObject`, `JsonArray`, `addProperty`, `add`, `format_version`,
`material_instance`, `uv_size`, `controller.render.*`, `textures/misc/...`. The metric
also strips comments, so it cannot detect copied prose. It is useful corroboration, not a
substitute for this review.

## 5. Rewrite decision

**No rewrite is required.** I found no clear copied expression: no distinctive third-party
comment, identifier, constant, test datum, or control-flow structure that I can attribute
to another project. The overlaps are public schema keys, mathematical rules, and public
documentation sample strings.

No production or test file was modified. The only file added is this report.

Non-blocking cleanup suggestions (not performed, because they are not provenance fixes):

1. Remove or rename `BedrockGeometry.mirrorFace` (`:223-226`) and adjust its test
   (`BedrockGeometryTest:66-71`). It is dead production code that reads like a copied
   stub.
2. Move or delete the orphaned Javadoc block in `BedrockAttachable:183-192`.
3. Rephrase the unverifiable provenance claims: `BedrockModelCompiler:38` ("proven
   Java-to-Bedrock rules") and `BedrockAttachable:71` ("arrived at independently").
   Cite the public convention instead, or say nothing.
4. Consider a shared `array(double...)` helper for the three copies
   (`BedrockGeometry:264-276`, `BedrockModelCompiler:670-681`,
   `BedrockHoldAnimations:121-132`). This is duplication hygiene only.

## 6. Residual risk and what an independent legal reviewer should still check

1. **Access history.** The repository admits the same agent saw the quarantined files
   during development. My review is independent, but it cannot reconstruct what was open
   when each of these files was written. Check editor/agent logs, shell history, file
   timestamps and any saved drafts for these three files. The repository is not a git
   checkout, so there is no blame history to date the code.
2. **The 12–16 "meaningful line" overlaps.** A human should diff those lines directly
   against `rpe-BedrockGeometry.java` and `rpe-BedrockContent.java` and decide whether
   they are unprotectable format rules/ideas (merger doctrine, *scènes à faire*) or
   protectable expression. My independent read says the Kalo side is schema keys and
   formulas, but I did not see the other side.
3. **Comments.** The automated audit strips comments. I read the Kalo comments and found
   no distinctive third-party prose, but I lacked the comparator. A line-by-line comment
   diff is still worthwhile, because comments are copyrightable expression even when the
   code is not.
4. **Blockbench is GPL-3.0, not a permissive format spec.** The task context calls the
   coordinate rules "public Blockbench Bedrock codec conventions." That is true as a
   behavioural statement, but Blockbench's *source* is GPL-3.0. Kalo's comments cite the
   convention, not a source file, and the implementation differs structurally (no UV
   scaling/clamping, no Blockbench identifiers, entity-space mode, diagnostics). A legal
   reviewer should still assess whether the auto-UV table and the animation conventions
   cross from idea/function to protectable expression, and whether any code was ported
   from Blockbench rather than written from the rules. My technical view is that the
   differences are substantial, but that is not a legal conclusion.
5. **Only six reference files were audited.** The retained audit covers
   `rpe-BedrockContent`, `rpe-BedrockGeometry`, `rpe-BedrockSupport`, `rpe-Geometry`,
   `rpe-GeyserBridge` and `rpe-ModelIcon`. Other GPL material could have been consulted.
6. **Do not treat docs claims as evidence.** `docs/CLEAN_ROOM.md` and
   `docs/BLOCKBENCH.md` assert independent implementation. Those are project statements,
   not proof. The technical evidence here supports them for these files, but the legal
   reviewer should verify the development history rather than rely on the prose.
7. **Functional validation is separate.** No rendered Bedrock client has checked these
   outputs; `docs/live-check-2026-11-19.md` records protocol-level delivery only. Three
   things I noticed while reading, which are **not** provenance findings and were not
   adjudicated:
   - `BedrockGeometry` and `BedrockModelCompiler` emit per-face `uv_rotation` under
     `format_version: 1.12.0`, while Microsoft's schema introduces `uv_rotation` in
     1.21.0 and Blockbench's own exporter raises the version when UV rotation is used.
   - The wider pipeline emits bone `binding` under `format_version: 1.12.0` (visible in
     the golden), while the public schema introduces `binding` in 1.16.0 and the Bedrock
     Wiki says model binding requires 1.16.0.
   - `defaultUv` does not scale or clamp inferred UVs for non-16×16 `texture_size`.
   These may be real functional issues, but they are evidence *against* a faithful port,
   not evidence of copying.

## 7. Bottom line

On the evidence available to this independent review, the three production files are
independently written expressions of public Bedrock/Blockbench format rules:

- `BedrockGeometry.java` — public format rules, independently expressed.
- `BedrockModelCompiler.java` — independent; the format-rule fragments are unavoidable
  and embedded in Kalo's own IR/diagnostics architecture.
- `BedrockAttachable.java` — public format rules, independently assembled; its structure
  follows Microsoft/Bedrock Wiki samples, not a GPL implementation.
- The three test classes — independent, with synthetic fixtures and a Kalo-generated
  golden.

No code rewrite is required. The remaining risk is not visible copying in these files; it
is the documented access history, the unexamined reference lines, comment-level
comparison, and Blockbench's own GPL-3.0 status. Those require an independent legal
review, not another code pass. This report is not a legal certification.

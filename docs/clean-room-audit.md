# Clean-room provenance audit — 2026-11-19

This audit began from six ResourcePack AI / RP Engine Java reference files found under
`/tmp` during development. An access-history reconstruction later showed the implementer
also had a full 747-file source tree and a docs site; the audit was expanded to cover those
(see [Actual-access re-audit](#actual-access-re-audit-2026-11-19)). They are
GPL-3.0-or-later source, while Kalo is MIT and derives from Neko (MIT). The files have
been moved out of the working tree to:

```text
/root/clean-room-quarantine/reference/
```

The directory is root-only (`drwx------`) so an unprivileged reviewer cannot read it.

They are not part of the repository, the build, or the distributed jar.

## Reference hashes

```text
a55854c6a830ed8a50ce5567c12a624b1a2414c8ec6f60e0ad04b74b77d73f27  rpe-BedrockContent.java
3138f47b6ab89ad1bad7f8a6032159daa06f66a79bc5a999f20314d1b4487627  rpe-BedrockGeometry.java
70cb4d34514256b6d65e4f29bd3868d990bb962d88af943745bfdc10ebf23b7e  rpe-BedrockSupport.java
3c4aad664a61dd9df945a65759e97b9b6edbae969a64b83468c041b59c5a2f06  rpe-Geometry.java
9be2f5a6b9df172b54da1d2af244fe2b5a37b644b11e9dfcfb1a72af15241910  rpe-GeyserBridge.java
fbcecffb9b47106ba5d8913ea1e1d27011ca501bb734b89bfcfa449e6a9da821  rpe-ModelIcon.java
```

## Method

`scripts/clean-room-audit.py` compares every Kalo Java source under `core`, `api` and
`mohist` with each reference file. It strips package/import/comment/blank lines and reports:

- exact normalized-line overlap;
- 8-token shingle Jaccard similarity;
- meaningful-line overlap, excluding short declarations and Java scaffolding.

High-risk thresholds are 0.10 Jaccard, or 0.15 meaningful-line overlap with at least eight
meaningful lines. The script is reproducible and can be run from the repository root:

```bash
sudo python3 scripts/clean-room-audit.py \
  --reference /root/clean-room-quarantine/reference \
  --output /tmp/clean-room-audit.txt
```


## Result

```text
sources: 370 files
pairs reported: 2220
high-risk pairs: 0
```

Highest review pairs:

```text
jaccard=0.042 meaningful=0  rpe-BedrockSupport.java -> VfxCameraTransport.java
jaccard=0.031 meaningful=2  rpe-BedrockSupport.java -> BedrockSupport.java
jaccard=0.027 meaningful=16 rpe-BedrockContent.java -> BedrockAttachable.java
jaccard=0.020 meaningful=12 rpe-BedrockGeometry.java -> BedrockModelCompiler.java
jaccard=0.019 meaningful=23 rpe-BedrockContent.java -> BedrockPackCompiler.java
jaccard=0.018 meaningful=13 rpe-BedrockGeometry.java -> BedrockGeometry.java
jaccard=0.017 meaningful=8  rpe-GeyserBridge.java -> GeyserBedrockSupport.java
```

What the overlaps are:

- `BedrockSupport` / `VfxCameraTransport`: `available()`, `NONE` and `return false` interface
  scaffolding. Neither implements the other's feature set.
- `BedrockContent` / `BedrockAttachable`: Bedrock format keys such as
  `"format_version": "1.10.0"`, `render_controllers`, `materials`, and
  `textures/misc/enchanted_actor_glint` — public Bedrock schema names.
- `BedrockGeometry` / `BedrockModelCompiler` and `BedrockGeometry`: the shared coordinate
  rules are Blockbench's public Bedrock codec; the Kalo compilers differ in API, model IR
  and feature coverage.
- `GeyserBridge` / `GeyserBedrockSupport`: `GeyserApi.connectionByUuid`, map bookkeeping and
  event-registration scaffolding; Kalo's registration surface is different.

No high-risk literal copy was detected in this comparison.

## What this audit does **not** prove

- It compares only the six known reference files. Other GPL material could have been
  consulted.
- The same agent that saw the reference files also wrote Kalo code. A strict clean-room
  wall (spec author separate from implementer) was not maintained.
- Similarity metrics cannot detect every non-literal derivation, especially small
  algorithmic choices.

Therefore this is **evidence of low literal-copy risk**, not a legal certification. Two
independent reviewer sessions subsequently reviewed the top API and geometry pairs without
access to the quarantine; both found independent expression and required no rewrite. A
legal reviewer with lawful reference access should still diff comments and close the items
listed in those reports before Kalo is described as certified clean-room or a license
decision is finalized.

## Actions taken

- Reference sources quarantined outside the repository, root-only, with SHA-256 hashes.
- Reproducible audit script added at `scripts/clean-room-audit.py`.
- Audit report retained in `build/reports/clean-room-audit.txt`.
- Two independent reviews completed for the top API and geometry pairs; both required no
  rewrite.
- `docs/CLEAN_ROOM.md` no longer makes an absolute no-copy claim; it records the audit,
  the independent reviews and the remaining legal checks.
- A legal-review handoff packet is at `docs/clean-room-legal-review-packet.md`; legal
  certification remains blocked pending an independent reviewer with lawful access.


## Independent reviews

Two fresh-context reviewer sessions, denied access to the quarantined directory, reviewed
the top pairs using public documentation and Kalo's own tests:

- [`clean-room-review-api-2026-11-19.md`](clean-room-review-api-2026-11-19.md) — API seam;
  no rewrite.
- [`clean-room-review-geometry-2026-11-19.md`](clean-room-review-geometry-2026-11-19.md) —
  geometry/attachable; no rewrite.

They are technical provenance reviews, not legal certification. A subsequent strict
separated rewrite replaced the comments/javadoc in the seven reviewed files and rewrote
`NmsVfxCameraTransport` from a behaviour contract without reading the old file; the
executable code was token-verified unchanged and the audit still reports 0 high-risk pairs.
See `docs/clean-room-rewrite-comments-2026-11-19.md` and
`docs/clean-room-rewrite-nms-2026-11-19.md`.


## Full-tree expansion (Full MIT)

After the six-file audit, a public shallow clone of RP Engine `0.2.90` (commit
`60be18cdaf90bf37c4a7db88c6b5040a00a32fe2`) was placed in the root-only quarantine and
compared with Kalo using `scripts/clean-room-full-audit.py`. The expanded run covers main
sources, test sources and text resources:

```text
references: 673 Java files
sources:    370 main + 137 test Java files, 5 text resources
main code        top 0.062   pairs >= 0.15: 0
main comments    top 0.030   pairs >= 0.15: 0
test code        top 0.052   pairs >= 0.15: 0
test comments    top 0.074   pairs >= 0.15: 0
resources        top 0.102   pairs >= 0.15: 0
```

The audit compares 8-token code shingles and 5-token comment shingles. No pair crossed the
review threshold. The resource pair is Bedrock geometry JSON built entirely from the 25
published schema keys; Kalo's golden is regenerated by `BlockbenchPipelineTest`. This is
broader technical evidence, still not a legal certification.

## Distribution guard

`scripts/clean-room-guard.py` runs without the quarantine and fails a build that would ship
GPL-derived material. It checks the four things an audit cannot see in a repository diff:

- `LICENSE` starts with MIT and keeps the Neko notice;
- no build file declares `resourcepack-ai`, `rp-engine` or `ai.resourcepack`;
- no jar contains `ai/resourcepack`/`rpengine` entries, embedded GPL text, or a non-`io.kalo`
  plugin main class;
- no Java source carries a GPL/AGPL header or SPDX marker.

Current result:

```text
PASS  LICENSE is MIT
PASS  LICENSE keeps the Neko upstream notice
PASS  no RP Engine dependency in build files
PASS  no GPL header in 507 Java sources
PASS  Kalo jar: no RP Engine packages (672 entries)
PASS  Kalo-Mohist jar: no RP Engine packages (31 entries)
RESULT: PASS
```

The guard is wired into `.github/workflows/build.yml` and before publishing in
`release.yml`. A negative test (injecting `ai/resourcepack/engine/Engine.class` into a copy
of the jar) makes it exit 1.

## Actual-access re-audit (2026-11-19)

A read-only query of the local session store showed the development session referenced 89
distinct RP Engine Java files and had a full source tree (`/tmp/rpe-src`, 747 files), a docs
site (`/tmp/rpe-docs`, 205 files including 66 Java code blocks), repository metadata and
archives. Those artifacts were quarantined at
`/root/clean-room-quarantine/access-history/` and the audit was rerun against what was
actually reachable:

```text
rpe-src, earlier revision (747 files)
  main code        top 0.062   pairs >= 0.15: 0
  main comments    top 0.030   pairs >= 0.15: 0
  test code        top 0.052   pairs >= 0.15: 0
  test comments    top 0.074   pairs >= 0.15: 0
  resources        top 0.102   pairs >= 0.15: 0

rpe-docs Java code blocks (66)
  main code        top 0.016   pairs >= 0.15: 0

rpe-docs prose (111 pages) vs Kalo docs (60 pages)
  prose            top 0.002   pairs >= 0.15: 0
```

The `rpe-src` result matches the clone audit because the two trees differ in only 8 files.
Artifact hashes, the session timeline and the limits of the reconstruction are in
[`clean-room-access-history-2026-11-19.md`](clean-room-access-history-2026-11-19.md). This
closes the "six files only" gap; it does not turn the work into a legal certification.

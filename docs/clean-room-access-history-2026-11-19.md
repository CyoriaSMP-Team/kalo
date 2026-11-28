# Access-history reconstruction — 2026-11-19

This document corrects an earlier understatement. The clean-room audit began from "six
RP Engine Java files found under `/tmp`". A reconstruction of the original development
session shows that the implementer had access to a full RP Engine source tree, its
documentation site, repository metadata, and file listings — and referenced 89 distinct
RP Engine Java files, not six.

The reconstruction is evidence about access, not about copying. Copying is measured by the
similarity audits below and by the independent reviews.

## Method

Read-only query of the local OpenCode session store
(`~/.local/share/opencode/opencode.db`, `session_message.data`) for paths matching
`rpe-`, plus an inventory of `/tmp`. No message content is reproduced; only paths, counts
and timestamps are reported. The store is the same one that drove this workspace, so it is
the best available record in a checkout with no Git history.

## Original development session

| Field | Value |
|---|---|
| Session | `ses_e12800d45ffekoo0347Yti3QH7` |
| First reference use | 2026-11-18T15:00:39 |
| Last reference use | 2026-11-19T15:24:40 |
| Instruction (opening message) | “Bring Kalo's model pipeline closer to ResourcePack AI” |
| Distinct RP Engine Java files referenced | 89 |

The 89 files span the areas the implementation touched: `bedrock` (content, geometry,
mobs, Geyser bridge), `block` (assets, states, shapes), `emote` (stances, store, rigs,
skins), `model` (bones, rigs, seats, placement), `vehicle` (controls, physics, sounds,
impacts), `vfx` (definitions, runtime, recipes), `sync` (studio push/wire), `armor3d`,
`api` (`BlockInfo`, `Blocks`, `EntityInfo`, `ModelInfo`), and 6 reference test classes.
The complete path list is reproducible from the query described above.

## Reference artifacts found on disk

All were moved to the root-only quarantine
`/root/clean-room-quarantine/access-history/` (mode 700) on 2026-11-19. SHA-256 and tree
digests are in `SHA256SUMS` there.

| Artifact | Content | Size | Digest |
|---|---|---|---|
| `rpe-src/` | Full source tree, 747 files | 12 MB | tree `b15048554f11ddfdb04d5199f1df5133d16cd0d570671d0e5d27d082097ff5ce` |
| `rpe-docs/` | Docs site, 205 files, incl. `src/openapi.json` and 66 Java code blocks in 16 pages | 14 MB | tree `777e3659f51409406d59826a94fde9b62d3d45052a30f121e74d3fbb70f986ca` |
| `rpe-docs.tar.gz` | Docs archive | 11.7 MB | `e9edfee4003cecaf37d12523288d5c4d631673da0ffdb415bb2114df13f0f2e3` |
| `rpe-repos.json` | GitHub metadata for 11 resourcepack-ai repositories | 72 KB | `3da5f9a723890089faad1c1b7ec0769ee608db8cfe2bf34d6615add6de5b8675` |
| `rpe-tree.json` | Repository file listing | 291 KB | `68640937044119a2df58515b3a37288d7fa3215e8e99cbf00a9cec7f7b9fdac1` |
| `rpe.tar.gz` | Source archive | 2.6 MB | `3dfbbc4e1b5ca9973cd10984d01619f3364937ad652f80217f5c7b40e6b78274` |

`rpe-src` is a near-exact earlier revision of the public RP Engine tree: 747 of the 752
files in the clone at commit `60be18c`, with 8 files differing in content and 5 files added
later. Auditing either tree therefore covers essentially the same material; both were run
(see below).

## Re-audits against what was actually seen

The full-tree audit (`scripts/clean-room-full-audit.py`) was rerun against the real
artifacts, not just the six files:

```text
/tmp/rpe-src (747 files, earlier revision)
  main code        top 0.062   pairs >= 0.15: 0
  main comments    top 0.030   pairs >= 0.15: 0
  test code        top 0.052   pairs >= 0.15: 0
  test comments    top 0.074   pairs >= 0.15: 0
  resources        top 0.102   pairs >= 0.15: 0

rpe-docs Java code blocks (66 blocks in 16 pages)
  main code        top 0.016   pairs >= 0.15: 0
  main comments    top 0.016   pairs >= 0.15: 0

rpe-docs prose (111 pages) vs Kalo docs (60 pages)
  top prose pair   0.002        pairs >= 0.15: 0
```

The `rpe-src` scores are identical to the clone audit, which is expected given the trees
differ in 8 files. The docs code blocks and prose do not match Kalo.

## Reviewer and rewrite sessions

The access query also returns rows from the later reviewer and rewrite sessions. Those
rows contain only the prohibition text in their prompts and their own reports ("I did not
read `/tmp/rpe-*` …"), not reference content: they contain no `/tmp/rpe-` path and no
`package ai.resourcepack` text. The reviewers ran with the six files already root-only
quarantined and reported no reference access; the full tree and docs were still under
`/tmp` at that point but were not opened by those sessions. Their statements are in
`docs/clean-room-review-*-2026-11-19.md` and `docs/clean-room-rewrite-*-2026-11-19.md`.

## Limits

- The session store covers OpenCode sessions only. Shell history, editor buffers and
  terminal scrollback may contain additional access that this query cannot see.
- The checkout has no Git history, so commit-level attribution is unavailable. Timestamps
  above come from the session store, not from file creation times.
- Access is not copying. The similarity audits and independent reviews are the copying
  evidence; this document only fixes the scope of what must be reviewed.
- The quarantine is root-only (`drwx------ root root`). An independent reviewer with
  lawful access can inspect it; implementers must not.

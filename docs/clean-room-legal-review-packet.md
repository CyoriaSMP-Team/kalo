# Clean-room legal review packet — 2026-11-19

## Status

Kalo is **not legally certified clean-room**. The technical provenance work is complete
enough for an independent legal reviewer to start:

- six known RP Engine reference files are quarantined with SHA-256 hashes;
- a reproducible similarity audit reports 2,220 pairs and 0 high-risk literal-copy pairs;
- two independent technical reviewers, denied reference access, found the top API and
  geometry pairs independently expressed;
- comments/javadoc were rewritten and the NMS camera transport was reimplemented from a
  behaviour contract by fresh-context implementers.

The remaining question is legal, not technical: whether the resulting code and comments
are free of protectable copied expression, especially given the original agent's access to
the quarantined references, Blockbench's GPL-3.0 status, and files outside the six-file
scope.

## Materials

| Material | Location / hash |
|---|---|
| Kalo source | this workspace; no git metadata |
| Kalo jar | `build/libs/Kalo-0.2.1-prealpha.16.jar` SHA-256 `4aae09172c01dbc1338e038217534384bf459168fbe9c492c03f8431fd1cd915` |
| Quarantined references | `/root/clean-room-quarantine/reference/`, full clone `/root/clean-room-quarantine/rp-engine/`, and actual-access artifacts `/root/clean-room-quarantine/access-history/` (root-only, mode 700) |
| Full reference revision | `60be18cdaf90bf37c4a7db88c6b5040a00a32fe2` (RP Engine 0.2.90) |
| Reference hashes | `/root/clean-room-quarantine/SHA256SUMS` and `/root/clean-room-quarantine/access-history/SHA256SUMS` |
| Similarity audit (six files) | `build/reports/clean-room-audit.txt` SHA-256 `3f0bb5c9b1f6da34a281e3c447cfe594e2c8d4b66070ef87204b259a0a3ce41e` |
| Full-tree audit | `build/reports/clean-room-full-audit.txt`; script `scripts/clean-room-full-audit.py` |
| Distribution guard | `scripts/clean-room-guard.py`, run in `.github/workflows/build.yml` and `release.yml` |
| Audit method | `scripts/clean-room-audit.py`, `docs/clean-room-audit.md` |
| Access history | `docs/clean-room-access-history-2026-11-19.md` — session `ses_e12800d45ffekoo0347Yti3QH7`, 89 RP Engine files referenced |
| Technical reviews | `docs/clean-room-review-api-2026-11-19.md`, `docs/clean-room-review-geometry-2026-11-19.md` |
| Strict rewrites | `docs/clean-room-rewrite-comments-2026-11-19.md`, `docs/clean-room-rewrite-nms-2026-11-19.md` |
| Live Bedrock evidence | `docs/live-check-2026-11-19.md` |

Reference hashes:

```text
a55854c6a830ed8a50ce5567c12a624b1a2414c8ec6f60e0ad04b74b77d73f27  rpe-BedrockContent.java
3138f47b6ab89ad1bad7f8a6032159daa06f66a79bc5a999f20314d1b4487627  rpe-BedrockGeometry.java
70cb4d34514256b6d65e4f29bd3868d990bb962d88af943745bfdc10ebf23b7e  rpe-BedrockSupport.java
3c4aad664a61dd9df945a65759e97b9b6edbae969a64b83468c041b59c5a2f06  rpe-Geometry.java
9be2f5a6b9df172b54da1d2af244fe2b5a37b644b11e9dfcfb1a72af15241910  rpe-GeyserBridge.java
fbcecffb9b47106ba5d8913ea1e1d27011ca501bb734b89bfcfa449e6a9da821  rpe-ModelIcon.java
```

## Reviewer access

The reference directory is root-only:

```text
drwx------ root root /root/clean-room-quarantine
drwx------ root root /root/clean-room-quarantine/reference
```

An independent reviewer with lawful access should be given read access to those directories
and the Kalo workspace. Implementers must not receive access; the reviewer may read both
sides.

The original agent had access to the references during development — not just six files,
but a 747-file source tree and docs site. The reconstructed timeline, the 89 files
referenced, and artifact hashes are in
[`clean-room-access-history-2026-11-19.md`](clean-room-access-history-2026-11-19.md). The
reviewer should treat that as an access-history risk, not as proof of copying.

## Required review scope

1. **Comments/javadoc** — diff every comment/javadoc block in the seven rewritten files
   against the reference files. The automated audit strips comments, so this is a manual
   comparison.
   - `BedrockSupport`
   - `VfxCameraTransport`, `VfxCameraTransports`, `VfxCameraDelivery`
   - `NmsVfxCameraTransport`
   - `BedrockGeometry`, `BedrockModelCompiler`, `BedrockAttachable`
2. **Non-literal structure** — compare the NMS camera flow and the geometry/attachable
   control-flow skeletons, not only exact lines. Decide whether any shared structure is
   unprotectable format/API necessity or protectable expression.
3. **Expanded full-tree audit** — the full RP Engine 0.2.90 tree is compared against 370
   Kalo main sources, 137 test sources and 5 text resources: 673 references, top main code
   pair 0.062, top main comment pair 0.030, top test code pair 0.052, top test comment pair
   0.074, top resource pair 0.102, no pair >= 0.15. The earlier `/tmp/rpe-src` tree the
   agent actually saw, the 66 Java code blocks in the docs site, and docs prose were also
   audited: top 0.062, 0.016 and 0.002 respectively. The reviewer should confirm the metric
   and inspect the top pairs. Any other GPL-adjacent reference beyond RP Engine remains
   outside this scope.
4. **Blockbench GPL-3.0** — assess the coordinate/UV/animation rules and auto-UV table.
   Kalo's implementation differs structurally from a literal Blockbench port, but the
   reviewer must decide whether any rule crosses from idea/function to protectable
   expression.
5. **Access history** — the reconstruction in
   `docs/clean-room-access-history-2026-11-19.md` fixes the session, the 89 referenced
   files, the full tree/docs artifacts and their hashes. The reviewer should verify it
   against shell/editor logs and inspect the quarantined access-history tree, and should
   still treat unseen terminal access as possible.
6. **Other GPL-adjacent material** — tests, fixtures, generated goldens and the five text
   resources are now in the automated audit; the reviewer should still inspect assets that
   are not text (textures, icons, `.bbmodel` fixtures) and documentation prose, which the
   metric does not cover.

## Required deliverable

A dated, signed review that either:

- certifies the reviewed scope as clean-room / free of protectable copied expression; or
- lists required remediation by file/line, including whether the remedy is rewrite,
  removal, attribution, or license change.

The review should state its legal basis and jurisdiction assumptions. It is not enough to
say "no high-risk pairs"; the certification must address comments, non-literal structure,
access history and the full reference scope.

## License decision

Until the review is complete, do not publish or distribute Kalo as MIT-only. The available
paths are:

- **MIT clean-room** — if the review certifies the code or all remediation is completed;
- **Hybrid / GPL** — if any GPL-derived expression remains and GPL compliance is chosen.

Repo-level `LICENSE` and the Neko MIT notice must remain intact in all cases.

## Reproduction commands

```bash
# Similarity audit (reviewer with read access to the quarantine)
sudo python3 scripts/clean-room-audit.py \
  --reference /root/clean-room-quarantine/reference \
  --output /tmp/clean-room-audit.txt

# Build / tests
~/.gradle/wrapper/dists/gradle-9.8.1-bin/*/gradle-9.8.1/bin/gradle clean build
```

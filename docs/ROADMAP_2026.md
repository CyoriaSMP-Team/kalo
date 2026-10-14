# Kalo completion roadmap — 2026

Kalo is pre-alpha. Successful compilation does not establish Bedrock compatibility.

## P0: Java + Bedrock equivalence
- [ ] 3D item texture atlases, parent model resolution, display transforms
- [ ] Live-player tests of static 3D items (PR #2) including held views/icons
- [ ] Real item animations on both editions and Bedrock animation controllers
- [ ] Bedrock-compatible virtual block visuals or explicit feature fallback
- [ ] Standalone Geyser mapping/pack synchronization tested in staging
- [ ] Verify exact pack_format for all advertised Minecraft releases
- [ ] Parity and regression test matrix for items, blocks, furniture, armor
- [ ] Generated resource pack validators with actionable failure reports

## P1: Runtime content engine
- [ ] True custom mobs/skills/bones/hitboxes, no simulated packets
- [ ] Furniture seating, rotation, storage and collision parity
- [ ] Native Bedrock UI/HUD, fallback to translated Java inventory GUI
- [ ] Full Folia safety audit of feature tasks, particularly cooldown/mana
- [ ] Complete semantic third-party import/export with real-world fixtures
- [ ] Optional Mohist/Forge adapters, separate from Paper/Folia core

## P2: Creator experience
- [ ] Browser-based editor, previews, resource inspectors and diagnostics
- [ ] Optional Kalo Cloud/CDN, keep self-hosting free
- [ ] Stable public API, migrations, CI, release and security review

## Definition of Done

Each feature needs deterministic Java/Bedrock artifacts, unit/integration
tests, real player tests where visuals/protocols matter, explicit handling
for unsupported content, and updated documentation. No unverified claims.

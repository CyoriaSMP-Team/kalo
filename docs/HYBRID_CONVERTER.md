# Kalo hybrid Java resource-pack conversion

## Objective

Keep Kalo's existing authoring path intact while adding a read-only resource-pack
inspection path and, later, an external-content-to-Geyser conversion path.

Two independent inputs:

- Native: Kalo YAML definitions -> Kalo IR -> Java and Bedrock compilers.
- Imported: Java pack + server-visible ItemStack/block-state evidence ->
  external content IR -> Bedrock assets and Geyser v2 mappings.

Merge only at a validated output snapshot. Never register imported content as if
Kalo owned the original gameplay item or block.

## Phase 1: implemented in this branch

- New read-only Java ZIP pack scanner: io.kalo.converter.JavaResourcePackScanner.
- Indexes modern item definitions (plain, condition, range_dispatch, select,
  composite), legacy item model overrides, possible block-state overrides,
  texture images, namespace sounds.json and language files.
- Returns immutable inventories of candidates, sorted by asset path.
- Operator command: /kalo inspect-pack <filename>. Reads only files directly in
  plugins/Kalo/imports/, rejects path escapes/symlinks, and scans asynchronously.
- No on-disk extraction, runtime registration, converter deployment or world changes.
- Archive, entry count and JSON size limits; rejects dangerous ZIP paths.
- Tests cover model kinds, malformed JSON, zip traversal and oversized JSON.

These files are CANDIDATES. A resource pack can contain item model definitions
never used by the server. A blockstates file may simply retexture vanilla.
No successful Geyser mapping is claimed by the scanner.

## Phase 2: associations and Geyser mappings

1. Configure a local source pack explicitly. Do not accept untrusted arbitrary
   paths or remote URLs from players.
2. Collect actual runtime item identity from server-visible ItemStacks,
   including item_model, custom_model_data and predicates. Inspect server block
   states using Paper/Folia-safe scheduling.
3. Build a separate ExternalContentDefinition, not a Kalo-owned item.
   Unknown identities are reported rather than guessed.
4. Compile external content into Bedrock assets and Geyser mappings. Merge into
   BedrockRegistrationSnapshot after collision/validation checks. Keep existing
   native IDs authoritative by default; report conflicts.
5. Support dry-run results (mapped/partial/unsupported), deterministic builds
   and atomic publish, retaining the prior working generation on failure.

This differs from current YAML importers for Oraxen/ItemsAdder/Neko/etc:
external conversion does not claim ownership of the gameplay mechanics.

## Phase 3: rendering and predicates

- Reuse Draft PR #2 for experimental static 3D geometry, parent models and
  atlases. Do not fork the geometry pipeline.
- Add condition/range/select item-model dispatch and the corresponding Geyser
  predicate mappings.
- Add flipbook animation, GUI icon and first/third person transform validation.
- Introduce separate verified coverage for skulls, waypoints, sounds, locales,
  legacy CustomModelData, and advanced equipment.
- Validate on REAL Bedrock clients. Passing unit tests alone is insufficient.

## Phase 4: optional client companion

A Paper plugin cannot read resource packs loaded ONLY on a Java player's
client, or inspect the client-only GUI. An opt-in Fabric companion can export a
bounded, versioned observation snapshot for these cases. Treat companion
payloads as untrusted; never grant permissions from a client report.

No companion is required for Kalo-authored content or external assets where
the server can supply the identities.

## Compatibility and safety

- Native mode remains default. External inspection/conversion stays off until
  explicitly selected by the server operator.
- Phase 1 adds a new permission-gated read-only inspect-pack command but does not
  modify existing Kalo commands, registries or pack builders.
- Require a dry run and validate all identifiers before committing conversion
  outputs. Reject silent overlap with existing Kalo mappings.
- Respect current pre-alpha and Geyser virtual-block limitations.
- Rainbow is a feature and format reference, NOT a code dependency. Kalo uses
  MIT; Rainbow has LGPL/GPL code. Use independent implementations of documented
  standards and public APIs rather than copying code.

## Verification gates

1. Scanner deterministic, bounded, and read-only.
2. Native-only generated packs unchanged when importer is disabled.
3. External pack plus real observed runtime identity yields valid Geyser v2
   mapping, with no duplicate Java/Bedrock identifiers.
4. Each asset family verified on Java and Bedrock clients before claiming it.
5. A failed conversion does not replace the last working pack.

STATUS: Only the read-only scanner and its tests are implemented in this
branch. Full Rainbow-equivalent conversion is NOT implemented here.

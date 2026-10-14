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

## Phase 2: opt-in observed item models (implemented, pre-alpha)

Kalo reuses BedrockPackCompiler and BedrockRegistrationSnapshot; it does not
register gameplay content, overwrite native definitions, or generate a second
pack and mapping file.

### Setup

1. Configure base-pack: my-server-pack.zip with the Java resource pack
   supplying existing custom items. Kalo merges it into generated.zip.
2. Set external-content.enabled: true. Optional: auto-learn: true under
   external-content to observe server-visible player inventories and GUI menus.
3. Hold an authored Java item and run /kalo learn-held (permission
   kalo.command.learn-held). Kalo reads its item_model namespaced key,
   underlying Java material and max stack size on the player's entity scheduler.
   An IO worker saves observations to plugins/Kalo/external-items.yml.
4. Restart Geyser/server. Kalo resolves the Java assets for each stored
   identity and compiles a sprite or static 3D item to Bedrock.
5. Geyser receives the existing single generated pack and mappings from Kalo.
   For standalone Geyser use the existing sync and restart its process.

Unsupported, ambiguous or missing assets are logged and not registered.
Native item and block model IDs take priority. The original Java plugin
continues to own all gameplay behaviour. Everything is off by default.

### Important limitations

- New registrations require a Geyser/server restart; they cannot hot-reload
  into the frozen item palette.
- Plain item models and condition/range_dispatch/select selector trees are
  compiled to branch-specific Geyser v2 item definitions. Each branch gets
  a stable deterministic Bedrock identifier. Predicates are shared by both
  the JSON mapping exporter and the in-process Geyser API bridge.
- Java conditions: broken, damaged, custom_model_data flags, has_component,
  fishing_rod/cast. Ranges: custom_model_data floats, count, damage and
  bundle/fullness, including scale/normalize where valid. Select:
  custom_model_data strings, charge_type, trim_material, context_dimension.
  Unknown selectors fail closed rather than silently using the wrong model.
- Legacy CustomModelData-only items are learned from the actual held item or
  auto-learn inventories, and saved to legacy-items.yml. For a base pack
  containing old-style models/item/<material>.json overrides, the last
  matching legacy override is compiled into the same Bedrock output, using
  Geyser's legacy mapping path.
- Conditional variants only support the 2D and static 3D leaf models Kalo's
  converter already handles. Nested unknown selectors, composites, animated
  textures, external block mappings, skulls, waypoint styles, additional
  Java display transforms and client-only packs remain out of scope.
- Neither legacy item matching nor predicate display has yet been verified
  end-to-end with a real Bedrock player on current Geyser versions.
- Auto-learn records server-visible items. Client-only resource packs and mods
  remain invisible to this plugin.
- Integration has compiler/unit tests, not yet real Bedrock client verification.
  No full Rainbow parity is claimed.

## Automatic local pack discovery (implemented, opt-in with external content)

- When external-content.enabled: true and base-pack is empty or "auto",
  auto-discover-packs: true searches under the plugins directory, no other
  arbitrary server paths. It accepts local Java pack ZIP archives with a
  validated pack.mcmeta plus at least one assets/ file, with bounded ZIP
  metadata and symlink/path traversal checks.
- The selected source is merged through the existing PackMerger. If exactly
  one candidate exists, it is used without a manual base-pack setting. If
  there are multiple candidates, **none is selected automatically**, because
  archives from older plugin builds might contain conflicting Java models.
- /kalo discover-packs prints the detected candidate paths and warnings
  asynchronously without touching files, world state or Geyser mappings.
- An explicit base-pack always takes priority; when external-content.enabled
  is false, auto discovery has no effect on native Kalo output.
- No remote URL fetching, network crawling, special access to client-only
  resource packs or automatic compatibility with Forge mod assets is promised.
- For hands-free observation of server-side item-model/CMD identities,
  explicitly enable external-content.auto-learn: true as well. New Geyser
  palette entries still require the next server/Geyser startup.

## Phase 3: rendering and predicates

- Reuse the merged static 3D geometry path for experimental conversion, parent models and
  atlases. Do not fork the geometry pipeline.
- Broaden model coverage to composite and advanced special selectors,
  including display_context GUI fallbacks and richer transforms.
- Add flipbook animation, GUI icon and first/third-person transform validation.
- Introduce separately tested coverage for skulls, waypoints, sounds, locales,
  advanced equipment and legacy behaviour on actual clients.
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
- Validate external model identities and required assets before registering them.
  Reject silent overlap with existing Kalo mappings; broader dry-run support
  is still planned.
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

STATUS: Phase 1 scanner and opt-in phase 2 plain-item conversion are
implemented. Full Rainbow parity and real Bedrock client validation are not
complete. No Fabric companion is shipped.

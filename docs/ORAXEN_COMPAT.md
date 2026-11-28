# Oraxen compatibility (`oraxen-compat`)

Kalo answers the Oraxen item API (`io.th0rgal.oraxen.*`), so a plugin written against Oraxen
runs with Kalo as the backend. Oraxen is a stand-in for Kalo (it defines the items other plugins
consume), so Kalo impersonates it; see `docs/NEXO_COMPAT.md` for the rule and the mechanism.

It is embedded in the Kalo jar. Kalo's own `paper-plugin.yml` declares `provides: [Nexo, Oraxen]`,
so `depend: Oraxen` is satisfied and `getPlugin("Oraxen")` returns Kalo. Written in plain Java
because Oraxen is plain Java: its API is static methods, with no Kotlin `object`.

**If the real Oraxen is also installed, it wins.** Paper logs `Ambiguous plugin name 'Oraxen'`
once and carries on; plugins get the real one.

## Signatures

Read from Oraxen's published source (`master`), signatures only. No Oraxen code is copied.
`OraxenApiSignatureTest` checks **equality**, not containment: every public member that exists
here is in the verified list, and nothing else exists, so nothing is invented.

Not verified: Oraxen's `plugin.yml` name (taken from `softdepend: [Oraxen]` in its docs) and the
Javadoc, which could not be reached.

## What is supported (items)

| API | Behaviour |
|---|---|
| `OraxenItems.getItemById`, `getOptionalItemById`, `exists`, `getIdByItem`, `getBuilderByItem` | Answered from Kalo's item registry; a missing id gives `null` / `false` |
| `getNames`, `nameArray`, `nameStream`, `getItemNames`, `getItems`, `itemStream`, `getEntries`, `getEntriesAsMap`, `entryStream`, `getUnexcludedItems()` | Every Kalo item |
| `hasMechanic` | Always `false` — Kalo has no Oraxen mechanics |
| `getMap`, `getUnexcludedItems(File)` | Empty — Kalo does not group items by source file |
| `loadItems` | No-op — Kalo owns loading |
| `ItemBuilder` | Constructors for `Material` and `ItemStack`; type, amount, unbreakable, durability, item model, custom model data, item flags, enchants, custom tags, `hasLores`, `build`, `buildArray`, `getReferenceClone`, `clone`, `regen` |
| `OraxenItemsLoadedEvent` | Fired after all plugins enable and after each Kalo content load |

Builders are copies: changing one never changes Kalo's registry.

### Ids

Flat Oraxen ids map to Kalo keys by the shared rule in `CompatIds`: a bare id matches the key with
that value (first namespace alphabetically if several packs define it); an id containing a colon is
taken as a full key.

## Not supported

Anything missing fails with `NoSuchMethodError` at the call site rather than doing nothing. Left out
deliberately:

- `OraxenItems.ITEM_ID`: Oraxen stores a flat id under it, Kalo stores a namespaced key under its
  own entry, so the value would differ silently.
- `getItemStacksByName`, `getSectionsNames`: meaning not verified.
- `ItemBuilder` name and lore accessors: Oraxen's string encoding for them is not verified.
  Also components (food, tool, equippable), attributes, trims, rarity, potion/fish settings, and
  `getOraxenMeta`/`setOraxenMeta` (need Oraxen-only types).
- `OraxenBlocks`, `OraxenFurniture`, `OraxenPack`, mechanics and their events.

## Verified on a real server

Paper 1.21.4, Java 21, built jar, `examples/testpack`, a probe plugin with `depend: [Oraxen]`
alongside a Nexo probe: `getPlugin("Oraxen")` is Kalo; `getNames()` lists the pack's items;
`getItemById` then `getIdByItem` returns the original id; a builder change does not leak into the
registry; the event class is visible.

**Not verified:** Paper 26.x, Folia, a real third-party Oraxen plugin.

## Licensing

Oraxen is a paid plugin under its own license ("ORAXEN LICENSE 1.0"), which forbids redistributing
the package, in full, in part or modified. This shim copies no Oraxen code, but whether
reimplementing its public API counts as a derivative is a legal question this project has taken a
position on (the API is not the code) without legal advice. Review before distributing widely.

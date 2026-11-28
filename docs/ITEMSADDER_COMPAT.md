# ItemsAdder compatibility (`itemsadder-compat`)

Kalo answers the ItemsAdder item API (`dev.lone.itemsadder.api`), so a plugin written against
ItemsAdder runs with Kalo as the backend. ItemsAdder is a stand-in for Kalo (it defines the items
other plugins consume), so Kalo impersonates it; see `docs/NEXO_COMPAT.md` for the rule and the
mechanism. Embedded in the Kalo jar; Kalo's `paper-plugin.yml` declares
`provides: [Nexo, Oraxen, ItemsAdder]`. Plain Java, like ItemsAdder's published API.

**If the real ItemsAdder is also installed, it wins.** Paper logs
`Ambiguous plugin name 'ItemsAdder'` once and carries on.

## Ids map one to one

ItemsAdder ids are already namespaced (`namespace:id`), exactly how Kalo keys items
(`pack:item`). `CustomStack.getNamespacedID()` is the Kalo key. A bare id is also accepted by
lookups, resolved by the shared rule in `CompatIds`.

## Signatures

Taken from ItemsAdder's published Javadoc (API 4.0.17-beta-12-test-8, a test build; the
deprecated methods may disappear in later releases). `ItemsAdderApiSignatureTest` checks
**equality**: every public member here is in the verified list and nothing else exists, so
nothing is invented.

## Supported

| API | Behaviour |
|---|---|
| `CustomStack.getInstance`, `byItemStack`, `isInRegistry`, `getNamespacedIdsInRegistry` | Answered from Kalo's item registry; a missing id gives `null` / `false` |
| `CustomStack.getNamespace`, `getId`, `getNamespacedID`, `matchNamespacedID`, `getItemStack` | From the Kalo key; `getItemStack` is a fresh copy |
| `CustomStack.isBlock(ItemStack)`, `isFurniture(ItemStack)`, `isBlock()` | From which Kalo registry the key is in: block or furniture (furniture is not reported as a block) |
| `ItemsAdder.getAllItems()`, `(String namespace)`, `(Material)` | Every Kalo item, filtered |
| `ItemsAdder.isCustomItem(ItemStack)`, `isCustomItem(String)`, `getCustomItem(String)`, `areItemsLoaded` | Deprecated in ItemsAdder but kept, since older plugins still call them |
| `CustomBlock.getInstance`, `byItemStack`, `byAlreadyPlaced`, `place` (static and instance), `remove` (static and instance), `isInRegistry`, `getNamespacedIdsInRegistry`, `getBaseBlockData`, `getBlock`, `isPlaced` | Kalo blocks, placed and removed from code (no block events fire). Removal does not drop the item (ItemsAdder's default is unverified) |
| `CustomFurniture.getNamespacedIdsInRegistry`, `byAlreadySpawned(Block)`, `spawn(String, Block)`, `remove(boolean)` | Kalo furniture, placed as a block |
| `CustomFurniture.getEntity()`, `byAlreadySpawned(Entity)`, static `remove(Entity, boolean)` | The entity side answers "none": `null`, `null`, and a no-op, because no Kalo furniture is an entity |
| `ItemsAdderLoadDataEvent` | Fired **asynchronously** (Bukkit refuses to call an async event from the main thread) after all plugins enable and after each Kalo content load |

## Not supported

Missing members fail with `NoSuchMethodError` at the call site rather than doing nothing.

- `CustomStack` display name / item name (encoding unverified), model and config accessors,
  permission, durability, usages, cooldowns, stats, drop, and the complex-furniture, consumable,
  sittable, trade-machine and vehicle queries (Kalo has no equivalent).
- `CustomBlock` break/place effects and sounds, loot and light levels; `CustomFurniture` precise placement, teleport, replace, colour, light level and the deprecated armor stand accessor; the nested `Advanced` classes.
- `CustomEntity`, `CustomCrop` and the other wrappers; their events.
- Resource-pack, recipe and totem methods on `ItemsAdder`.
- The nested `ItemsAdderLoadDataEvent.Cause` enum (values not read); the event's flags are accepted
  and not stored because no accessor is verified.
- The no-argument `public CustomStack()` constructor.

**Blocks and furniture count as items, and can be placed and removed.** As in ItemsAdder, Nexo and Oraxen, a block or
furniture piece has an item form and an id, so the item lookup spans Kalo's item, block and furniture
registries (`KaloItemLookup`). Placing and removing go through the `BlockWorld` service on `KaloPlugin`.
Kalo furniture is block-backed, not an entity, so plugins that rely on furniture *entities* (seats,
hitboxes, rotation) will not work.

## Verified on a real server

Paper 1.21.4, Java 21, built jar, `examples/testpack`, a probe with `depend: [ItemsAdder]`
alongside Nexo and Oraxen probes: `getPlugin("ItemsAdder")` is Kalo; the registry lists the pack's
items, blocks and furniture; `getInstance` then `getItemStack` then `byItemStack` returns the original id; the load event
arrives with `isAsynchronous() == true` and off the main thread.

**Not verified:** the plugin name (`ItemsAdder`, from common usage; the pages read did not state it),
Paper 26.x, Folia, a real third-party ItemsAdder plugin.

## Licensing

ItemsAdder is a paid plugin. Its API repository has no license file and says it contains no
ItemsAdder source; its Terms of Service page was not read. This shim copies no ItemsAdder code, but
whether reimplementing its public API is permitted is a legal question this project has not had
answered. Review the terms before distributing widely.

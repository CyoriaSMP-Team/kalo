# CraftEngine compatibility (`craftengine-compat`)

Kalo answers the part of the CraftEngine API (`net.momirealms.craftengine.bukkit.api`) that uses only
plain Bukkit types. CraftEngine is a stand-in for Kalo (it defines the items, blocks and furniture other
plugins consume), so Kalo impersonates it; see `docs/NEXO_COMPAT.md` for the rule and mechanism.
Embedded in the Kalo jar; the Paper descriptor declares `provides: [Nexo, Oraxen, ItemsAdder, CraftEngine]`.

**If the real CraftEngine is also installed, it wins.** Paper logs `Ambiguous plugin name 'CraftEngine'`
once and carries on.

## Why this is much smaller than the other shims

Most of CraftEngine's API traffics in CraftEngine's own types: `Key`, `BukkitItemDefinition`,
`ImmutableBlockState`, `BukkitFurniture`, `FurnitureDefinition`, `CompoundTag`, and so on. Plugins call
methods on those objects after they get them, so serving `byId(Key)` or `place(Location, Key, ...)` would
mean reimplementing CraftEngine's core, not answering an API. That is deliberately not attempted. Only
members whose parameters and return type are plain Bukkit types or `boolean` are provided.

## Supported

| API | Behaviour |
|---|---|
| `CraftEngineItems.isCustomItem(ItemStack)` | True for any Kalo item, including the item form of a block or furniture piece |
| `CraftEngineBlocks.isCustomBlock(Block)` | True for a Kalo block at that world block (furniture is not a block, as in CraftEngine) |
| `CraftEngineBlocks.remove(Block)`, `remove(Block, boolean)` | Removes a Kalo block, leaving air, **without** dropping its item and without firing block events; CraftEngine's own defaults are unverified. The piston flag changes nothing. Call on the thread that owns the block |
| `CraftEngineFurniture.isFurniture(Entity)`, `isCollisionEntity`, `isSeat`, `remove(Entity)` | Always `false`: Kalo furniture is block-backed and has no entity, so no entity is furniture, a collider or a seat |
| `CraftEngineReloadEvent` | Fired after all plugins enable (`isFirstReload() == true`) and after each later Kalo content load (`false`) |

`CraftEngineReloadEvent` has no public constructor and no `plugin()`: CraftEngine's takes and returns its
own plugin class, which Kalo does not have. Listening to the event works. Kalo builds it through
`CraftEngineReloadEvents`, which is not part of CraftEngine's API.

## Not supported

Anything missing fails with `NoSuchMethodError` at the call site rather than doing nothing.

- `CraftEngineItems`: `byId`, `byItemStack`, `loadedItems`, `getCustomItemId` (CraftEngine `Key` and definition types).
- `CraftEngineBlocks`: every `place`, `byId`, `loadedBlocks`, `getCustomBlockState`, `getBukkitBlockData`,
  `isVanillaBlockState`, and the player/loot overloads of `remove`.
- `CraftEngineFurniture`: `place`, `byId`, `loadedFurniture`, `rayTrace`, and the `getLoadedFurnitureBy...` lookups.
- `CraftEngineImages`, `BukkitAdaptor`, and the other 16 events (block/furniture place, break, interact, resource pack).

## Signatures

Read from CraftEngine's source on its **`dev`** branch. The `main` branch was **not** read, so a stable
release could differ. `CraftEngineApiSignatureTest` checks equality against what was read: every public member
here is in the verified list, and nothing else exists.

Not verified: the plugin name (`CraftEngine`, from the wiki's `softdepend` example; the descriptor could not be
fetched), the Maven version scheme, and the nine event classes whose signatures were not read.

## Verified on a real server

Paper 1.21.4, Java 21, built jar, `examples/testpack`, a probe with `depend: [CraftEngine]`:
`getPlugin("CraftEngine")` is Kalo; the reload event arrives with `isFirstReload() == true`; `isCustomItem` is
true for a Kalo sword, a block item and a furniture item and false for plain stone; `isCustomBlock` is true for a
placed Kalo block and false for air and for furniture; `remove` leaves air; the entity tests are false for an armor
stand. **Not verified:** Paper 26.x, Folia, a real third-party CraftEngine plugin.

## Licensing

CraftEngine is GPL-3.0. The shim copies no CraftEngine code; it reimplements the public method names and
signatures. Whether that creates a work "based on" CraftEngine under the GPL, given Kalo is MIT, is a legal
question this project has taken a position on (the API is not the code) without legal advice. Review before
distributing widely.

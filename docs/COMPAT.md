# Compatibility layer (`compat`)

Kalo answers the API of plugins that are a **stand-in for Kalo**: plugins whose job is to
define the items, blocks, furniture and models other plugins consume. A plugin written
against that API then runs with Kalo as the backend.

**The rule:** if a plugin is a stand-in for Kalo, Kalo impersonates it. If a plugin depends
on Kalo's content (MythicMobs drops, CrackShot weapons), Kalo adapts to it instead and does
not pretend to be it.

| Plugin | Role | Kalo's side |
|---|---|---|
| Nexo | content provider | **impersonated — items done** |
| Oraxen, ItemsAdder, CraftEngine | content providers | to do |
| Neko | content provider | to do |
| ModelEngine | model provider | to do, only the part Kalo has |
| MMOItems | item provider | to do, id lookup only (Kalo has no stat system) |
| MythicMobs, CrackShot | consume content | not impersonated — hooks and importers only |

Import and export already exist for the providers (`/kalo import`, `/kalo export`).
This is the third leg: impersonation at runtime.

## How it works

It is **embedded in the Kalo jar**, not a separate plugin, and needs no setup.

- Kalo's own `paper-plugin.yml` declares `provides: [Nexo]`. Paper then treats
  `depend: Nexo` as satisfied by Kalo, `getPlugin("Nexo")` returns Kalo, and the shim classes
  (`com.nexomc.nexo.*`) are visible to those plugins.
- The names in `provides` are fixed in the descriptor, so a name is listed **only once its
  API exists**. Keep `core/build.gradle.kts` and `KaloCompat.PROVIDED_NAMES` in step.
- The shims are Kotlin, written as `object` + `@JvmStatic` exactly as Nexo does, so Java
  callers (static) and Kotlin callers (`INSTANCE`) both link. `kotlin-stdlib` is fetched by
  `KaloPluginLoader` at startup and is deliberately not shaded or relocated: the signatures
  other plugins link against mention `kotlin.Unit`.

### A real plugin always wins

If the real Nexo is also installed, Paper logs
`Ambiguous plugin name 'Nexo' ...` once and carries on. The real plugin is the one other
plugins get; Kalo's copy is shadowed. The message is noise, not a fault. There is no way to
claim a name only when the real plugin is absent, because `provides` is static.

## Verified on a real server

Paper 1.21.4, Java 21, with the built Kalo jar and a probe plugin that `depend`s on `Nexo`:

- the probe loads, `getPlugin("Nexo")` is Kalo, and `NexoItems` resolves both statically and
  through `INSTANCE`;
- with `examples/testpack` loaded, `itemNames()` lists its items and
  `itemFromId` then `idFromItem` returns the original id;
- with a stand-in "real" Oraxen also present, that one wins, with the ambiguity message and no
  failure.

**Not verified:** Paper 26.x, Folia, a consumer written in Kotlin, a Paper-descriptor
consumer, or any actual third-party Nexo plugin. Test those before relying on this.

## Nexo — what is supported

Written from Nexo's published Javadoc (API 1.8); `NexoApiSignatureTest` pins the signatures.

| API | Behaviour |
|---|---|
| `NexoItems.itemFromId`, `optionalItemFromId`, `exists`, `idFromItem`, `builderFromItem` | Answered from Kalo's item registry |
| `NexoItems.itemNames`, `entries`, `items`, `unexcludedItemNames` | Every Kalo item |
| `NexoItems.hasMechanic` | Always `false` — Kalo has no Nexo mechanics |
| `NexoItems.itemMap`, `unexcludedItems` | Empty — Kalo does not group items by source file |
| `NexoItems.loadItems`, `reloadItem` | No-op — Kalo owns loading |
| `ItemBuilder` | Constructors for `Material`, `ItemStack`, `ItemBuilder`; amount, type, names, lore, durability, unbreakable, flags, custom model data, enchants, custom tags, `build`, `buildArray`, `referenceCopy`, `clone` |
| `NexoItemsLoadedEvent` | Fired after all plugins enable and after each Kalo content load |

Anything not listed fails with `NoSuchMethodError` at the call site rather than doing nothing:
a silent stub would hand players the wrong item. Not yet implemented: `NexoBlocks`,
`NexoFurniture`, `NexoPack`, `NexoMeta`, the mechanic classes, Nexo's other events, and the
component/attribute/potion members of `ItemBuilder`.

### Ids

Nexo ids are flat (`ruby_sword`); Kalo keys are namespaced (`mypack:ruby_sword`). A bare id
matches the Kalo key with that value; if several packs define it, the first namespace
alphabetically wins, so the result does not depend on load order. An id with a colon is taken
as a full Kalo key. See `NexoIds`.

## Licensing

Written from public Javadoc only; no Nexo code is copied. Review each plugin's license before
distributing a shim for it.

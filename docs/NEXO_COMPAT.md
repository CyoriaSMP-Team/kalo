# Nexo API compatibility

Kalo embeds a compatibility surface for the Nexo **item API** in the main
`Kalo-<version>.jar`. There is no second plugin to install. On Paper, the
`paper-plugin.yml` metadata declares `Nexo` as a provided plugin name, so a
plugin dependency on `Nexo` resolves to Kalo.

**Do not install the real Nexo plugin alongside Kalo's compatibility API.**
Both providers use the `Nexo` identity; only one can satisfy that dependency.

Kalo content is already importable from and exportable to Nexo (`/kalo import`,
`/kalo export`). The embedded API adds runtime item-API compatibility.

## What is supported (Phase 1: items)

Written against Nexo's published Javadoc (API 1.8). `NexoApiSignatureTest`
pins the signatures.

| API | Behaviour |
|---|---|
| `NexoItems.itemFromId`, `optionalItemFromId`, `exists`, `idFromItem`, `builderFromItem` | Answered from Kalo's item registry |
| `NexoItems.itemNames`, `entries`, `items`, `unexcludedItemNames` | Every Kalo item |
| `NexoItems.hasMechanic` | Always `false` — Kalo has no Nexo mechanics |
| `NexoItems.itemMap`, `unexcludedItems` | Empty — Kalo does not group items by source file |
| `NexoItems.loadItems`, `reloadItem` | No-op — Kalo owns loading |
| `ItemBuilder` | Constructors for `Material`, `ItemStack`, `ItemBuilder`; amount, type, names, lore, durability, unbreakable, flags, custom model data, enchants, custom tags, `build`, `buildArray`, `referenceCopy`, `clone` |
| `NexoItemsLoadedEvent` | Fired after all plugins enable and after each later Kalo content reload |

### IDs

Nexo ids are flat (`ruby_sword`); Kalo keys are namespaced
(`mypack:ruby_sword`). A bare id matches the Kalo key with that value; if
several packs define it, the first namespace alphabetically wins, so the
result does not depend on load order. An id with a colon is taken as a full
Kalo key. See `NexoIds`.

## Not supported yet

Anything not in the table fails loudly with `NoSuchMethodError` at the call
site rather than doing nothing. Not yet implemented:

- `NexoBlocks`, `NexoFurniture`, `NexoPack`, `NexoMeta` and mechanic classes
- Nexo's other events (for example `NexoMechanicsRegisteredEvent`)
- `ItemBuilder` members for components (food, tool, equippable), attributes,
  potions and trims

## Compatibility notes

- The compatibility API is compiled into the Kalo artifact, and Paper metadata
  advertises the `Nexo` alias. Paper plugins use isolated classloaders: a third-party
  plugin that is itself a Paper plugin must declare `Nexo` as a server dependency
  with `join-classpath: true` to access these classes. A live test with real Nexo
  consumers is still required. Legacy Bukkit plugin classloading should also be
  tested against the actual consumer plugins.
- The Nexo API is a Paper/Folia compatibility surface. The Mohist 1.20.1 fallback
  intentionally does not advertise Nexo because these API classes target Java 21
  and the modern Paper API.
- Kotlin callers use the same `INSTANCE`/static entry points exposed by the
  compatibility classes. A real Kotlin-built Nexo consumer has not yet been
  verified.
- The API surface is based on public Nexo Javadocs. No Nexo implementation
  code is copied.

# Nexo compatibility (`nexo-compat`)

An optional jar, `Kalo-NexoCompat-<version>.jar`, that answers as the plugin named **Nexo** so
a plugin written against the Nexo API uses Kalo as the backend. It is a separate module on
purpose: it is not in the main Kalo jar, and it is opt-in.

Kalo content is already importable from and exportable to Nexo (`/kalo import`, `/kalo export`).
This is the third leg: **impersonation** at runtime.

## Install

1. Install Kalo, then drop `Kalo-NexoCompat-<version>.jar` into `plugins/`.
2. Plugins that `depend`/`softdepend` on `Nexo` now find it.
3. **Never install it beside the real Nexo.** Two plugins cannot share a name; the server will
   refuse the second. Remove one.

## What is supported (Phase 1: items)

Written against Nexo's published Javadoc (API 1.8). `NexoApiSignatureTest` pins the signatures.

| API | Behaviour |
|---|---|
| `NexoItems.itemFromId`, `optionalItemFromId`, `exists`, `idFromItem`, `builderFromItem` | Answered from Kalo's item registry |
| `NexoItems.itemNames`, `entries`, `items`, `unexcludedItemNames` | Every Kalo item |
| `NexoItems.hasMechanic` | Always `false` — Kalo has no Nexo mechanics |
| `NexoItems.itemMap`, `unexcludedItems` | Empty — Kalo does not group items by source file |
| `NexoItems.loadItems`, `reloadItem` | No-op — Kalo owns loading |
| `ItemBuilder` | Constructors for `Material`, `ItemStack`, `ItemBuilder`; amount, type, names, lore, durability, unbreakable, flags, custom model data, enchants, custom tags, `build`, `buildArray`, `referenceCopy`, `clone` |
| `NexoItemsLoadedEvent` | Fired after all plugins enable and after each Kalo content load |

### Ids

Nexo ids are flat (`ruby_sword`); Kalo keys are namespaced (`mypack:ruby_sword`). A bare id
matches the Kalo key with that value; if several packs define it, the first namespace
alphabetically wins, so the result does not depend on load order. An id with a colon is taken
as a full Kalo key. See `NexoIds`.

## Not supported yet

Anything not in the table fails loudly with `NoSuchMethodError` at the call site rather than
doing nothing: a silent stub would hand players wrong items. Not yet implemented:

- `NexoBlocks`, `NexoFurniture`, `NexoPack`, `NexoMeta` and the mechanic classes
- Nexo's other events (for example `NexoMechanicsRegisteredEvent`)
- `ItemBuilder` members for components (food, tool, equippable), attributes, potions, trims

## Known risks — not verified on a server

- **Class visibility.** Kalo is a Paper plugin with an isolated classloader; this jar is a
  legacy-descriptor plugin. Whether a third-party plugin's loader can see `com.nexomc.nexo.*`
  and whether this jar can see `io.kalo.*` has been checked only by compiling and by
  reflection tests, not by running Paper. Test on a real server before relying on it.
- **Binary compatibility with Kotlin callers.** Members are `@JvmStatic` on an `object`, as in
  Nexo, so both Java (static) and Kotlin (`INSTANCE`) callers should link. Unconfirmed against
  a real Kotlin-built Nexo consumer.
- Written from the public Javadoc only. No Nexo code is copied; review Nexo's license before
  distributing.

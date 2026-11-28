# Kalo Provider Contract

Kalo owns custom content. This document is the contract other plugins compile against.
It is deliberately small: three methods, no platform types beyond `ItemStack`.

## Entry point

```java
import io.kalo.Kalo;
import io.kalo.items.ItemIdentity;

ItemIdentity items = Kalo.plugin().items();
```

`Kalo.plugin()` throws if Kalo has not enabled yet. Declare `depend: [Kalo]` (or
`softdepend: [Kalo]` and check `Bukkit.getPluginManager().isPluginEnabled("Kalo")`).

## The three calls

```java
Optional<Key>       idOf(ItemStack stack);   // what is this?
Optional<ItemStack> create(Key key);         // give me that item
boolean             isCustom(ItemStack stack);
```

### `idOf`

Returns the content key of a stack, or empty when the stack is not custom content.

Two markers are read, in this order:

1. The Kalo item id in the persistent data container. This is authoritative because it
   is the only marker that also carries behaviour.
2. The vanilla `item_model` component, **but only if the server has already learned how
   to rebuild it** (see `external-items.yml`). A model that only exists in a resource
   pack is not enough.

That second rule exists so the round trip below always holds. An id `idOf` returns is an
id `create` can rebuild.

### `create`

Returns a fresh stack for a key. Works for items, armor, and learned external content.
Returns empty for anything unknown — never a placeholder item.

### Round trip

```java
Key id = items.idOf(stack).orElseThrow();
ItemStack copy = items.create(id).orElseThrow();
```

`copy` is a new stack with the same material, model and Kalo behaviour marker. It is not
reference-equal, so callers may mutate it freely.

## Id format

`idOf` returns the full key, always namespaced:

```text
teddy_loy:teddy_krathong
kalo:ruby
plny_christmas_2025_set:armor_boots
```

Some plugins store a bare id (`teddy_krathong`). That form is supported on the way in by
the compatibility bridges, not by `ItemIdentity` itself. If you need to display an id,
use `Key#asString()` so two packs with the same value stay distinguishable.

## What Kalo guarantees

| Guarantee | Detail |
|---|---|
| `create(idOf(stack))` resolves | For every stack where `idOf` is present |
| No guessed items | An unknown key never becomes a placeholder |
| Reload safe | Registries may be swapped under you; re-read, do not cache keys forever |
| Thread safe | Callable from any thread; an id may briefly stop resolving during a reload |
| No NMS | Only `org.bukkit` and Adventure types |

## What Kalo does not guarantee

| Not guaranteed | Why |
|---|---|
| Stable ids across packs | Pack authors own their namespace |
| That `idOf` sees another plugin's private markers | Only Kalo's PDC id and `item_model` are read |
| Item equality by `ItemStack#equals` | Use `idOf`, then compare keys |
| Behaviour on a stack you built yourself | Behaviour lives in the Kalo item, not in the key |

## Consumer checklist

- Declare the dependency in `plugin.yml`.
- Call `idOf` instead of matching display names, lore or `custom_model_data`.
- Store `key.asString()`, never `ItemStack` serialization, when persisting.
- Treat `Optional.empty()` as "not custom content", not as an error.
- Do not cache `ItemStack` instances across a Kalo reload.

## Compatibility bridges

Plugins that only know another provider are served by a bridge that implements that
provider's public API over `ItemIdentity`:

```text
kalo-compat-nexo   ->  com.nexomc.nexo.api.NexoItems
```

A bridge declares the provider's plugin name because the consumer hardcodes it. Install a
bridge **or** the real provider, never both — Bukkit refuses duplicate plugin names.

### What the Nexo bridge implements

Only the members installed consumers link against, discovered by scanning their bytecode:

| Type | Members |
|---|---|
| `com.nexomc.nexo.api.NexoItems` | `itemFromId(String)`, `idFromItem(ItemStack)`, `exists(String)`, `exists(ItemStack)` |
| `com.nexomc.nexo.items.ItemBuilder` | `build()` |
| `com.nexomc.nexo.glyphs.GlyphTag` | `INSTANCE`, `getRESOLVER()` |
| `com.nexomc.nexo.api.events.NexoItemsLoadedEvent` | Bukkit event, fired after every plugin is enabled |

`NexoItemsLoadedEvent` is fired on `ServerLoadEvent`, because plugins enable one after
another: zMenu registers its listener many seconds after the bridge's own `onEnable`, so a
broadcast from `onEnable` reaches nobody. A 200-tick fallback covers forks that do not fire
`ServerLoadEvent`.

Ids resolve in both forms. `itemFromId("teddy_krathong")` and
`itemFromId("teddy_loy:teddy_krathong")` return the same item. `idFromItem` returns the bare
value when exactly one pack defines it, and the namespaced key when two packs collide — so
the result always round trips.

Bridges are written from the consumer's observed bytecode references, not from the
provider's source, so they stay inside Kalo's clean-room provenance rules.

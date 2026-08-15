# Kalo Content IR — design

The intermediate representation that makes "define once, compile to Java **and** Bedrock"
structurally possible rather than aspirational.

> **Status:** implemented for items in `io.kalo.content.item.definition`, with the Java
> compilers in `io.kalo.platform.java`. Blocks, furniture and armor follow the same
> pattern. The Bedrock compiler is Phase 2 — `BedrockOptions` already exists so that it
> is an addition rather than a re-architecture.

---

## Why this exists

Neko's content model is expressed in Bukkit types end to end:

```java
public record ItemProperties(Material type, Component name, List<Component> lore)
//                           ^^^^^^^^ org.bukkit.Material
```

`ContentType.load` takes a Bukkit `ConfigurationSection`; `Item.itemStack()` returns a
Bukkit `ItemStack`, eagerly constructed in the `ItemImpl` constructor. There is no point
in the pipeline where content exists as anything other than Java-platform data.

Bolting a Bedrock compiler onto that means writing a translator that reads Java decisions
and reverse-engineers the intent behind them — "this is `PAPER` with item_model
`mypack:ruby_sword`, so on Bedrock it should be… some base item, guessing". Parity
becomes a maintenance treadmill and every new content type has to be solved twice, in
opposite directions.

The IR moves the decision point earlier. YAML describes **intent**; each platform
compiler makes its own platform decisions from that intent independently.

```
                        ┌──────────────────────────┐
  packs/*/configs/*.yml │      Kalo Definition     │  platform-neutral
        ──────────────► │   (parsed, validated)    │
                        └────────────┬─────────────┘
                                     │
                      ┌──────────────┴──────────────┐
                      ▼                             ▼
             ┌─────────────────┐          ┌───────────────────┐
             │  JavaCompiler   │          │  BedrockCompiler  │
             │  (Phase 1)      │          │  (Phase 2)        │
             └────────┬────────┘          └─────────┬─────────┘
                      │                             │
        ┌─────────────┴──────────┐        ┌─────────┴──────────┐
        ▼                        ▼        ▼                    ▼
  resourcepack.zip        Bukkit ItemStack  .mcpack      Geyser mappings
  (models, textures,      (runtime,                      (item/block
   lang, item defs)        Material chosen               translation)
                           by this compiler)
```

---

## Core rule

> **No `org.bukkit.*`, no Geyser type, and no pack-format constant may appear in the IR.**

If a field can only be satisfied by naming a Java concept, it belongs in the Java
compiler's config, not the IR. The test is: *could a Bedrock compiler consume this field
without asking "what did they mean?"*

`Material` fails that test. A Kalo item says "I look like this model and I stack to 64";
the Java compiler decides that means `PAPER` with an `item_model` component, and the
Bedrock compiler decides it means a custom item entry in the mappings. Neither decision
leaks into the definition.

---

## Shape

### `ItemDefinition`

```java
public record ItemDefinition(
    Key key,                       // mypack:ruby_sword — namespace comes from the pack
    DisplayDefinition display,     // name, lore, gloss
    ModelDefinition model,         // how it looks
    ItemBehaviour behaviour,       // stack size, durability, food, wearable…
    List<FeatureBuilder> features  // attached behaviours
) {}
```

### `DisplayDefinition`

```java
public record DisplayDefinition(
    @Nullable Component name,      // Adventure is platform-neutral enough to keep
    List<Component> lore,
    boolean enchantmentGlint
) {}
```

Adventure's `Component` is the one third-party type allowed in. It is a serialization
format, not a platform binding — Bedrock text can be rendered from it via Geyser's own
translation, and MiniMessage is already the authoring format.

### `ModelDefinition`

The crux. Java 1.21.4+ and Bedrock both ultimately want "a geometry and a set of
textures", but express it completely differently. The IR describes the *source assets*
and the *intent*, never the output format.

```java
public sealed interface ModelDefinition {

    /** A flat sprite — the overwhelmingly common case. */
    record Sprite(Key texture) implements ModelDefinition {}

    /** A model file authored in Blockbench, shipped with the pack. */
    record Custom(Key model, Map<String, Key> textures) implements ModelDefinition {}

    /** Reuse a vanilla model verbatim. */
    record Vanilla(Key model) implements ModelDefinition {}
}
```

- **Java compiler** turns `Sprite` into `assets/<ns>/items/<name>.json` (an item
  definition with a `minecraft:model` entry) plus a generated
  `assets/<ns>/models/item/<name>.json` with parent `item/generated`.
- **Bedrock compiler** turns the same `Sprite` into an item texture entry in
  `textures/item_texture.json` and a Geyser mapping — no model file at all, because
  Bedrock sprite items do not need one.

Same input, two correct and *natively idiomatic* outputs. That is the property worth
protecting.

### `ItemBehaviour`

```java
public record ItemBehaviour(
    int maxStackSize,
    @Nullable Integer maxDurability,
    boolean fireResistant
) {}
```

Deliberately small for v0.1. Every field here must have a meaningful answer on both
platforms; anything Java-only goes in a Java-compiler escape hatch (see below), not here.

---

## Escape hatches

Purity that blocks real work is not a virtue. Two explicit, *named* escape hatches:

```yaml
ruby_sword:
  type: item
  model:
    sprite: mypack:item/ruby_sword

  java:                 # consumed only by JavaCompiler, ignored by Bedrock
    base_material: NETHERITE_SWORD
    components:
      minecraft:attribute_modifiers: [...]

  bedrock:              # consumed only by BedrockCompiler, ignored by Java
    enabled: true
    icon: ruby_sword
```

These are opt-in, clearly labelled, and their presence is a visible signal that a
definition has stopped being portable. The IR itself stays clean; the platform sections
hang off the side of it.

`bedrock.enabled` defaults to `true` — Bedrock support is not a paid tier and should not
be an opt-in either.

---

## What changes in the existing code

| Current | Becomes |
|---|---|
| `ItemProperties(Material, name, lore)` | `ItemDefinition` as above; `Material` moves into `JavaItemCompiler` |
| `ContentType.load(Registries, ConfigurationSection)` | `ContentType.load(PackContext, ConfigurationSection)` — `PackContext` carries the pack's namespace, fixing the `minecraft:` namespace bug in the same change |
| `ItemImpl` builds its `ItemStack` in the constructor | The Java compiler builds it; `ItemImpl` holds the definition |
| `ResourcePackGenerationEvent` fired per item with nothing listening | `JavaResourcePackCompiler` walks definitions and writes real assets; the event stays as the third-party extension point |

The registry layer, feature system, and manager lifecycle are unaffected — they are
already generic over `Content` and need no changes. That is the part of Neko worth
keeping, and it survives this intact.

---

## Sequencing

1. ✅ Land `ItemDefinition` + `ModelDefinition` and re-point `ItemType` at them.
2. ✅ Write `JavaItemCompiler` (definition → `ItemStack`) and `JavaPackCompiler`
   (definition → pack assets). **This is the first change a user can see in-game.**
3. Blocks, furniture, armor each add a `*Definition` and a case in both compilers.
4. Phase 2 adds `BedrockCompiler` against an IR that already exists and is already
   exercised by four content types — no rewrite, no translator.

Step 4 is the whole point. Everything before it is done in service of making it a normal
week's work rather than a re-architecture.

`JavaPackCompilerTest` includes a test named `javaOptionsIsTheOnlyPlaceMaterialAppears`,
which exists to make the rule at the top of this document fail loudly if someone
reintroduces a platform type into the definition layer.

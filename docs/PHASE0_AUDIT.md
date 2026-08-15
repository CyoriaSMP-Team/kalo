# Phase 0 — Resurrection Audit

Audit of the Neko codebase as the bootstrap for **Kalo**.
Date: 2026-08-16 · Commit: `971732d` · 62 source files, 48 commits, last activity 2026-03-27.

---

## 1. Verdict

Neko is an **architecture sketch, not a working custom-content plugin**. The registry
layer, content-type dispatch, and feature event bus are genuinely well-shaped and worth
keeping. Everything downstream of them — the part that actually makes custom content
appear in-game — does not exist yet.

Concretely: **Kalo Phase 1 is a build-out on Neko's skeleton, not an extension of a
working product.** Plan accordingly.

### What actually works

| Area | State |
|---|---|
| Registry system (lock/unlock/clear/merge, direct + entry-builder variants) | Solid, reusable |
| Content type dispatch (`ContentType` → registries) | Solid, reusable |
| Feature system (factory → builder → per-content event bus) | Good idea, needs fixes |
| Pack discovery + YAML loading (`pack.yml` + `configs/`) | Works |
| Manager lifecycle (`preload`/`start`/`end` + `Reloadable`) | Works |
| Commands (`/neko reload`, `/neko give`) | Works |

### What does not exist

| Advertised | Reality |
|---|---|
| Custom item **models / textures** | **None.** `ItemProperties` is `(Material, name, lore)`. No model, no texture, no CMD, no item_model. |
| Resource pack generation | **Produces an empty pack.** `ResourcePackManagerImpl` builds a `ResourcePack`, fires an event nothing listens to, and writes it. No models, no textures, no lang. |
| Pack hosting | `creative-server` is a declared dependency and is **never imported**. |
| Custom blocks / furniture / armor / bows / GUI / recipes / sounds / glyphs | **None.** Only `ItemType` exists. |
| Bedrock anything | **None.** No abstraction that would allow it either — see §4. |

So the current plugin's total observable behaviour is: register a `PAPER` item with a
name and lore, give it to a player, and write a resource pack containing nothing.

---

## 2. Blocking platform findings

### 2.1 Minecraft moved to calendar versioning — the target is three generations stale

`gradle.properties` pins `minecraft_version=1.20.1`; `paperPluginYaml.apiVersion = "1.20"`.

Meanwhile Paper's current releases are **26.1.x and 26.2** — Minecraft has left the
`1.21.x` scheme entirely. And the Maven coordinates changed with it:

```
1.21.11-R0.1-SNAPSHOT      ← old scheme, ends at 1.21.11
26.1.1.build.23-alpha      ← new scheme
26.2.build.112-stable      ← current stable
```

Two consequences:

1. **`-R0.1-SNAPSHOT` no longer exists.** `paper-conventions.gradle.kts` builds the
   dependency string as `"io.papermc.paper:paper-api:$minecraftVersion-R0.1-SNAPSHOT"`.
   That template cannot express a modern Paper version at all — the build file needs
   changing, not just the property.
2. Builds must now **pin an exact build number**, which is stricter but more
   reproducible than a rolling snapshot.

`build.gradle.kts` declares a `supportedVersions` list running `1.20` → `1.21.11`. That
list is **declared and never referenced anywhere** — dead code expressing an intention
that was never implemented.

Moving the baseline to 26.2 cascades into three further forced upgrades, each discovered
by the build failing on the previous one:

| Forced by | Was | Must become |
|---|---|---|
| `paper-api:26.2` requires JVM 25+ | Java 21 toolchain | **Java 25** |
| `run-paper` 3.1.0 declares `plugin-api-version 9.7.0` | Gradle wrapper 9.0.0 | **Gradle 9.7.0** |
| Java 25 toolchain not installed locally | — | **foojay-resolver** in `settings.gradle.kts` |

Java 25 is not optional: it is the floor for running Minecraft 26.x at all, so it is also
the floor for the plugin.

### 2.1b The shadow jar is 3.1 MB of mostly nothing

`standard-conventions.gradle.kts` applied `kotlin("jvm")` to every module and
`build.gradle.kts` relocated `kotlin` into the shaded namespace. The repository contains
**zero `.kt` files** — every `compileKotlin` task reports `NO-SOURCE`. The result was
**1043 Kotlin stdlib classes shaded into the released plugin jar** for no reason,
plus a permanent "Inconsistent JVM Target Compatibility" warning on every build.

Removing the Kotlin plugin from the module conventions (it stays in `buildSrc`, where
`kotlin-dsl` genuinely needs it) drops the dependency and the warning together.

### 2.2 The item model system changed under us — "Auto CMD" is obsolete

1.21.4 replaced CustomModelData-based model swapping with **item definitions** in
`assets/<namespace>/items/*.json` plus the `minecraft:item_model` component. Any
resource-pack compiler written against the 1.20.1 mental model (pack every custom model
into the base item's `overrides` array, hand out CMD integers) targets a system that has
been legacy for two major generations.

**This must be settled before any compiler code is written**, because it determines the
shape of the item definition in YAML. Recommendation: baseline **26.2**, item-definition
model system only, no CMD path at all. Kalo has no existing users to keep compatible —
this is the one moment where dropping legacy is free.

### 2.3 Dependency drift

| Dependency | Pinned | Current | Note |
|---|---|---|---|
| `paper-api` | `1.20.1-R0.1-SNAPSHOT` | `26.2.build.112-stable` | Scheme change, see §2.1 |
| `org.incendo:cloud-paper` | `2.0.0-beta.14` | `2.0.0` | Stable is out |
| `xyz.jpenilla.run-paper` | `2.3.1` | `3.1.0` | Major bump; needed for 26.x server downloads |
| `com.gradleup.shadow` | `9.4.0` | `9.6.1` | Minor |
| `resource-factory-paper-convention` | `1.3.1` | `1.3.1` | Current |
| `team.unnamed:creative-*` | `1.7.3` | `1.7.3` | Latest published — **and stale, see §2.4** |

`LegacyPaperCommandManager` is used in `CommandManager`; Cloud 2.0 stable ships
`PaperCommandManager` with native Brigadier via Paper's own Commands API.

### 2.4 The resource-pack library is abandoned relative to the target — **strategic**

Creative is the library the entire resource-pack engine is built on. Its latest published
release is **1.7.3**, Maven Central `lastUpdated` **2024-07-02**, and the class files
inside the jar are dated **2024-04-19**.

Minecraft 1.21.4 shipped in December 2024. So Creative predates the item-definition
system entirely. Inspecting `creative-api-1.7.3.jar` confirms it:

```
team/unnamed/creative/model/Model.class
team/unnamed/creative/model/ItemOverride.class      ← legacy CMD overrides
team/unnamed/creative/model/ItemPredicate.class     ← legacy CMD predicates
```

There is no item-definition type, and nothing for any format introduced since. **The
library cannot express the model system Kalo needs to target.**

Two implications, one tactical and one strategic:

- **Tactical (Phase 1):** `ResourceContainer.unknownFile(String, Writable)` exists, so
  modern `assets/<ns>/items/*.json` can be emitted by hand while Creative still handles
  the version-agnostic parts it is good at — the zip container, textures, sounds, fonts,
  lang. This is enough to ship.
- **Strategic (Phase 1.5):** resource-pack compilation *is* Kalo's core competency.
  Depending on an unmaintained third-party library for it means being blocked on every
  future Minecraft release by someone else's release schedule. Kalo should own its pack
  writer. This is not urgent in week one, but it should be a deliberate decision rather
  than something discovered during a launch.

---

## 3. Bugs found

Ordered by how much they will hurt.

### 3.1 Item keys silently land in the `minecraft:` namespace — **critical**

`ItemType.load` (`ItemType.java:52`):

```java
Key key = Key.key(config.getName());
```

`config.getName()` is the YAML section name, e.g. `ruby_sword`. Adventure's
`Key.key(String)` defaults the namespace to `minecraft` when no `:` is present. So a
pack named `mypack` defining `ruby_sword` registers **`minecraft:ruby_sword`**, not
`mypack:ruby_sword`.

Effects: two packs defining the same content name collide and throw
`"There is conflict with the registry"`, killing pack loading; and every generated
resource-pack path would be written under `assets/minecraft/`. The pack id is loaded in
`PackLoader` and then never threaded down to content loading — `ContentType.load` has no
access to it.

**Fix requires an API change**: `ContentType.load` needs the owning pack (or at least its
namespace) in its signature.

### 3.2 Data folder points at the wrong directory — **high**

`Constants.java:8`:

```java
public static final File DATA_FOLDER = new File("plugins", PLUGIN_ID); // "plugins/neko"
```

Paper creates the data folder from the plugin *name* — `plugins/Neko`. On any
case-sensitive filesystem (i.e. every Linux server) these are two different directories.
Packs get read from `plugins/neko/packs` while the server created `plugins/Neko`. Also
it is a relative path resolved against the process CWD rather than
`plugin.getDataFolder()`.

### 3.3 Registry is not actually thread-safe — **high**

`ScalableRegistry` synchronizes `lock`/`unlock`/`merge` but **not** `get`, `entries`,
`clear`, or the `register` methods in either subclass — all of which touch the same plain
`HashMap`. Resource pack generation runs on `CompletableFuture.runAsync`, i.e. a
ForkJoinPool thread, and iterates `registries().item()` while the main thread may still
be mutating it. The commit `158ca0d "refactor: thread-safe ScalableRegistry"` only did
half the job.

Use a `ConcurrentHashMap` and make the locked-state check-then-act atomic.

### 3.4 Permission nodes reference a different project — **medium**

`CommandManager` registers commands under `neko` but guards them with
`mint.command.reload` / `mint.command.give`. Leftover from an earlier project name.
Every server admin's permission config would silently fail to match.

### 3.5 `ItemType.load` swallows every exception — **medium**

```java
} catch (Exception e) {
    return false;
}
```

No logging, no message, no stack trace. Any malformed item — including the very common
`Material.valueOf` failure on a bad `type:` — vanishes with at most a generic
"Failed to load" line from the caller. This is the single worst thing for content-creator
experience in the codebase.

### 3.6 Non-YAML files are parsed as configs — **medium**

`PackLoader.loadConfigs` runs `Files.listFilesRecursively(configsFolder)` and feeds
**every regular file** to `YamlConfiguration::loadConfiguration`. A `.png` in `configs/`
gets parsed as YAML. Filter on extension.

### 3.7 Leaked file-walk streams — **medium**

`Files.listFilesRecursively` returns an unclosed `java.nio.file.Files.walk` stream. Those
hold an open directory handle and must be closed. It also passes `FOLLOW_LINKS`, which
will loop forever on a symlink cycle.

### 3.8 Feature event bus is exact-class-match and not thread-safe — **medium**

`FeatureEventBusImpl.call` dispatches via `subscribers.get(event.getClass())`. Subscribing
to a supertype never fires. The backing `HashMultimap` is unsynchronized and `call` is
invoked from the async pack-generation thread.

### 3.9 `getNekoItemByStack` is O(n) per lookup — **medium**

`ContentManagerImpl.getNekoItemByStack` streams every registered item and calls
`isSimilar` on each. `isSimilar` itself reads the PDC key. Since the PDC already stores
the exact item key, this should be a single PDC read followed by one map lookup. As
written, an inventory-click handler on a server with a few thousand items is a real
per-event cost.

### 3.10 Placeholder pack format — **low, but ships broken**

`ResourcePackManagerImpl:42`:

```java
PackFormat.format(99, 1, Integer.MAX_VALUE)
```

Format `99` with a range of `1..Integer.MAX_VALUE` is a stand-in that claims compatibility
with every pack format that has ever existed. Needs a real value derived from the target
Minecraft version.

### 3.11 Korean debug string in a thrown exception — **low**

`PackLoader.java:74`:

```java
.orElseThrow(() -> new IllegalStateException("그 사이에 인젝션은 말이 안됨!!!!"));
```

Untranslated developer aside, shipping in an exception message.

### 3.12 Assorted

- `ContentsPackConfigSchema` imports `FeatureFactory`, `RegistryManager`, `Constants`,
  `Objects` and uses none of them.
- Content validation happens twice: `ContentConfigSchema` validates features exist, then
  `ItemType.load` re-resolves them and throws on failure.
- `ConfigSchema.Result` is mutable with a public `failed()` — fine internally, but it is
  reachable from the `ContentType` API surface that third parties implement.
- `ItemBuilder` falls back to `Component.translatable(item)` when no name is set, but
  nothing ever generates a lang file, so unnamed items display a raw translation key.
- No tests. No CI. Zero test sources in the repo.

---

## 4. The architectural decision that matters most

Kalo's differentiator is *one definition compiles to both Java and Bedrock*. Nothing in
the current design allows that, and the reason is subtle enough to be worth stating
plainly:

**`ItemProperties` holds `org.bukkit.Material`.** The content model is expressed directly
in Java-platform types, all the way down. `ContentType.load` takes a Bukkit
`ConfigurationSection` and produces Bukkit-typed objects. `Item.itemStack()` returns a
Bukkit `ItemStack` and is built eagerly in the constructor.

If the Bedrock compiler is added in Phase 2 against this model, it will be a translator
sitting on top of Java-shaped data, guessing at intent — exactly the architecture that
makes cross-platform parity impossible to maintain.

The fix belongs in **Phase 1, before any compiler is written**:

```
YAML  →  Kalo IR (platform-neutral)  →  ┬→ JavaCompiler   → resourcepack.zip
                                        └→ BedrockCompiler → .mcpack + Geyser mappings
```

The IR describes *intent* — "this item looks like this model, has this display name,
behaves like this" — with no Bukkit, no Geyser, and no pack-format types in it. Platform
compilers own every platform-specific decision. `Material` becomes a Java-compiler
concern; Bedrock picks its own base item independently.

This is the single highest-leverage change in Phase 1 and it gets more expensive every
week it is deferred.

---

## 5. Recommended Phase 0 → 1 order

1. **Set the baseline to Paper 26.2**, fix the `-R0.1-SNAPSHOT` template, bump
   run-paper/Cloud/shadow. Confirm the server boots on both Paper and Folia.
2. **Fix §3.1, §3.2, §3.3, §3.5** — these are correctness bugs that would corrupt any
   work built on top of them.
3. **Rebrand to Kalo**, keeping the MIT notice for Woobeen Jeon and adding Kalo's own.
   Do this *after* the build is green, so a broken build is never confused with a broken
   rename.
4. **Introduce the IR** and re-point `ItemType` at it.
5. **Then** write the Java resource-pack compiler: item definitions, textures, lang.
   This is the first commit where the plugin does something a user can see.

Everything past that — blocks, furniture, armor — is comparatively mechanical once the
IR and the compiler exist.

---

## 6. What was actually done

Phase 0 completed, and Phase 1 got further than planned because several findings were
cheaper to fix now than to work around.

| | Before | After |
|---|---|---|
| Minecraft | 1.20.1 | 26.2 |
| Java | 21 | 25 |
| Gradle | 9.0.0 | 9.7.0 |
| Cloud | 2.0.0-beta.14 (`LegacyPaperCommandManager`) | 2.0.0 (`PaperCommandManager`) |
| Pack library | Creative 1.7.3 (incompatible) | own writer, `io.kalo.pack` |
| Shadow jar | 3,137,188 bytes | 108,155 bytes |
| Tests | none | 12, all passing |
| Item models | not implemented | 1.21.4+ item definitions |
| Package | `io.github.bindglam.neko` | `io.kalo` |

Every bug in §3 is fixed. Two more surfaced during verification:

- The pack compiler called `item.definition()` **outside** its own try block, so a single
  broken item aborted asset generation for every other item in the pack. Caught by a test
  written for exactly that property.
- `parseBehaviour` read durability as
  `config.contains(k) ? config.getInt(k) : defaults.maxDurability()`. That ternary mixes
  `int` and `Integer`, so Java unboxes **both** branches and the null default threw NPE —
  meaning every item that did not declare a durability failed to load, which on a real
  pack is most of them. Caught on the first live server run, and only visible because the
  §3.5 error-logging fix now names the item, the file and the stack trace instead of
  swallowing it.

### Verification

`./gradlew build` green; **18 tests passing**, with the pack assertions checked against
formats extracted from the vanilla 26.2 client jar rather than from memory.

A live Paper 26.2 server (Java 25) starts clean with **zero Kalo warnings**:

```
[Kalo] Enabling Kalo v0.0.1
[RegistryManagerImpl] Initializing registries...
[ContentManagerImpl] Loading packs...
[Kalo] Hello testpack:greeting_paper!
[Kalo] Argument 'msg' : compiled from the IR
[ContentManagerImpl] Loaded 1 packs!
[ResourcePackManagerImpl] Successfully generated resource pack (4 files)
```

`generated.zip` contains exactly what it should:

```
pack.mcmeta                                    {"pack_format":88,...}
assets/testpack/items/ruby_sword.json          {"model":{"type":"minecraft:model",...}}
assets/testpack/models/item/ruby_sword.json    {"parent":"minecraft:item/generated",...}
assets/testpack/textures/item/ruby_sword.png   copied from the pack
assets/testpack/lang/en_us.json                3 entries
```

`plain_apple` correctly emits no item definition — its `item_model` points straight at
`minecraft:apple`.

### Still open

- Joining with a real client to confirm the custom model renders, and `/kalo give`
  in-game. Everything up to the client download is verified.
- `setFireResistant` is deprecated in favour of damage-type tags, but Paper exposes no
  `Tag` constant for `minecraft:is_fire`; revisit when there is a non-reflective path.
- `PackFormats.CURRENT` must be re-verified against the client jar on every Minecraft bump.
- Blocks, furniture and armor — each needs a `*Definition` plus a case in each compiler.

## 7. Attribution

Neko is MIT-licensed, Copyright (c) 2026 Woobeen Jeon. The notice stays in `LICENSE`
verbatim; Kalo's own copyright is added alongside it, not in place of it.

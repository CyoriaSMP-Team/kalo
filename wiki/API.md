# 🔌 API Reference

## Add-on API

### Register Custom Content Type

```java
@EventHandler
public void onRegistryInitialize(RegistryInitializeEvent event) {
    event.getRegistries().types().register(MyType.KEY, new MyType());
    event.getRegistries().features().register(MyFeature.KEY, new MyFeature.Factory());
}
```

### ContentType Interface

```java
public interface ContentType<T extends Content> {
    @NotNull String id();
    @NotNull Class<T> clazz();
    boolean load(@NotNull PackContext pack, @NotNull Registries registries, 
                 @NotNull ConfigurationSection config);
    @NotNull Iterable<T> contents(@NotNull Registries registries);
    void compilePack(@NotNull ResourcePack resourcePack, @NotNull Iterable<T> contents);
}
```

## Events

| Event | Description |
|-------|-------------|
| `RegistryInitializeEvent` | Register custom types |
| `ResourcePackGenerationEvent` | Add custom assets |
| `AsyncResourcePackGenerationEvent` | Async pack generation |

## Placeholders (PlaceholderAPI)

| Placeholder | Description |
|-------------|-------------|
| `%kalo_held_id%` | Kalo id of held item |
| `%kalo_held_name%` | Display name |
| `%kalo_is_held_<key>%` | Is holding item |
| `%kalo_count_<key>%` | Count in inventory |
| `%kalo_items%` | Total items |
| `%kalo_blocks%` | Total blocks |

## Content Model

### ItemDefinition

```java
public record ItemDefinition(
    Key key,
    DisplayDefinition display,
    ModelDefinition model,
    ItemBehaviour behaviour,
    List<FeatureBuilder> features
) {}
```

### ModelDefinition

```java
public sealed interface ModelDefinition {
    record Sprite(Key texture) implements ModelDefinition {}
    record Custom(Key model, Map<String, Key> textures) implements ModelDefinition {}
    record Vanilla(Key model) implements ModelDefinition {}
}
```

### BlockDefinition

```java
public record BlockDefinition(
    Key key,
    DisplayProperties display,
    BlockModelDefinition model,
    BlockBehaviour behaviour,
    JavaBlockOptions java,
    BedrockOptions bedrock
) {}
```

## Utilities

### ContentManager

```java
ContentManager manager = Kalo.plugin().contentManager();
Optional<Item> item = manager.getItemByStack(itemStack);
```

### RegistryManager

```java
Registries registries = RegistryManager.GlobalRegistries.registries();
registries.item().entries().forEach(entry -> {
    // Process each item
});
```

---

## See Also

- [[Configuration]] — Config options
- [[Commands]] — Available commands

---

## 📜 Footer

**Kalo** — Open Custom Content Engine for Minecraft

[GitHub](https://github.com/CyoriaSMP-Team/kalo) • [Issues](https://github.com/CyoriaSMP-Team/kalo/issues) • [Discord](https://discord.gg/kalo) (coming soon)

> **Version:** 0.1.0 • **License:** MIT • **Java:** 21+ / 25

*Build once. Play everywhere.*

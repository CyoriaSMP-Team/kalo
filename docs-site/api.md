---
title: API Reference
---

# 🔌 API Reference

Kalo provides a public API for third-party plugins.

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

### RegistryInitializeEvent

Fired when registries are initialized. Register custom types here.

### ResourcePackGenerationEvent

Fired when resource pack is generated. Add custom assets here.

### AsyncResourcePackGenerationEvent

Async version of pack generation event.

## Placeholders (PlaceholderAPI)

| Placeholder | Description |
|-------------|-------------|
| `%kalo_held_id%` | Kalo id of held item |
| `%kalo_held_name%` | Display name of held item |
| `%kalo_is_held_<key>%` | Is player holding this item |
| `%kalo_count_<key>%` | Count in inventory |
| `%kalo_items%` | Total registered items |
| `%kalo_blocks%` | Total registered blocks |

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
    // Process each registered item
});
```

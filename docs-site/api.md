---
layout: default
title: "🔌 API Reference"
nav_order: 11
---

# 🔌 API Reference

Developer API for building Kalo add-ons.

---

## Maven/Gradle

### Maven

```xml
<repository>
  <id>jitpack.io</id>
  <url>https://jitpack.io</url>
</repository>

<dependency>
  <groupId>com.github.CyoriaSMP-Team</groupId>
  <artifactId>kalo</artifactId>
  <version>0.1.0</version>
  <scope>provided</scope>
</dependency>
```

### Gradle

```groovy
repositories {
  maven { url 'https://jitpack.io' }
}

dependencies {
  compileOnly 'com.github.CyoriaSMP-Team:kalo:0.1.0'
}
```

## Core Classes

### KaloPlugin

Main plugin instance.

```java
KaloPlugin kalo = (KaloPlugin) Bukkit.getPluginManager().getPlugin("Kalo");
```

### ContentType Registry

Access all registered content types.

```java
Registry<ContentType> types = kalo.getRegistries().contentTypes();
```

### Content Pack Manager

Load and manage content packs.

```java
ContentPackManager packManager = kalo.getContentPackManager();
ContentPack pack = packManager.getPack("mypack");
```

## API Methods

### Get Item

```java
Optional<ItemStack> item = kalo.getItemRegistry()
    .getItem(NamespacedKey.of("mypack", "ruby_sword"));
```

### Get Block

```java
Optional<KaloBlock> block = kalo.getBlockRegistry()
    .getBlock(NamespacedKey.of("mypack", "ruby_block"));
```

### Get Furniture

```java
Optional<Furniture> furniture = kalo.getFurnitureRegistry()
    .getFurniture(NamespacedKey.of("mypack", "ruby_chair"));
```

### Check if Kalo Block

```java
if (KaloBlock.isKaloBlock(block)) {
    KaloBlock kaloBlock = KaloBlock.fromBlock(block);
    NamespacedKey key = kaloBlock.getKey();
}
```

## Events

### KaloItemInteractEvent

Fired when a player interacts with a Kalo item.

```java
@EventHandler
public void onItemInteract(KaloItemInteractEvent event) {
    Player player = event.getPlayer();
    ItemStack item = event.getItem();
    InteractionAction action = event.getAction();
}
```

### KaloBlockPlaceEvent

Fired when a Kalo block is placed.

```java
@EventHandler
public void onBlockPlace(KaloBlockPlaceEvent event) {
    Player player = event.getPlayer();
    KaloBlock block = event.getBlock();
}
```

### KaloBlockBreakEvent

Fired when a Kalo block is broken.

```java
@EventHandler
public void onBlockBreak(KaloBlockBreakEvent event) {
    Player player = event.getPlayer();
    KaloBlock block = event.getBlock();
}
```

### KaloFurnitureInteractEvent

Fired when a player interacts with furniture.

```java
@EventHandler
public void onFurnitureInteract(KaloFurnitureInteractEvent event) {
    Player player = event.getPlayer();
    Furniture furniture = event.getFurniture();
}
```

## Placeholders

Register custom placeholders with PlaceholderAPI.

```java
if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
    new KaloPlaceholders(kalo).register();
}
```

Available placeholders:

- `%kalo_item_<key>%` — Get item display name
- `%kalo_block_<key>%` — Get block display name

## Registry Access

### Content Types

```java
Registry<ContentType> types = kalo.getRegistries().contentTypes();
Optional<ContentType> itemType = types.get(NamespacedKey.of("kalo", "item"));
```

### Custom Model Data

```java
Registry<CustomModelData> cmdRegistry = kalo.getRegistries().customModelData();
Optional<CustomModelData> cmd = cmdRegistry.get(NamespacedKey.of("mypack", "ruby_sword"));
```

## Add-on Registration

Register custom content types.

```java
public class MyAddon implements KaloAddon {
    @Override
    public void onEnable(KaloPlugin kalo) {
        // Register custom content type
        kalo.getRegistries().contentTypes().register(
            NamespacedKey.of("myaddon", "custom"),
            MyContentType::new
        );
    }
    
    @Override
    public void onDisable() {
        // Cleanup
    }
}
```

---

**Next:** [Configuration]({% link configuration.md %})

**Previous:** [Migration]({% link migration.md %})

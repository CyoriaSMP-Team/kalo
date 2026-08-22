---
title: Getting Started
---

# 🚀 Getting Started

## Requirements

- Java 21+ (for Paper 1.21.4) or Java 25 (for 26.2)
- Paper or Folia server 1.21.4 or later

## Installation

### 1. Download Kalo

Download the latest JAR from [GitHub Releases](https://github.com/CyoriaSMP-Team/kalo/releases).

### 2. Install Plugin

Copy `Kalo-*.jar` to your server's `plugins/` folder:

```bash
cp Kalo-*.jar /path/to/server/plugins/
```

### 3. Start Server

Start or restart your server. Kalo will create its configuration files:

```
plugins/Kalo/
├── config.yml
├── packs/
│   └── example/
│       ├── pack.yml
│       ├── configs/
│       └── assets/
└── generated.zip
```

### 4. Create Your First Content Pack

#### Step 1: Create Pack Structure

```bash
mkdir -p plugins/Kalo/packs/myitem/configs
mkdir -p plugins/Kalo/packs/myitem/assets/myitem/textures/item
```

#### Step 2: Create pack.yml

```yaml
# plugins/Kalo/packs/myitem/pack.yml
id: myitem
version: 1.0
author: YourName
```

#### Step 3: Create Items

```yaml
# plugins/Kalo/packs/myitem/configs/items.yml
ruby_sword:
  type: item
  display:
    name: "<gradient:#ff5f6d:#ffc371>Ruby Sword</gradient>"
    lore:
      - "<gray>A blade cut from a single ruby.</gray>"
    glint: true
  model:
    sprite: "item/ruby_sword"
  behaviour:
    durability: 250
  java:
    base_material: NETHERITE_SWORD
```

#### Step 4: Add Texture

Place your texture at:
```
plugins/Kalo/packs/myitem/assets/myitem/textures/item/ruby_sword.png
```

### 5. Reload

```bash
/kalo reload
```

Your custom item is now in-game!

## Commands

| Command | Description |
|---------|-------------|
| `/kalo reload` | Reload all packs |
| `/kalo give <player> <item>` | Give a custom item |
| `/kalo import <plugin>` | Import from another plugin |
| `/kalo doctor` | Check for issues |
| `/kalo migrate-world` | Check world block states |

## Next Steps

- [Features](features) — Learn about all content types
- [Furniture](furniture) — Create rotatable furniture with seats
- [Configuration](configuration) — All config options

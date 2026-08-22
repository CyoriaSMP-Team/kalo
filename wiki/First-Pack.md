# 📦 Creating Your First Pack

This guide walks you through creating your first custom item with Kalo.

## Step 1: Create Pack Structure

```bash
mkdir -p plugins/Kalo/packs/myitem/configs
mkdir -p plugins/Kalo/packs/myitem/assets/myitem/textures/item
```

## Step 2: Create pack.yml

```yaml
# plugins/Kalo/packs/myitem/pack.yml
id: myitem
version: 1.0
author: YourName
```

## Step 3: Create Items

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

## Step 4: Add Texture

Place your texture at:
```
plugins/Kalo/packs/myitem/assets/myitem/textures/item/ruby_sword.png
```

**Texture Requirements:**
- Format: PNG
- Recommended size: 16x16 or 32x32
- Transparent background for items

## Step 5: Reload

```bash
/kalo reload
```

## Step 6: Test In-Game

```bash
/kalo give PlayerName myitem:ruby_sword
```

---

## What Just Happened?

1. Kalo read your `pack.yml` and found the pack namespace `myitem`
2. It parsed `items.yml` and found the `ruby_sword` definition
3. It generated a resource pack with:
   - Item definition (`assets/myitem/items/ruby_sword.json`)
   - Model file (`assets/myitem/models/item/ruby_sword.json`)
   - Language entry (`assets/myitem/lang/en_us.json`)
4. It copied your texture into the pack
5. The server sent the pack to your client

---

## Next Steps

- [[Items]] — Learn more about items
- [[Blocks]] — Create custom blocks
- [[Furniture]] — Create rotatable furniture
- [[Configuration]] — All config options

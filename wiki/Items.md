# 🗡️ Items

Custom items with models, textures, and behaviors.

## Basic Item

```yaml
ruby_sword:
  type: item
  display:
    name: "<gold>Ruby Sword</gold>"
  model:
    sprite: "item/ruby_sword"
```

## Options

### Display

```yaml
display:
  name: "<gradient:#ff5f6d:#ffc371>Ruby Sword</gradient>"
  lore:
    - "<gray>A powerful blade</gray>"
    - "<dark_gray>Durability: 250</dark_gray>"
  glint: true
```

| Option | Type | Description |
|--------|------|-------------|
| `name` | string | Item name (MiniMessage) |
| `lore` | list | Lore lines |
| `glint` | boolean | Enchantment glint |

### Model

```yaml
model:
  sprite: "item/texture_name"
```

**Model Types:**

| Type | Description |
|------|-------------|
| `sprite` | Flat texture (auto-generated model) |
| `vanilla` | Reuse a vanilla item's appearance |
| `custom` | Hand-authored model |

#### Sprite Model

```yaml
model:
  sprite: "item/ruby_sword"
```

#### Vanilla Model

```yaml
model:
  vanilla: "minecraft:diamond_sword"
```

#### Custom Model

```yaml
model:
  custom: "item/ruby_sword"
  textures:
    layer0: "item/ruby_sword"
```

### Behaviour

```yaml
behaviour:
  max_stack_size: 1
  durability: 250
  fire_resistant: true
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `max_stack_size` | int | 64 | Max stack size |
| `durability` | int | null | Max durability |
| `fire_resistant` | boolean | false | Fire resistant |

### Java Options

```yaml
java:
  base_material: NETHERITE_SWORD
  components: {}
```

| Option | Type | Description |
|--------|------|-------------|
| `base_material` | Material | Base vanilla material |
| `components` | map | Additional components |

### Bedrock Options

```yaml
bedrock:
  enabled: true
  icon: custom_icon
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | boolean | true | Enable on Bedrock |
| `icon` | string | null | Custom icon name |

---

## Examples

### Simple Item

```yaml
ruby_dust:
  type: item
  display:
    name: "<gold>Ruby Dust</gold>"
  model:
    sprite: "item/ruby_dust"
```

### Tool with Durability

```yaml
ruby_pickaxe:
  type: item
  display:
    name: "<gold>Ruby Pickaxe</gold>"
    lore:
      - "<gray>Mines quickly</gray>"
  model:
    sprite: "item/ruby_pickaxe"
  behaviour:
    durability: 500
  java:
    base_material: NETHERITE_PICKAXE
```

### Food

```yaml
ruby_apple:
  type: item
  display:
    name: "<gold>Ruby Apple</gold>"
  model:
    sprite: "item/ruby_apple"
  behaviour:
    food:
      nutrition: 4
      saturation: 1.2
      always_food: false
```

---

## See Also

- [[Blocks]] — Custom blocks
- [[Furniture]] — Rotatable furniture
- [[Configuration]] — All config options

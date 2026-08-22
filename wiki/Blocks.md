# 🧱 Custom Blocks

Placeable blocks using vanilla block states.

## Basic Block

```yaml
ruby_block:
  type: block
  model:
    cube_all: "block/ruby_block"
  behaviour:
    hardness: 3.0
```

## Block Modes

### Native Mode (Default)

Borrows a real vanilla block state (893 available).

```yaml
java:
  mode: native
  carrier: NOTE_BLOCK  # NOTE_BLOCK, TRIPWIRE, SCAFFOLDING
```

**Carriers:**

| Carrier | States | Best For |
|---------|--------|----------|
| NOTE_BLOCK | 799 | Full solid blocks |
| TRIPWIRE | 63 | Non-solid decorative |
| SCAFFOLDING | 31 | Tall decorative |

### Virtual Mode

Uses ItemDisplay entity (unlimited, but no Bedrock).

```yaml
java:
  mode: virtual
```

## Model Types

### Cube All

Same texture on all faces:

```yaml
model:
  cube_all: "block/ruby_block"
```

### Cube (Per-Face)

Different textures per face:

```yaml
model:
  cube:
    up: "block/ruby_top"
    down: "block/ruby_bottom"
    north: "block/ruby_side"
    south: "block/ruby_side"
    west: "block/ruby_side"
    east: "block/ruby_side"
```

### Custom Model

Hand-authored model:

```yaml
model:
  custom: "block/ruby_pillar"
  textures:
    all: "block/ruby_block"
```

## Behaviour

```yaml
behaviour:
  hardness: 3.0          # Break time (-1 = unbreakable)
  requires_tool: true    # Need correct tool for drop
```

## Bedrock Options

```yaml
bedrock:
  enabled: true
```

---

## Full Example

```yaml
ruby_block:
  type: block
  display:
    name: "<gold>Ruby Block</gold>"
  model:
    cube_all: "block/ruby_block"
  behaviour:
    hardness: 3.0
    requires_tool: true
  java:
    mode: native
    carrier: NOTE_BLOCK
  bedrock:
    enabled: true
```

---

## See Also

- [[Items]] — Custom items
- [[Furniture]] — Rotatable furniture
- [[Bedrock]] — Bedrock support

---

## 📜 Footer

**Kalo** — Open Custom Content Engine for Minecraft

[GitHub](https://github.com/CyoriaSMP-Team/kalo) • [Issues](https://github.com/CyoriaSMP-Team/kalo/issues) • [Discord](https://discord.gg/kalo) (coming soon)

> **Version:** 0.1.0 • **License:** MIT • **Java:** 21+ / 25

*Build once. Play everywhere.*

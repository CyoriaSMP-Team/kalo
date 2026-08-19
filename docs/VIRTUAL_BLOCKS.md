# Virtual blocks

Kalo has two Java representations for custom blocks:

| Mode | World representation | Capacity | Use when |
|---|---|---:|---|
| `virtual` | invisible Barrier anchor + persistent `ItemDisplay` | state-unlimited | ordinary decorative blocks and furniture |
| `native` | spare Note Block/Tripwire state | 862 current states | redstone, piston, fluid or other vanilla block mechanics |

## Configuration

```yaml
ruby_block:
  type: block
  model:
    cube_all: "block/ruby_block"
  java:
    mode: virtual
```

Virtual mode uses the same generated item definition and block model as native mode. On
placement Kalo replaces the temporary vanilla placement with a Barrier anchor and spawns
an `ItemDisplay` at the block centre. The display stores the namespaced Kalo key in a
Persistent Data Container, which is the source of truth for break, drop, interaction and
reload resolution. Both the anchor and the display are persistent, so the representation
does not depend on an in-memory map or a block-state allocation.

If `java.mode` is omitted, Kalo keeps `native` for backwards compatibility with existing
packs and worlds. New packs should opt into `virtual` explicitly.

## Trade-offs

“Unlimited” means unlimited content keys, not unlimited server resources. Each placed
virtual block is an entity and therefore costs entity storage, tracking bandwidth and
client rendering. Virtual blocks also do not become native redstone/piston/fluid blocks
automatically. If a definition needs those vanilla mechanics, opt into native mode:

```yaml
java:
  mode: native
  carrier: NOTE_BLOCK
```

Native mode remains stable and persistent through `block-states.json`; states are never
reused. It is a compatibility/backend choice, not a premium capacity tier. If an existing
native key is changed to `virtual`, Kalo keeps its old state in the generated pack as a
legacy read path while all new placements use the virtual backend.

## Bedrock

The Bedrock compiler emits a real custom-block definition for both modes and labels the
mapping with `java_mode`. Native entries additionally carry `java_carrier_state`; virtual
entries intentionally do not. The Bedrock side still needs an end-to-end Geyser client
smoke test for placement and entity rendering before that path is called certified.

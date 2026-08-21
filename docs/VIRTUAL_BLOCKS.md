# Virtual blocks

Kalo has two Java representations for custom blocks:

| Mode | World representation | Capacity | Use when |
|---|---|---|---|
| `native` *(default, preferred)* | a spare state of a real vanilla block | **893** — Note Block 799 + Tripwire 63 + Scaffolding 31, filled in that order | **almost always** |
| `virtual` | invisible Barrier anchor + persistent `ItemDisplay` | unlimited content keys | the carriers are full, or a block needs more visual freedom than a borrowed state allows |

**Prefer `native`.** A native block *is* a block: the server treats it as one, it costs no
entity, and vanilla mechanics apply to it. A virtual block is an entity wearing a block's
appearance, and everything that follows from that — tracking bandwidth, client rendering
cost, no redstone or piston behaviour — is the price. Reach for `virtual` when native runs
out of room, not as the starting point.

There is no third option that is "more native" than borrowing a state. A vanilla client
can only draw states it already knows, so even a real server-side block registration would
still need a visual state to be shown as — see the `BlockCarrier` javadoc.

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

If `java.mode` is omitted, Kalo uses `native`. That is both the backwards-compatible
choice for existing packs and worlds and the recommended one for new packs — `virtual` is
an explicit opt-in.

### When the carriers fill up

`BlockStateAllocator.allocate()` walks `FILL_ORDER` and spills into the next carrier when
one is full, so running out of note block states does not mean failure. When every carrier
is exhausted it throws:

```
Ran out of block states after <n> custom blocks; carriers available: [NOTE_BLOCK, TRIPWIRE, SCAFFOLDING]
```

That message is the signal to move the *decorative* blocks in the pack to `virtual` and
leave the mechanically meaningful ones on `native`. It is not a reason to convert
everything — already-placed native blocks keep their assigned state, which is never reused.

## Trade-offs

“Unlimited” means unlimited content keys, not unlimited server resources. Each placed
virtual block is an entity and therefore costs entity storage, tracking bandwidth and
client rendering. Virtual blocks also do not become native redstone/piston/fluid blocks
automatically. If a definition needs those vanilla mechanics, leave it on native mode and
pick the carrier explicitly:

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

**Virtual blocks do not render on Bedrock.** A Bedrock player sees nothing where one is
placed — confirmed on a live Geyser server, not inferred.

The reason is structural. A virtual block is an `ItemDisplay` entity on Java, and Geyser
has no translation that puts a Java display entity on a Bedrock client. There is no Java
block state for it to override either, so it cannot appear in the custom-block mapping:
the mapping is keyed by the state being replaced, and a virtual block replaces none.

Native blocks are unaffected and render correctly, item icons and all.

So the trade-off in the table above is sharper than it looks: `virtual` buys unlimited
content keys and costs Bedrock support entirely. On a server with Bedrock players, treat
the 893 native states as the real ceiling for anything they must be able to see.

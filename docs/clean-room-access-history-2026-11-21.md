# Clean-room access history — 2026-11-21

## Scope

Method-level reading of the already-quarantined reference, at the exact revision the
2026-11-19 audit recorded:

```text
repository  resourcepack-ai/rp-engine
revision    60be18cdaf90bf37c4a7db88c6b5040a00a32fe2   (0.2.90)
```

No new revision was fetched: `git rev-parse HEAD` of a shallow clone returned the same
commit the audit already covers. No source file was copied, quoted, or translated into
code. The purpose was to answer "how does the reference handle held 3D items on Bedrock",
because Kalo's own attachable path produced oversized held models.

## Files read

```text
src/main/java/ai/resourcepack/engine/core/bedrock/BedrockContent.java   (header + item/armour sections)
src/main/java/ai/resourcepack/engine/core/bedrock/ModelIcon.java        (existence only, not read)
```

## What was learned (method, not expression)

The reference does **not** ship 3D held-item attachables. Every custom item becomes a flat
Bedrock item: a sprite keeps its texture, a 3D model is rendered to an inventory icon, and
an item with neither falls back to its base material's icon. Armour is the only item
attachable, built over Bedrock's vanilla armour geometry. Placed models, screens and HUD
overlays are not converted at all.

This is an idea about scope, not code. Kalo's own flattening work — a Java-model renderer
and an icon path — was written before this reading and is unchanged by it.

## Consequence

The revision was already part of the recorded access, so this adds no new reference
material. `docs/CLEAN_ROOM.md` remains **not certified**: an independent human reviewer
still has to decide what the existing access means for the MIT claim.

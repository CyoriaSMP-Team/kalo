# Bedrock held-item pose

Java poses a held item with `display` data on the model. Bedrock has no equivalent, so Kalo
writes an animation on the bone bound to the hand slot (`animations/<ns>_<key>.animation.json`,
one for first person and one for third person).

## What Kalo derives

| Part | Rule |
|---|---|
| Pivot | the model's centre, so rotation and scale act about the centre as in Java |
| Position | `HAND_ANCHOR - centre + (-tx, ty, -tz)`; `HAND_ANCHOR = (0, 24, 0)` undoes the 24-unit offset Bedrock applies to a bound bone |
| Rotation | Java `[rx, ry, rz]` becomes `[rx, -ry, -rz]` (X is mirrored) |
| Scale | the average of Java's three axes |
| Unauthored view | no rotation, scale 1 |

## Status: unmeasured

None of these has been checked on a Bedrock client. The 24-unit offset comes from the Bedrock
Wiki's description, the sign flips from the X-mirror convention. The constants are all in
`BedrockItemDisplay` and pinned by `BedrockItemDisplayTest`, so a measured correction is one edit.

Provenance: derived by Kalo from the above. No constant or code is taken from java2bedrock.sh
(AGPL-3.0) or any other GPL/AGPL converter.

## Per-item correction

When an item sits wrong, adjust it in its YAML. Values are added to the derived pose
(position in 1/16 block, rotation in degrees, scale multiplies):

```yaml
bedrock:
  hold:
    first_person:
      position: [0, 2, 0]
      rotation: [0, 0, 15]
      scale: 0.8
    third_person:
      position: [0, -1, 0]
```

Every key is optional. `position` and `rotation` must have exactly three numbers; anything else
is rejected with the key's name.

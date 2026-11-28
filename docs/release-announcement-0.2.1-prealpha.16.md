# Release announcement draft — 0.2.1-prealpha.16

เทียบกับ release ล่าสุด **v0.2.1-prealpha.7** (14 ตุลาคม 2026) ไม่มี release `.8`–`.15`
คั่น therefore ถือเป็นการกระโดดจาก `.7` → `.16` โดยตรง

## ตัวเลขที่ต่างกัน

| รายการ | prealpha.7 | prealpha.16 | เปลี่ยน |
|---|---|---|---|
| Java files | 304 | 507 | **+203 (+67%)** |
| main sources | 238 | 370 | +132 |
| test sources | 66 | 137 | +71 |
| ไฟล์ที่ถูกลบ | — | 0 | — |
| CHANGELOG Added | 4 | 50 | +46 |
| CHANGELOG Fixed | — | 2 | +2 |

ไฟล์ใหม่กระจุกที่:

```text
io/kalo/platform   55
io/kalo/content    49
io/kalo/model      14
io/kalo/converter   5
io/kalo/pack        5
io/kalo/integration 4
tests              61
```

---

## สรุปฟีเจอร์ใหม่ (เรียงตามกลุ่ม)

### 1. Blockbench pipeline เต็มรูปแบบ

- import `.bbmodel` format 3.2–5.0 ผ่าน model IR กลาง
- แปลงเป็น Java model และ Bedrock geometry จาก IR เดียวกัน
- เก็บ cube bounds, per-face UV, pivot, rotation, visibility/export flag, outliner
  hierarchy, keyframe และ display transform
- ปฏิเสธไฟล์เสีย/ซ้ำ/ oversized/box-UV/outliner cycle แทนการ bake ผิด
- 3D item/block item มี inventory preview 64×64 แบบ isometric

### 2. Animation

- Blockbench keyframes → Bedrock animation JSON 1.8.0
- hold animation สำหรับ 3D item ที่ผูกกับ attachable
- Bezier และ Catmull-Rom keyframe
- animation controller + Molang ครบขึ้น: state variables, ternary, loop, struct, array,
  string, `->`, `??`, assignment
- parent-bone motion propagation ใช้ร่วมกันทั้ง block, placed model, mob และ armor
- `animation: {idle, interact}` สำหรับ block/furniture/mob/armor/placed model

### 3. 3D content ใหม่

- placed model: `place:` → ItemDisplay + Interaction hitbox + Bedrock client entity
- `place.storage` เก็บของใน container ข้าม restart
- `place.states` เปลี่ยน scale/light/solid/model/sound
- 3D worn armor (`armor_model:`)
- custom mob (`type: mob`) จาก LivingEntity จริง
- vehicle (`type: vehicle`) ทั้ง land/water/air พร้อม buoyancy/gravity
- emote prop, multi-performer emote, player-body emote + skin

### 4. Content types ใหม่

- `type: liquid` — water/lava rule, color, potion effect, damage
- `type: vfx` — 10 preset, inline recipe, model layer, camera control
- `type: dialog` — Java dialog + Bedrock form
- `type: hud` — image → PUA glyph overlay
- `type: vehicle`, `type: mob`, `type: emote`

### 5. Bedrock / Geyser

- per-session Bedrock packs แยกตาม XUID
- `kalo-content.json` + gameplay entitlement ต่อ content key
- PUA glyph → `font/glyph_XX.png`
- placed model ส่ง client entity/geometry/animation ให้ Geyser
- Floodgate/Geyser detection แบบ fail-closed ก่อนโหลด Cumulus

### 6. Fixes

- Bedrock cube/bone rest rotation ตามกฎ Blockbench Java→Bedrock
- skin fetch ลบ in-flight marker ก่อน completion (กัน stale/race)

### 7. เติม CHANGELOG แล้ว

ตรวจจาก diff กับ `.7` แล้วพบ 2 ฟีเจอร์ที่เป็นของใหม่แต่ยังไม่มีในหัวข้อ `.16` จึงเติมลง
CHANGELOG แล้ว:

- `DeluxeMenuFormBridge` — แปลง DeluxeMenus GUI เป็น Bedrock Form ผ่าน Geyser/Cumulus
- `GuiHeadIcons` + `GuiCustomSkullScanner` — แปลงหัวผู้เล่นใน GUI เป็นไอคอน/รูป

(`PlayerSkinCache` เป็นส่วนหนึ่งของหัวข้อ “Automatic skin resolution” ที่มีอยู่แล้ว)

ตอนนี้หัวข้อ `.16` มี 50 Added, 4 Changed, 2 Fixed, 5 Limitations

---

## ข้อจำกัดที่ต้องบอกในประกาศ

- placed model เป็น slice แรก — Java เป็น display entity + hitbox, ยังไม่เท่า Bedrock เต็ม
- held animation ต่อเฉพาะ attachable; animated block/furniture/mob ยังไม่ครบทุกกรณี
- box-UV, mesh element, texture mesh, layered/emissive ยังไม่รองรับ
- Blockbench import ทดสอบกับ synthetic fixtures ยังไม่มี corpus ของไฟล์จริงจำนวนมาก
- 1.21.4 ปลายล่างของช่วงรองรับ compile ผ่านแต่ยังไม่ได้ทดสอบ runtime
- ยังไม่มี rendered Bedrock client verification (มีแค่ protocol-level live check)

---

## ประกาศแบบสั้น (TH)

> **Kalo 0.2.1-prealpha.16 — อัปเดตใหญ่จาก prealpha.7**
>
> รอบนี้ยกเครื่อง pipeline ของ Blockbench ทั้งเส้น: import `.bbmodel` 3.2–5.0 แล้ว compile
> เป็นทั้ง Java model และ Bedrock geometry จาก IR เดียวกัน พร้อม animation, Bezier,
> Catmull-Rom, controller และ Molang ที่ครบขึ้น
>
> เพิ่ม content type ใหม่: liquid, vfx, dialog, hud, vehicle, mob และ emote
> พร้อม placed model ที่เก็บสถานะข้าม restart, 3D armor, multi-performer emote และ
> per-session Bedrock pack แยกตามผู้เล่น
>
> ไฟล์ Java เพิ่มจาก 304 → 507 (+67%) และ test เพิ่มเป็น 753 รายการ
>
> ยังเป็น pre-alpha: ดูข้อจำกัดด้านล่างก่อนใช้งานจริง

## Short announcement (EN)

> **Kalo 0.2.1-prealpha.16 — the big one since prealpha.7**
>
> The Blockbench pipeline is rebuilt end to end: `.bbmodel` 3.2–5.0 imports compile to
> both Java models and Bedrock geometry from one shared IR, with keyframes, Bezier,
> Catmull-Rom, animation controllers and broader Molang.
>
> New content types: liquid, vfx, dialog, hud, vehicle, mob and emote — plus placed models
> with persistent storage and state cycling, 3D armor, multi-performer emotes and
> per-player Bedrock packs.
>
> Java sources: 304 → 507 (+67%). Tests: 753 passing.
>
> Still pre-alpha — read the limitations before production use.

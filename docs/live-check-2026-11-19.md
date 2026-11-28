# Live Java/Bedrock delivery check — 2026-11-19

This is a protocol-level live check of Kalo's generated Bedrock pack and VFX entities. The
Bedrock side was a `bedrock-protocol` 3.61.0 client (Bedrock 1.26.60) in offline/Floodgate
mode, not a rendering Minecraft client. It can prove what the server delivered to a
Bedrock client; it cannot prove how the pixels looked on screen.

## Environment

| Component | Version / value |
|---|---|
| Server | Canvas/Paper 1.21.11 |
| Geyser | Geyser-Spigot 2.12.1-SNAPSHOT |
| Floodgate | 2.2.5-SNAPSHOT |
| Via | ViaVersion 5.12.2, ViaBackwards 5.12.2, ViaRewind 4.2.0 |
| Kalo | `0.2.1-prealpha.16`, jar SHA-256 `d31b249b2cc2babd821df02548289d26135b1401170249b487a17fa5dc26af5d` |
| Test server | separate instance under `/tmp/opencode/kalo-live`; production was not touched |
| Bedrock client | `bedrock-protocol` 3.61.0, Bedrock 1.26.60, username `KaloProbe5` / `KaloLiveA` |

Test content:

- `livecheck:ruby_sword` — a sprite item.
- `livecheck:spark` — motes (4), camera (FOV 55), and model (`ruby_sword`, scale 1.5,
  pitch 0, roll 30, spin 45) layers.
- `livecheck:acid` — water-based liquid with poison and 1 damage/second.

## Pack observation

The Bedrock client received `resource_packs_info` with two packs. The second is Kalo's:

```json
{
  "version": "1.1.0",
  "size": "11575",
  "content_identity": "81909e58-8b86-32f0-bcee-7d447bd4a7e1"
}
```

The staged per-session file on the server is exactly that pack:

```text
plugins/Kalo/bedrock-packs/0.mcpack
size 11575
sha256 91bbe118377b7414f0f2c371d6a955cf8e0a0fa91b0960a09637dc81d3cab351
manifest header uuid 81909e58-8b86-32f0-bcee-7d447bd4a7e1
```

It contains the VFX sprite, camera and model entities, their geometry/textures, and the
model roll wrapper:

```json
{"name":"kalo_vfx_roll","pivot":[0,0,0],"rotation":[0,0,30.0]}
```

The client's `content_identity` and the staged manifest UUID match. The client did not
download the payload in this check because `bedrock-protocol` auto-answered the resource
pack negotiation with "completed"; the advertised identity and size still match the file
the server staged.

## VFX observation

After `/kalo vfx livecheck:spark` was issued as the Bedrock player, the client received:

```text
add_entity kalo:vfx_livecheck_spark_vfx_model_0   (6.5, -59.0, -1.5)
add_entity kalo:vfx_livecheck_spark_vfx_camera_0  (6.348, -58.562, -1.918)
add_entity kalo:vfx_livecheck_spark_vfx_0         (6.304, -60.199, -3.893)
add_entity kalo:vfx_livecheck_spark_vfx_0         (6.505, -59.603, -3.508)
```

A second run observed four `kalo:vfx_livecheck_spark_vfx_0` sprite entities plus the model
entity. This proves the sprite, camera-sprite, and model client entities reached a Bedrock
client with the expected identifiers.

**Camera:** the client received no Bedrock `camera` packet. Geyser did not translate the
Java `ClientboundSetCameraPacket` camera control; the camera-sprite entity still arrived.
Bedrock players keep the normal camera, as documented.

## Liquid observation

The Bedrock player ran:

```text
/kalo liquid corner
/kalo liquid fill livecheck:acid
```

and received:

```text
Liquid corner set at your block
Marked liquid volume livecheck:acid
```

The volume persisted in `plugins/Kalo/liquids.json`:

```json
{"liquid":"livecheck:acid","world":"bedrock","min":[5,-60,-10],"max":[5,-60,-10]}
```

The first attempt used a flat world with hostile mobs, so mob damage contaminated the
health packets. The check was repeated in a **void world** (one bedrock layer,
`minecraft:the_void`, peaceful difficulty, no animals/monsters) with the acid's colour and
potion effect removed and `damage: 5.0` so the contact tick was unambiguous:

```text
20 -> 15 (contact tick)
     16 -> 18 (natural regeneration)
     13 (next contact tick)
     14 -> 16
     11
     12 -> 14
      9
     10 -> 12
      7
```

The volume was at the player's feet:

```json
{"liquid":"livecheck:acid","world":"voidcheck","min":[-1,-63,-6],"max":[-1,-63,-6]}
```

The five-point steps are the configured acid damage; natural regeneration raises health
between ticks. No mob damage or death message appeared in the void run. This isolates the
liquid contact tick.

## Bugs found and fixed during the check

1. **Duplicate command chains.** Kalo failed to enable with
   `IllegalStateException: Duplicate command chains detected` for `kalo vehicle` and
   `kalo spawn`. The duplicated second `vehicle`/`spawn` block was removed from
   `CommandManager`.
2. **Invalid colored-liquid biome JSON.** On 1.21.11 the generated biome lacked
   `carvers`, `features`, `spawn_costs`, and `spawners`, so the server refused to load the
   `kalo-liquids` data pack. Those required empty keys are now emitted; `LiquidBiomePackTest`
   asserts them.
3. **Folia/Canvas `setBiome` during enable.** With an existing colored liquid volume,
   `LiquidRuntime.initialize` called `world.setBiome` from the server thread and Kalo
   disabled itself. Biome application is now scheduled on each volume's owning region.
   A restart with an existing `livecheck:acid` volume completed successfully.

## Residual limitations

- The client was a protocol client, not a rendered Bedrock client; visual appearance is
  still unverified.
- Geyser does not deliver Java packet camera control to Bedrock; camera layers are
  Java-only in practice.
- Acid contact damage was isolated in the void rerun; the test used `damage: 5.0` and no
  potion effect/colour so the five-point health steps were unambiguous.
- The resource-pack payload itself was advertised and staged identically, but this client
  did not download it.


## 1.21.4-floor rendered check — blocked

A rendered Bedrock client is not available in this environment, so the rendered-visual and
1.21.4-floor checks could not be run:

- The connected Android device (Xiaomi `21061110AG`, Android 16, adb serial
  `pzxw7x6tljzlibij`) is reachable, but `com.mojang.minecraftpe` is **not installed**, the
  Play Store package is not installed, and `cmd package resolve-activity` finds no
  Minecraft activity. Installing Bedrock there needs the owner's licence/account and a
  manual install; it would also be a change to the user's device.
- The host has `Xvfb`, `adb` and `xvfb-run`, but no Wine, `mcpelauncher`, Bedrock APK or
  other rendered client. `DISPLAY` is empty.
- Local server versions contain only 1.21.11; no 1.21.4 server jar is present. Downloading
  one would still leave the rendered-client side missing.

The protocol-level client from the earlier sections is not a substitute for a rendered
client: it proves packets and pack identity, not pixels, animation, sound or the 1.21.4
floor's rendering behaviour.

To continue, provide one of:

1. Minecraft Bedrock installed on the connected Android device (or another rendered
   client) with permission to drive it, plus confirmation that its account/licence is
   available; or
2. screenshots/recording from a 1.21.4-floor server and a rendered Bedrock client.

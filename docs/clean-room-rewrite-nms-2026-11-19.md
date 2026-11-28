# Clean-room rewrite — `NmsVfxCameraTransport`

**Date:** 2026-11-19
**Scope:** `core/src/main/java/io/kalo/platform/java/NmsVfxCameraTransport.java`
**Outcome:** replaced with a new implementation written from a behaviour contract.

## 1. Clean-room statement

I did not read, list, copy, or diff the previous
`core/src/main/java/io/kalo/platform/java/NmsVfxCameraTransport.java`. The file was
replaced by writing a new one; it was never opened for comparison or editing.

I did not read `/root`, `/root/clean-room-quarantine`,
`/tmp/opencode/clean-room-quarantine`, any `/tmp/rpe-*` path, or any file whose package is
`ai.resourcepack.engine`. I did not search the web for, fetch, or clone RP Engine source.

The implementation below was derived from:

- the behaviour contract supplied for this rewrite;
- Kalo's own interfaces and call sites: `VfxCameraTransport`, `VfxCameraTransports`,
  `VfxCameraDelivery`, `VfxRuntime`, `VfxCameraFrame`, `VfxCameraPlan`,
  `GeyserIntegration`, `BedrockSupport`, and `Plugins`;
- Kalo's targeted tests for the camera seam and the Bedrock seam;
- public Java, Bukkit/Paper 1.21.4 API, and Mojang-mapped Minecraft server knowledge
  (the set-camera packet, `ServerPlayer.connection`, and entity handles).

No external implementation text was copied.

## 2. Behaviour contract used

`NmsVfxCameraTransport` is a package-private final implementation of
`VfxCameraTransport`, selected by the unchanged `VfxCameraTransports.create()` factory.

- `tryCreate()` reflectively loads
  `net.minecraft.network.protocol.game.ClientboundSetCameraPacket`,
  `net.minecraft.network.protocol.Packet`, and
  `net.minecraft.world.entity.Entity`, requires a set-camera constructor that accepts
  `Entity`, and returns `null` on any failure.
- `available()` is true only for an instance that passed that construction gate.
- `apply(UUID, VfxCameraFrame)` resolves the Bukkit player and returns when the player is
  absent, offline, or a Bedrock client according to
  `GeyserIntegration.support().isBedrock(playerId)`.
- One non-persistent, invisible `Marker` anchor is retained per player. It is spawned when
  absent, invalid, in another world, or no longer bound to the current player session; it
  is shown only to the caster. A `ClientboundSetCameraPacket` makes that marker the client
  camera, and each later call moves and rotates the marker to the frame pose.
- Any `Throwable` is logged at `Level.FINE` and the current view is left alone. If the
  camera packet cannot be sent, the marker and its map entry are removed instead of being
  retained in a broken state.
- `clear(UUID)` removes the retained anchor and sends a set-camera packet targeting the
  player again so the client returns to its own view. Bedrock players are skipped, an
  offline player is tolerated, and failures are logged at `Level.FINE`.
- Reflection route: `player.getClass().getMethod("getHandle")`, the public
  `ServerPlayer.connection` field, and that connection's `send(Packet)` method. The anchor
  is converted to its NMS entity with the same `getHandle()` route.
- No new dependencies and no change to `VfxCameraTransport`, `VfxCameraTransports`, or
  `VfxCameraDelivery`.

## 3. Design choices in the new file

**Single construction gate.** `tryCreate()` resolves the three required classes, checks
that the camera packet is actually a `Packet`, and resolves the constructor while holding
no live player. It also resolves `ServerPlayer.connection` and `send(Packet)` once, so a
server whose network-listener shape has moved falls back to `VfxCameraTransport.NONE`
instead of failing on the first cast. All construction failures collapse to `null`.

**Narrow, cached reflection.** The packet constructor, connection field, and send method
are stored as final fields. Entity handles use a small two-slot cache for the player and
marker classes, populated by the contract's
`bukkitEntity.getClass().getMethod("getHandle")` route. This avoids a method lookup every
tick without assuming a versioned CraftBukkit package name.

**Anchor lifecycle.** Each map value is a private `Anchor(Player viewer, Marker marker)`
record. A retained marker is reused only while its viewer is the same `Player` instance,
the entity is valid, and the world still matches. This also covers a player rejoining with
the same UUID but a new session object: the old marker is retired and a fresh one is
spawned. The marker is created with `setPersistent(false)`, `setVisibleByDefault(false)`,
`setSilent(true)`, and `setGravity(false)`; `player.showEntity(plugin, marker)` gives the
caster the only spawn packet. `Marker` is used rather than a visible entity.

**Packet route.** A fresh anchor is attached with
`ClientboundSetCameraPacket(markerHandle)` sent through
`playerHandle.connection.send(packet)`. Once attached, later frames only call
`marker.teleport(location)`, so position and yaw/pitch travel through the ordinary entity
tracker. This keeps the packet traffic to one attachment per anchor rather than one packet
per tick, while the marker's teleport updates remain per-frame.

**Failure containment for the view.** The entire `apply()` body is guarded. A failed move
leaves the client where it was, which is the contract's "leave the player's view alone".
The only destructive case is a failed attachment: the map entry is removed and the marker
is retired, because the client never adopted it. `clear()` sends the restore packet before
retiring the marker, and retires it in a `finally` block even when the restore packet or
the player lookup fails.

**Bedrock and offline players.** `apply()` checks Bedrock before touching any entity.
`clear()` skips the restore packet for Bedrock players but still drops any stale anchor.
`Bukkit.getPlayer` returning `null` means no packet is attempted; the marker is still
retired.

**FOV.** The contract drives position and rotation only. `VfxCameraPlan` already converts
the requested FOV into a dolly offset in the frame position, so this transport does not
apply `fov` a second time.

**Logging.** Failures use the Kalo logger at `Level.FINE` through a helper that cannot let
a logger initialization problem replace the original camera failure.

## 4. Compile and test results

The checkout's `gradlew` script is not executable and the declared wrapper distribution is
Gradle 9.7.0. The cached Gradle 9.8.1 distribution permitted by the task was used instead.

```bash
~/.gradle/wrapper/dists/gradle-9.8.1-bin/.../gradle-9.8.1/bin/gradle \
  :core:compileJava --console=plain --offline
```

**Result:** `BUILD SUCCESSFUL` (`:core:compileJava` executed).

```bash
~/.gradle/wrapper/dists/gradle-9.8.1-bin/.../gradle-9.8.1/bin/gradle \
  :core:test \
  --tests io.kalo.platform.java.VfxCameraDeliveryTest \
  --tests io.kalo.integration.BedrockSupportTest \
  --console=plain --offline
```

**Result:** `BUILD SUCCESSFUL` — 6 tests passed. Final verification re-ran both tasks with
`--rerun-tasks` so the results reflect the final source rather than Gradle's up-to-date
cache.

| Test class | Tests | Result |
|---|---:|---|
| `VfxCameraDeliveryTest` | 2 | passed |
| `BedrockSupportTest` | 4 | passed |

The fallback factory assertion in
`VfxCameraDeliveryTest.theNoOpTransportAndFactoryAreHarmless()` passes: outside a server
runtime the reflective classes are absent, so `VfxCameraTransports.create()` still returns
`VfxCameraTransport.NONE` with `available() == false`. The NMS transport itself is not
constructible in unit tests without server internals, which is why the targeted tests
exercise the seam and the fallback rather than a live packet send.

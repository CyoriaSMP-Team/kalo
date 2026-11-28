# Comment rewrite — clean-room pass (2026-11-19)

## 1. Clean-room statement

I rewrote the prose in the seven named files without access to any reference
implementation. I did not open, read, search, diff or execute anything under `/root`,
`/root/clean-room-quarantine`, `/tmp/opencode/clean-room-quarantine`, `/tmp/rpe-*`, or any
file in package `ai.resourcepack.engine`, and I did not search the web for RP Engine
source. The only inputs were the Kalo sources and tests themselves, `CLAUDE.md`, other
Kalo docs, and general public knowledge of the Java model, Bedrock geometry/attachable and
Blockbench formats. No third-party source file was consulted, quoted or paraphrased.

## 2. Files changed

| File | Rewritten prose |
|---|---|
| `core/src/main/java/io/kalo/integration/BedrockSupport.java` | Interface purpose, the `NONE` fallback, per-event docs, the overload-selection note, XUID/refresh/pack-update docs |
| `core/src/main/java/io/kalo/platform/java/VfxCameraTransport.java` | Camera-packet rationale, `NONE`, `available`/`apply`/`clear` |
| `core/src/main/java/io/kalo/platform/java/VfxCameraTransports.java` | Class doc and `create()` doc |
| `core/src/main/java/io/kalo/platform/java/VfxCameraDelivery.java` | Class doc and both forwarding methods |
| `core/src/main/java/io/kalo/platform/bedrock/BedrockGeometry.java` | Coordinate/UV rules, rotation mapping, face handling, texture/material helpers |
| `core/src/main/java/io/kalo/model/bedrock/BedrockModelCompiler.java` | Class doc, `compile`/`compileEntity`, animation baking and controller emission, bone naming, `Result` docs |
| `core/src/main/java/io/kalo/platform/bedrock/BedrockAttachable.java` | Attachable rationale, slot/geometry mapping, layer hiding, held-item/emote/armor builders |

No other file was edited. No tests, build files or resources were touched.

## 3. Comment policy

- Every comment was written from the code beside it: inputs, branches, emitted JSON keys
  and defaults. Where a comment explains *why*, it names the concrete failure the choice
  avoids (for example, a negative `uv_size` is kept because normalising it flips up/down
  textures).
- Public format rules are stated generically: Bedrock geometry uses a centred,
  X-mirrored coordinate space; Java cuboids are two corners while Bedrock uses
  origin+size; Bedrock wants a corner plus a signed size for UVs; attachables hide vanilla
  armor layers through `parent_setup`; Bedrock names the freeze-on-last-frame mode
  `hold_on_last_frame`. No file, repository or author is cited.
- No provenance claims, no "independently derived" language, no references to a prior
  converter. Historical details were kept only where they explain current code (for
  example, that `geometry.player_armor.*` is not a geometry Bedrock defines).
- Comments were kept short and only where they add information beyond the signature;
  obvious accessors got one line or none rather than restating their name.

## 4. Executable code unchanged

Before editing, the sources were captured with comments removed and whitespace collapsed
(`/tmp/opencode/kalo-comment-tools/code-before.txt`, SHA-256
`1af4d6f013b7fe86bc5853da1f656263d48fcbba235a4ba6e768ed6e2b3a1c1b`). After the rewrite the
same extraction produced a byte-identical file, and a separate token comparison (comments
ignored, string/char literals preserved) reported `OK` for all seven files. This confirms
that identifiers, literals, operators, statement order and method signatures are
unchanged; only comment text (and the whitespace that comment removal exposes) differs.

The repository has no Git metadata (`.git` is absent), so a `git diff` was not available;
the stripped-source comparison above is the substitute.

## 5. Compile result

Run with the cached Gradle 9.8.1 distribution (`gradlew` points at 9.7.0, which is not in
the wrapper cache):

```text
gradle :core:compileJava --rerun-tasks
BUILD SUCCESSFUL in 16s
11 actionable tasks: 11 executed
```

The compiler reported only the pre-existing deprecation note. No comment-related or code
errors were introduced.

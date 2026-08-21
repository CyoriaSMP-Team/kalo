# Changelog

Kalo is distributed through four channels, each of which wants its own release notes.
This file is the one place they are written; the release workflow reads the section for
the version being tagged and hands it to every channel.

Versions follow `plugin_version` in `gradle.properties`, and a tag must match it.

## 0.1.0-rc.5

### Fixed

- **Custom blocks never reached Bedrock at all.** The Geyser bridge waited on a
  registration snapshot that production code never published, so it timed out after 30
  seconds on every startup and registered nothing. Items and the resource pack were
  unaffected, which is what made the path look like it worked.
- **Bedrock blocks were named inconsistently.** Blocks registered under the content pack's
  namespace through the API, but a mapping file can only produce Geyser's `geyser_custom`
  namespace. The generated pack is keyed by one of them, so the other rendered untextured.
  Both paths now agree.
- **Blocks past the note block carrier were mapped to the wrong Java state.** The carrier
  was dropped on the way to the Bedrock compiler, so anything on tripwire or scaffolding
  was named as a note block.
- **`/kalo migrate-world` reported "nothing to migrate" when it could not look.** On Folia
  every cross-region block read threw, the error was swallowed per world, and the count
  stayed zero. It now scans through the region scheduler, off the server thread, and
  reports unreachable chunks as unreachable.
- **Virtual blocks placed just before shutdown could be lost.** A debounced save and the
  shutdown flush shared no lock, so an older snapshot could land on top of a newer one.
- `PackFormats` returned a pack_format for 1.21.5 through 1.21.11 as if it had been
  verified. It had not; it now says when it is guessing.
- **The PlaceholderAPI expansion never registered on any server that had PlaceholderAPI.**
  A Paper plugin's classloader is isolated, so the hook's supertype could not resolve and
  the guarded call failed with `NoClassDefFoundError` every time. It now declares the
  classpath edge, the way the Geyser dependency already did.

### Verified

- Deployed to a live Paper server running Geyser 2.11.2 and Floodgate. Geyser confirms the
  handshake from its own side rather than taking Kalo's word for it: against Kalo's "2
  block(s), 5 item(s)" it logs `Registered 2 custom block item overrides` and its custom
  item count rises accordingly, with no rejection. The generated pack is attached through
  Geyser's API, and both `custom_mappings` files are written.
- No Bedrock client has connected, so what a player actually sees is still unverified.
  Everything above is the two servers agreeing about what they handed each other.

### Fixed after a Bedrock player tested it

- **Custom armor equipped invisibly.** The attachable named
  `geometry.player_armor.helmet`, which Bedrock does not define, while already hiding the
  base material's armor underneath — so the piece drew nothing at all. All four slots were
  wrong the same way, and the test asserted the broken string.
- **Every custom block showed as a note block in the hotbar.** Blocks had no Bedrock item
  definition, so they fell back to their Java base material, which is always `NOTE_BLOCK`.
- **Native custom blocks could not be broken by anyone.** `onInteract` cancelled the whole
  `PlayerInteractEvent` to stop note-block tuning, which also swallowed
  `LEFT_CLICK_BLOCK` — where mining starts. That is the standard trick for making a block
  indestructible, applied by accident to every native block. `JavaBlockRules.preventsTuning`
  had encoded the right rule, tests and all, and nothing in production called it.
- **The Java resource pack declared the wrong `pack_format` on 1.21.11.** It sent 46, the
  1.21.4 number, because nothing between the ends of the supported range had been checked.
  The real value is 75.

### Known limitations

- **Virtual blocks do not render on Bedrock and cannot.** A virtual block is an
  `ItemDisplay` entity, which Geyser does not translate to a Bedrock client, and it
  overrides no Java block state so it cannot be mapped either. Native blocks are
  unaffected. On a server with Bedrock players, the 893 native states are the real ceiling.
- `pack_format` is verified for 1.21.4 (46), 1.21.11 (75) and 26.2 (88), each read out of
  that version's own client jar. Any other version gets the nearest number in its family
  plus a console warning saying it is a guess. A wrong `pack_format` can make the client
  reject the pack outright, so treat that warning as work to do, not noise.

### Changed

- **The `geyser-extension` jar is gone.** Geyser reads a `custom_mappings` folder natively,
  so Kalo writes Geyser's own format to `plugins/Kalo/geyser/` instead of shipping a second
  artifact to run inside Geyser. A standalone Geyser now needs two files copied and nothing
  installed. Releases carry one jar.
- Blocks default to `native` mode in the documentation as well as in code; `virtual` is the
  answer to exhausting the 893 carrier states, not the starting point.
- The generated pack is no longer reopened and reparsed on every Bedrock connection.

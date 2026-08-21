# Changelog

Kalo is distributed through four channels, each of which wants its own release notes.
This file is the one place they are written; the release workflow reads the section for
the version being tagged and hands it to every channel.

Versions follow `plugin_version` in `gradle.properties`, and a tag must match it.

## 0.1.0-rc.2

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

- Deployed to a live Paper server running Geyser and Floodgate. Kalo registers 5 items,
  2 native blocks and the generated pack with Geyser at startup; the third block is
  virtual and reported as rendering through its entity instead. Both `custom_mappings`
  files are written. No Bedrock client has connected yet, so what a player actually sees
  remains unverified.

### Known limitations

- `pack_format` is only verified for 1.21.4 and 26.2. A server on any version in between
  gets 46 and a console warning saying so. A wrong `pack_format` can make the client reject
  the pack outright, so treat the warning as work to do, not noise.

### Changed

- **The `geyser-extension` jar is gone.** Geyser reads a `custom_mappings` folder natively,
  so Kalo writes Geyser's own format to `plugins/Kalo/geyser/` instead of shipping a second
  artifact to run inside Geyser. A standalone Geyser now needs two files copied and nothing
  installed. Releases carry one jar.
- Blocks default to `native` mode in the documentation as well as in code; `virtual` is the
  answer to exhausting the 893 carrier states, not the starting point.
- The generated pack is no longer reopened and reparsed on every Bedrock connection.

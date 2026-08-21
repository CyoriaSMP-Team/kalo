# Releasing Kalo

One tag publishes everywhere it can be published automatically. The rest is one manual
upload.

## Before tagging

1. Rename the `## Unreleased` heading in `CHANGELOG.md` to the version being released.
   The release workflow **fails** if that section is missing or empty — four channels each
   render release notes, and a blank box on Modrinth cannot be taken back.
2. Set `plugin_version` in `gradle.properties` to the same number. The tag must be
   `v<plugin_version>`; the workflow refuses a mismatch rather than shipping a jar whose
   name contradicts its release.

## Tagging

```bash
git tag v0.1.0 && git push origin v0.1.0
```

That runs `.github/workflows/release.yml`, which builds and tests, checks the jar is still
Java 21 bytecode, then publishes to:

| Channel | How | Needs |
|---|---|---|
| GitHub Releases | `gh release create` | nothing — uses the workflow token |
| Modrinth | `POST /v2/version` | secret `MODRINTH_TOKEN`, variable `MODRINTH_PROJECT_ID` |
| Hangar | `POST /api/v1/projects/{slug}/upload` | secret `HANGAR_TOKEN`, variable `HANGAR_SLUG` |
| SpigotMC | **manual** | see below |

Modrinth and Hangar are **skipped, not failed**, when their secret is absent, so the
workflow is useful before either account exists. Both read the supported Minecraft
versions from the repository variable `SUPPORTED_GAME_VERSIONS`, a JSON array — for
example `["1.21.4","26.1","26.2"]`. Keep it in step with the README's badge.

## The Java 21 check

The jar has to start on the oldest server in the supported range, and only its class file
version proves that. Both workflows read the major version out of `KaloPluginImpl.class`
and fail on anything but 65. A dependency or a language feature that quietly raises the
target would otherwise be discovered by a 1.21.4 owner whose server refuses to load the
plugin.

## SpigotMC

SpigotMC has no upload API, so this stays manual:

1. Open the resource page → **Post Resource Update**.
2. Attach `Kalo-<version>.jar` from the GitHub release.
3. Paste the same `CHANGELOG.md` section the other channels received.

Doing it from the GitHub release rather than a local build guarantees every channel is
serving the same bytes.

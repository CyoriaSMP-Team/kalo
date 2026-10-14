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

## Release maturity (Pre-alpha until verified)

Kalo is currently **Pre-alpha**. Build success alone does not mean
production stability. Tags such as `v0.2.1-prealpha.1` require the matching
`plugin_version=0.2.1-prealpha.1` and the same version heading in
`CHANGELOG.md`.

| Tag suffix | GitHub | Modrinth | Hangar | Featured |
|---|---|---|---|---|
| `-prealpha`, `-alpha` | Pre-release | alpha | Alpha | No |
| `-beta`, `-rc` | Pre-release | beta | Beta | No |
| No suffix | Normal | release | Release | Yes |

The existing `v0.2.0` GitHub release was not marked Pre-release. This
workflow change applies to *future* tags, not historical releases. Do not
ship unmerged draft-PR features or describe unverified crossplay as complete.

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
| MCMall | optional `PATCH/POST /api/v1/products` workflow | secret `MCMALL_API_KEY`, variable `MCMALL_PRODUCT_ID` |
| SpigotMC | **manual** | see below |

Modrinth, Hangar and MCMall are **skipped, not failed**, when their required credentials or variables are absent, so the
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

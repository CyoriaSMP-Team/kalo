# Resource pack format audit

This record is based on **official Mojang Minecraft client jar** files and
their SHA-1 digests from the official version metadata. Each jar's embedded
`version.json` supplied the resource-pack version.

| Minecraft | Pack resource major | Embedded JSON key |
|---|---:|---|
| 1.21.4 | 46 | resource_major (previously verified) |
| 1.21.5 | 55 | resource |
| 1.21.6 | 63 | resource |
| 1.21.7 | 64 | resource |
| 1.21.8 | 64 | resource |
| 1.21.9 | 69 | resource_major |
| 1.21.10 | 69 | resource_major |
| 1.21.11 | 75 | resource_major (previously verified) |
| 26.1 | 84 | resource_major |
| 26.2 | 88 | resource_major (previously verified) |

Sources: Mojang `version_manifest_v2.json` and the signed metadata
`downloads.client.sha1` pointing to each release client jar.

This validates the *resource major number only*. New format capabilities
and live resource-pack rendering still require an integration test for
each supported server version.

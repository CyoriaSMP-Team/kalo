# 🌏 Bedrock Support

Kalo has built-in Bedrock support through Geyser. No additional plugins or paid addons required.

## Requirements

- [Geyser](https://geysermc.org/) plugin installed on the same server
- [Floodgate](https://geysermc.org/) (optional, for Bedrock player authentication)

## Setup

### Same Server (Recommended)

If Geyser runs as a plugin on the same server, Kalo automatically registers everything:

1. Install Geyser and Floodgate
2. Install Kalo
3. Start the server

Kalo will log:
```
[Kalo] Registering Kalo items, blocks and resource pack with Geyser directly
[Kalo] Registered and mapped 2 block(s) with Geyser natively
[Kalo] Registered 8 item(s) with Geyser natively
[Kalo] Registered generated.mcpack with Geyser
```

### Standalone Geyser

If Geyser runs separately:

1. Copy `plugins/Kalo/geyser/kalo-items.json` to Geyser's `custom_mappings/`
2. Copy `plugins/Kalo/geyser/kalo-blocks.json` to Geyser's `custom_mappings/`
3. Copy `plugins/Kalo/generated.mcpack` to Geyser's `packs/`

## Configuration

```yaml
# plugins/Kalo/config.yml
bedrock: auto  # Options: auto, always, never
```

| Option | Description |
|--------|-------------|
| `auto` | Build Bedrock pack only if Geyser is present |
| `always` | Always build Bedrock pack |
| `never` | Never build Bedrock pack |

## Supported Content

| Content | Bedrock Status |
|---------|----------------|
| Items (2D sprites) | ✅ Works |
| Items (3D models) | ⚠️ Geometry conversion |
| Custom Blocks (native) | ✅ Works |
| Custom Blocks (virtual) | ❌ Not supported |
| Furniture (native) | ✅ Works |
| Armor | ✅ Works |
| Paintings | ⚠️ Basic support |
| Music Discs | ⚠️ Basic support |
| GUIs | ❌ Server-side only |
| Glyphs | ✅ Works |

## Virtual Blocks on Bedrock

Virtual blocks use `ItemDisplay` entities, which Geyser cannot translate to Bedrock clients. On servers with Bedrock players, use native mode for content they need to see.

## Troubleshooting

### Blocks not rendering

1. Check Kalo and Geyser logs for registration messages
2. Ensure Geyser is loaded before Kalo registers
3. Restart server after first installation

### Items show as missing texture

1. Verify textures exist in `assets/<pack>/textures/`
2. Run `/kalo doctor` to check for broken references
3. Regenerate pack with `/kalo reload`

---

## See Also

- [[Getting-Started]] — Installation guide
- [[Configuration]] — All config options

---

## 📜 Footer

**Kalo** — Open Custom Content Engine for Minecraft

[GitHub](https://github.com/CyoriaSMP-Team/kalo) • [Issues](https://github.com/CyoriaSMP-Team/kalo/issues) • [Discord](https://discord.gg/kalo) (coming soon)

> **Version:** 0.1.0 • **License:** MIT • **Java:** 21+ / 25

*Build once. Play everywhere.*

# Native Pause Menu Branding

Kalo can generate the branding for Minecraft Java's ESC menu directly in
`generated.zip`. **No client mod and no hand-edited ZIP are needed.**

Put a transparent PNG at `plugins/Kalo/branding/pause-logo.png` and enable
this in `plugins/Kalo/config.yml`:

```yaml
pause-menu:
  enabled: true
  logo: "branding/pause-logo.png"
  glyph-codepoint: "E7C0"
  height: 30
  ascent: 26
  translations:
    en_us:
      return-to-game: "Back to CyoriaSMP"
      disconnect: "Leave CyoriaSMP"
    en_gb:
      return-to-game: "Back to CyoriaSMP"
      disconnect: "Leave CyoriaSMP"
    th_th:
      return-to-game: "กลับสู่ CyoriaSMP"
      disconnect: "ออกจาก CyoriaSMP"
```

Run `/kalo reload`. Kalo reads the PNG directly from its data folder,
merges the bitmap font provider with any pre-existing fonts, and adds
language entries for the configured locales. If a `base-pack` is configured,
its item models, textures and language keys remain intact. Kalo publishes
the resulting pack through the configured pack-host/Relay mechanism.

You can tune image display via `height` and `ascent`; the PNG's aspect ratio
is preserved. `glyph-codepoint` must be an unused private-use Unicode
character, otherwise the build will skip branding rather than silently
overwrite another icon.

**Limitations:** On an unmodded Minecraft client, a server resource pack can
change the pause menu's title artwork and existing translated labels, but
cannot rearrange actual vanilla widgets or add functional clickable links.
Bedrock edition has a separate UI resource-pack format; this feature is for
the vanilla Java pause menu only.

## Native clickable ESC button and dialog (Minecraft Java 1.21.11)

Minecraft 1.21.6+ has a server-side `minecraft:dialog` registry and
`minecraft:pause_screen_additions` tag. This is separate from the Java
Resource Pack. Kalo can generate a dedicated, isolated Data Pack at
`<main-world>/datapacks/kalo-pause-menu`:

```yaml
pause-menu:
  dialog:
    enabled: true
    title: "CyoriaSMP"
    pause-button: "เมนู CyoriaSMP"
    description: "ทางลัดสำหรับผู้เล่น CyoriaSMP"
    columns: 2
    buttons:
      - { label: "กลับจุดเกิด", type: command, value: "/spawn" }
      - { label: "ตลาด", type: command, value: "/shop" }
      - { label: "เว็บไซต์", type: url, value: "https://www.cyoriasmp.com" }
      - { label: "Discord", type: url, value: "https://discord.cyoriasmp.com" }
```

**Newly installed/updated dialog registries require the world to load the
Data Pack**, typically via a server restart or a deliberate `/minecraft:reload`.
A `/kalo reload` regenerates the managed files, but does not force reload all
world datapacks or restart Folia. No data packs belonging to other plugins are
overwritten. If other plugins add pause dialogs, the game may show a shared
"Custom Options" selector instead of a direct single Kalo button.

Links use HTTPS; commands execute as the *player*, never as the console.
Only a single command name with no user-configured arguments is allowed.
Actual menu/shop commands must exist and permit player use on that server.
Bedrock's pause UI is independent of this Java-only feature.

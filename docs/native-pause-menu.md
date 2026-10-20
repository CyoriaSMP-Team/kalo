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

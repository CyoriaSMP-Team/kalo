# Kalo Native Glyph Registry

Independent Kalo implementation of Minecraft bitmap font glyphs. No Oraxen source code is used.

## Named glyphs
Create plugins/Kalo/glyphs/cyoria.yml:

~~~yaml
cyoria_logo:
  texture: branding/cyoriasmp-logo.png
  codepoint: E7D0
  height: 30
  ascent: 26
shop_icon:
  texture: branding/shop.png
  codepoint: E7E0
  height: 16
  ascent: 12
~~~

Put source PNG files inside plugins/Kalo/, and run /kalo reload.

Kalo reads all glyph YAML files, validates private-use Unicode ranges, merges
the bitmap providers with the existing font/default.json, and publishes
the normal resource pack over the configured pack host/Relay.

The built-in pause_logo also uses this registry. Choose it in config.yml:

~~~yaml
pause-menu:
  enabled: true
  logo: "branding/cyoriasmp-logo.png"
  title: "<glyph:pause_logo>"
  glyph-codepoint: "E7C0"
  height: 30
  ascent: 26
~~~

Instead, set pause-menu.title to "<glyph:cyoria_logo>" when using a named
cyoria_logo glyph in glyphs/cyoria.yml.

Oversize images are resized into 256x256 limits, then split into multiple
adjacent codepoints if the displayed advance of one glyph is excessive.
The source PNG is never overwritten. Overlapping codepoints cause a build error.

This is for Java resource packs only. Minecraft client resource packs can
override the effective font; actual in-client testing is required to confirm
the appearance. Clickable pause-screen buttons are implemented separately
through Kalo's datapack dialog feature.

Glyph placeholders currently resolve in pause-menu.title; applications
that use other config fields must explicitly call NativeGlyphRegistry.render.

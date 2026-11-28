package io.kalo.content.hud.definition;

import io.kalo.content.glyph.definition.GlyphDefinition;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * A HUD overlay rendered from a font glyph.
 *
 * <p>The picture is bound to a Private Use Area character and drawn as text. The slot
 * decides whether the runtime keeps it on the action bar or a boss bar; the glyph metrics
 * position it vertically.</p>
 */
public record HudDefinition(
        @NotNull Key key,
        @NotNull Key texture,
        @NotNull Slot slot,
        int character,
        int height,
        int ascent,
        @NotNull Key font,
        int offset
) {

    /** Offset range; the corresponding space glyphs live in supplementary PUA plane 15. */
    public static final int MAX_OFFSET = 512;
    private static final int OFFSET_CHARACTER_BASE = 0xF0000;

    public HudDefinition(@NotNull Key key, @NotNull Key texture, @NotNull Slot slot,
                         int character, int height, int ascent, @NotNull Key font) {
        this(key, texture, slot, character, height, ascent, font, 0);
    }

    public enum Slot {
        ACTION_BAR,
        BOSS_BAR;

        public static @NotNull Slot parse(@NotNull String value) {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "action_bar", "actionbar" -> ACTION_BAR;
                case "boss_bar", "bossbar" -> BOSS_BAR;
                default -> throw new IllegalArgumentException(
                        "hud slot must be action_bar or boss_bar, got '" + value + "'");
            };
        }
    }

    public HudDefinition {
        // Reuse the glyph validation: height, ascent and codepoint rules are identical.
        new GlyphDefinition(key, texture, character, ascent, height, font);
        if (offset < -MAX_OFFSET || offset > MAX_OFFSET) {
            throw new IllegalArgumentException("hud offset must be within -"
                    + MAX_OFFSET + ".." + MAX_OFFSET);
        }
    }

    public @NotNull String glyph() {
        return new String(Character.toChars(character));
    }

    /** The Java-only negative/positive space prefix for the horizontal offset. */
    public @NotNull String offsetGlyph() {
        return offset == 0 ? "" : new String(Character.toChars(offsetCharacter()));
    }

    /** The exact Java text payload: offset space followed by the image glyph. */
    public @NotNull String overlayText() {
        return offsetGlyph() + glyph();
    }

    /** One shared space character per offset value in Plane 15 PUA. */
    public int offsetCharacter() {
        return OFFSET_CHARACTER_BASE + offset + MAX_OFFSET;
    }

    public @NotNull GlyphDefinition asGlyph() {
        return new GlyphDefinition(key, texture, character, ascent, height, font);
    }
}

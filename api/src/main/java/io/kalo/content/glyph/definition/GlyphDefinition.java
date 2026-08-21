package io.kalo.content.glyph.definition;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

/**
 * A custom character rendered from an image.
 *
 * <p>Glyphs are how servers put icons into chat, item names and menus without a client
 * mod: the pack binds an image to a character, and writing that character draws the
 * image.</p>
 *
 * <p>The character is the load-bearing detail. It must be one the server will never want
 * to display literally, which is why the Private Use Area exists — {@code U+E000} upward
 * has no meaning of its own, so nothing else can collide with it.</p>
 *
 * @param key       the glyph's content key
 * @param texture   the image, e.g. {@code mypack:font/coin}
 * @param character the codepoint that draws it
 * @param ascent    how far above the baseline it sits; must not exceed {@code height}
 * @param height    rendered height in pixels
 * @param font      the font it belongs to; {@code minecraft:default} makes it work
 *                  everywhere without a font tag
 */
public record GlyphDefinition(
        @NotNull Key key,
        @NotNull Key texture,
        int character,
        int ascent,
        int height,
        @NotNull Key font
) {
    /** Where the Private Use Area starts; glyphs live here to avoid colliding with text. */
    public static final int PRIVATE_USE_AREA_START = 0xE000;
    private static final int PRIVATE_USE_AREA_END = 0xF8FF;

    public GlyphDefinition {
        if (height < 1) {
            throw new IllegalArgumentException("height must be positive, got " + height);
        }
        if (ascent > height) {
            // Vanilla rejects the whole font file for this, taking every other glyph with
            // it, so it is worth catching per glyph with a message naming the culprit.
            throw new IllegalArgumentException(
                    "ascent (" + ascent + ") must not exceed height (" + height + ")");
        }
        if (!Character.isValidCodePoint(character) || character == 0
                || (character >= Character.MIN_SURROGATE && character <= Character.MAX_SURROGATE)) {
            throw new IllegalArgumentException(
                    "character must be a valid Unicode scalar value, got U+"
                            + Integer.toHexString(character).toUpperCase(java.util.Locale.ROOT));
        }
    }

    /** The codepoint as the string a player or config would actually write. */
    public @NotNull String asString() {
        return new String(Character.toChars(character));
    }

    /**
     * Whether the chosen character is in the Private Use Area.
     *
     * <p>Not enforced — a pack may have a reason — but anything outside it is a character
     * that also means something in ordinary text, so it is worth warning about.</p>
     */
    public boolean usesPrivateUseArea() {
        return character >= PRIVATE_USE_AREA_START && character <= PRIVATE_USE_AREA_END;
    }
}

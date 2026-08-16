package io.kalo.content.glyph;

import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.glyph.definition.GlyphDefinition;
import io.kalo.content.item.Item;
import io.kalo.pack.ResourcePack;
import io.kalo.platform.java.JavaGlyphCompiler;
import io.kalo.registry.Registries;
import io.kalo.utils.Constants;
import io.kalo.utils.Plugins;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Loads custom glyphs from pack YAML.
 *
 * <pre>
 * coin:
 *   type: glyph
 *   texture: "font/coin"
 *   character: ""     # or 57344, or "U+E000"
 *   ascent: 8
 *   height: 9
 * </pre>
 *
 * <p>The character accepts three spellings because none of them is comfortable in YAML:
 * a literal Private Use Area character is invisible in an editor, a decimal codepoint is
 * unreadable, and {@code U+E000} is what documentation uses.</p>
 */
public final class GlyphType implements ContentType<Item> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "glyph");

    private static final Key DEFAULT_FONT = Key.key("minecraft", "default");

    private final Map<Key, GlyphDefinition> glyphs = new ConcurrentHashMap<>();

    @Override
    public @NotNull String id() {
        return "glyph";
    }

    @Override
    public @NotNull Class<Item> clazz() {
        return Item.class;
    }

    @Override
    public @NotNull Iterable<Item> contents(@NotNull Registries registries) {
        return List.of();
    }

    @Override
    public boolean load(@NotNull PackContext pack, @NotNull Registries registries,
                        @NotNull ConfigurationSection config) {
        Key key = pack.key(config.getName());
        try {
            glyphs.put(key, parse(key, config));
            return true;
        } catch (Exception e) {
            Plugins.logger().log(Level.WARNING,
                    "Failed to load glyph '" + key.asString() + "': " + e.getMessage(), e);
            return false;
        }
    }

    static @NotNull GlyphDefinition parse(@NotNull Key key, @NotNull ConfigurationSection config) {
        String texture = config.getString("texture");
        if (texture == null) {
            throw new IllegalArgumentException("glyph has no texture");
        }

        int height = config.getInt("height", 8);
        return new GlyphDefinition(
                key,
                resolve(key.namespace(), texture),
                parseCharacter(config.get("character")),
                config.getInt("ascent", height - 1),
                height,
                config.contains("font") ? resolve("minecraft", config.getString("font")) : DEFAULT_FONT);
    }

    /** Accepts a literal character, a decimal codepoint, or {@code U+XXXX}. */
    static int parseCharacter(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("glyph has no character");
        }
        if (value instanceof Number number) {
            return number.intValue();
        }

        String text = value.toString();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("glyph character is empty");
        }
        if (text.regionMatches(true, 0, "U+", 0, 2)) {
            return Integer.parseInt(text.substring(2), 16);
        }
        // A literal character, which may be a surrogate pair.
        return text.codePointAt(0);
    }

    private static @NotNull Key resolve(@NotNull String fallbackNamespace, @NotNull String value) {
        int separator = value.indexOf(':');
        return separator < 0
                ? Key.key(fallbackNamespace, value)
                : Key.key(value.substring(0, separator), value.substring(separator + 1));
    }

    @Override
    public void compilePack(@NotNull ResourcePack resourcePack, @NotNull Iterable<Item> contents) {
        compile(resourcePack);
    }

    /** Compiles even with no registered content, since glyphs live outside the registries. */
    public void compile(@NotNull ResourcePack resourcePack) {
        JavaGlyphCompiler.compileGlyphs(resourcePack, new ArrayList<>(glyphs.values()));
    }

    public void clear() {
        glyphs.clear();
    }

    public int size() {
        return glyphs.size();
    }

    /** Exposed so a placeholder or command can render a glyph by key. */
    public @NotNull Map<Key, GlyphDefinition> glyphs() {
        return Map.copyOf(glyphs);
    }
}

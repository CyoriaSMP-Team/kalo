package io.kalo.content.hud;

import io.kalo.content.ContentType;
import io.kalo.content.PackContext;
import io.kalo.content.glyph.GlyphType;
import io.kalo.content.glyph.definition.GlyphDefinition;
import io.kalo.content.hud.definition.HudDefinition;
import io.kalo.content.item.Item;
import io.kalo.pack.ResourcePack;
import io.kalo.platform.java.JavaGlyphCompiler;
import io.kalo.platform.java.JavaHudOffsetCompiler;
import io.kalo.registry.Registries;
import io.kalo.utils.Constants;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads HUD overlays and compiles each one into a Java font glyph. Bedrock reads the same
 * glyph through a generated glyph page.
 *
 * <pre>
 * mana:
 *   type: hud
 *   texture: "hud/mana"
 *   character: "U+E100"
 *   height: 32
 *   ascent: 24
 *   slot: action_bar
 * </pre>
 */
public final class HudType implements ContentType<Item> {
    public static final Key KEY = Key.key(Constants.PLUGIN_ID, "hud");

    private static final Logger LOGGER = Logger.getLogger(HudType.class.getName());
    private static final Key DEFAULT_FONT = Key.key("minecraft", "default");

    private final Map<Key, HudDefinition> huds = new ConcurrentHashMap<>();

    @Override
    public @NotNull String id() {
        return "hud";
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
            HudDefinition definition = parse(key, config);
            if (huds.putIfAbsent(key, definition) != null) {
                LOGGER.warning("Duplicate HUD '" + key.asString() + "'; keeping the first definition");
                return false;
            }
            return true;
        } catch (Exception error) {
            LOGGER.log(Level.WARNING,
                    "Failed to load HUD '" + key.asString() + "': " + error.getMessage(), error);
            return false;
        }
    }

    public static @NotNull HudDefinition parse(@NotNull Key key,
                                               @NotNull ConfigurationSection config) {
        String texture = config.getString("texture");
        if (texture == null || texture.isBlank()) {
            throw new IllegalArgumentException("hud has no texture");
        }
        int height = config.getInt("height", 16);
        int ascent = config.getInt("ascent", height - 1);
        int offset = Math.max(-HudDefinition.MAX_OFFSET,
                Math.min(HudDefinition.MAX_OFFSET, config.getInt("offset", 0)));
        Key font = config.contains("font")
                ? resolve("minecraft", config.getString("font"))
                : DEFAULT_FONT;
        return new HudDefinition(
                key,
                resolve(key.namespace(), texture),
                HudDefinition.Slot.parse(config.getString("slot", "action_bar")),
                GlyphType.parseCharacter(config.get("character")),
                height,
                ascent,
                font,
                offset);
    }

    /** Exposed for tests and programmatic registration. */
    public void put(@NotNull HudDefinition definition) {
        huds.put(definition.key(), definition);
    }

    /** The HUDs as glyphs, in the same shape the Java/Bedrock glyph compilers use. */
    public @NotNull Map<Key, GlyphDefinition> glyphs() {
        Map<Key, GlyphDefinition> glyphs = new LinkedHashMap<>();
        huds.values().stream()
                .sorted(java.util.Comparator.comparing(definition -> definition.key().asString()))
                .forEach(definition -> glyphs.put(definition.key(), definition.asGlyph()));
        return glyphs;
    }

    public @NotNull Map<Key, HudDefinition> huds() {
        return Map.copyOf(huds);
    }

    public int size() {
        return huds.size();
    }

    public void clear() {
        huds.clear();
    }

    @Override
    public void compilePack(@NotNull ResourcePack resourcePack,
                            @NotNull Iterable<Item> contents) {
        compile(resourcePack);
    }

    /** Compiles even with no registered content, since HUDs live outside the registries. */
    public void compile(@NotNull ResourcePack resourcePack) {
        JavaGlyphCompiler.compileGlyphs(resourcePack, glyphs().values());
        JavaHudOffsetCompiler.compile(resourcePack, huds.values());
    }

    private static @NotNull Key resolve(@NotNull String fallbackNamespace,
                                        @NotNull String value) {
        int separator = value.indexOf(':');
        return separator < 0
                ? Key.key(fallbackNamespace, value)
                : Key.key(value.substring(0, separator), value.substring(separator + 1));
    }
}

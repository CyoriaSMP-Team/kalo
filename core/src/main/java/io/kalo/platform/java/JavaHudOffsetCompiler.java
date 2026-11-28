package io.kalo.platform.java;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.kalo.content.hud.definition.HudDefinition;
import io.kalo.pack.Json;
import io.kalo.pack.ResourcePack;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.TreeMap;

/**
 * Adds the Java {@code space} providers that slide a HUD glyph horizontally.
 *
 * <p>A HUD's offset character lives in supplementary Private Use Area plane 15, one
 * character per pixel value, so two HUDs with the same offset share a provider and no
 * glyph codepoint can collide with an offset character. The font file is appended to, not
 * replaced.</p>
 */
public final class JavaHudOffsetCompiler {

    private JavaHudOffsetCompiler() {
    }

    public static void compile(@NotNull ResourcePack pack,
                               @NotNull Iterable<HudDefinition> huds) {
        Map<Key, Map<String, Double>> byFont = new TreeMap<>(
                java.util.Comparator.comparing(Key::asString));
        for (HudDefinition hud : huds) {
            if (hud.offset() == 0) {
                continue;
            }
            byFont.computeIfAbsent(hud.font(), ignored -> new java.util.LinkedHashMap<>())
                    .put(hud.offsetGlyph(), (double) hud.offset());
        }

        byFont.forEach((font, advances) -> {
            String path = "assets/" + font.namespace() + "/font/" + font.value() + ".json";
            JsonArray providers = JavaGlyphCompiler.existingProviders(pack, path, font);
            JsonObject space = new JsonObject();
            space.addProperty("type", "space");
            JsonObject values = new JsonObject();
            advances.forEach(values::addProperty);
            space.add("advances", values);
            providers.add(space);

            JsonObject root = new JsonObject();
            root.add("providers", providers);
            pack.file(path, Json.writable(root));
        });
    }
}

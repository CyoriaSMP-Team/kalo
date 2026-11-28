package io.kalo.content.dialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

/**
 * Resolves Kalo's open-time dialog placeholders inside a JSON dialog tree.
 *
 * <p>Only known names are replaced, so an unknown {@code {name}} stays visible rather
 * than disappearing. Keys are not rewritten; values are copied recursively and the source
 * tree is never mutated.</p>
 */
public final class DialogTextResolver {

    private static final Set<String> KNOWN = Set.of(
            "player", "target", "uuid", "world");

    private DialogTextResolver() {
    }

    public static @NotNull JsonElement resolve(@NotNull JsonElement element,
                                               @NotNull Map<String, String> placeholders) {
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            return primitive.isString()
                    ? new JsonPrimitive(render(primitive.getAsString(), placeholders))
                    : primitive.deepCopy();
        }
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                result.add(resolve(child, placeholders));
            }
            return result;
        }
        if (element.isJsonObject()) {
            JsonObject result = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                result.add(entry.getKey(), resolve(entry.getValue(), placeholders));
            }
            return result;
        }
        return element.deepCopy();
    }

    public static @NotNull String render(@NotNull String text,
                                         @NotNull Map<String, String> placeholders) {
        String rendered = text;
        for (String name : KNOWN) {
            String value = placeholders.get(name);
            if (value != null) {
                rendered = rendered.replace("{" + name + "}", value);
            }
        }
        return rendered;
    }
}

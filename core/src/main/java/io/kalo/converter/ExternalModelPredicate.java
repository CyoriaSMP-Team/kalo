package io.kalo.converter;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A portable predicate understood by Geyser's v2 JSON mapping and native API. */
public record ExternalModelPredicate(
        @NotNull Kind kind, @NotNull String property,
        @Nullable String value, @Nullable Double threshold,
        int index, boolean expected, boolean normalize) {
    public enum Kind { CONDITION, RANGE, MATCH }

    public ExternalModelPredicate {
        if (index < 0 || index > 255) throw new IllegalArgumentException("Invalid item data index");
        if (kind == Kind.RANGE && (threshold == null || !Double.isFinite(threshold))) {
            throw new IllegalArgumentException("Invalid range threshold");
        }
        if (kind == Kind.MATCH && (value == null || value.isBlank())) {
            throw new IllegalArgumentException("Missing match value");
        }
    }

    public @NotNull ExternalModelPredicate negated() {
        return new ExternalModelPredicate(kind, property, value, threshold, index, !expected, normalize);
    }

    public @NotNull JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", switch (kind) {
            case CONDITION -> "condition";
            case RANGE -> "range_dispatch";
            case MATCH -> "match";
        });
        json.addProperty("property", property);
        if (value != null) json.addProperty("value", value);
        if (threshold != null) json.addProperty("threshold", threshold);
        if (index != 0 || property.equals("custom_model_data")) json.addProperty("index", index);
        if (normalize) json.addProperty("normalize", true);
        if (!expected) json.addProperty("expected", false);
        return json;
    }
}

package io.kalo.platform.bedrock;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encodes Geyser's custom block mapping file ({@code format_version} 1).
 *
 * <p>Geyser keys this file by a plain Java block id — {@code minecraft:note_block} — and
 * keeps the tricky part in {@code state_overrides}, whose keys are the state string alone:
 * {@code instrument=harp,note=0,powered=true}. Emitting the bracketed state form as the
 * outer key looks reasonable and is rejected outright: the reader parses every key as an
 * Adventure {@link net.kyori.adventure.key.Key}, which cannot contain {@code [}. One bad
 * entry aborts the whole file, so no block registers at all.</p>
 */
public final class GeyserBlockMappings {

    private GeyserBlockMappings() {
    }

    /**
     * @param registrations blocks the Bedrock compiler emitted successfully
     * @return the mapping document, or an empty {@code blocks} object
     */
    public static @NotNull JsonObject encode(
            @NotNull List<BedrockBlockRegistration> registrations) {
        Map<String, List<BedrockBlockRegistration>> byBase = new LinkedHashMap<>();
        for (BedrockBlockRegistration registration : registrations) {
            String identifier = registration.javaIdentifier();
            if (identifier == null) {
                continue;
            }
            String base = baseOf(identifier);
            byBase.computeIfAbsent(base, ignored -> new ArrayList<>()).add(registration);
        }

        JsonObject blocks = new JsonObject();
        for (Map.Entry<String, List<BedrockBlockRegistration>> group : byBase.entrySet()) {
            List<BedrockBlockRegistration> states = group.getValue();
            BedrockBlockRegistration first = states.get(0);

            // Components live at the top level as defaults; state_overrides may narrow them.
            // Every state carries the full set, because a furniture frame differs in texture
            // and a partial entry would inherit a neighbour's faces.
            JsonObject entry = components(first);
            entry.addProperty("name", first.bedrockName());
            if (first.displayName() != null) {
                entry.addProperty("display_name", first.displayName());
            }
            // Obtained through Kalo's own items, so a second copy in the Bedrock creative
            // menu would only be confusing.
            entry.addProperty("included_in_creative_inventory", false);

            JsonObject overrides = new JsonObject();
            for (BedrockBlockRegistration registration : states) {
                String state = stateOf(registration.javaIdentifier());
                if (state == null || !registration.bedrockName().equals(first.bedrockName())) {
                    // Geyser holds one name per Java block. A second name on the same base
                    // cannot be expressed here and is left to the in-process API path.
                    continue;
                }
                overrides.add(state, components(registration));
            }
            if (!overrides.isEmpty()) {
                entry.addProperty("only_override_states", true);
                entry.add("state_overrides", overrides);
            }
            blocks.add(group.getKey(), entry);
        }

        JsonObject mappings = new JsonObject();
        mappings.addProperty("format_version", 1);
        mappings.add("blocks", blocks);
        return mappings;
    }

    /** {@code minecraft:note_block[instrument=harp]} becomes {@code minecraft:note_block}. */
    static @NotNull String baseOf(@NotNull String javaIdentifier) {
        int bracket = javaIdentifier.indexOf('[');
        return bracket < 0 ? javaIdentifier : javaIdentifier.substring(0, bracket);
    }

    /** {@code minecraft:note_block[instrument=harp]} becomes {@code instrument=harp}. */
    static @Nullable String stateOf(@Nullable String javaIdentifier) {
        if (javaIdentifier == null) {
            return null;
        }
        int open = javaIdentifier.indexOf('[');
        int close = javaIdentifier.lastIndexOf(']');
        if (open < 0 || close <= open + 1) {
            return null;
        }
        return javaIdentifier.substring(open + 1, close);
    }

    private static @NotNull JsonObject components(@NotNull BedrockBlockRegistration registration) {
        JsonObject entry = new JsonObject();
        entry.addProperty("geometry", registration.geometry());
        entry.addProperty("destructible_by_mining", registration.hardness());

        JsonObject materials = new JsonObject();
        registration.materialInstances().forEach((slot, texture) -> {
            JsonObject instance = new JsonObject();
            instance.addProperty("texture", texture);
            instance.addProperty("render_method", "opaque");
            instance.addProperty("face_dimming", true);
            instance.addProperty("ambient_occlusion", true);
            materials.add(slot, instance);
        });
        entry.add("material_instances", materials);
        return entry;
    }
}

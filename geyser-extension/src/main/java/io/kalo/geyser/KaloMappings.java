package io.kalo.geyser;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The half of Kalo's output that the Geyser side needs.
 *
 * <p>The server plugin and this extension run in different processes — Paper and Geyser —
 * so they share a file, not classes. That file is {@code bedrock-mappings.json}, written
 * by the plugin next to the generated packs.</p>
 */
public record KaloMappings(List<BlockEntry> blocks, List<ItemEntry> items) {

    private static final List<String> NOTE_BLOCK_INSTRUMENTS = List.of(
            "harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar",
            "chime", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit",
            "banjo", "pling"
    );
    private static final int NOTES_PER_INSTRUMENT = 25;
    private static final int POWER_STATES = 2;

    /**
     * One custom block, as both platforms see it.
     *
     * @param javaKey          the Kalo content key, e.g. {@code mypack:ruby_block}
     * @param bedrockId        the Bedrock block identifier to register
     * @param javaCarrierState which of the note block states this block occupies on Java;
     *                         {@code null} when the plugin had not assigned one yet
     */
    public record BlockEntry(String javaKey, String bedrockId, Integer javaCarrierState,
                             String javaIdentifier, String geometry, String displayName,
                             Float hardness, Map<String, String> materialInstances) {
        public BlockEntry {
            materialInstances = Map.copyOf(materialInstances);
        }
    }

    /** One Geyser v2 item definition flattened out of the generated mapping file. */
    public record ItemEntry(String javaIdentifier, String bedrockId, String model,
                            String icon, String displayName, int maxStackSize,
                            Integer maxDamage, boolean enchantmentGlint) {
    }

    public static KaloMappings empty() {
        return new KaloMappings(List.of(), List.of());
    }

    /**
     * Reads the mapping file.
     *
     * <p>A missing file is not an error: Geyser may well start before the Paper side has
     * ever generated one, and an extension that refused to load in that case would be
     * harder to set up than one that simply has nothing to register yet.</p>
     */
    public static KaloMappings load(Path file) throws IOException {
        if (!Files.exists(file)) {
            return empty();
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            try {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) {
                    throw new IOException("expected a JSON object in " + file);
                }
                return parse(parsed.getAsJsonObject());
            } catch (IOException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new IOException("invalid Kalo mappings in " + file + ": " + e.getMessage(), e);
            }
        }
    }

    static KaloMappings parse(JsonObject root) throws IOException {
        JsonElement format = root.get("format_version");
        if (format != null && (!format.isJsonPrimitive() || !format.getAsJsonPrimitive().isNumber()
                || format.getAsInt() != 2)) {
            throw new IOException("unsupported Kalo mapping format_version: " + format);
        }

        List<BlockEntry> blocks = new ArrayList<>();
        List<ItemEntry> items = new ArrayList<>();
        Set<String> javaIdentifiers = new HashSet<>();
        Set<String> bedrockIdentifiers = new HashSet<>();

        JsonObject geometries = object(root, "kalo:geometries");

        JsonElement blockArray = root.get("kalo:blocks");
        if (blockArray != null) {
            if (!blockArray.isJsonArray()) {
                throw new IOException("kalo:blocks must be an array");
            }
            for (JsonElement element : blockArray.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    throw new IOException("block entry must be an object: " + element);
                }
                JsonObject entry = element.getAsJsonObject();

                String javaKey = string(entry, "java_key");
                String bedrockId = string(entry, "bedrock_identifier");
                if (blank(javaKey) || blank(bedrockId)) {
                    throw new IOException("block entry is missing java_key or bedrock_identifier: " + entry);
                }
                if (!bedrockId.contains(":")) {
                    throw new IOException("invalid bedrock_identifier '" + bedrockId + "' in " + entry);
                }

                Integer state = integer(entry, "java_carrier_state");
                String javaIdentifier = string(entry, "java_identifier");
                if (blank(javaIdentifier) && state != null) {
                    javaIdentifier = noteBlockIdentifier(state);
                }
                if (!blank(javaIdentifier) && !javaIdentifiers.add(javaIdentifier)) {
                    throw new IOException("Java block state is mapped more than once: " + javaIdentifier);
                }
                if (!bedrockIdentifiers.add(bedrockId)) {
                    throw new IOException("Bedrock block identifier is mapped more than once: " + bedrockId);
                }

                String geometry = string(entry, "geometry");
                if (blank(geometry) && geometries != null) {
                    geometry = string(geometries, javaKey);
                }
                String displayName = string(entry, "display_name");
                Float hardness = decimal(entry, "hardness");
                Map<String, String> materialInstances = stringMap(
                        object(entry, "material_instances"), "material_instances");

                blocks.add(new BlockEntry(javaKey, bedrockId, state, javaIdentifier,
                        geometry, displayName, hardness, materialInstances));
            }
        }

        JsonObject itemMappings = object(root, "items");
        Set<String> itemModels = new HashSet<>();
        Set<String> itemBedrockIdentifiers = new HashSet<>();
        if (itemMappings != null) {
            for (var mapping : itemMappings.entrySet()) {
                String javaIdentifier = mapping.getKey();
                if (blank(javaIdentifier) || !javaIdentifier.contains(":")) {
                    throw new IOException("invalid Java item identifier '" + javaIdentifier + "'");
                }
                if (!mapping.getValue().isJsonArray()) {
                    throw new IOException("item definitions for " + javaIdentifier + " must be an array");
                }

                for (JsonElement element : mapping.getValue().getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        throw new IOException("item definition for " + javaIdentifier
                                + " must be an object: " + element);
                    }
                    JsonObject definition = element.getAsJsonObject();
                    String type = string(definition, "type");
                    if (type != null && !"definition".equals(type)) {
                        throw new IOException("unsupported item definition type '" + type
                                + "' for " + javaIdentifier);
                    }

                    String bedrockId = string(definition, "bedrock_identifier");
                    String model = string(definition, "model");
                    if (blank(bedrockId) || !bedrockId.contains(":") || blank(model)
                            || !model.contains(":")) {
                        throw new IOException("item definition is missing a valid bedrock_identifier or model: "
                                + definition);
                    }

                    JsonObject bedrockOptions = object(definition, "bedrock_options");
                    String icon = bedrockOptions != null ? string(bedrockOptions, "icon") : null;
                    if (blank(icon)) {
                        // Backward compatibility with Kalo mappings generated before the
                        // icon was moved into Geyser v2's required section.
                        icon = string(definition, "icon");
                    }
                    if (blank(icon)) {
                        icon = bedrockId.replace(':', '.').replace('/', '_');
                    }

                    JsonObject components = object(definition, "components");
                    Integer maxStackSize = components != null
                            ? integer(components, "minecraft:max_stack_size") : null;
                    Integer maxDamage = components != null
                            ? integer(components, "minecraft:max_damage") : null;
                    if (maxDamage == null && components != null) {
                        // Older Kalo output used the Bedrock output-component name here.
                        maxDamage = integer(components, "minecraft:durability");
                    }
                    int stack = maxStackSize != null ? maxStackSize : 1;
                    if (stack < 1 || stack > 99) {
                        throw new IOException("minecraft:max_stack_size must be within 1..99 in " + definition);
                    }
                    if (maxDamage != null && maxDamage < 0) {
                        throw new IOException("minecraft:max_damage must not be negative in " + definition);
                    }
                    if (maxDamage != null && maxDamage > 0 && stack > 1) {
                        throw new IOException("a damageable item cannot stack in " + definition);
                    }
                    Boolean glint = components != null
                            ? bool(components, "minecraft:enchantment_glint_override") : null;

                    String modelMapping = javaIdentifier + "\u0000" + model;
                    if (!itemModels.add(modelMapping)) {
                        throw new IOException("Java item model is mapped more than once: "
                                + javaIdentifier + " / " + model);
                    }
                    if (!itemBedrockIdentifiers.add(bedrockId)) {
                        throw new IOException("Bedrock item identifier is mapped more than once: " + bedrockId);
                    }

                    items.add(new ItemEntry(javaIdentifier, bedrockId, model, icon,
                            string(definition, "display_name"), stack, maxDamage,
                            Boolean.TRUE.equals(glint)));
                }
            }
        }

        return new KaloMappings(List.copyOf(blocks), List.copyOf(items));
    }

    private static String string(JsonObject object, String member) {
        JsonElement element = object.get(member);
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
    }

    private static JsonObject object(JsonObject root, String member) throws IOException {
        JsonElement element = root.get(member);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonObject()) {
            throw new IOException(member + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static Map<String, String> stringMap(JsonObject object, String member)
            throws IOException {
        if (object == null) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (var entry : object.entrySet()) {
            if (blank(entry.getKey()) || !entry.getValue().isJsonPrimitive()
                    || !entry.getValue().getAsJsonPrimitive().isString()
                    || blank(entry.getValue().getAsString())) {
                throw new IOException(member + " must map non-empty names to non-empty strings: "
                        + object);
            }
            values.put(entry.getKey(), entry.getValue().getAsString());
        }
        return Map.copyOf(values);
    }

    private static Integer integer(JsonObject object, String member) throws IOException {
        JsonElement element = object.get(member);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IOException(member + " must be an integer in " + object);
        }
        try {
            double number = element.getAsDouble();
            if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                throw new IOException(member + " must be an integer in " + object);
            }
            return (int) number;
        } catch (RuntimeException e) {
            throw new IOException(member + " must be an integer in " + object, e);
        }
    }

    private static Boolean bool(JsonObject object, String member) throws IOException {
        JsonElement element = object.get(member);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IOException(member + " must be a boolean in " + object);
        }
        return element.getAsBoolean();
    }

    private static Float decimal(JsonObject object, String member) throws IOException {
        JsonElement element = object.get(member);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IOException(member + " must be a number in " + object);
        }
        try {
            return element.getAsFloat();
        } catch (RuntimeException e) {
            throw new IOException(member + " must be a number in " + object, e);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** Converts Kalo's persisted note-block index into Geyser's Java state identifier. */
    static String noteBlockIdentifier(int index) throws IOException {
        int stateCount = NOTE_BLOCK_INSTRUMENTS.size() * NOTES_PER_INSTRUMENT * POWER_STATES;
        if (index <= 0 || index >= stateCount) {
            throw new IOException("java_carrier_state " + index + " is outside Kalo's note-block range");
        }

        boolean powered = index % POWER_STATES == 1;
        int rest = index / POWER_STATES;
        int note = rest % NOTES_PER_INSTRUMENT;
        int instrument = rest / NOTES_PER_INSTRUMENT;
        return "minecraft:note_block[instrument=" + NOTE_BLOCK_INSTRUMENTS.get(instrument)
                + ",note=" + note + ",powered=" + powered + "]";
    }

    /** Convenience for {@code kalo:blocks} being absent entirely. */
    public boolean isEmpty() {
        return blocks.isEmpty() && items.isEmpty();
    }
}

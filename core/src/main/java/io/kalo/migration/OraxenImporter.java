package io.kalo.migration;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Converts Oraxen — and Nexo, which continues the same format — item configs into Kalo
 * definitions.
 *
 * <p>The shapes are close enough that most items map cleanly:</p>
 *
 * <pre>
 * ruby_sword:                        ruby_sword:
 *   displayname: "&lt;red&gt;Ruby"          type: item
 *   material: NETHERITE_SWORD   →     display:
 *   Pack:                               name: "&lt;red&gt;Ruby"
 *     generate_model: true            model:
 *     textures:                         sprite: "item/ruby_sword"
 *       - item/ruby_sword.png         java:
 *   durability:                         base_material: NETHERITE_SWORD
 *     value: 250                      behaviour:
 *                                       durability: 250
 * </pre>
 *
 * <p>What does not map is the mechanics: Oraxen's {@code Mechanics} block drives its own
 * behaviour system, which Kalo expresses through features instead. Those are reported as
 * unsupported rather than guessed at — a mechanic quietly dropped is a bug a server owner
 * finds out about from their players.</p>
 *
 * <p><b>Caveat.</b> This is written against the documented format rather than validated
 * against a corpus of real packs, so treat a first import as something to review, not to
 * trust. The report names everything it could not carry over.</p>
 */
public final class OraxenImporter {

    /**
     * Top-level keys the importer understands. Anything else in an item is reported, which
     * is what makes an unrecognised or newer format visible instead of silently lossy.
     */
    private static final Set<String> KNOWN_KEYS = Set.of(
            "displayname", "display_name", "material", "Pack", "pack",
            "durability", "itemname", "lore", "unbreakable", "color",
            "customModelData", "custom_model_data"
    );

    private OraxenImporter() {
    }

    /**
     * Converts one Oraxen/Nexo items file into Kalo pack YAML.
     *
     * @param namespace the Kalo pack id the imported content will live under
     * @return the generated YAML, ready to be written to {@code configs/}
     */
    public static @NotNull String convert(@NotNull YamlConfiguration source,
                                          @NotNull String namespace,
                                          @NotNull ImportReport report) {
        YamlConfiguration output = new YamlConfiguration();
        int blocks = 0;
        int furniture = 0;

        for (String key : source.getKeys(false)) {
            ConfigurationSection item = source.getConfigurationSection(key);
            if (item == null) {
                continue;
            }
            try {
                // Oraxen expresses a custom block as an item carrying the noteblock
                // mechanic, so what a config calls an item may be either.
                if (hasFurnitureMechanic(item)) {
                    convertFurniture(key, item, output, report);
                    furniture++;
                } else if (hasNoteBlockMechanic(item)) {
                    convertBlock(key, item, output, report);
                    blocks++;
                } else {
                    convertItem(key, item, output, report);
                }
                report.imported(namespace + ":" + key);
            } catch (Exception e) {
                report.failed(key, e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }

        BlockImportNotice.addTo(report, blocks + furniture);
        FurnitureImportNotice.addTo(report, furniture);
        return output.saveToString();
    }

    /** Oraxen marks a custom block with {@code Mechanics.noteblock}. */
    static boolean hasNoteBlockMechanic(@NotNull ConfigurationSection item) {
        ConfigurationSection mechanics = item.getConfigurationSection("Mechanics");
        if (mechanics == null) {
            mechanics = item.getConfigurationSection("mechanics");
        }
        return mechanics != null && (mechanics.contains("noteblock") || mechanics.contains("block"));
    }

    /**
     * Converts an Oraxen recipes file, which lives in its own folder rather than
     * alongside items.
     *
     * <p>Oraxen writes an ingredient as a section naming either {@code oraxen_item} or
     * {@code minecraft_type}, which is how it distinguishes its own content from vanilla —
     * the same distinction Kalo makes with the namespace.</p>
     */
    public static @NotNull String convertRecipes(@NotNull YamlConfiguration source,
                                                 @NotNull String namespace,
                                                 @NotNull ImportReport report) {
        YamlConfiguration output = new YamlConfiguration();

        for (String key : source.getKeys(false)) {
            ConfigurationSection recipe = source.getConfigurationSection(key);
            if (recipe == null) {
                continue;
            }
            try {
                ConfigurationSection ingredients = recipe.getConfigurationSection("ingredients");
                if (ingredients == null) {
                    throw new IllegalArgumentException("recipe has no ingredients section");
                }

                ConfigurationSection resultSection = recipe.getConfigurationSection("result");
                String result = resultSection != null ? resultSection.getString("oraxen_item") : null;
                if (result == null) {
                    String vanillaResult = resultSection != null
                            ? resultSection.getString("minecraft_type") : null;
                    if (vanillaResult != null) {
                        // Kalo recipes produce Kalo content; a vanilla result would need a
                        // datapack instead.
                        report.unsupported(key + ".result.minecraft_type "
                                + "(Kalo recipes produce Kalo content, not vanilla items)");
                        continue;
                    }
                    throw new IllegalArgumentException("recipe has no result");
                }

                int amount = resultSection.getInt("amount", 1);
                List<String> pattern = recipe.getStringList("shape");

                output.createSection(key, RecipeImport.convert(key, pattern, ingredients,
                        result, amount, OraxenImporter::readIngredient, report));
                report.imported(namespace + ":" + key);
            } catch (Exception e) {
                report.failed(key, e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }

        return output.saveToString();
    }

    private static @Nullable String readIngredient(@NotNull ConfigurationSection ingredient,
                                                   @NotNull String slot) {
        String oraxenItem = ingredient.getString("oraxen_item");
        if (oraxenItem != null) {
            // Unqualified means "in this pack", which is what Kalo assumes too.
            return oraxenItem;
        }
        String material = ingredient.getString("minecraft_type");
        return material != null ? RecipeImport.vanilla(material) : null;
    }

    /** Oraxen marks furniture with {@code Mechanics.furniture}. */
    static boolean hasFurnitureMechanic(@NotNull ConfigurationSection item) {
        ConfigurationSection mechanics = item.getConfigurationSection("Mechanics");
        if (mechanics == null) {
            mechanics = item.getConfigurationSection("mechanics");
        }
        return mechanics != null && mechanics.contains("furniture");
    }

    private static void convertFurniture(@NotNull String key,
                                         @NotNull ConfigurationSection item,
                                         @NotNull YamlConfiguration output,
                                         @NotNull ImportReport report) {
        // Same shape as a block on Kalo's side; the difference is everything that does
        // not come with it.
        convertBlock(key, item, output, report);

        ConfigurationSection converted = output.getConfigurationSection(key);
        if (converted != null) {
            converted.set("type", "furniture");
        }

        ConfigurationSection mechanics = item.getConfigurationSection("Mechanics");
        if (mechanics == null) {
            mechanics = item.getConfigurationSection("mechanics");
        }
        ConfigurationSection furniture = mechanics != null
                ? mechanics.getConfigurationSection("furniture") : null;
        if (furniture == null) {
            return;
        }

        // Named individually rather than as one blanket warning: a chair losing its seat
        // and a lamp losing its hitbox need different follow-up work.
        for (String property : furniture.getKeys(false)) {
            switch (property) {
                case "hitbox", "barriers", "barrier" ->
                        report.unsupported(key + ".Mechanics.furniture." + property
                                + " (Kalo furniture occupies exactly one block)");
                case "seat", "seats" ->
                        report.unsupported(key + ".Mechanics.furniture." + property
                                + " (Kalo furniture cannot be sat on)");
                case "rotatable", "rotation" ->
                        report.unsupported(key + ".Mechanics.furniture." + property
                                + " (Kalo furniture faces one way)");
                case "type", "display_entity", "item_frame" ->
                        report.unsupported(key + ".Mechanics.furniture." + property
                                + " (Kalo furniture is block-backed, not entity-backed)");
                default -> report.unsupported(key + ".Mechanics.furniture." + property);
            }
        }
    }

    private static void convertBlock(@NotNull String key,
                                     @NotNull ConfigurationSection item,
                                     @NotNull YamlConfiguration output,
                                     @NotNull ImportReport report) {
        Map<String, Object> converted = new LinkedHashMap<>();
        converted.put("type", "block");

        Map<String, Object> display = new LinkedHashMap<>();
        String name = firstNonNull(item.getString("displayname"), item.getString("display_name"),
                item.getString("itemname"));
        if (name != null) {
            display.put("name", name);
        }
        if (!display.isEmpty()) {
            converted.put("display", display);
        }

        ConfigurationSection pack = item.getConfigurationSection("Pack");
        if (pack == null) {
            pack = item.getConfigurationSection("pack");
        }
        String model = pack != null ? pack.getString("model") : null;
        if (model != null) {
            converted.put("model", Map.of("custom", stripExtension(model)));
        } else {
            List<String> textures = pack != null ? pack.getStringList("textures") : List.of();
            if (textures.isEmpty()) {
                throw new IllegalArgumentException("block has no model or texture in its Pack section");
            }
            converted.put("model", Map.of("cube_all", stripExtension(textures.get(0))));
        }

        ConfigurationSection mechanics = item.getConfigurationSection("Mechanics");
        if (mechanics == null) {
            mechanics = item.getConfigurationSection("mechanics");
        }
        ConfigurationSection noteblock = mechanics != null ? mechanics.getConfigurationSection("noteblock") : null;
        if (noteblock != null && noteblock.contains("custom_variation")) {
            // Deliberately not carried over: Kalo owns its own state allocation, and
            // adopting Oraxen's numbering would collide with blocks Kalo already placed.
            report.unsupported(key + ".Mechanics.noteblock.custom_variation "
                    + "(Kalo allocates its own states; see the world-migration warning)");
        }

        Map<String, Object> behaviour = new LinkedHashMap<>();
        if (mechanics != null) {
            ConfigurationSection hardness = mechanics.getConfigurationSection("hardness");
            if (hardness != null || mechanics.contains("hardness")) {
                behaviour.put("hardness", mechanics.getDouble("hardness", 1.5));
            }
        }
        if (!behaviour.isEmpty()) {
            converted.put("behaviour", behaviour);
        }

        output.createSection(key, converted);
    }

    private static void convertItem(@NotNull String key,
                                    @NotNull ConfigurationSection item,
                                    @NotNull YamlConfiguration output,
                                    @NotNull ImportReport report) {
        Map<String, Object> converted = new LinkedHashMap<>();
        converted.put("type", "item");

        Map<String, Object> display = new LinkedHashMap<>();
        String name = firstNonNull(item.getString("displayname"), item.getString("display_name"),
                item.getString("itemname"));
        if (name != null) {
            // Oraxen accepts both MiniMessage and legacy '&' codes. Kalo is MiniMessage
            // only, so legacy is flagged rather than half-translated.
            if (name.indexOf('&') >= 0 || name.indexOf('§') >= 0) {
                report.warn("'" + key + "' uses legacy colour codes in its name; rewrite as MiniMessage");
            }
            display.put("name", name);
        }
        List<String> lore = item.getStringList("lore");
        if (!lore.isEmpty()) {
            display.put("lore", lore);
        }
        if (!display.isEmpty()) {
            converted.put("display", display);
        }

        ConfigurationSection pack = item.getConfigurationSection("Pack");
        if (pack == null) {
            pack = item.getConfigurationSection("pack");
        }
        Map<String, Object> model = convertModel(key, pack, report);
        if (model != null) {
            converted.put("model", model);
        }

        Map<String, Object> behaviour = new LinkedHashMap<>();
        ConfigurationSection durability = item.getConfigurationSection("durability");
        if (durability != null && durability.contains("value")) {
            behaviour.put("durability", durability.getInt("value"));
        }
        if (!behaviour.isEmpty()) {
            converted.put("behaviour", behaviour);
        }

        String material = item.getString("material");
        if (material != null) {
            Material parsed = Material.matchMaterial(material.toUpperCase(Locale.ROOT));
            if (parsed == null) {
                throw new IllegalArgumentException("unknown material '" + material + "'");
            }
            converted.put("java", Map.of("base_material", parsed.name()));
        }

        reportUnknownKeys(key, item, report);

        output.createSection(key, converted);
    }

    /**
     * Oraxen's {@code Pack} section is where the model lives. {@code generate_model: true}
     * with a texture list is the sprite case; a {@code model} path is a hand-authored one.
     */
    private static @Nullable Map<String, Object> convertModel(@NotNull String key,
                                                              @Nullable ConfigurationSection pack,
                                                              @NotNull ImportReport report) {
        if (pack == null) {
            return null;
        }

        String model = pack.getString("model");
        if (model != null) {
            return Map.of("custom", stripExtension(model));
        }

        List<String> textures = pack.getStringList("textures");
        if (textures.isEmpty()) {
            String texture = pack.getString("texture");
            if (texture != null) {
                textures = List.of(texture);
            }
        }

        if (textures.isEmpty()) {
            return null;
        }
        if (textures.size() > 1) {
            // Multiple layers mean a tinted or composited item; Kalo's sprite model is a
            // single layer, so the rest would be dropped without saying so.
            report.unsupported(key + ".Pack.textures (" + textures.size() + " layers; only the first is used)");
        }

        return Map.of("sprite", stripExtension(textures.get(0)));
    }

    private static void reportUnknownKeys(@NotNull String key,
                                          @NotNull ConfigurationSection item,
                                          @NotNull ImportReport report) {
        for (String child : item.getKeys(false)) {
            if (KNOWN_KEYS.contains(child)) {
                continue;
            }
            if (child.equals("Mechanics") || child.equals("mechanics")) {
                // Oraxen's behaviour system. Kalo expresses these as features, which is a
                // different model — a mapping would be guesswork.
                report.unsupported(key + ".Mechanics (Kalo uses features; port these by hand)");
                continue;
            }
            report.unsupported(key + "." + child);
        }
    }

    /** Oraxen texture paths carry a {@code .png}; Kalo keys do not. */
    static @NotNull String stripExtension(@NotNull String path) {
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        return dot > slash ? path.substring(0, dot) : path;
    }

    @SafeVarargs
    private static <T> @Nullable T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}

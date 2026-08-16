package io.kalo.migration;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * Adapts {@link OraxenImporter} to the {@link Importer} contract.
 *
 * <p>Oraxen splits items and recipes across separate folders with no marker in the file
 * itself, so which of the two converters to run is decided by shape: a recipe entry has a
 * {@code result} section and an item never does.</p>
 */
public final class OraxenFormatImporter implements Importer {

    @Override
    public @NotNull String name() {
        return "Oraxen";
    }

    @Override
    public int detect(@NotNull YamlConfiguration source) {
        for (String key : source.getKeys(false)) {
            ConfigurationSection section = source.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            // Distinctive to Oraxen: a Pack section, or its own ingredient naming.
            if (section.isConfigurationSection("Pack") || section.isConfigurationSection("pack")) {
                return 80;
            }
            if (section.isConfigurationSection("Mechanics") || section.isConfigurationSection("mechanics")) {
                return 80;
            }
            if (looksLikeRecipe(section)) {
                return 70;
            }
        }
        return 0;
    }

    static boolean looksLikeRecipe(@NotNull ConfigurationSection section) {
        return section.isConfigurationSection("result");
    }

    @Override
    public @NotNull String convert(@NotNull YamlConfiguration source,
                                   @NotNull String namespace,
                                   @NotNull ImportReport report) {
        boolean recipes = source.getKeys(false).stream()
                .map(source::getConfigurationSection)
                .anyMatch(section -> section != null && looksLikeRecipe(section));

        return recipes
                ? OraxenImporter.convertRecipes(source, namespace, report)
                : OraxenImporter.convert(source, namespace, report);
    }
}

package io.kalo.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

/** Adapts {@link ItemsAdderImporter} to the {@link Importer} contract. */
public final class ItemsAdderFormatImporter implements Importer {

    @Override
    public @NotNull String name() {
        return "ItemsAdder";
    }

    @Override
    public int detect(@NotNull YamlConfiguration source) {
        // info.namespace is unique to ItemsAdder and the strongest signal available.
        if (ItemsAdderImporter.namespaceOf(source) != null) {
            return 100;
        }
        return ItemsAdderImporter.looksLikeItemsAdder(source) ? 75 : 0;
    }

    @Override
    public @NotNull String convert(@NotNull YamlConfiguration source,
                                   @NotNull String namespace,
                                   @NotNull ImportReport report) {
        return ItemsAdderImporter.convert(source, report);
    }
}

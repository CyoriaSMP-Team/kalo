package io.kalo.mohist.content;

import java.util.Locale;

/**
 * Extension point for asset classification. Implementations may be provided
 * by trusted classpath extensions (ServiceLoader) without changing the core
 * scanner. No provider is loaded from arbitrary Forge jars.
 */
public interface ContentProvider {
    String id();

    /** Return null when the asset is not recognized by this provider. */
    Kind classify(String archivePath);

    enum Kind {
        ITEM_MODEL, BLOCK_MODEL, BLOCK_STATE, TEXTURE, ANIMATION_METADATA,
        ENTITY_ANIMATION, SOUND, LANGUAGE, PARTICLE, FONT, RECIPE, LOOT_TABLE,
        ENTITY_MODEL, GEOMETRY, MOD_METADATA, OTHER_ASSET;

        public String jsonName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}

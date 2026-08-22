package io.kalo.content.painting;

import io.kalo.content.Content;
import io.kalo.content.painting.definition.PaintingDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * A registered custom painting.
 */
public interface Painting extends Content {
    @NotNull PaintingDefinition paintingDefinition();
}

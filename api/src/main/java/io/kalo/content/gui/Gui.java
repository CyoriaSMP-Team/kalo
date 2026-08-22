package io.kalo.content.gui;

import io.kalo.content.Content;
import org.jetbrains.annotations.NotNull;

/**
 * A registered custom GUI (inventory menu).
 */
public interface Gui extends Content {
    @NotNull GuiDefinition guiDefinition();
}

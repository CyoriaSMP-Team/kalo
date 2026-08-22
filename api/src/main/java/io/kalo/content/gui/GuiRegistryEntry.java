package io.kalo.content.gui;

import io.kalo.content.ContentRegistryEntry;
import org.jetbrains.annotations.NotNull;

public interface GuiRegistryEntry extends ContentRegistryEntry<GuiRegistryEntry, Gui> {
    @NotNull GuiRegistryEntry definition(@NotNull GuiDefinition definition);
}

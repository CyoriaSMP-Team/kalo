package io.kalo.registry;

import io.kalo.content.block.Block;
import io.kalo.content.block.BlockRegistryEntry;
import io.kalo.content.furniture.Furniture;
import io.kalo.content.furniture.FurnitureRegistryEntry;
import io.kalo.content.item.Item;
import io.kalo.content.item.ItemRegistryEntry;
import io.kalo.content.armor.Armor;
import io.kalo.content.armor.ArmorRegistryEntry;
import io.kalo.content.painting.Painting;
import io.kalo.content.painting.PaintingRegistryEntry;
import io.kalo.content.musicdisc.MusicDisc;
import io.kalo.content.musicdisc.MusicDiscRegistryEntry;
import io.kalo.content.gui.Gui;
import io.kalo.content.gui.GuiRegistryEntry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public interface Registries {
    @NotNull EntryWritableRegistry<Item, ItemRegistryEntry> item();

    @NotNull EntryWritableRegistry<Block, BlockRegistryEntry> block();

    @NotNull EntryWritableRegistry<Furniture, FurnitureRegistryEntry> furniture();

    @NotNull EntryWritableRegistry<Armor, ArmorRegistryEntry> armor();

    @NotNull EntryWritableRegistry<Painting, PaintingRegistryEntry> painting();

    @NotNull EntryWritableRegistry<MusicDisc, MusicDiscRegistryEntry> musicDisc();

    @NotNull EntryWritableRegistry<Gui, GuiRegistryEntry> gui();

    void lockAll();

    void unlockAll();

    @ApiStatus.Internal
    void clearAll();
}

package io.kalo.registry;

import io.kalo.content.block.Block;
import io.kalo.content.block.BlockRegistryEntry;
import io.kalo.content.block.BlockRegistryEntryImpl;
import io.kalo.content.furniture.Furniture;
import io.kalo.content.furniture.FurnitureRegistryEntry;
import io.kalo.content.furniture.FurnitureRegistryEntryImpl;
import io.kalo.content.armor.Armor;
import io.kalo.content.armor.ArmorRegistryEntry;
import io.kalo.content.armor.ArmorRegistryEntryImpl;
import io.kalo.content.item.Item;
import io.kalo.content.item.ItemRegistryEntry;
import io.kalo.content.item.ItemRegistryEntryImpl;
import io.kalo.content.painting.Painting;
import io.kalo.content.painting.PaintingRegistryEntry;
import io.kalo.content.painting.PaintingRegistryEntryImpl;
import io.kalo.content.musicdisc.MusicDisc;
import io.kalo.content.musicdisc.MusicDiscRegistryEntry;
import io.kalo.content.musicdisc.MusicDiscRegistryEntryImpl;
import io.kalo.content.gui.Gui;
import io.kalo.content.gui.GuiRegistryEntry;
import io.kalo.content.gui.GuiRegistryEntryImpl;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RegistriesImpl implements Registries {
    protected final List<Registry<?>> allRegistries = new ArrayList<>();

    @Getter @Accessors(fluent = true)
    private final EntryWritableRegistry<Item, ItemRegistryEntry> item = create(
            new EntryScalableRegistry<>(ItemRegistryEntryImpl::new)
    );

    @Getter @Accessors(fluent = true)
    private final EntryWritableRegistry<Block, BlockRegistryEntry> block = create(
            new EntryScalableRegistry<>(BlockRegistryEntryImpl::new)
    );

    @Getter @Accessors(fluent = true)
    private final EntryWritableRegistry<Furniture, FurnitureRegistryEntry> furniture = create(
            new EntryScalableRegistry<>(FurnitureRegistryEntryImpl::new)
    );

    @Getter @Accessors(fluent = true)
    private final EntryWritableRegistry<Armor, ArmorRegistryEntry> armor = create(
            new EntryScalableRegistry<>(ArmorRegistryEntryImpl::new)
    );

    @Getter @Accessors(fluent = true)
    private final EntryWritableRegistry<Painting, PaintingRegistryEntry> painting = create(
            new EntryScalableRegistry<>(PaintingRegistryEntryImpl::new)
    );

    @Getter @Accessors(fluent = true)
    private final EntryWritableRegistry<MusicDisc, MusicDiscRegistryEntry> musicDisc = create(
            new EntryScalableRegistry<>(MusicDiscRegistryEntryImpl::new)
    );

    @Getter @Accessors(fluent = true)
    private final EntryWritableRegistry<Gui, GuiRegistryEntry> gui = create(
            new EntryScalableRegistry<>(GuiRegistryEntryImpl::new)
    );

    protected <T extends Registry<?>> T create(@NotNull T registry) {
        allRegistries.add(registry);
        return registry;
    }

    @Override
    public void lockAll() {
        for (Registry<?> registry : allRegistries) {
            if (registry instanceof WritableRegistry<?> writableRegistry) {
                writableRegistry.lock();
            }
        }
    }

    @Override
    public void unlockAll() {
        for (Registry<?> registry : allRegistries) {
            if (registry instanceof WritableRegistry<?> writableRegistry) {
                writableRegistry.unlock();
            }
        }
    }

    @Override
    public void clearAll() {
        for (Registry<?> registry : allRegistries) {
            if (registry instanceof WritableRegistry<?> writableRegistry) {
                writableRegistry.clear();
            }
        }
    }
}

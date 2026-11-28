package io.kalo.content.vehicle;

import io.kalo.content.ContentRegistryEntry;
import org.jetbrains.annotations.NotNull;

public interface VehicleRegistryEntry extends ContentRegistryEntry<VehicleRegistryEntry, Vehicle> {
    @NotNull VehicleRegistryEntry definition(@NotNull VehicleDefinition definition);
}

package io.kalo.content.vehicle;

import org.jetbrains.annotations.NotNull;

/** Where a vehicle is meant to operate. */
public enum VehicleMedium {
    LAND,
    WATER,
    AIR;

    public static @NotNull VehicleMedium parse(@NotNull String value) {
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "land", "ground" -> LAND;
            case "water", "boat", "swim" -> WATER;
            case "air", "fly", "flight" -> AIR;
            default -> throw new IllegalArgumentException(
                    "vehicle medium must be land, water or air, got '" + value + "'");
        };
    }
}

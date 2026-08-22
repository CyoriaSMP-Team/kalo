package io.kalo.platform.java;

import com.google.gson.JsonObject;
import io.kalo.content.furniture.Furniture;
import io.kalo.content.furniture.definition.FurnitureDefinition;
import io.kalo.content.furniture.definition.FurnitureDisplay;
import io.kalo.pack.Json;
import io.kalo.pack.ResourcePack;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

/**
 * Compiles furniture-specific display properties into the resource pack.
 *
 * <p>Extends the base block compiler with furniture-specific display transforms:
 * rotation tracking, translation, scale, brightness, and shadow properties.</p>
 */
public final class JavaFurnitureCompiler {
    private static final Logger LOGGER = Logger.getLogger(JavaFurnitureCompiler.class.getName());

    private JavaFurnitureCompiler() {
    }

    /**
     * Compiles furniture display properties into the resource pack.
     *
     * <p>Writes display transform files that control how furniture models are
     * rendered in the world: rotation, translation, scale, brightness, and shadow.</p>
     */
    public static void compileFurniture(@NotNull ResourcePack pack, @NotNull Iterable<Furniture> furniture) {
        for (Furniture piece : furniture) {
            try {
                compileFurniturePiece(pack, piece);
            } catch (Exception e) {
                LOGGER.warning("Failed to compile furniture display for " + piece.key().asString() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Compiles a single furniture piece's display properties.
     */
    private static void compileFurniturePiece(@NotNull ResourcePack pack, @NotNull Furniture furniture) {
        FurnitureDefinition definition = furniture.furnitureDefinition();
        FurnitureDisplay display = definition.displayTransform();

        // Only write display transform file if there are non-default properties
        if (hasCustomDisplay(display)) {
            JsonObject displayJson = buildDisplayJson(display);
            String path = "assets/" + definition.key().namespace() + "/models/furniture/" + definition.key().value() + ".display.json";
            pack.file(path, Json.writable(displayJson));
        }

        // Write brightness override if specified
        if (display.brightness() != null) {
            JsonObject brightnessJson = new JsonObject();
            brightnessJson.addProperty("block_light", display.brightness().blockLight());
            brightnessJson.addProperty("sky_light", display.brightness().skyLight());

            String path = "assets/" + definition.key().namespace() + "/models/furniture/" + definition.key().value() + ".brightness.json";
            pack.file(path, Json.writable(brightnessJson));
        }
    }

    /**
     * Checks if the display has any non-default properties.
     */
    private static boolean hasCustomDisplay(@NotNull FurnitureDisplay display) {
        return display.displayTransform() != null
                || display.trackingRotation() != null
                || display.translation() != null
                || display.scale() != null
                || display.shadowRadius() != null
                || display.shadowStrength() != null
                || display.viewRange() != null
                || display.displayWidth() != null
                || display.displayHeight() != null;
    }

    /**
     * Builds a JSON object for the display transform.
     */
    private static @NotNull JsonObject buildDisplayJson(@NotNull FurnitureDisplay display) {
        JsonObject root = new JsonObject();

        // Display transform type
        if (display.displayTransform() != null) {
            root.addProperty("transform", display.displayTransform());
        }

        // Tracking rotation (billboard)
        if (display.trackingRotation() != null) {
            root.addProperty("tracking_rotation", display.trackingRotation());
        }

        // Translation
        if (display.translation() != null) {
            JsonObject translation = new JsonObject();
            translation.addProperty("x", display.translation()[0]);
            translation.addProperty("y", display.translation()[1]);
            translation.addProperty("z", display.translation()[2]);
            root.add("translation", translation);
        }

        // Scale
        if (display.scale() != null) {
            JsonObject scale = new JsonObject();
            scale.addProperty("x", display.scale()[0]);
            scale.addProperty("y", display.scale()[1]);
            scale.addProperty("z", display.scale()[2]);
            root.add("scale", scale);
        }

        // Shadow
        if (display.shadowRadius() != null) {
            root.addProperty("shadow_radius", display.shadowRadius());
        }
        if (display.shadowStrength() != null) {
            root.addProperty("shadow_strength", display.shadowStrength());
        }

        // View range
        if (display.viewRange() != null) {
            root.addProperty("view_range", display.viewRange());
        }

        // Culling
        if (display.displayWidth() != null || display.displayHeight() != null) {
            JsonObject culling = new JsonObject();
            if (display.displayWidth() != null) {
                culling.addProperty("width", display.displayWidth());
            }
            if (display.displayHeight() != null) {
                culling.addProperty("height", display.displayHeight());
            }
            root.add("culling", culling);
        }

        return root;
    }
}

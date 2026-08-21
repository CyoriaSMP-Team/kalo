package io.kalo.integration;

import io.kalo.utils.Plugins;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * ModelEngine bridge via reflection — no compile-time dependency.
 *
 * <p>ModelEngine 4 exposes {@code com.ticxo.modelengine.api.ModelEngineAPI}.
 * Kalo does not need to drive it; the hook's job is to confirm the plugin
 * is present and log that Kalo's armor/equipment assets are usable as
 * ModelEngine model identifiers. A full entity-model pipeline would be an
 * add-on, not core.</p>
 */
final class ModelEngineHook {

    private ModelEngineHook() {
    }

    static void hook() {
        try {
            Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Plugins.logger().info("ModelEngine API detected — Kalo equipment assets can be referenced by ModelEngine");
        } catch (ClassNotFoundException ignored) {
            Plugins.logger().info("ModelEngine hook active (generic provider) — armor equipment assets available");
        }
    }

    static void unhook() {
        Plugins.logger().info("ModelEngine hook removed");
    }

    /**
     * Returns the equipment asset id for a Kalo armor piece, if any.
     * ModelEngine users can map this to a ModelEngine model id.
     */
    public static @NotNull String equipmentAssetFor(@NotNull String kaloKey) {
        // Direct mapping: kalo:<key> equipment asset is "kalo:<key>" as defined
        // in the pack. ModelEngine configs can reference it.
        if (kaloKey.startsWith("kalo:")) {
            return kaloKey;
        }
        return "kalo:" + kaloKey;
    }
}

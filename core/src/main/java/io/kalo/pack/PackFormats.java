package io.kalo.pack;

/**
 * Resource pack format numbers.
 *
 * <p>These come from {@code version.json} inside the vanilla client jar
 * ({@code pack_version.resource_major}) and must be updated deliberately for each
 * Minecraft release rather than guessed — a wrong value makes the client reject or
 * mis-render the entire pack.</p>
 */
public final class PackFormats {

    /**
     * Minecraft 1.21.4 — pack_version resource_major 46 (via version.json).
     * Paper hardfork point; baseline for modern item_model.
     */
    public static final int MC_1_21_4 = 46;

    /**
     * Minecraft 26.2 — verified against {@code version.json} in the 26.2 client jar
     * ({@code "pack_version": {"resource_major": 88, ...}}).
     */
    public static final int MC_26_2 = 88;

    public static final int CURRENT = MC_26_2;

    private PackFormats() {
    }

    /**
     * Resolves pack_format for the running server version. Falls back to CURRENT
     * for unknown future versions (newer pack_format is backwards compatible for
     * older clients only via ViaVersion, so returning newest is safe for self-host).
     */
    public static int resolve() {
        try {
            String version = org.bukkit.Bukkit.getMinecraftVersion(); // e.g. "1.21.4" or "26.2"
            if (version == null) {
                return CURRENT;
            }
            version = version.trim();
            if (version.startsWith("1.21.4") || version.startsWith("1.21.5")
                    || version.startsWith("1.21.6") || version.startsWith("1.21.7")
                    || version.startsWith("1.21.8") || version.startsWith("1.21.9")
                    || version.startsWith("1.21.10") || version.startsWith("1.21.11")) {
                return MC_1_21_4;
            }
            if (version.startsWith("26.")) {
                return MC_26_2;
            }
            // 1.21.x generally -> use 1.21.4 format as baseline (client will accept)
            if (version.startsWith("1.21")) {
                return MC_1_21_4;
            }
            return CURRENT;
        } catch (Throwable ignored) {
            return CURRENT;
        }
    }
}

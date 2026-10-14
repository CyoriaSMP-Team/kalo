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

    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(PackFormats.class.getName());

    /**
     * Minecraft 1.21.4 — pack_version resource_major 46 (via version.json).
     * Paper hardfork point; baseline for modern item_model.
     */
    public static final int MC_1_21_4 = 46;

    /**
     * Minecraft 1.21.11 — read out of {@code version.json} in the 1.21.11 client jar
     * ({@code "pack_version": {"resource_major": 75, "resource_minor": 0, ...}}).
     *
     * <p>Added because the first real deployment landed on exactly this version and took
     * 46 — the 1.21.4 number — because nothing between the two ends of the supported range
     * had ever been checked.</p>
     */
    /** Verified against the official release client jars and Mojang SHA1 manifests. */
    public static final int MC_1_21_5 = 55;
    public static final int MC_1_21_6 = 63;
    public static final int MC_1_21_7 = 64;
    public static final int MC_1_21_8 = 64;
    public static final int MC_1_21_9 = 69;
    public static final int MC_1_21_10 = 69;
    public static final int MC_26_1 = 84;

    public static final int MC_1_21_11 = 75;

    /**
     * Minecraft 26.2 — verified against {@code version.json} in the 26.2 client jar
     * ({@code "pack_version": {"resource_major": 88, ...}}).
     */
    public static final int MC_26_2 = 88;

    public static final int CURRENT = MC_26_2;

    private PackFormats() {
    }

    /**
     * Resource-pack major numbers verified from official client jar version.json.
     *
     * <p>Minecraft 1.21.5-1.21.8 used the older
     * {@code pack_version.resource} JSON field; later versions use
     * {@code pack_version.resource_major}. Both are checked against the
     * official Mojang client SHA-1 metadata.</p>
     */
    private static final java.util.Map<String, Integer> VERIFIED = java.util.Map.ofEntries(
            java.util.Map.entry("1.21.4", MC_1_21_4),
            java.util.Map.entry("1.21.5", MC_1_21_5),
            java.util.Map.entry("1.21.6", MC_1_21_6),
            java.util.Map.entry("1.21.7", MC_1_21_7),
            java.util.Map.entry("1.21.8", MC_1_21_8),
            java.util.Map.entry("1.21.9", MC_1_21_9),
            java.util.Map.entry("1.21.10", MC_1_21_10),
            java.util.Map.entry("1.21.11", MC_1_21_11),
            java.util.Map.entry("26.1", MC_26_1),
            java.util.Map.entry("26.2", MC_26_2)
    );

    public static int resolve() {
        String version;
        try {
            version = org.bukkit.Bukkit.getMinecraftVersion();
        } catch (LinkageError | RuntimeException e) {
            return CURRENT; // Compiler tests without a running Paper server.
        }
        return version == null ? CURRENT : resolveForVersion(version);
    }

    /** Package-private: test without a running Bukkit registry or server. */
    static int resolveForVersion(@org.jetbrains.annotations.NotNull String serverVersion) {
        String version = serverVersion.trim();
        // Paper may report a suffix such as -R0.1; only the release prefix
        // is part of the Minecraft version's pack-format identity.
        String baseVersion = version.split("-", 2)[0];
        Integer exact = VERIFIED.get(baseVersion);
        if (exact != null) return exact;

        // Never claim an unsupported/untested release is verified. An older
        // pack format may load in some clients but will be missing new features.
        if (version.startsWith("1.21")) {
            warnUnverified(version, MC_1_21_4);
            return MC_1_21_4;
        }
        warnUnverified(version, CURRENT);
        return CURRENT;
    }


    private static void warnUnverified(@org.jetbrains.annotations.NotNull String version, int using) {
        LOGGER.warning("No verified pack_format for Minecraft " + version + "; using " + using
                + ". Read pack_version.resource_major from that version's client jar and add"
                + " it here — a wrong pack_format can make the client reject the whole pack.");
    }
}

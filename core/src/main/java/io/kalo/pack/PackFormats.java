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
     * Minecraft 26.2 — verified against {@code version.json} in the 26.2 client jar
     * ({@code "pack_version": {"resource_major": 88, ...}}).
     */
    public static final int MC_26_2 = 88;

    public static final int CURRENT = MC_26_2;

    private PackFormats() {
    }

    /**
     * Resolves pack_format for the running server version.
     *
     * <p>Only the two versions with a verified number are answered outright. Anything else
     * gets the nearest number in its family plus a warning: guessing is the failure this
     * class exists to prevent, so at least say so out loud. Newer is not the safe guess —
     * a client refuses a pack_format above its own but loads one below it.</p>
     *
     * <p>An earlier version enumerated 1.21.5 through 1.21.11 and returned 46 for each with
     * no warning, as if the number had been checked. It had not — those releases each moved
     * {@code resource_major} — and the enumeration was unreachable anyway, because a
     * trailing {@code "1.21"} prefix test already caught them. The behaviour for those
     * versions is unchanged; what changed is that it now says it is guessing.</p>
     */
    public static int resolve() {
        String version;
        try {
            version = org.bukkit.Bukkit.getMinecraftVersion(); // e.g. "1.21.4" or "26.2"
        } catch (LinkageError | RuntimeException e) {
            // No server around: compilers must stay runnable outside one.
            return CURRENT;
        }
        if (version == null) {
            return CURRENT;
        }

        version = version.trim();
        if (version.equals("1.21.4") || version.startsWith("1.21.4-")) {
            return MC_1_21_4;
        }
        if (version.equals("26.2") || version.startsWith("26.2.") || version.startsWith("26.2-")) {
            return MC_26_2;
        }

        // Neither number is verified for this version. Answer with the nearest one in the
        // same family rather than the newest: a client rejects a pack_format newer than
        // itself outright, while an older one loads with a warning.
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

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
     * Minecraft 26.2 — verified against {@code version.json} in the 26.2 client jar
     * ({@code "pack_version": {"resource_major": 88, ...}}).
     */
    public static final int MC_26_2 = 88;

    public static final int CURRENT = MC_26_2;

    private PackFormats() {
    }
}

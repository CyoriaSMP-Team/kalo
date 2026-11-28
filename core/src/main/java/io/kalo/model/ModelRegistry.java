package io.kalo.model;

import io.kalo.model.blockbench.BlockbenchImporter;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Namespaced, content-addressed cache for imported model sources.
 *
 * <p>Cache identity is the namespaced source key plus a SHA-256 of the bytes. Re-saving a
 * model therefore invalidates its cache entry on the next file change without requiring a
 * server restart, and a reload cannot keep serving a stale geometry. The cache is shared by
 * the Java and Bedrock compilers so one pack generation parses a model once and both
 * platforms consume the same IR.</p>
 */
public final class ModelRegistry {

    /** Full-speed entry count. Scaled down by the runtime budget, never below the floor. */
    private static final int BASE_CAPACITY = 512;
    private static final int MIN_CAPACITY = 64;

    private static final BoundedCache<String, CachedModel> CACHE =
            new BoundedCache<>(BASE_CAPACITY);

    private ModelRegistry() {
    }

    /**
     * Pack-relative path of a model source. The model lives under the contents pack's
     * {@code assets/<namespace>/} tree so it is copied into the generated Java pack by the
     * normal asset stage.
     */
    public static @NotNull String assetPath(@NotNull Key source) {
        if (!source.value().endsWith(".bbmodel")) {
            throw new IllegalArgumentException("Blockbench model key must end in .bbmodel: "
                    + source);
        }
        validateSegment(source.namespace(), "namespace");
        for (String segment : source.value().split("/", -1)) {
            validateSegment(segment, "model path");
        }
        return "assets/" + source.namespace() + "/" + source.value();
    }

    /** Loads a model from the in-memory pack produced by the asset-copy stage. */
    public static @NotNull ModelIR load(@NotNull ResourcePack pack, @NotNull Key source)
            throws IOException, ModelImportException {
        String path = assetPath(source);
        Writable content = pack.file(path);
        if (content == null) {
            throw new ModelImportException("Missing Blockbench model " + source.asString()
                    + " (expected pack file " + path + ")");
        }
        return load(content.toByteArray(), source, path);
    }

    /** Load-time path used before the generated pack exists. */
    public static @NotNull ModelIR load(@NotNull Path packRoot, @NotNull Key source)
            throws IOException, ModelImportException {
        Path root = packRoot.toAbsolutePath().normalize();
        Path file = root.resolve(assetPath(source)).normalize();
        if (!file.startsWith(root)) {
            throw new ModelImportException("Blockbench model path escapes its pack: "
                    + source.asString());
        }
        if (!Files.isRegularFile(file)) {
            throw new ModelImportException("Missing Blockbench model " + source.asString()
                    + " (expected " + file + ")");
        }
        Path realRoot = root.toRealPath();
        Path realFile = file.toRealPath();
        if (!realFile.startsWith(realRoot)) {
            throw new ModelImportException("Blockbench model resolves outside its pack: "
                    + source.asString());
        }
        long size = Files.size(realFile);
        if (size > BlockbenchImporter.MAX_FILE_BYTES) {
            throw new ModelImportException("Blockbench model " + source.asString() + " is "
                    + size + " bytes; maximum is " + BlockbenchImporter.MAX_FILE_BYTES);
        }
        return load(Files.readAllBytes(realFile), source, realFile.toString());
    }

    /**
     * Imports bytes and caches the result by content hash. A malformed model is never
     * cached, so fixing the file takes effect on the next attempt.
     */
    public static @NotNull ModelIR load(byte @NotNull [] bytes, @NotNull Key source,
                                        @NotNull String label)
            throws ModelImportException {
        if (bytes.length > BlockbenchImporter.MAX_FILE_BYTES) {
            throw new ModelImportException("Blockbench model " + source.asString() + " is "
                    + bytes.length + " bytes; maximum is "
                    + BlockbenchImporter.MAX_FILE_BYTES);
        }
        String fingerprint = fingerprint(bytes);
        CachedModel cached = CACHE.get(source.asString());
        if (cached != null && cached.fingerprint().equals(fingerprint)) {
            return cached.model();
        }
        ModelIR model = BlockbenchImporter.importBytes(bytes, source, label);
        CACHE.put(source.asString(), new CachedModel(fingerprint, model));
        return model;
    }

    /** Removes compile-time Blockbench projects from a generated client pack. */
    public static void removeSources(@NotNull ResourcePack pack) {
        pack.files().keySet().stream()
                .filter(path -> path.endsWith(".bbmodel"))
                .toList()
                .forEach(pack::removeFile);
    }

    /** Removes one source from the cache; mostly useful for tests and hot reload. */
    public static void invalidate(@NotNull Key source) {
        CACHE.remove(source.asString());
    }

    /** Drops every cached model so a reload cannot reuse bytes from a previous pack set. */
    public static void invalidateAll() {
        CACHE.clear();
    }

    public static int size() {
        return CACHE.size();
    }

    /** Current entry limit; exposed so diagnostics can show budget consumption. */
    public static int capacity() {
        return CACHE.capacity();
    }

    /**
     * Applies {@link io.kalo.performance.RuntimeBudget#cacheRetentionScale()} to the model
     * cache. A server under pressure stops holding cold geometry instead of growing the
     * heap until the collector cannot keep up.
     */
    public static void applyRetentionScale(double scale) {
        CACHE.resize((int) Math.max(MIN_CAPACITY, Math.round(BASE_CAPACITY * scale)));
    }

    public static @Nullable String cachedFingerprint(@NotNull Key source) {
        CachedModel cached = CACHE.get(source.asString());
        return cached == null ? null : cached.fingerprint();
    }

    private static String fingerprint(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 is required by the Java platform; fall back to a length+hash code so
            // a broken runtime still invalidates on content changes most of the time.
            return bytes.length + ":" + java.util.Arrays.hashCode(bytes);
        }
    }

    private static void validateSegment(String segment, String field) {
        if (segment.isEmpty() || segment.equals(".") || segment.equals("..")
                || segment.indexOf('\\') >= 0 || segment.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Unsafe " + field + " segment: '" + segment + "'");
        }
    }

    private record CachedModel(@NotNull String fingerprint, @NotNull ModelIR model) {
    }
}

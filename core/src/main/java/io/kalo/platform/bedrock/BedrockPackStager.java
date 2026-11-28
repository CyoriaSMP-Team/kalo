package io.kalo.platform.bedrock;

import io.kalo.pack.Writable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Stages one Bedrock pack per XUID.
 *
 * <p>Bedrock negotiates its pack list during login. The generated global archive is not
 * the object a session is handed: each session gets its own copy under
 * {@code bedrock-packs/<xuid>.mcpack} with a small session marker. A later content build
 * replaces the staged bytes, and the bridge transfers the player back through the join
 * address so the next login reads the new copy.</p>
 *
 * <p>Staging is atomic and deterministic. A session file is only rebuilt when the shared
 * source archive or that player's overlay digest changes; that stops a rejoin from
 * rewriting the same pack forever.</p>
 */
public final class BedrockPackStager {

    public static final String SESSION_MARKER = "kalo-session.json";
    private static final Map<String, String> SERVED = new ConcurrentHashMap<>();

    private BedrockPackStager() {
    }

    /**
     * Safe filename stem for an XUID. Numeric XUIDs are the normal case and pass through
     * unchanged; anything else keeps a readable prefix plus a short hash so two distinct
     * raw XUIDs cannot sanitize onto the same staged file.
     */
    public static @NotNull String key(@NotNull String xuid) {
        String cleaned = xuid.replaceAll("[^0-9A-Za-z_-]", "");
        if (cleaned.isBlank()) {
            return "unknown";
        }
        if (cleaned.equals(xuid)) {
            return cleaned;
        }
        return cleaned + "-" + shortHash(xuid);
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException impossible) {
            return "x";
        }
    }

    public static @NotNull Path directory(@NotNull Path dataFolder) {
        return dataFolder.resolve("bedrock-packs");
    }

    /**
     * Builds or refreshes the session archive and returns its path.
     *
     * @param dataFolder Kalo's plugin data directory
     * @param source     the generated global `.mcpack`
     * @param xuid       the Bedrock session XUID
     */
    public static @NotNull Path stage(@NotNull Path dataFolder, @NotNull Path source,
                                      @NotNull String xuid) throws IOException {
        return stage(dataFolder, source, xuid, Map.of(), SessionContentPolicy.all());
    }

    /**
     * Stages a session pack with per-player texture overlays (emote skins). Overlays are
     * part of the digest: changing a skin rebuilds that session even when the global pack
     * did not change.
     */
    public static @NotNull Path stage(@NotNull Path dataFolder, @NotNull Path source,
                                      @NotNull String xuid,
                                      @NotNull Map<String, byte[]> overlays)
            throws IOException {
        return stage(dataFolder, source, xuid, overlays, SessionContentPolicy.all());
    }

    /**
     * Stages a session pack filtered by the session's content policy. Content-owned files
     * whose key is not allowed are omitted; shared files stay. The policy fingerprint and
     * overlays are part of the digest, so changing either rebuilds only that session.
     */
    public static @NotNull Path stage(@NotNull Path dataFolder, @NotNull Path source,
                                      @NotNull String xuid,
                                      @NotNull Map<String, byte[]> overlays,
                                      @NotNull SessionContentPolicy policy)
            throws IOException {
        return stage(dataFolder, source, xuid, overlays, policy, node -> false);
    }

    /**
     * @param hasPermission the player's permission predicate; permission-restricted content
     *                      fails closed when no predicate can grant it
     */
    public static @NotNull Path stage(@NotNull Path dataFolder, @NotNull Path source,
                                      @NotNull String xuid,
                                      @NotNull Map<String, byte[]> overlays,
                                      @NotNull SessionContentPolicy policy,
                                      @NotNull Predicate<String> hasPermission)
            throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new IOException("Missing Bedrock source pack: " + source);
        }
        Path directory = directory(dataFolder);
        Files.createDirectories(directory);
        String key = key(xuid);
        Path target = directory.resolve(key + ".mcpack");
        Path digestFile = directory.resolve(key + ".sha256");
        String sourceDigest = digest(source);
        String combined = sessionDigest(sourceDigest, overlays,
                policy.fingerprint(xuid, hasPermission));
        if (Files.isRegularFile(target) && Files.isRegularFile(digestFile)) {
            try {
                if (combined.equals(Files.readString(digestFile).trim())) {
                    return target;
                }
            } catch (IOException unreadableDigest) {
                // A corrupt digest file must not block staging; fall through and rebuild.
            }
        }

        byte[] session = sessionPack(source, xuid, sourceDigest, overlays, policy,
                hasPermission);
        Path temporary = Files.createTempFile(directory, ".kalo-session-", ".tmp");
        try {
            Files.write(temporary, session);
            move(temporary, target);
            moveText(directory, digestFile, combined + "\n");
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }

    /**
     * The revision a session was served: the shared source archive plus that player's
     * overlays. Comparing only the source would miss a changed emote skin.
     */
    public static @NotNull String sessionDigest(@NotNull Path source,
                                                @NotNull Map<String, byte[]> overlays)
            throws IOException {
        return sessionDigest(source, overlays, "all");
    }

    public static @NotNull String sessionDigest(@NotNull Path source,
                                                @NotNull Map<String, byte[]> overlays,
                                                @NotNull String policyFingerprint)
            throws IOException {
        return sessionDigest(digest(source), overlays, policyFingerprint);
    }

    public static @NotNull String sessionDigest(@NotNull String sourceDigest,
                                                @NotNull Map<String, byte[]> overlays)
            throws IOException {
        return sessionDigest(sourceDigest, overlays, "all");
    }

    public static @NotNull String sessionDigest(@NotNull String sourceDigest,
                                                @NotNull Map<String, byte[]> overlays,
                                                @NotNull String policyFingerprint)
            throws IOException {
        String combined = sourceDigest + " " + overlayDigest(overlays);
        // Keep the pre-policy format for sessions that receive everything, so an
        // unfiltered upgrade does not invalidate every staged pack.
        return "all".equals(policyFingerprint) ? combined
                : combined + " " + policyFingerprint;
    }

    public static @NotNull String digest(@NotNull Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("JVM is missing SHA-256", impossible);
        }
    }

    /** Records which session digest a session was actually served at login. */
    public static void markServed(@NotNull String xuid, @NotNull String digest) {
        SERVED.put(key(xuid), digest);
    }

    public static @Nullable String servedDigest(@NotNull String xuid) {
        return SERVED.get(key(xuid));
    }

    public static void forget(@NotNull String xuid) {
        SERVED.remove(key(xuid));
    }

    /** A session file should be replaced and the viewer reconnected. */
    public static boolean isStale(@NotNull Path dataFolder, @NotNull Path source,
                                  @NotNull String xuid) throws IOException {
        return isStale(dataFolder, source, xuid, Map.of());
    }

    public static boolean isStale(@NotNull Path dataFolder, @NotNull Path source,
                                  @NotNull String xuid,
                                  @NotNull Map<String, byte[]> overlays)
            throws IOException {
        return isStale(dataFolder, source, xuid, overlays, SessionContentPolicy.all());
    }

    public static boolean isStale(@NotNull Path dataFolder, @NotNull Path source,
                                  @NotNull String xuid,
                                  @NotNull Map<String, byte[]> overlays,
                                  @NotNull SessionContentPolicy policy)
            throws IOException {
        return isStale(dataFolder, source, xuid, overlays, policy, node -> false);
    }

    public static boolean isStale(@NotNull Path dataFolder, @NotNull Path source,
                                  @NotNull String xuid,
                                  @NotNull Map<String, byte[]> overlays,
                                  @NotNull SessionContentPolicy policy,
                                  @NotNull Predicate<String> hasPermission)
            throws IOException {
        String served = servedDigest(xuid);
        return served == null || !served.equals(sessionDigest(
                source, overlays, policy.fingerprint(xuid, hasPermission)));
    }

    private static String overlayDigest(Map<String, byte[]> overlays) throws IOException {
        if (overlays.isEmpty()) {
            return "none";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, byte[]> entry : new TreeMap<>(overlays).entrySet()) {
                digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
                digest.update(entry.getValue());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("JVM is missing SHA-256", impossible);
        }
    }

    private static byte[] sessionPack(Path source, String xuid, String sourceDigest,
                                      Map<String, byte[]> overlays,
                                      SessionContentPolicy policy,
                                      Predicate<String> hasPermission) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        try (ZipFile zip = new ZipFile(source.toFile())) {
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    entries.put(entry.getName(), input.readAllBytes());
                }
            }
        }
        byte[] manifestBytes = entries.get(SessionContentManifest.PATH);
        Map<String, String> owners = SessionContentManifest.read(manifestBytes == null
                ? null : Writable.bytes(manifestBytes));

        Map<String, byte[]> filtered = new TreeMap<>();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (entry.getKey().equals(SessionContentManifest.PATH)) {
                continue;
            }
            String owner = owners.get(entry.getKey());
            if (owner != null && !policy.allows(xuid, owner, hasPermission)) {
                continue;
            }
            filtered.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, byte[]> overlay : overlays.entrySet()) {
            String owner = owners.get(overlay.getKey());
            if (owner != null) {
                if (!policy.allows(xuid, owner, hasPermission)) {
                    continue;
                }
            } else if (!entries.containsKey(overlay.getKey())) {
                // An overlay is a replacement for a compiled texture. Never invent a
                // path that the source pack does not contain.
                continue;
            }
            filtered.put(overlay.getKey(), overlay.getValue());
        }

        Map<String, String> keptOwners = new TreeMap<>();
        for (String path : filtered.keySet()) {
            String owner = owners.get(path);
            if (owner != null) {
                keptOwners.put(path, owner);
            }
        }
        filtered.put(SessionContentManifest.PATH,
                SessionContentManifest.json(keptOwners).toString()
                        .getBytes(StandardCharsets.UTF_8));

        String marker = "{\"xuid\":\"" + json(xuid) + "\",\"source_sha256\":\""
                + json(sourceDigest) + "\",\"content_policy\":\""
                + json(policy.fingerprint(xuid, hasPermission)) + "\"}";
        filtered.put(SESSION_MARKER, marker.getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : filtered.entrySet()) {
                byte[] bytes = entry.getValue();
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(0L);
                zipEntry.setSize(bytes.length);
                CRC32 crc = new CRC32();
                crc.update(bytes);
                zipEntry.setCrc(crc.getValue());
                zip.putNextEntry(zipEntry);
                zip.write(bytes);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void moveText(Path directory, Path target, String value) throws IOException {
        Path temporary = Files.createTempFile(directory, ".kalo-digest-", ".tmp");
        try {
            Files.writeString(temporary, value);
            move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

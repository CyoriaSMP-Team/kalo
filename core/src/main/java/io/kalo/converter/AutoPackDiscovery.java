package io.kalo.converter;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Read-only, bounded discovery of actual Java resource-pack archives inside
 * the server's plugin data directories. Does not download, install or rewrite
 * plugin assets, and does not crawl arbitrary server/user directories.
 */
public final class AutoPackDiscovery {
    private static final int MAX_VISITED_ARCHIVES = 200;
    private static final int MAX_ARCHIVE_ENTRIES = 40_000;
    private static final long MAX_ARCHIVE_BYTES = 256L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 512L * 1024 * 1024;
    private static final long MAX_SINGLE_ENTRY_BYTES = 48L * 1024 * 1024;
    private static final int MAX_CANDIDATES = 20;
    private static final int MAX_META_BYTES = 64 * 1024;
    private static final int MAX_WARNINGS = 12;

    private AutoPackDiscovery() {}

    public record Candidate(@NotNull Path path, int assetCount, long expandedBytes) {}
    public record Report(@NotNull List<Candidate> candidates, @NotNull List<String> warnings) {
        public Report {
            candidates = List.copyOf(candidates);
            warnings = List.copyOf(warnings);
        }

        /** No guessing among multiple packs, which may include old/stale variants. */
        public Candidate unique() {
            return candidates.size() == 1 ? candidates.getFirst() : null;
        }
    }

    public static @NotNull Report discover(@NotNull Path pluginDataDirectory) throws IOException {
        Path pluginRoot = pluginDataDirectory.toAbsolutePath().normalize().getParent();
        if (pluginRoot == null || !Files.isDirectory(pluginRoot, LinkOption.NOFOLLOW_LINKS)) {
            return new Report(List.of(), List.of("Plugin data directory unavailable"));
        }
        Path trustedRoot = pluginRoot.toRealPath();
        List<Candidate> found = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<Path> possible = new ArrayList<>();

        // Java's Files.walk does not follow symbolic links by default. Limit depth
        // and ZIP candidates so an unrelated plugin cannot force a huge scan.
        try (Stream<Path> stream = Files.walk(trustedRoot, 4)) {
            stream.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> looksLikePackName(p.getFileName().toString()))
                    .filter(p -> !excluded(p, trustedRoot, pluginDataDirectory.getFileName().toString()))
                    .limit(MAX_VISITED_ARCHIVES + 1L)
                    .forEach(possible::add);
        }
        possible.sort(Comparator.comparing(Path::toString));
        if (possible.size() > MAX_VISITED_ARCHIVES) {
            warnings.add("Found more than " + MAX_VISITED_ARCHIVES
                    + " possible archives; automatic selection disabled");
            return new Report(List.of(), warnings);
        }

        for (Path path : possible) {
            try {
                Candidate candidate = inspect(path, trustedRoot);
                if (candidate != null) {
                    found.add(candidate);
                    if (found.size() > MAX_CANDIDATES) {
                        warnings.add("Too many Java pack candidates; refine plugin pack output");
                        return new Report(List.of(), warnings);
                    }
                }
            } catch (IOException invalid) {
                warn(warnings, "Skipped " + trustedRoot.relativize(path) + ": " + invalid.getMessage());
            }
        }

        return new Report(found, warnings);
    }

    private static boolean looksLikePackName(String filename) {
        String name = filename.toLowerCase(Locale.ROOT);
        return name.endsWith(".zip")
                && (name.contains("pack") || name.contains("resource")
                    || name.contains("asset") || name.contains("texture")
                    || name.equals("generated.zip"));
    }

    private static boolean excluded(Path file, Path root, String ownDirectory) {
        Path relative = root.relativize(file);
        for (Path segment : relative) {
            String part = segment.toString().toLowerCase(Locale.ROOT);
            if (part.equals("backup") || part.equals("backups") || part.equals("cache")
                    || part.equals("logs") || part.equals(".git") || part.equals("temp")
                    || part.equals("tmp") || part.equals("archive") || part.equals("old")) {
                return true;
            }
        }
        return relative.getNameCount() == 2
                && relative.getName(0).toString().equalsIgnoreCase(ownDirectory)
                && (file.getFileName().toString().equalsIgnoreCase("generated.zip")
                    || file.getFileName().toString().equalsIgnoreCase("generated.mcpack"));
    }

    private static Candidate inspect(Path archive, Path root) throws IOException {
        if (Files.isSymbolicLink(archive) || !archive.toRealPath().startsWith(root)) {
            throw new IOException("Symbolic link or path outside plugins/");
        }
        long size = Files.size(archive);
        if (size < 50 || size > MAX_ARCHIVE_BYTES) {
            throw new IOException("ZIP size out of bounds");
        }
        boolean hasMetadata = false;
        int assets = 0;
        int entries = 0;
        long expanded = 0;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> contents = zip.entries();
            while (contents.hasMoreElements()) {
                ZipEntry entry = contents.nextElement();
                if (++entries > MAX_ARCHIVE_ENTRIES) {
                    throw new IOException("Too many ZIP entries");
                }
                String name = entry.getName();
                if (name.startsWith("/") || name.indexOf('\\') >= 0
                        || name.equals("..") || name.startsWith("../")
                        || name.contains("/../") || name.contains("/./")) {
                    throw new IOException("Unsafe ZIP path");
                }
                if (entry.isDirectory()) continue;
                long bytes = entry.getSize();
                if (bytes < 0 || bytes > MAX_SINGLE_ENTRY_BYTES
                        || expanded > MAX_EXPANDED_BYTES - bytes) {
                    throw new IOException("ZIP expansion limit exceeded");
                }
                expanded += bytes;
                if (name.equals("pack.mcmeta")) {
                    if (bytes > MAX_META_BYTES) {
                        throw new IOException("pack.mcmeta too large");
                    }
                    try (var input = zip.getInputStream(entry)) {
                        byte[] text = input.readNBytes(MAX_META_BYTES + 1);
                        if (text.length > MAX_META_BYTES) {
                            throw new IOException("pack.mcmeta too large");
                        }
                        JsonElement meta = JsonParser.parseString(
                                new String(text, StandardCharsets.UTF_8));
                        if (!meta.isJsonObject() || !meta.getAsJsonObject().has("pack")
                                || !meta.getAsJsonObject().get("pack").isJsonObject()) {
                            throw new IOException("Invalid pack.mcmeta");
                        }
                        hasMetadata = true;
                    } catch (com.google.gson.JsonParseException invalid) {
                        throw new IOException("Malformed pack.mcmeta", invalid);
                    }
                } else if (name.startsWith("assets/") && name.indexOf('/', 7) > 7) {
                    assets++;
                }
            }
        }
        return hasMetadata && assets > 0 ? new Candidate(archive, assets, expanded) : null;
    }

    private static void warn(List<String> warnings, String message) {
        if (warnings.size() < MAX_WARNINGS) warnings.add(message);
    }
}

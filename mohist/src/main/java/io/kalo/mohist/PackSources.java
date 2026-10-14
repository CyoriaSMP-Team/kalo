package io.kalo.mohist;

import com.google.gson.JsonParser;

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
 * Bounded read-only ZIP/Forge JAR asset detection. The presence of a mod's
 * Java texture does NOT imply Geyser can connect to a Forge-modded server.
 */
public final class PackSources {
    private static final int MAX_CANDIDATES = 160;
    private static final int MAX_ENTRIES = 30_000;
    private static final long MAX_ARCHIVE_BYTES = 256L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 512L * 1024 * 1024;
    private static final int MAX_METADATA = 64 * 1024;
    private PackSources() {}

    public enum Kind { RESOURCE_PACK, FORGE_MOD }
    public record Source(Path path, Kind kind, int models, int textures) {}
    public record Report(List<Source> packs, List<Source> mods, List<String> warnings) {
        public Report {
            packs = List.copyOf(packs);
            mods = List.copyOf(mods);
            warnings = List.copyOf(warnings);
        }
        public Source uniquePack() { return packs.size() == 1 ? packs.get(0) : null; }
    }

    public static Report discover(Path serverRoot) throws IOException {
        Path root = serverRoot.toRealPath();
        var files = new ArrayList<Path>();
        List<String> warnings = new ArrayList<>();
        Path plugins = root.resolve("plugins");
        if (Files.isDirectory(plugins, LinkOption.NOFOLLOW_LINKS)) {
            try (Stream<Path> stream = Files.walk(plugins, 4)) {
                stream.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                        .filter(p -> {
                            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return n.endsWith(".zip") && (n.contains("pack") || n.contains("resource")
                                    || n.contains("asset") || n.equals("generated.zip"));
                        })
                        .filter(p -> !excluded(plugins.relativize(p)))
                        .limit(MAX_CANDIDATES + 1L).forEach(files::add);
            }
        }
        Path mods = root.resolve("mods");
        if (Files.isDirectory(mods, LinkOption.NOFOLLOW_LINKS)) {
            try (Stream<Path> stream = Files.list(mods)) {
                stream.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                        .limit(MAX_CANDIDATES + 1L).forEach(files::add);
            }
        }
        files.sort(Comparator.comparing(Path::toString));
        if (files.size() > MAX_CANDIDATES) {
            return new Report(List.of(), List.of(),
                    List.of("Too many archives; no automatic selection"));
        }
        List<Source> packs = new ArrayList<>();
        List<Source> forge = new ArrayList<>();
        for (Path file : files) {
            try {
                Source source = inspect(file, root, file.startsWith(mods) ? Kind.FORGE_MOD : Kind.RESOURCE_PACK);
                if (source == null) continue;
                if (source.kind() == Kind.FORGE_MOD) forge.add(source);
                else packs.add(source);
            } catch (IOException bad) {
                if (warnings.size() < 16) warnings.add(root.relativize(file) + ": " + bad.getMessage());
            }
        }
        return new Report(packs, forge, warnings);
    }

    private static boolean excluded(Path path) {
        for (Path part : path) {
            String name = part.toString().toLowerCase(Locale.ROOT);
            if (List.of("backup","backups","cache","old","tmp","temp","archive",".git").contains(name))
                return true;
        }
        return path.getNameCount() == 2 && path.getName(0).toString().equalsIgnoreCase("Kalo-Mohist")
                && path.getFileName().toString().startsWith("generated");
    }

    static Source inspect(Path file, Path root, Kind kind) throws IOException {
        if (Files.isSymbolicLink(file) || !file.toRealPath().startsWith(root))
            throw new IOException("Symlink/escaped path");
        long compressed = Files.size(file);
        if (compressed < 80 || compressed > MAX_ARCHIVE_BYTES) throw new IOException("Archive size limit");
        int models = 0, textures = 0, entries = 0;
        boolean packMeta = false;
        long expanded = 0;
        try (ZipFile zip = new ZipFile(file.toFile())) {
            Enumeration<? extends ZipEntry> all = zip.entries();
            while (all.hasMoreElements()) {
                ZipEntry entry = all.nextElement();
                if (++entries > MAX_ENTRIES) throw new IOException("Archive entry limit");
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("\\") || name.contains("/../")
                        || name.startsWith("../") || name.equals("..") || name.contains("/./"))
                    throw new IOException("Unsafe ZIP path");
                long bytes = entry.getSize();
                if (bytes < 0 || bytes > 48L * 1024 * 1024 || expanded > MAX_EXPANDED_BYTES - bytes)
                    throw new IOException("ZIP expansion limit");
                expanded += bytes;
                if (entry.isDirectory()) continue;
                if ("pack.mcmeta".equals(name)) {
                    if (bytes > MAX_METADATA) throw new IOException("pack.mcmeta too large");
                    try (var input = zip.getInputStream(entry)) {
                        byte[] metadata = input.readNBytes(MAX_METADATA + 1);
                        if (metadata.length > MAX_METADATA
                                || !JsonParser.parseString(new String(metadata, StandardCharsets.UTF_8))
                                    .getAsJsonObject().has("pack")) {
                            throw new IOException("Invalid pack.mcmeta");
                        }
                        packMeta = true;
                    } catch (RuntimeException bad) {
                        throw new IOException("Malformed pack.mcmeta", bad);
                    }
                }
                if (name.startsWith("assets/") && name.contains("/models/") && name.endsWith(".json")) models++;
                if (name.startsWith("assets/") && name.contains("/textures/") && name.endsWith(".png")) textures++;
            }
        }
        if (kind == Kind.RESOURCE_PACK && !packMeta) return null;
        if (models == 0 && textures == 0) return null;
        return new Source(file, kind, models, textures);
    }
}

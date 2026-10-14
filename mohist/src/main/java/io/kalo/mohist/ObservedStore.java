package io.kalo.mohist;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/** Dedicated Mohist 1.20.1 data store. Does not touch native Kalo data files. */
public final class ObservedStore {
    private static final int MAX_OBSERVED = 512;
    private ObservedStore() {}

    public static synchronized List<LegacyBedrockExporter.Observed> load(Path file) throws IOException {
        if (!Files.exists(file)) return List.of();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Observations must be a regular file");
        Properties data = new Properties();
        try (var reader = Files.newBufferedReader(file)) {
            data.load(reader);
        }
        if (data.size() > MAX_OBSERVED) throw new IOException("Observation limit");
        List<LegacyBedrockExporter.Observed> results = new ArrayList<>();
        for (String key : data.stringPropertyNames()) {
            if (!"true".equals(data.getProperty(key))) throw new IOException("Invalid observation value: "+key);
            String[] parts = key.split(":", 2);
            if (parts.length != 2) throw new IOException("Invalid observation ID: "+key);
            try {
                results.add(new LegacyBedrockExporter.Observed(parts[0],Integer.parseInt(parts[1])));
            } catch (RuntimeException malformed) {
                throw new IOException("Bad observation ID",malformed);
            }
        }
        results.sort(java.util.Comparator.comparing(LegacyBedrockExporter.Observed::key)
                .thenComparingInt(LegacyBedrockExporter.Observed::customModelData));
        return List.copyOf(results);
    }

    public static synchronized boolean remember(Path file, LegacyBedrockExporter.Observed item)
            throws IOException {
        Set<String> rows = new LinkedHashSet<>();
        for (var existing : load(file)) rows.add(existing.material()+":"+existing.customModelData());
        if (!rows.add(item.material()+":"+item.customModelData())) return false;
        if (rows.size() > MAX_OBSERVED) throw new IOException("Observation limit");
        Files.createDirectories(file.getParent());
        Path staged = Files.createTempFile(file.getParent(), ".kalo-remember-", ".tmp");
        try {
            Properties props = new Properties();
            for (String row : rows) props.setProperty(row, "true");
            try (var writer = Files.newBufferedWriter(staged)) {
                props.store(writer, "Kalo Mohist observed legacy CustomModelData");
            }
            try {
                Files.move(staged,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(staged,file,StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } finally {
            Files.deleteIfExists(staged);
        }
    }
}

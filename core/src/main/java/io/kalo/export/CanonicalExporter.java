package io.kalo.export;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

/**
 * Exports Kalo content as canonical JSON/YAML format.
 * This is a portable format that can be imported by other tools.
 */
public class CanonicalExporter implements Exporter {
    
    @Override
    public String getName() {
        return "Canonical";
    }
    
    @Override
    public String getFileExtension() {
        return "json";
    }
    
    @Override
    public ExportReport export(Path source, Path target) throws Exception {
        ExportReport report = new ExportReport();
        
        if (!Files.exists(source)) {
            report.failed(source.toString(), "Source directory does not exist");
            return report;
        }
        
        // Create target directory
        Files.createDirectories(target);
        
        // Find all YAML/JSON config files
        try (Stream<Path> paths = Files.walk(source)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> {
                     String name = path.getFileName().toString();
                     return name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".json");
                 })
                 .forEach(path -> {
                     try {
                         Path relativePath = source.relativize(path);
                         Path targetPath = target.resolve(relativePath);
                         Files.createDirectories(targetPath.getParent());
                         Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                         report.exported(relativePath.toString());
                     } catch (IOException e) {
                         report.failed(path.toString(), e.getMessage());
                     }
                 });
        }
        
        return report;
    }
    
    @Override
    public boolean canExport(Path source) {
        return Files.exists(source) && Files.isDirectory(source);
    }
}

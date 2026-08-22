package io.kalo.export;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

/**
 * Exports Kalo content as a Kalo Pack bundle.
 * This is the primary export format for backup/share/versioning.
 */
public class KaloPackExporter implements Exporter {
    
    @Override
    public String getName() {
        return "Kalo Pack";
    }
    
    @Override
    public String getFileExtension() {
        return "zip";
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
        
        // Copy all files from source to target
        try (Stream<Path> paths = Files.walk(source)) {
            paths.filter(Files::isRegularFile)
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

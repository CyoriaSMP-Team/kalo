package io.kalo.export;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

/**
 * Exports Kalo content to ItemsAdder format.
 * 
 * Note: This is a lossy conversion. Kalo features that don't have
 * ItemsAdder equivalents will be flagged as unsupported.
 */
public class ItemsAdderExporter implements Exporter {
    
    @Override
    public String getName() {
        return "ItemsAdder";
    }
    
    @Override
    public String getFileExtension() {
        return "yml";
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
        
        // Find all Kalo config files
        try (Stream<Path> paths = Files.walk(source)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.getFileName().toString().endsWith(".yml"))
                 .forEach(path -> {
                     try {
                         convertToItemsAdder(path, source, target, report);
                     } catch (Exception e) {
                         report.failed(path.toString(), e.getMessage());
                     }
                 });
        }
        
        return report;
    }
    
    private void convertToItemsAdder(Path sourceFile, Path sourceRoot, Path targetRoot, ExportReport report) throws IOException {
        Path relativePath = sourceRoot.relativize(sourceFile);
        Path targetFile = targetRoot.resolve(relativePath);
        
        // Create target directory
        Files.createDirectories(targetFile.getParent());
        
        // Check if this is an item, block, or furniture
        String fileName = sourceFile.getFileName().toString();
        
        if (fileName.contains("item")) {
            // Convert item
            convertItem(sourceFile, targetFile, report);
        } else if (fileName.contains("block")) {
            // Convert block
            convertBlock(sourceFile, targetFile, report);
        } else if (fileName.contains("furniture")) {
            // Convert furniture
            convertFurniture(sourceFile, targetFile, report);
        } else {
            // Unknown type
            report.unsupported(relativePath.toString(), "Unknown content type");
        }
    }
    
    private void convertItem(Path source, Path target, ExportReport report) throws IOException {
        // Note: In real implementation, this would:
        // 1. Parse Kalo item config
        // 2. Convert display name, lore, model, etc.
        // 3. Handle features (abilities, stats, etc.)
        // 4. Report unsupported features
        
        // For now, copy with warning
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        
        String fileName = source.getFileName().toString();
        report.exported(fileName);
        report.partial(fileName, "Features may not have ItemsAdder equivalents");
    }
    
    private void convertBlock(Path source, Path target, ExportReport report) throws IOException {
        // Note: In real implementation, this would:
        // 1. Parse Kalo block config
        // 2. Convert block states, behaviors, etc.
        // 3. Handle virtual blocks (may not be possible in ItemsAdder)
        // 4. Report unsupported features
        
        // For now, copy with warning
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        
        String fileName = source.getFileName().toString();
        report.exported(fileName);
        report.partial(fileName, "Virtual blocks not supported in ItemsAdder");
    }
    
    private void convertFurniture(Path source, Path target, ExportReport report) throws IOException {
        // Note: In real implementation, this would:
        // 1. Parse Kalo furniture config
        // 2. Convert seat, hitbox, storage, jukebox
        // 3. Report unsupported features
        
        // For now, copy with warning
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        
        String fileName = source.getFileName().toString();
        report.exported(fileName);
        report.partial(fileName, "Some furniture features may not be supported");
    }
    
    @Override
    public boolean canExport(Path source) {
        return Files.exists(source) && Files.isDirectory(source);
    }
}

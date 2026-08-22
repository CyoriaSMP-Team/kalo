package io.kalo.export;

import java.nio.file.Path;

/**
 * Interface for exporting Kalo content to various formats.
 */
public interface Exporter {
    
    /**
     * Get the name of this exporter.
     */
    String getName();
    
    /**
     * Get the file extension for this format.
     */
    String getFileExtension();
    
    /**
     * Export Kalo content to the target format.
     * 
     * @param source The source Kalo pack directory
     * @param target The target directory for exported files
     * @return Export report with details about what was exported
     */
    ExportReport export(Path source, Path target) throws Exception;
    
    /**
     * Check if this exporter can handle the given content.
     */
    boolean canExport(Path source);
}

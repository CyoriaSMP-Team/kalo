package io.kalo.export;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages all exporters for Kalo content.
 */
public class ExportManager {
    
    private final Map<String, Exporter> exporters = new HashMap<>();
    
    public ExportManager() {
        // Register built-in exporters
        register(new KaloPackExporter());
        register(new CanonicalExporter());
        register(new OraxenExporter());
        register(new ItemsAdderExporter());
        register(new NexoExporter());
    }
    
    /**
     * Register an exporter.
     */
    public void register(Exporter exporter) {
        exporters.put(exporter.getName().toLowerCase(), exporter);
    }
    
    /**
     * Get an exporter by name.
     */
    public Exporter getExporter(String name) {
        return exporters.get(name.toLowerCase());
    }
    
    /**
     * Get all available exporter names.
     */
    public Set<String> getAvailableExporters() {
        return exporters.keySet();
    }
    
    /**
     * Export content using the specified exporter.
     */
    public ExportReport export(String format, Path source, Path target) throws Exception {
        Exporter exporter = getExporter(format);
        if (exporter == null) {
            throw new IllegalArgumentException("Unknown export format: " + format);
        }
        
        if (!exporter.canExport(source)) {
            throw new IllegalArgumentException("Cannot export from: " + source);
        }
        
        return exporter.export(source, target);
    }
    
    /**
     * Export content to Kalo Pack format.
     */
    public ExportReport exportToKaloPack(Path source, Path target) throws Exception {
        return export("kalo pack", source, target);
    }
    
    /**
     * Export content to canonical format.
     */
    public ExportReport exportToCanonical(Path source, Path target) throws Exception {
        return export("canonical", source, target);
    }
    
    /**
     * Export content to Oraxen format.
     */
    public ExportReport exportToOraxen(Path source, Path target) throws Exception {
        return export("oraxen", source, target);
    }
    
    /**
     * Export content to ItemsAdder format.
     */
    public ExportReport exportToItemsAdder(Path source, Path target) throws Exception {
        return export("itemsadder", source, target);
    }
    
    /**
     * Export content to Nexo format.
     */
    public ExportReport exportToNexo(Path source, Path target) throws Exception {
        return export("nexo", source, target);
    }
}

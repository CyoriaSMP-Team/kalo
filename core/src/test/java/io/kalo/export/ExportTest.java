package io.kalo.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the export system.
 */
class ExportTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    @DisplayName("ExportReport tracks exported items")
    void exportReportTracksExportedItems() {
        ExportReport report = new ExportReport();
        report.exported("item1.yml");
        report.exported("item2.yml");
        
        assertEquals(2, report.getExportedCount());
        assertEquals(100.0, report.getCompatibilityPercent());
    }
    
    @Test
    @DisplayName("ExportReport tracks partial items")
    void exportReportTracksPartialItems() {
        ExportReport report = new ExportReport();
        report.exported("item1.yml");
        report.partial("item2.yml", "Feature not supported");
        
        assertEquals(1, report.getExportedCount());
        assertEquals(1, report.getPartialCount());
        assertEquals(50.0, report.getCompatibilityPercent());
    }
    
    @Test
    @DisplayName("ExportReport tracks unsupported items")
    void exportReportTracksUnsupportedItems() {
        ExportReport report = new ExportReport();
        report.exported("item1.yml");
        report.unsupported("item2.yml", "Virtual blocks not supported");
        
        assertEquals(1, report.getExportedCount());
        assertEquals(1, report.getUnsupportedCount());
        assertEquals(50.0, report.getCompatibilityPercent());
    }
    
    @Test
    @DisplayName("ExportReport tracks failed items")
    void exportReportTracksFailedItems() {
        ExportReport report = new ExportReport();
        report.exported("item1.yml");
        report.failed("item2.yml", "File not found");
        
        assertEquals(1, report.getExportedCount());
        assertEquals(1, report.getFailedCount());
        assertEquals(50.0, report.getCompatibilityPercent());
    }
    
    @Test
    @DisplayName("ExportReport toString formats correctly")
    void exportReportToStringFormatsCorrectly() {
        ExportReport report = new ExportReport();
        report.exported("item1.yml");
        report.partial("item2.yml", "Feature not supported");
        report.unsupported("item3.yml", "Virtual blocks not supported");
        report.failed("item4.yml", "File not found");
        
        String output = report.toString();
        
        assertTrue(output.contains("Exported:    1"));
        assertTrue(output.contains("Partial:     1"));
        assertTrue(output.contains("Unsupported: 1"));
        assertTrue(output.contains("Failed:      1"));
        assertTrue(output.contains("Compatibility: 25.0%"));
    }
    
    @Test
    @DisplayName("ExportManager registers exporters")
    void exportManagerRegistersExporters() {
        ExportManager manager = new ExportManager();
        
        assertTrue(manager.getAvailableExporters().contains("kalo pack"));
        assertTrue(manager.getAvailableExporters().contains("canonical"));
        assertTrue(manager.getAvailableExporters().contains("oraxen"));
        assertTrue(manager.getAvailableExporters().contains("itemsadder"));
        assertTrue(manager.getAvailableExporters().contains("nexo"));
    }
    
    @Test
    @DisplayName("ExportManager gets exporter by name")
    void exportManagerGetsExporterByName() {
        ExportManager manager = new ExportManager();
        
        assertNotNull(manager.getExporter("kalo pack"));
        assertNotNull(manager.getExporter("canonical"));
        assertNotNull(manager.getExporter("oraxen"));
        assertNotNull(manager.getExporter("itemsadder"));
        assertNotNull(manager.getExporter("nexo"));
        assertNull(manager.getExporter("unknown"));
    }
    
    @Test
    @DisplayName("ExportManager has 5 built-in exporters")
    void exportManagerHasFiveBuiltInExporters() {
        ExportManager manager = new ExportManager();
        
        assertEquals(5, manager.getAvailableExporters().size());
    }
}

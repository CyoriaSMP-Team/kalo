package io.kalo.migration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LiveServerScanner.
 */
class LiveServerScannerTest {
    
    @Test
    void testScannerCreation() {
        LiveServerScanner scanner = LiveServerScanner.getInstance();
        assertNotNull(scanner, "Scanner should be created");
    }
    
    @Test
    void testScanResultCreation() {
        LiveServerScanner.ScanResult result = new LiveServerScanner.ScanResult();
        assertNotNull(result, "Scan result should be created");
        assertFalse(result.hasContent(), "New scan result should have no content");
    }
    
    @Test
    void testMigrationResultCreation() {
        LiveServerScanner.MigrationResult result = new LiveServerScanner.MigrationResult();
        assertNotNull(result, "Migration result should be created");
        assertEquals(0, result.getTotalConverted(), "New migration result should have 0 converted");
    }
    
    @Test
    void testPluginInfo() {
        java.io.File folder = new java.io.File("/tmp/test");
        LiveServerScanner.ScanResult.PluginInfo info = new LiveServerScanner.ScanResult.PluginInfo(folder, 10);
        
        assertEquals(folder, info.folder(), "Folder should match");
        assertEquals(10, info.contentCount(), "Content count should match");
    }
    
    @Test
    void testMigrationInfo() {
        LiveServerScanner.MigrationResult.MigrationInfo info = 
            new LiveServerScanner.MigrationResult.MigrationInfo(true, 5);
        
        assertTrue(info.success(), "Migration should be successful");
        assertEquals(5, info.converted(), "Converted count should match");
    }
    
    @Test
    void testScanResultToString() {
        LiveServerScanner.ScanResult result = new LiveServerScanner.ScanResult();
        String str = result.toString();
        
        assertNotNull(str, "ToString should not be null");
        assertTrue(str.contains("Live Server Scan Result"), "Should contain header");
    }
    
    @Test
    void testMigrationResultToString() {
        LiveServerScanner.MigrationResult result = new LiveServerScanner.MigrationResult();
        result.addMigrated("TestPlugin", true, 10);
        
        String str = result.toString();
        
        assertNotNull(str, "ToString should not be null");
        assertTrue(str.contains("Migration Result"), "Should contain header");
        assertTrue(str.contains("TestPlugin"), "Should contain plugin name");
        assertTrue(str.contains("10"), "Should contain converted count");
    }
}

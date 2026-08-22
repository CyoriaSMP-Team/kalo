package io.kalo.export;

import java.util.ArrayList;
import java.util.List;

/**
 * Report for export operations.
 * Shows what was exported, what was partial, and what was unsupported.
 */
public class ExportReport {
    private final List<String> exported = new ArrayList<>();
    private final List<String> partial = new ArrayList<>();
    private final List<String> unsupported = new ArrayList<>();
    private final List<String> failed = new ArrayList<>();
    
    public void exported(String item) {
        exported.add(item);
    }
    
    public void partial(String item, String reason) {
        partial.add(item + " — " + reason);
    }
    
    public void unsupported(String item, String reason) {
        unsupported.add(item + " — " + reason);
    }
    
    public void failed(String item, String reason) {
        failed.add(item + " — " + reason);
    }
    
    public int getExportedCount() { return exported.size(); }
    public int getPartialCount() { return partial.size(); }
    public int getUnsupportedCount() { return unsupported.size(); }
    public int getFailedCount() { return failed.size(); }
    
    public double getCompatibilityPercent() {
        int total = exported.size() + partial.size() + unsupported.size() + failed.size();
        if (total == 0) return 100.0;
        return (double) exported.size() / total * 100;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Export Report\n");
        sb.append("═══════════════════════════════════════\n");
        sb.append(String.format("Exported:    %d\n", exported.size()));
        sb.append(String.format("Partial:     %d\n", partial.size()));
        sb.append(String.format("Unsupported: %d\n", unsupported.size()));
        sb.append(String.format("Failed:      %d\n", failed.size()));
        sb.append(String.format("Compatibility: %.1f%%\n", getCompatibilityPercent()));
        sb.append("═══════════════════════════════════════\n");
        
        if (!partial.isEmpty()) {
            sb.append("\n⚠️  Partial conversions:\n");
            partial.forEach(p -> sb.append("  • ").append(p).append("\n"));
        }
        
        if (!unsupported.isEmpty()) {
            sb.append("\n❌ Unsupported features:\n");
            unsupported.forEach(u -> sb.append("  • ").append(u).append("\n"));
        }
        
        if (!failed.isEmpty()) {
            sb.append("\n💥 Failed conversions:\n");
            failed.forEach(f -> sb.append("  • ").append(f).append("\n"));
        }
        
        return sb.toString();
    }
}

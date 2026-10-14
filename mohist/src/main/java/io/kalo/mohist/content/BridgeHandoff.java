package io.kalo.mohist.content;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * File-based contract for opt-in downstream bridges. The plugin does not
 * change the currently running ViaProxy, Geyser or any other bridge.
 */
public final class BridgeHandoff {
    private BridgeHandoff() {}

    public static JsonObject manifest(ContentIndex.Result inventory, boolean legacyExportPresent) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", "kalo.bridge-handoff/1");
        root.addProperty("producer", "kalo-mohist");
        root.addProperty("minecraft", "1.20.1");
        root.addProperty("index", "kalo-content-index.json");
        root.addProperty("asset_conversion", "partial");
        root.addProperty("protocol_support", false);
        root.addProperty("automatic_deployment", false);
        root.addProperty("requires_review", true);
        JsonArray capabilities = new JsonArray();
        for(String c:List.of("asset-index", "forge-registry-observation",
                "legacy-custom-model-data", "provider-extensions")) capabilities.add(c);
        root.add("capabilities",capabilities);
        root.addProperty("indexed_assets",inventory.assets().size());
        root.addProperty("runtime_registry_entries",inventory.registryLinks().size());
        root.addProperty("index_complete",inventory.complete());
        if (legacyExportPresent) {
            JsonObject legacy=new JsonObject();
            legacy.addProperty("resource_pack","kalo-mohist-generated.mcpack");
            legacy.addProperty("geyser_legacy_mappings","kalo-mohist-items.json");
            legacy.addProperty("verified_on_client",false);
            root.add("legacy_2d",legacy);
        }
        return root;
    }

    public static void write(Path path, ContentIndex.Result index, boolean legacyPresent) throws IOException {
        if (!index.complete()) throw new IOException("Incomplete index cannot be published");
        Files.createDirectories(path.getParent());
        if (Files.isSymbolicLink(path)) throw new IOException("Refusing symlink path");
        Path tmp=Files.createTempFile(path.getParent(),".kalo-handoff-",".tmp");
        try {
            Files.writeString(tmp,new GsonBuilder().setPrettyPrinting().create()
                    .toJson(manifest(index,legacyPresent)), StandardCharsets.UTF_8);
            try {
                Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}

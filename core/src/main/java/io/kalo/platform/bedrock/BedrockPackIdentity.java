package io.kalo.platform.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.pack.Json;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/** Version Bedrock packs by actual contents so clients never reuse an empty cached pack. */
public final class BedrockPackIdentity {
    private BedrockPackIdentity() {}

    public static void refreshVersion(@NotNull ResourcePack pack) throws IOException {
        Writable manifest = pack.file("manifest.json");
        if (manifest == null) throw new IOException("Missing Bedrock manifest.json");
        JsonObject root;
        try {
            root = JsonParser.parseString(new String(manifest.toByteArray(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (RuntimeException bad) {
            throw new IOException("Invalid generated Bedrock manifest", bad);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, Writable> file : pack.files().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                if (file.getKey().equals("manifest.json")) continue;
                digest.update(file.getKey().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                try (OutputStream output = new DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
                    file.getValue().write(output);
                }
                digest.update((byte) 0);
            }
            byte[] hash = digest.digest();
            int versionPatch = (((hash[0] & 127) << 24) | ((hash[1] & 255) << 16)
                    | ((hash[2] & 255) << 8) | (hash[3] & 255));
            JsonArray version = new JsonArray();
            version.add(1);
            version.add(0);
            version.add(versionPatch);
            root.getAsJsonObject("header").add("version", version);
            for (var module : root.getAsJsonArray("modules")) {
                module.getAsJsonObject().add("version", version.deepCopy());
            }
            pack.file("manifest.json", Json.writable(root));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("JVM is missing SHA-256", impossible);
        }
    }
}

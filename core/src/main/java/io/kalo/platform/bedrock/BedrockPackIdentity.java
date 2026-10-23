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
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * Give each distinct Bedrock pack a distinct identity, rather than encoding a
 * 31-bit SHA-256 fragment in a manifest version number.
 *
 * Bedrock can reject oversized version parts. Furthermore, an arbitrary hash
 * converted to a version may go *backwards*, making an updated pack appear
 * older to clients caching a previous release under the same UUID.
 * The immutable content-derived UUID prevents that cache collision and the
 * conservative version numbers remain valid for normal Bedrock manifests.
 */
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
            // Different content must *not* reuse the same Bedrock cache key. Use
            // domain-separated UUIDs for the header and resource module so they
            // are deterministic across server restarts without sharing identities.
            JsonObject header = root.getAsJsonObject("header");
            JsonArray modules = root.getAsJsonArray("modules");
            if (header == null || modules == null || modules.isEmpty()) {
                throw new IOException("Generated Bedrock pack is missing header/modules");
            }
            // A fixed, small version is sufficient because changes rotate the
            // content-derived UUID. 1.1.0 also avoids the old 1.0.<hash> scheme.
            JsonArray version = new JsonArray();
            version.add(1);
            version.add(1);
            version.add(0);
            header.add("version", version);
            header.addProperty("uuid", contentUuid("kalo:header", hash));
            for (var module : modules) {
                JsonObject definition = module.getAsJsonObject();
                if (!"resources".equals(definition.get("type").getAsString())) {
                    throw new IOException("Unsupported Kalo Bedrock module type");
                }
                definition.add("version", version.deepCopy());
                definition.addProperty("uuid", contentUuid("kalo:module", hash));
            }
            pack.file("manifest.json", Json.writable(root));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("JVM is missing SHA-256", impossible);
        }
    }

    private static String contentUuid(String scope, byte[] contentHash) {
        byte[] scopeBytes = scope.getBytes(StandardCharsets.UTF_8);
        byte[] identity = Arrays.copyOf(scopeBytes, scopeBytes.length + contentHash.length);
        System.arraycopy(contentHash, 0, identity, scopeBytes.length, contentHash.length);
        return UUID.nameUUIDFromBytes(identity).toString();
    }
}

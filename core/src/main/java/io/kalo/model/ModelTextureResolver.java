package io.kalo.model;

import io.kalo.model.ModelIR.Diagnostic;
import io.kalo.model.ModelIR.Severity;
import io.kalo.model.ModelIR.Texture;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves Blockbench texture references to namespaced Kalo texture keys.
 *
 * <p>Resolution order is an explicit override from the item/block definition, then an
 * embedded PNG, then a pack-relative texture path. Missing or ambiguous references are
 * diagnostics rather than an invented key: a model drawing the wrong texture is harder to
 * diagnose than one that refuses to compile.</p>
 */
public final class ModelTextureResolver {

    private ModelTextureResolver() {
    }

    /**
     * @param model            imported model
     * @param overrides        author overrides keyed by texture id, name or path
     * @param fallbackNamespace namespace for embedded/path-derived textures
     * @param assetStem        deterministic file stem for embedded textures
     */
    public static @NotNull Result resolve(@NotNull ModelIR model,
                                          @NotNull Map<String, Key> overrides,
                                          @NotNull String fallbackNamespace,
                                          @NotNull String assetStem) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<String, ResolvedTexture> byId = new LinkedHashMap<>();
        Map<Key, String> materialByKey = new LinkedHashMap<>();
        int index = 0;

        for (Texture texture : model.textures()) {
            if (texture.uvWidth() != model.textureWidth()
                    || texture.uvHeight() != model.textureHeight()) {
                diagnostics.add(new Diagnostic(Severity.UNSUPPORTED,
                        "texture.uv-space-mismatch",
                        "Texture '" + texture.name() + "' uses a "
                                + texture.uvWidth() + "x" + texture.uvHeight()
                                + " UV space; the model uses " + model.textureWidth() + "x"
                                + model.textureHeight()
                                + ". Static Java/Bedrock models have one shared UV space."));
                index++;
                continue;
            }

            Key key = override(texture, overrides);
            byte[] embedded = null;
            if (key == null && texture.embeddedPng() != null) {
                embedded = texture.embeddedPng();
                key = Key.key(fallbackNamespace, "item/" + safe(assetStem) + "_"
                        + safe(texture.name()) + "_" + index);
            }
            if (key == null && texture.relativePath() != null) {
                key = keyFromPath(texture.relativePath(), fallbackNamespace);
            }
            if (key == null) {
                diagnostics.add(new Diagnostic(Severity.UNSUPPORTED,
                        "texture.unresolved",
                        "Texture '" + texture.name() + "' has no Kalo override, embedded"
                                + " PNG or safe pack-relative path"));
                index++;
                continue;
            }

            String material = materialByKey.get(key);
            if (material == null) {
                material = "t" + materialByKey.size();
                materialByKey.put(key, material);
            }
            byId.put(texture.id(), new ResolvedTexture(texture.id(), material, key,
                    embedded, texture.relativePath(), texture.width(), texture.height()));
            index++;
        }

        boolean complete = byId.size() == model.textures().size();
        if (!complete && model.textures().isEmpty()) {
            diagnostics.add(new Diagnostic(Severity.UNSUPPORTED, "texture.none",
                    "Model declares no textures; a static Java/Bedrock model needs at"
                            + " least one material"));
        }
        return new Result(byId, materialByKey, List.copyOf(diagnostics), complete);
    }

    private static @Nullable Key override(Texture texture, Map<String, Key> overrides) {
        if (overrides.isEmpty()) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(texture.id());
        candidates.add(texture.name());
        if (texture.uuid() != null) {
            candidates.add(texture.uuid());
        }
        if (texture.name().contains(".")) {
            candidates.add(texture.name().substring(0, texture.name().lastIndexOf('.')));
        }
        if (texture.relativePath() != null) {
            String path = texture.relativePath();
            candidates.add(path);
            if (path.startsWith("textures/")) {
                candidates.add(path.substring("textures/".length()));
            }
            String withoutExtension = stripExtension(path);
            candidates.add(withoutExtension);
            candidates.add(stripExtension(path.startsWith("textures/")
                    ? path.substring("textures/".length()) : path));
            int slash = withoutExtension.lastIndexOf('/');
            if (slash >= 0) {
                candidates.add(withoutExtension.substring(slash + 1));
            }
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            Key direct = overrides.get(candidate);
            if (direct != null) {
                return direct;
            }
            for (Map.Entry<String, Key> entry : overrides.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(candidate)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /** Converts a pack-relative texture path to {@code namespace:textures-relative-key}. */
    static @Nullable Key keyFromPath(String path, String namespace) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("textures/")) {
            normalized = normalized.substring("textures/".length());
        }
        normalized = stripExtension(normalized);
        if (normalized.isBlank() || normalized.startsWith("../")
                || normalized.contains("/../") || normalized.startsWith("/")) {
            return null;
        }
        // Only paths rooted at a known texture directory are safe to derive. A bare file
        // name is ambiguous between item/ and block/, so the author must override it.
        if (!(normalized.startsWith("item/") || normalized.startsWith("block/")
                || normalized.startsWith("entity/"))) {
            return null;
        }
        try {
            return Key.key(namespace, normalized);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String stripExtension(String value) {
        return value.endsWith(".png") || value.endsWith(".PNG")
                ? value.substring(0, value.length() - 4) : value;
    }

    private static String safe(String value) {
        String lower = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        return lower.replace('/', '_').replace('.', '_');
    }

    public record ResolvedTexture(
            @NotNull String textureId,
            @NotNull String material,
            @NotNull Key key,
            byte @Nullable [] embeddedPng,
            @Nullable String sourcePath,
            int width,
            int height
    ) {
        public ResolvedTexture {
            embeddedPng = embeddedPng == null ? null : embeddedPng.clone();
        }

        @Override
        public byte @Nullable [] embeddedPng() {
            return embeddedPng == null ? null : embeddedPng.clone();
        }
    }

    /**
     * @param textures       resolved textures by Blockbench texture id
     * @param materials      material name to texture key, in deterministic first-use order
     * @param diagnostics    unsupported/missing texture diagnostics
     * @param complete       whether every declared texture resolved and shared a UV space
     */
    public record Result(
            @NotNull Map<String, ResolvedTexture> textures,
            @NotNull Map<Key, String> materials,
            @NotNull List<Diagnostic> diagnostics,
            boolean complete
    ) {
        public Result {
            textures = Collections.unmodifiableMap(new LinkedHashMap<>(textures));
            materials = Collections.unmodifiableMap(new LinkedHashMap<>(materials));
            diagnostics = List.copyOf(diagnostics);
        }

        public @Nullable ResolvedTexture texture(@Nullable String id) {
            return id == null ? null : textures.get(id);
        }

        public @Nullable ResolvedTexture first() {
            return textures.values().stream().findFirst().orElse(null);
        }
    }
}

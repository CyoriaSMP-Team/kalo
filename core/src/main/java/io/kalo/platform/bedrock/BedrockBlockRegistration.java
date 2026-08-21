package io.kalo.platform.bedrock;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * One block definition that the Bedrock compiler actually emitted successfully.
 *
 * <p>The native Geyser bridge consumes this exact result instead of independently
 * guessing from the live registry. That prevents it from advertising a palette entry
 * whose custom geometry or texture failed compilation.</p>
 */
public record BedrockBlockRegistration(
        @NotNull String javaKey,
        @NotNull String bedrockIdentifier,
        @Nullable String javaIdentifier,
        @NotNull String geometry,
        @Nullable String displayName,
        float hardness,
        @NotNull Map<String, String> materialInstances
) {
    /**
     * Geyser's namespace for content it registers on a server's behalf.
     *
     * <p>Not a preference. A block defined through a {@code custom_mappings} file gets this
     * namespace and no other — the file format only lets you name the block, not namespace
     * it — so the in-process API path uses it too. Anything else means the identifier
     * depends on which path registered the block, while the generated pack keys its
     * {@code blocks.json} by exactly one of them.</p>
     */
    public static final String NAMESPACE = "geyser_custom";

    public BedrockBlockRegistration {
        materialInstances = Map.copyOf(materialInstances);
    }

    /** The bare name Geyser appends to {@link #NAMESPACE}. */
    public @NotNull String bedrockName() {
        int colon = bedrockIdentifier.indexOf(':');
        return colon < 0 ? bedrockIdentifier : bedrockIdentifier.substring(colon + 1);
    }

    /** Builds the identifier the way both registration paths must agree on. */
    public static @NotNull String identifierFor(@NotNull String name) {
        return NAMESPACE + ":" + name;
    }
}

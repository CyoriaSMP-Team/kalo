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
    public BedrockBlockRegistration {
        materialInstances = Map.copyOf(materialInstances);
    }
}

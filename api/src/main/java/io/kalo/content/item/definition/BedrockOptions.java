package io.kalo.content.item.definition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bedrock-platform escape hatch, read only by the Bedrock compiler.
 *
 * <p>Nothing consumes this yet — the Bedrock compiler lands in Phase 2. It exists now so
 * that the definition layer is shaped for two platforms from the start rather than being
 * retrofitted for a second one later, and so {@code bedrock:} keys in pack YAML are
 * accepted and validated rather than silently dropped.</p>
 *
 * <p>{@code enabled} defaults to {@code true}: Bedrock support is not an add-on and
 * should not require opting in.</p>
 *
 * @param iconOverride Bedrock texture shorthand to use instead of the derived one
 */
public record BedrockOptions(boolean enabled, @Nullable String iconOverride) {

    public static @NotNull BedrockOptions defaults() {
        return new BedrockOptions(true, null);
    }
}

package io.kalo.platform.bedrock;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One item definition that the Bedrock compiler actually emitted successfully.
 *
 * <p>The sibling of {@link BedrockBlockRegistration}, and it exists for the same reason:
 * the in-process Geyser bridge used to re-derive all of this from the live registries,
 * which meant the decision was made twice — once for the mapping file and once for the
 * API — and the two could drift. The compiler decides; both paths consume.</p>
 *
 * @param javaItem          the vanilla item this rides on, e.g. {@code minecraft:paper}
 * @param bedrockIdentifier the identifier Bedrock knows it by
 * @param model             the Java {@code item_model} Geyser matches against, which is
 *                          what lets many custom items share one vanilla item
 * @param icon              the flat atlas key its inventory icon resolves through
 */
public record BedrockItemRegistration(
        @NotNull String javaItem,
        @NotNull String bedrockIdentifier,
        @NotNull String model,
        @NotNull String icon,
        @Nullable String displayName,
        int maxStackSize,
        @Nullable Integer maxDamage,
        boolean enchantmentGlint
) {
}

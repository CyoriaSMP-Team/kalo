package io.kalo.content.vfx.definition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Condition context with built-in values winning over an external placeholder resolver.
 *
 * <p>This is what keeps PlaceholderAPI from shadowing a built-in such as {@code health};
 * only unknown names are handed to the external resolver.</p>
 */
public final class VfxConditionContext implements VfxCondition.Context {

    private final Map<String, String> builtIns;
    private final Function<String, @Nullable String> external;
    private final Predicate<String> permission;

    private VfxConditionContext(@NotNull Map<String, String> builtIns,
                                @NotNull Function<String, @Nullable String> external,
                                @NotNull Predicate<String> permission) {
        this.builtIns = Map.copyOf(builtIns);
        this.external = external;
        this.permission = permission;
    }

    public static @NotNull VfxCondition.Context create(
            @NotNull Map<String, String> builtIns,
            @NotNull Function<String, @Nullable String> external,
            @NotNull Predicate<String> permission) {
        return new VfxConditionContext(builtIns, external, permission);
    }

    @Override
    public @Nullable String value(@NotNull String name) {
        if (builtIns.containsKey(name)) {
            return builtIns.get(name);
        }
        String resolved = external.apply(name);
        return resolved == null ? "" : resolved;
    }

    @Override
    public boolean permission(@NotNull String node) {
        return permission.test(node);
    }
}

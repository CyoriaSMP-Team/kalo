package io.kalo.registry;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

public final class DirectScalableRegistry<T> extends ScalableRegistry<T> implements DirectWritableRegistry<T> {

    @Override
    public synchronized @NotNull T register(@NotNull Key key, @NotNull T value) {
        checkWritable();
        if (map.putIfAbsent(key.asString(), value) != null) {
            throw new IllegalStateException("The registry already contains '" + key.asString() + "'");
        }
        markDirty();
        return value;
    }
}

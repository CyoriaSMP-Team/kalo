package io.kalo.platform.java;

import io.kalo.content.block.Block;
import io.kalo.content.block.BlockWorld;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** {@link BlockWorld} backed by the live block listener, which is replaced when Kalo restarts. */
public final class BlockWorldImpl implements BlockWorld {
    private final Supplier<JavaBlockListener> listener;

    public BlockWorldImpl(@NotNull Supplier<JavaBlockListener> listener) {
        this.listener = listener;
    }

    private @Nullable JavaBlockListener current() {
        return listener.get();
    }

    @Override
    public @Nullable Block blockAt(@NotNull org.bukkit.block.Block worldBlock) {
        JavaBlockListener l = current();
        return l == null ? null : l.resolve(worldBlock);
    }

    @Override
    public boolean place(@NotNull Block content, @NotNull org.bukkit.block.Block target) {
        JavaBlockListener l = current();
        return l != null && l.placeContent(content, target);
    }

    @Override
    public boolean remove(@NotNull org.bukkit.block.Block target, boolean dropItem) {
        JavaBlockListener l = current();
        return l != null && l.removeContent(target, dropItem);
    }

    @Override
    public @Nullable BlockData blockData(@NotNull Block content) {
        JavaBlockListener l = current();
        return l == null ? null : l.nativeBlockData(content);
    }
}

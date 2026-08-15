package io.kalo.manager;

import java.util.concurrent.CompletableFuture;

public interface ResourcePackManager {
    CompletableFuture<Void> generateResourcePack();
}

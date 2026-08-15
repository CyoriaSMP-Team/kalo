package io.kalo.manager;

import io.kalo.KaloPluginImpl;
import org.jetbrains.annotations.NotNull;

public record Context(
        @NotNull KaloPluginImpl plugin
) {
}

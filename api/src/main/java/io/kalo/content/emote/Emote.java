package io.kalo.content.emote;

import io.kalo.content.Content;
import org.jetbrains.annotations.NotNull;

/** A registered emote prop. */
public interface Emote extends Content {
    @NotNull EmoteDefinition definition();

    @NotNull String translationKey();
}

package io.kalo.content.musicdisc;

import io.kalo.content.Content;
import io.kalo.content.musicdisc.definition.MusicDiscDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * A registered custom music disc.
 */
public interface MusicDisc extends Content {
    @NotNull MusicDiscDefinition musicDiscDefinition();
}

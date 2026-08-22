package io.kalo.content.musicdisc;

import io.kalo.content.AbstractContent;
import io.kalo.content.feature.FeatureBuilder;
import io.kalo.content.musicdisc.definition.MusicDiscDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class MusicDiscImpl extends AbstractContent implements MusicDisc {
    private final MusicDiscDefinition musicDiscDefinition;
    private final List<FeatureBuilder> featureBuilders;

    public MusicDiscImpl(@NotNull MusicDiscDefinition musicDiscDefinition, @NotNull List<FeatureBuilder> featureBuilders) {
        super(musicDiscDefinition.key(), featureBuilders);
        this.musicDiscDefinition = musicDiscDefinition;
        this.featureBuilders = featureBuilders;
    }

    @Override
    public @NotNull MusicDiscDefinition musicDiscDefinition() {
        return musicDiscDefinition;
    }
}

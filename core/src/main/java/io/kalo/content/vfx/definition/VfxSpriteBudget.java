package io.kalo.content.vfx.definition;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure helper for the server-wide VFX sprite budget. */
public final class VfxSpriteBudget {

    public record Usage(long sequence, int sprites) {
    }

    private VfxSpriteBudget() {
    }

    /**
     * @return sequences of the oldest casts to stop until the planned sprite count fits
     */
    public static @NotNull List<Long> stopOldest(@NotNull List<Usage> usages, int maximum) {
        if (maximum <= 0) {
            return List.of();
        }
        List<Usage> ordered = new ArrayList<>(usages);
        ordered.sort(Comparator.comparingLong(Usage::sequence));
        long total = ordered.stream().mapToLong(Usage::sprites).sum();
        List<Long> stop = new ArrayList<>();
        for (Usage usage : ordered) {
            if (total <= maximum) {
                break;
            }
            stop.add(usage.sequence());
            total -= usage.sprites();
        }
        return List.copyOf(stop);
    }
}

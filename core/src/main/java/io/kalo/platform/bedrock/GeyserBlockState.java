package io.kalo.platform.bedrock;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Names the Java note-block state behind a Kalo block in the form Geyser's override API
 * expects.
 *
 * <p>The integer persisted by Kalo is an index into the stable instrument/note/powered
 * product used by the Java block compiler. Geyser does not accept that internal index;
 * it needs the complete Java state identifier. Keeping the conversion here also lets
 * both the native bridge and the standalone mapping writer use exactly the same form.</p>
 */
public final class GeyserBlockState {

    /**
     * Must stay in lockstep with the append-only carrier order in JavaBlockCompiler.
     * Existing worlds persist the resulting indexes, so reordering either list would
     * change the identity of every placed custom block.
     */
    private static final List<String> INSTRUMENTS = List.of(
            "harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar",
            "chime", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit",
            "banjo", "pling"
    );
    private static final int NOTE_COUNT = 25;
    private static final int POWERED_COUNT = 2;
    private static final int STATE_COUNT = INSTRUMENTS.size() * NOTE_COUNT * POWERED_COUNT;

    private GeyserBlockState() {
    }

    /**
     * @param index Kalo's persisted carrier index; zero is reserved for vanilla
     * @return e.g. {@code minecraft:note_block[instrument=harp,note=0,powered=true]}
     * @throws IllegalArgumentException when the index is reserved or outside the carrier
     */
    public static @NotNull String javaIdentifier(int index) {
        if (index <= 0 || index >= STATE_COUNT) {
            throw new IllegalArgumentException(
                    "note-block carrier index must be within 1.." + (STATE_COUNT - 1) + ", got " + index);
        }

        boolean powered = index % POWERED_COUNT == 1;
        int rest = index / POWERED_COUNT;
        int note = rest % NOTE_COUNT;
        int instrument = rest / NOTE_COUNT;

        return "minecraft:note_block[instrument=" + INSTRUMENTS.get(instrument)
                + ",note=" + note + ",powered=" + powered + "]";
    }
}

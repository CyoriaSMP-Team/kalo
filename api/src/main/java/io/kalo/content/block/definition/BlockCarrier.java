package io.kalo.content.block.definition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A vanilla block whose spare block states a custom block borrows for its appearance.
 *
 * <p>Java cannot add a block a vanilla client will render, so a custom block is a vanilla
 * block placed in a state the resource pack tells the client to draw differently. Which
 * vanilla block is borrowed decides how many custom blocks fit, what shape they can be,
 * and what vanilla behaviour has to be suppressed.</p>
 *
 * <p><b>This layer survives even if Kalo later registers real blocks server-side.</b> A
 * vanilla client can only render states it already knows, so a real block still needs a
 * visual state to be shown as — the same conclusion CraftEngine reached, which maps every
 * custom block to a "visual block state" of exactly this kind. Registering real blocks
 * changes what the <em>server</em> believes; it does not change what the client can
 * draw.</p>
 *
 * <p>Each carrier owns its own state maths. Nothing outside this enum should know that a
 * note block has 25 notes, or that tripwire has four directions — that knowledge leaking
 * outward is what would force a rewrite when the next carrier is added.</p>
 */
public enum BlockCarrier {

    /**
     * Note blocks: {@code instrument} × {@code note} × {@code powered}, every combination
     * drawn identically by vanilla.
     *
     * <p>The workhorse for solid full-cube blocks. Costs: the server recomputes
     * {@code instrument} from the block beneath on any neighbour update, so physics has to
     * be suppressed, and real note blocks lose their instrument variety.</p>
     */
    NOTE_BLOCK("minecraft:note_block", List.of(
            new Property("instrument", INSTRUMENTS()),
            new Property("note", numbers(25)),
            new Property("powered", List.of("false", "true")))),

    /**
     * Tripwire: seven booleans, and vanilla draws the string identically for all of them
     * when {@code attached} is false.
     *
     * <p>Non-solid, so it suits flat or decorative blocks rather than full cubes. Included
     * because it is the second carrier every implementation reaches for, and because
     * having two proves the abstraction holds.</p>
     */
    TRIPWIRE("minecraft:tripwire", List.of(
            new Property("attached", List.of("false")),
            new Property("disarmed", List.of("false", "true")),
            new Property("east", List.of("false", "true")),
            new Property("north", List.of("false", "true")),
            new Property("powered", List.of("false", "true")),
            new Property("south", List.of("false", "true")),
            new Property("west", List.of("false", "true")))),

     /**
      * Scaffolding: {@code distance} 0-7 plus {@code bottom} and {@code waterlogged}.
      *
      * <p>Vertical, gravity-affected and non-solid when not at the base, so it suits
      * tall decorative or temporary-structure blocks. Added as the third carrier
      * to lift the native cap from 862 to 893 without requiring virtual mode.</p>
      */
     SCAFFOLDING("minecraft:scaffolding", List.of(
             new Property("bottom", List.of("false", "true")),
             new Property("distance", List.of("0", "1", "2", "3", "4", "5", "6", "7")),
             new Property("waterlogged", List.of("false", "true"))));

    /** One block state property and the values this carrier may use for it. */
    public record Property(@NotNull String name, @NotNull @Unmodifiable List<String> values) {
        public Property {
            values = List.copyOf(values);
        }
    }

    private final String vanillaBlock;
    private final List<Property> properties;
    private final int stateCount;

    BlockCarrier(@NotNull String vanillaBlock, @NotNull List<Property> properties) {
        this.vanillaBlock = vanillaBlock;
        this.properties = List.copyOf(properties);

        int count = 1;
        for (Property property : properties) {
            count *= property.values().size();
        }
        this.stateCount = count;
    }

    /** The vanilla block key, e.g. {@code minecraft:note_block}. */
    public @NotNull String vanillaBlock() {
        return vanillaBlock;
    }

    /** The blockstates file this carrier's variants are written to. */
    public @NotNull String blockStatesPath() {
        String value = vanillaBlock.substring(vanillaBlock.indexOf(':') + 1);
        return "assets/minecraft/blockstates/" + value + ".json";
    }

    /** The vanilla model a state falls back to when no custom block occupies it. */
    public @NotNull String vanillaModel() {
        String value = vanillaBlock.substring(vanillaBlock.indexOf(':') + 1);
        return "minecraft:block/" + value;
    }

    public @NotNull @Unmodifiable List<Property> properties() {
        return properties;
    }

    /** Every state this carrier provides. */
    public int stateCount() {
        return stateCount;
    }

    /**
     * States usable by custom blocks.
     *
     * <p>One is reserved so an untouched vanilla block still has a state to occupy and
     * renders normally.</p>
     */
    public int usableStateCount() {
        return stateCount - 1;
    }

    /**
     * Decomposes a state index into concrete property values.
     *
     * <p>Mixed-radix over {@link #properties()}, so a carrier is described by its
     * properties alone and needs no bespoke encode/decode of its own.</p>
     */
    public @NotNull @Unmodifiable Map<String, String> decode(int index) {
        if (index < 0 || index >= stateCount) {
            throw new IllegalArgumentException(
                    index + " is outside the " + stateCount + " states " + this + " provides");
        }

        Map<String, String> values = new java.util.LinkedHashMap<>();
        int remaining = index;
        // Last property varies fastest, matching how the variants are enumerated.
        for (int i = properties.size() - 1; i >= 0; i--) {
            Property property = properties.get(i);
            int size = property.values().size();
            values.put(property.name(), property.values().get(remaining % size));
            remaining /= size;
        }
        return java.util.Collections.unmodifiableMap(values);
    }

    /** The blockstate variant string, e.g. {@code instrument=harp,note=0,powered=false}. */
    public @NotNull String variantKey(int index) {
        Map<String, String> values = decode(index);
        StringBuilder key = new StringBuilder();
        // Sorted by property name, which is the order vanilla writes them in.
        new java.util.TreeMap<>(values).forEach((name, value) -> {
            if (!key.isEmpty()) {
                key.append(',');
            }
            key.append(name).append('=').append(value);
        });
        return key.toString();
    }

    public static @NotNull BlockCarrier fromId(@NotNull String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }

    /**
     * The note block instruments whose state can be relied upon.
     *
     * <p>Order is load-bearing: the allocator indexes into it, so reordering would
     * reassign every already-placed custom block. Append only.</p>
     *
     * <p>The mob-head instruments and the trumpet variants are excluded — vanilla derives
     * those from surrounding blocks rather than from the state alone, so they cannot be
     * held.</p>
     */
    private static List<String> INSTRUMENTS() {
        return List.of("harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar",
                "chime", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit",
                "banjo", "pling");
    }

    private static List<String> numbers(int count) {
        List<String> values = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(Integer.toString(i));
        }
        return List.copyOf(values);
    }
}

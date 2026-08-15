package io.kalo.content.block.definition;

/**
 * The vanilla block whose spare block states a custom block borrows on Java.
 *
 * <p>Java has no way to add a genuinely new block without a client mod, so custom blocks
 * are vanilla blocks placed in a state the client has been told to render differently by
 * the resource pack. Which vanilla block is borrowed determines how many custom blocks
 * fit and what vanilla behaviour has to be suppressed.</p>
 *
 * <p>This is a Java-platform concern and lives in {@link JavaBlockOptions}. Bedrock adds
 * real custom blocks through Geyser and borrows nothing.</p>
 */
public enum BlockCarrier {

    /**
     * Note blocks: {@code instrument} × {@code note} × {@code powered}, all rendered
     * identically by vanilla, which makes them the standard carrier for solid blocks.
     *
     * <p>Costs: the server recomputes {@code instrument} from the block underneath on
     * every neighbour update, so physics has to be suppressed, and real note blocks lose
     * their instrument variety.</p>
     */
    NOTE_BLOCK(16 * 25 * 2);

    private final int stateCount;

    BlockCarrier(int stateCount) {
        this.stateCount = stateCount;
    }

    /**
     * Total states this carrier provides. One is reserved so an untouched vanilla block
     * still has a state to occupy, so the usable count is one lower.
     */
    public int stateCount() {
        return stateCount;
    }

    public int usableStateCount() {
        return stateCount - 1;
    }
}

package io.kalo.platform.java;

import io.kalo.content.block.Block;
import io.kalo.content.block.definition.BlockCarrier;
import io.kalo.manager.RegistryManager;
import net.kyori.adventure.key.Key;
import org.bukkit.GameMode;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Makes borrowed note block states behave like real blocks.
 *
 * <p>Vanilla treats note blocks as instruments, not as carriers of arbitrary state, and
 * fights back in three ways. Each is suppressed here:</p>
 *
 * <ul>
 *   <li><b>Instrument recomputation.</b> Vanilla derives {@code instrument} from the
 *       block underneath on every neighbour update. Left alone, placing a block below a
 *       custom block would silently change what that block is. {@link #onPhysics} stops
 *       the update from reaching note blocks.</li>
 *   <li><b>Tuning.</b> Right-clicking a note block increments {@code note}, which would
 *       turn one custom block into a different one. {@link #onInteract} blocks it.</li>
 *   <li><b>Playing.</b> Left-clicking or redstone plays a sound that has nothing to do
 *       with the block the player is looking at. {@link #onNotePlay} cancels it.</li>
 * </ul>
 */
public final class JavaBlockListener implements Listener {

    /**
     * Bukkit's counterparts to {@link JavaBlockCompiler#INSTRUMENT_IDS}, in the same
     * order — index {@code i} here must be the same instrument as index {@code i} there,
     * or placed blocks will not match what the pack tells the client to draw.
     *
     * <p>Bukkit's enum names do not match vanilla's ids ({@code PIANO} is {@code harp}),
     * so the two lists are written out separately rather than derived from each other.
     * This one lives here because the enum is registry-backed and cannot be touched
     * outside a running server.</p>
     */
    private static final List<Instrument> INSTRUMENTS = List.of(
            Instrument.PIANO,          // harp
            Instrument.BASS_DRUM,      // basedrum
            Instrument.SNARE_DRUM,     // snare
            Instrument.STICKS,         // hat
            Instrument.BASS_GUITAR,    // bass
            Instrument.FLUTE,
            Instrument.BELL,
            Instrument.GUITAR,
            Instrument.CHIME,
            Instrument.XYLOPHONE,
            Instrument.IRON_XYLOPHONE,
            Instrument.COW_BELL,
            Instrument.DIDGERIDOO,
            Instrument.BIT,
            Instrument.BANJO,
            Instrument.PLING
    );

    static {
        if (INSTRUMENTS.size() != JavaBlockCompiler.INSTRUMENT_IDS.size()) {
            throw new IllegalStateException("Instrument tables are out of step: "
                    + INSTRUMENTS.size() + " Bukkit entries vs "
                    + JavaBlockCompiler.INSTRUMENT_IDS.size() + " pack ids");
        }
    }

    private final BlockStateAllocator allocator;

    public JavaBlockListener(@NotNull BlockStateAllocator allocator) {
        this.allocator = allocator;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        String id = JavaBlockItemCompiler.idOf(inHand);
        if (id == null) {
            return;
        }

        Block block = RegistryManager.GlobalRegistries.registries().block().get(Key.key(id)).orElse(null);
        if (block == null) {
            block = RegistryManager.GlobalRegistries.registries().furniture().get(Key.key(id)).orElse(null);
        }
        if (block == null) {
            // Registered when the item was made, gone now — a pack was removed while the
            // item stayed in someone's inventory.
            event.setCancelled(true);
            return;
        }

        Integer index = allocator.indexOf(block.definition().key());
        if (index == null) {
            event.setCancelled(true);
            return;
        }

        applyState(event.getBlockPlaced(), index);
    }

    /**
     * Suppresses neighbour updates on note blocks so vanilla cannot rewrite the
     * instrument out from under a placed custom block.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (event.getChangedType() == Material.NOTE_BLOCK
                || event.getBlock().getType() == Material.NOTE_BLOCK) {
            event.setCancelled(true);
        }
    }

    /** Stops right-click tuning, which would turn one custom block into another. */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        org.bukkit.block.Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.NOTE_BLOCK) {
            return;
        }
        if (resolve(clicked) != null) {
            event.setCancelled(true);
        }
    }

    /** A borrowed state has no musical meaning; playing it would be noise. */
    @EventHandler(ignoreCancelled = true)
    public void onNotePlay(NotePlayEvent event) {
        if (resolve(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        org.bukkit.block.Block broken = event.getBlock();
        Block block = resolve(broken);
        if (block == null) {
            return;
        }

        if (block.definition().behaviour().unbreakable()
                && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
            return;
        }

        // Vanilla would drop a note block; drop the custom block's own item instead.
        event.setDropItems(false);

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            broken.getWorld().dropItemNaturally(broken.getLocation().add(0.5, 0.5, 0.5),
                    block.itemStack().get());
        }
    }

    /** Identifies the custom block occupying a world block, if any. */
    public @Nullable Block resolve(@NotNull org.bukkit.block.Block worldBlock) {
        if (worldBlock.getType() != Material.NOTE_BLOCK) {
            return null;
        }
        if (!(worldBlock.getBlockData() instanceof NoteBlock data)) {
            return null;
        }

        int instrument = INSTRUMENTS.indexOf(data.getInstrument());
        if (instrument < 0) {
            return null; // an instrument outside the allocatable set — a real note block
        }

        int index = BlockStateAllocator.encode(new BlockStateAllocator.NoteBlockState(
                instrument, data.getNote().getId(), data.isPowered()));
        if (index == BlockStateAllocator.RESERVED_VANILLA_INDEX) {
            return null;
        }

        for (Map.Entry<String, Integer> entry : allocator.assignments().entrySet()) {
            if (entry.getValue() == index) {
                Key key = Key.key(entry.getKey());
                Block block = RegistryManager.GlobalRegistries.registries().block().get(key).orElse(null);
                return block != null ? block
                        : RegistryManager.GlobalRegistries.registries().furniture().get(key).orElse(null);
            }
        }
        return null;
    }

    /** Writes a state index into the world without triggering a physics update. */
    private static void applyState(@NotNull org.bukkit.block.Block worldBlock, int index) {
        BlockStateAllocator.NoteBlockState state = BlockStateAllocator.decode(index);

        NoteBlock data = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
        Instrument instrument = INSTRUMENTS.get(state.instrument());
        data.setInstrument(instrument);
        data.setNote(new Note(state.note()));
        data.setPowered(state.powered());

        // applyPhysics=false: a physics pass here would immediately recompute the
        // instrument from the block below and undo the assignment.
        worldBlock.setBlockData(data, false);
    }

    public @NotNull BlockCarrier carrier() {
        return BlockCarrier.NOTE_BLOCK;
    }
}

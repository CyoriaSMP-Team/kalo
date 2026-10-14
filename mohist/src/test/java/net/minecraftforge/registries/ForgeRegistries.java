package net.minecraftforge.registries;

import java.util.List;

/** Minimal fixture, NOT a Forge dependency in the shipping Mohist jar. */
public final class ForgeRegistries {
    public static final FakeRegistry ITEMS = new FakeRegistry(List.of("example:widget","minecraft:paper"));
    public static final FakeRegistry BLOCKS = new FakeRegistry(List.of("example:marble"));
    public static final FakeRegistry ENTITY_TYPES = new FakeRegistry(List.of("example:drone"));
    public static final FakeRegistry FLUIDS = new FakeRegistry(List.of());
    public static final FakeRegistry SOUND_EVENTS = new FakeRegistry(List.of("example:beep"));

    public static final class FakeRegistry {
        private final List<String> ids;
        public FakeRegistry(List<String> ids) { this.ids = ids; }
        public List<String> getKeys() { return ids; }
    }
}

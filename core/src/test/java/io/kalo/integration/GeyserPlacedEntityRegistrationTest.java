package io.kalo.integration;

import io.kalo.platform.bedrock.BedrockPlacedModelRegistration;
import net.kyori.adventure.key.Key;
import org.geysermc.geyser.api.entity.custom.CustomEntityDefinition;
import org.geysermc.geyser.api.entity.definition.GeyserEntityDefinition;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent;
import org.geysermc.geyser.api.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeyserPlacedEntityRegistrationTest {

    @Test
    void aPlacedModelRegistrationBecomesAGeyserEntityDefinition() {
        FakeEvent event = new FakeEvent();
        var placed = new BedrockPlacedModelRegistration(
                Key.key("testpack", "chair"),
                "kalo:model_testpack_chair",
                "geometry.kalo.testpack_chair.placed",
                "textures/items/testpack_chair_t0",
                Map.of("swing", "animation.kalo.testpack_chair.swing"),
                List.of("swing"), 1.0);

        assertEquals(1, GeyserBridge.registerPlacedEntities(event, List.of(placed),
                value -> definition(value)));
        assertEquals(1, event.custom.size());
        assertEquals("kalo:model_testpack_chair",
                event.custom.getFirst().identifier().toString());
    }

    @Test
    void aVehicleOrEmoteRigBecomesAClientEntityRegistration() {
        FakeEvent event = new FakeEvent();
        var rig = new io.kalo.platform.bedrock.BedrockRigRegistration(
                Key.key("testpack", "speeder"), "kalo:vehicle_testpack_speeder_vehicle");

        assertEquals(1, GeyserBridge.registerRigs(event, List.of(rig),
                value -> definition(value)));
        assertEquals(1, event.custom.size());
        assertEquals("kalo:vehicle_testpack_speeder_vehicle",
                event.custom.getFirst().identifier().toString());
    }

    @Test
    void aCustomMobBecomesAClientEntityRegistration() {
        FakeEvent event = new FakeEvent();
        var mob = new io.kalo.platform.bedrock.BedrockMobRegistration(
                Key.key("testpack", "golem"), "kalo:mob_testpack_golem");

        assertEquals(1, GeyserBridge.registerMobs(event, List.of(mob),
                value -> definition(value)));
        assertEquals(1, event.custom.size());
        assertEquals("kalo:mob_testpack_golem",
                event.custom.getFirst().identifier().toString());
    }

    @Test
    void anAnimatedBlockBecomesAClientEntityRegistration() {
        FakeEvent event = new FakeEvent();
        var animated = new io.kalo.platform.bedrock.BedrockAnimatedBlockRegistration(
                Key.key("testpack", "fan"), "kalo:block_testpack_fan");

        assertEquals(1, GeyserBridge.registerAnimatedBlockEntities(event,
                List.of(animated), value -> definition(value)));
        assertEquals(1, event.custom.size());
        assertEquals("kalo:block_testpack_fan",
                event.custom.getFirst().identifier().toString());
    }

    private static CustomEntityDefinition definition(String value) {
        return new CustomEntityDefinition() {
            @Override
            public Identifier identifier() {
                int colon = value.indexOf(':');
                String namespace = colon < 0 ? "minecraft" : value.substring(0, colon);
                String path = colon < 0 ? value : value.substring(colon + 1);
                return new Identifier() {
                    @Override
                    public String namespace() {
                        return namespace;
                    }

                    @Override
                    public String path() {
                        return path;
                    }

                    @Override
                    public String toString() {
                        return namespace + ":" + path;
                    }
                };
            }

            @Override
            public List<org.geysermc.geyser.api.entity.property.GeyserEntityProperty<?>> properties() {
                return List.of();
            }

            @Override
            public boolean vanilla() {
                return false;
            }

            @Override
            public boolean registered() {
                return false;
            }
        };
    }

    private static final class FakeEvent implements GeyserDefineEntitiesEvent {
        private final List<CustomEntityDefinition> custom = new ArrayList<>();

        @Override
        public Collection<GeyserEntityDefinition> entities() {
            return List.of();
        }

        @Override
        public Collection<CustomEntityDefinition> customEntities() {
            return custom;
        }

        @Override
        public void register(CustomEntityDefinition definition) {
            custom.add(definition);
        }
    }
}

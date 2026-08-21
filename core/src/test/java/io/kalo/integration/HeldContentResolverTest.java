package io.kalo.integration;

import io.kalo.content.Content;
import io.kalo.content.feature.Feature;
import io.kalo.content.feature.FeatureEventBus;
import io.kalo.content.feature.FeatureEventBusImpl;
import io.kalo.registry.MappedRegistry;
import io.kalo.registry.Registry;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class HeldContentResolverTest {

    @Test
    void resolvesItemArmorBlockAndFurnitureForms() {
        StubContent item = new StubContent(Key.key("pack", "item"));
        StubContent armor = new StubContent(Key.key("pack", "helmet"));
        StubContent block = new StubContent(Key.key("pack", "block"));
        StubContent furniture = new StubContent(Key.key("pack", "chair"));

        Registry<Content> items = registry(item);
        Registry<Content> armors = registry(armor);
        Registry<Content> blocks = registry(block);
        Registry<Content> furnitureRegistry = registry(furniture);

        assertSame(item, HeldContentResolver.resolveIds(
                item.key().asString(), null, items, armors, blocks, furnitureRegistry).content());
        assertSame(armor, HeldContentResolver.resolveIds(
                armor.key().asString(), null, items, armors, blocks, furnitureRegistry).content());
        assertSame(block, HeldContentResolver.resolveIds(
                null, block.key().asString(), items, armors, blocks, furnitureRegistry).content());
        assertSame(furniture, HeldContentResolver.resolveIds(
                null, furniture.key().asString(), items, armors, blocks, furnitureRegistry).content());
    }

    @Test
    void rejectsUnknownAndMalformedPersistentIds() {
        Registry<Content> empty = new MappedRegistry<>(Map.of());

        assertNull(HeldContentResolver.resolveIds(
                "not a key", null, empty, empty, empty, empty));
        assertNull(HeldContentResolver.resolveIds(
                "pack:missing", null, empty, empty, empty, empty));
    }

    @Test
    void unnamedContentFallsBackToItsKeyValue() {
        StubContent content = new StubContent(Key.key("pack", "plain_name"));
        assertEquals("plain_name", HeldContentResolver.displayName(content));
    }

    private static Registry<Content> registry(Content content) {
        return new MappedRegistry<>(Map.of(content.key(), content));
    }

    private record StubContent(Key key) implements Content {
        @Override
        public Collection<Feature> features() {
            return List.of();
        }

        @Override
        public FeatureEventBus featureEventBus() {
            return new FeatureEventBusImpl();
        }
    }
}

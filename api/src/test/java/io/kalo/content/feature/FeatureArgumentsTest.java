package io.kalo.content.feature;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class FeatureArgumentsTest {

    @Test
    void constructorTakesAnImmutableSnapshot() {
        Map<String, String> source = new HashMap<>();
        source.put("message", "before");

        FeatureArguments arguments = new FeatureArguments(source);
        source.put("message", "after");

        assertEquals("before", arguments.get("message"));
        assertThrows(UnsupportedOperationException.class,
                () -> arguments.map().put("another", "value"));
    }

    @Test
    void builderDoesNotLeakItsMutableBackingMap() {
        FeatureArguments.Builder builder = FeatureArguments.builder()
                .argument("message", "first");
        FeatureArguments first = builder.build();

        builder.argument("message", "second");

        assertEquals("first", first.get("message"));
        assertEquals("second", builder.build().get("message"));
    }
}

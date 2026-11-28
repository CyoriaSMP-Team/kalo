package io.kalo.content;

import io.kalo.content.item.definition.BedrockOptions;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Reads the {@code bedrock:} section shared by every content type.
 *
 * <pre>
 * bedrock:
 *   enabled: true
 *   icon: my_icon
 *   hold:
 *     first_person: { position: [0, 0, 0], rotation: [0, 0, 0], scale: 1.0 }
 *     third_person: { position: [0, 0, 0], rotation: [0, 0, 0], scale: 1.0 }
 * </pre>
 */
public final class BedrockOptionsParser {

    private BedrockOptionsParser() {
    }

    public static @NotNull BedrockOptions parse(@NotNull ConfigurationSection bedrock) {
        BedrockOptions.HoldPose pose = null;
        ConfigurationSection hold = bedrock.getConfigurationSection("hold");
        if (hold != null) {
            pose = new BedrockOptions.HoldPose(
                    view(hold.getConfigurationSection("first_person")),
                    view(hold.getConfigurationSection("third_person")));
        }
        return new BedrockOptions(bedrock.getBoolean("enabled", true), bedrock.getString("icon"), pose);
    }

    private static @Nullable BedrockOptions.View view(@Nullable ConfigurationSection section) {
        if (section == null) return null;
        return new BedrockOptions.View(
                triple(section, "position"),
                triple(section, "rotation"),
                section.isSet("scale") ? section.getDouble("scale") : null);
    }

    private static @Nullable List<Double> triple(@NotNull ConfigurationSection section, @NotNull String key) {
        if (!section.isSet(key)) return null;
        List<Double> values = section.getDoubleList(key);
        if (values.size() != 3) {
            throw new IllegalArgumentException("bedrock.hold." + section.getName() + "." + key
                    + " needs exactly 3 numbers, got " + values.size());
        }
        return values;
    }
}

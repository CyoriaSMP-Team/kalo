package io.kalo.content.vfx.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A cleaned, platform-neutral VFX recipe.
 *
 * <p>The format is Studio's JSON: a duration, an optional loop section, and layers. Three
 * layer types are understood: {@code motes} draw world-space sprites, {@code camera}
 * draws sprites in front of the caster's eyes, and {@code model} places a real item model
 * for its time window. Unknown layer types are dropped with a diagnostic instead of
 * making the whole effect fail.</p>
 */
public record VfxRecipe(
        int duration,
        @Nullable Loop loop,
        @NotNull List<Layer> layers,
        @NotNull List<Camera> cameras,
        @NotNull List<Model> models,
        @NotNull List<String> diagnostics,
        @NotNull List<Sound> sounds
) {

    public static final int MAX_DURATION = 1200;
    public static final int MAX_SPRITES = 240;
    public static final int MAX_MODELS = 32;

    public enum Shape {
        SPHERE,
        RING,
        DISC,
        LINE,
        CONE;

        public static @NotNull Shape parse(@NotNull String value) {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "sphere", "ball" -> SPHERE;
                case "ring", "circle" -> RING;
                case "disc", "disk" -> DISC;
                case "line", "beam" -> LINE;
                case "cone" -> CONE;
                default -> throw new IllegalArgumentException(
                        "unsupported VFX shape '" + value + "'");
            };
        }
    }

    public record Loop(int start, int end) {
        public Loop {
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException("VFX loop must have start < end");
            }
        }
    }

    public record Vec3(double x, double y, double z) {
        public static final Vec3 ZERO = new Vec3(0, 0, 0);
    }

    public record Sound(int tick, @NotNull String name, double volume, double pitch) {
        public Sound {
            if (tick < 0 || name.isBlank() || !name.matches("[a-z0-9_.:/-]{1,128}")
                    || volume < 0 || volume > 4 || pitch < 0.5 || pitch > 2) {
                throw new IllegalArgumentException("VFX sound event is invalid");
            }
        }
    }

    /**
     * A world-space sprite burst.
     *
     * <p>{@code at} is an offset from the cast origin. For camera layers the same record
     * carries camera-space offsets instead: X is right, Y is up and Z is forward.</p>
     */
    public record Layer(
            @NotNull String kind,
            int start,
            int end,
            int burst,
            @NotNull Vec3 at,
            @NotNull Shape shape,
            double radius,
            double speedMin,
            double speedMax,
            double size,
            int lifetime,
            double gravity,
            int color
    ) {
        public Layer {
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException("VFX layer has an invalid start/end window");
            }
            if (burst < 1 || burst > MAX_SPRITES) {
                throw new IllegalArgumentException("VFX layer burst must be within 1.."
                        + MAX_SPRITES);
            }
            if (radius < 0 || speedMin < 0 || speedMax < speedMin
                    || size <= 0 || lifetime < 1) {
                throw new IllegalArgumentException("VFX layer has an invalid distance or speed");
            }
        }
    }

    /**
     * A sprite burst anchored to the caster's view.
     *
     * <p>{@code distance} pushes the burst that many blocks in front of the eyes beyond its
     * own {@code at.z}, and {@code shake} adds a deterministic camera-space wobble in
     * blocks. No client packets are involved: the sprites are ordinary world entities
     * recomputed from the caster's eye position and look direction each tick.</p>
     */
    public record Camera(@NotNull Layer layer, double distance, double shake,
                         double fov, double yaw, double pitch) {
        public Camera {
            if (distance < 0.25 || distance > 16) {
                throw new IllegalArgumentException(
                        "VFX camera distance must be within 0.25..16");
            }
            if (shake < 0 || shake > 1) {
                throw new IllegalArgumentException("VFX camera shake must be within 0..1");
            }
            if (fov < 30 || fov > 110) {
                throw new IllegalArgumentException("VFX camera fov must be within 30..110");
            }
            if (yaw < -180 || yaw > 180) {
                throw new IllegalArgumentException("VFX camera yaw must be within -180..180");
            }
            if (pitch < -90 || pitch > 90) {
                throw new IllegalArgumentException("VFX camera pitch must be within -90..90");
            }
        }

        /** Backwards-compatible constructor with the default 70° field of view. */
        public Camera(@NotNull Layer layer, double distance, double shake) {
            this(layer, distance, shake, 70, 0, 0);
        }

        /** Backwards-compatible constructor with default view offsets. */
        public Camera(@NotNull Layer layer, double distance, double shake, double fov) {
            this(layer, distance, shake, fov, 0, 0);
        }
    }

    /**
     * A real item model placed for its time window.
     *
     * <p>{@code model} is any item-model key the pack contains, such as one of Kalo's own
     * item keys. {@code at} is an offset from the cast origin, {@code yaw}/{@code pitch}/
     * {@code roll} are relative to the cast direction, and {@code spin} turns the model
     * around its vertical axis per recipe tick.</p>
     */
    public record Model(
            @NotNull Key model,
            int start,
            int end,
            @NotNull Vec3 at,
            double scale,
            double yaw,
            double pitch,
            double roll,
            double spin
    ) {
        public Model {
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException("VFX model has an invalid start/end window");
            }
            if (scale <= 0 || scale > 10) {
                throw new IllegalArgumentException("VFX model scale must be within 0..10");
            }
        }
    }

    public VfxRecipe {
        duration = Math.max(1, Math.min(MAX_DURATION, duration));
        loop = loop == null ? null : new Loop(
                Math.max(0, Math.min(duration - 1, loop.start())),
                Math.max(1, Math.min(duration, loop.end())));
        layers = List.copyOf(layers);
        cameras = List.copyOf(cameras);
        models = List.copyOf(models);
        diagnostics = List.copyOf(diagnostics);
        sounds = List.copyOf(sounds);
    }

    /** Backwards-compatible constructor for recipes without camera/model layers. */
    public VfxRecipe(int duration, @Nullable Loop loop, @NotNull List<Layer> layers,
                     @NotNull List<String> diagnostics, @NotNull List<Sound> sounds) {
        this(duration, loop, layers, List.of(), List.of(), diagnostics, sounds);
    }

    /** Backwards-compatible constructor for recipes without sounds or extra layers. */
    public VfxRecipe(int duration, @Nullable Loop loop, @NotNull List<Layer> layers,
                     @NotNull List<String> diagnostics) {
        this(duration, loop, layers, List.of(), List.of(), diagnostics, List.of());
    }

    public static @NotNull VfxRecipe parse(@NotNull ConfigurationSection section) {
        return parse(toJson(section));
    }

    public static @NotNull VfxRecipe parse(@NotNull JsonObject root) {
        List<String> diagnostics = new ArrayList<>();
        int duration = integer(root, "duration", 30);
        if (duration < 1 || duration > MAX_DURATION) {
            diagnostics.add("duration " + duration + " clamped to 1.." + MAX_DURATION);
        }
        duration = Math.max(1, Math.min(MAX_DURATION, duration));

        Loop loop = null;
        JsonElement loopElement = root.get("loop");
        if (loopElement != null && !loopElement.isJsonNull()) {
            try {
                int[] pair = pair(loopElement);
                loop = new Loop(Math.max(0, Math.min(duration - 1, pair[0])),
                        Math.max(1, Math.min(duration, pair[1])));
                if (loop.end() <= loop.start()) {
                    diagnostics.add("loop section is empty; one-shot playback is used");
                    loop = null;
                }
            } catch (RuntimeException invalid) {
                diagnostics.add("ignored invalid loop section: " + invalid.getMessage());
            }
        }

        List<Sound> sounds = sounds(root, duration, diagnostics);

        JsonElement layerElement = root.get("layers");
        if (layerElement == null || !layerElement.isJsonArray()) {
            throw new IllegalArgumentException("VFX recipe has no layers array");
        }

        List<Layer> layers = new ArrayList<>();
        List<Camera> cameras = new ArrayList<>();
        List<Model> models = new ArrayList<>();
        int totalSprites = 0;
        JsonArray array = layerElement.getAsJsonArray();
        for (int index = 0; index < array.size(); index++) {
            JsonElement element = array.get(index);
            if (!element.isJsonObject()) {
                diagnostics.add("layer " + index + " is not an object; skipped");
                continue;
            }
            JsonObject json = element.getAsJsonObject();
            String type = string(json, "type", "motes").toLowerCase(Locale.ROOT);
            switch (type) {
                case "motes" -> {
                    int remaining = MAX_SPRITES - totalSprites;
                    Layer layer = spriteLayer(json, index, duration, remaining, diagnostics);
                    if (layer == null) {
                        continue;
                    }
                    totalSprites += layer.burst();
                    layers.add(layer);
                }
                case "camera" -> {
                    int remaining = MAX_SPRITES - totalSprites;
                    Layer layer = spriteLayer(json, index, duration, remaining, diagnostics);
                    if (layer == null) {
                        continue;
                    }
                    totalSprites += layer.burst();
                    double distance = clamp(number(json, "distance", 1.5), 0.25, 16);
                    double shake = clamp(number(json, "shake", 0), 0, 1);
                    double fov = clamp(number(json, "fov", 70), 30, 110);
                    double yaw = clamp(number(json, "yaw", 0), -180, 180);
                    double pitch = clamp(number(json, "pitch", 0), -90, 90);
                    cameras.add(new Camera(layer, distance, shake, fov, yaw, pitch));
                }
                case "model" -> {
                    if (models.size() >= MAX_MODELS) {
                        diagnostics.add("VFX model layers capped at " + MAX_MODELS);
                        continue;
                    }
                    Model model = modelLayer(json, index, duration, diagnostics);
                    if (model != null) {
                        models.add(model);
                    }
                }
                default -> diagnostics.add("layer " + index + " type '" + type
                        + "' is not supported; skipped");
            }
        }

        if (layers.isEmpty() && cameras.isEmpty() && models.isEmpty()) {
            throw new IllegalArgumentException("VFX recipe has no drawable layers");
        }
        return new VfxRecipe(duration, loop, layers, cameras, models, diagnostics, sounds);
    }

    private static @Nullable Layer spriteLayer(@NotNull JsonObject json, int index,
                                               int duration, int remaining,
                                               @NotNull List<String> diagnostics) {
        String kind = string(json, "kind", "glow").toLowerCase(Locale.ROOT);
        if (!kind.matches("[a-z0-9_-]{1,32}")) {
            diagnostics.add("layer " + index + " kind '" + kind
                    + "' is invalid; using glow");
            kind = "glow";
        }
        int start = integer(json, "start", 0);
        int end = integer(json, "end", duration);
        start = Math.max(0, Math.min(duration - 1, start));
        end = Math.max(start + 1, Math.min(duration, end));
        if (end <= start) {
            diagnostics.add("layer " + index + " has an empty time window; skipped");
            return null;
        }
        int burst = integer(json, "burst", integer(json, "count", 24));
        burst = Math.max(0, Math.min(MAX_SPRITES, burst));
        if (burst == 0) {
            diagnostics.add("layer " + index + " has no sprites; skipped");
            return null;
        }
        if (remaining <= 0) {
            diagnostics.add("sprite budget reached at layer " + index + "; skipped");
            return null;
        }
        if (burst > remaining) {
            diagnostics.add("layer " + index + " capped at " + remaining
                    + " sprites by the effect budget");
            burst = remaining;
        }

        Vec3 at = vec3(json.get("at"));
        Shape shape;
        try {
            shape = Shape.parse(string(json, "shape", "sphere"));
        } catch (IllegalArgumentException unknown) {
            diagnostics.add("layer " + index + ": " + unknown.getMessage()
                    + "; using sphere");
            shape = Shape.SPHERE;
        }
        double radius = clamp(number(json, "radius", 1.0), 0, 32);
        double[] speed = speed(json.get("speed"));
        double size = clamp(number(json, "size", 0.3), 0.01, 4);
        int lifetime = Math.max(1, Math.min(200,
                integer(json, "lifetime", integer(json, "life", 20))));
        double gravity = clamp(number(json, "gravity", 0), -1, 1);
        int color = color(json.get("color"), diagnostics, index);

        return new Layer(kind, start, end, burst, at, shape, radius,
                speed[0], speed[1], size, lifetime, gravity, color);
    }

    private static @Nullable Model modelLayer(@NotNull JsonObject json, int index,
                                              int duration,
                                              @NotNull List<String> diagnostics) {
        String modelText = string(json, "model", "");
        if (modelText.isBlank()) {
            diagnostics.add("layer " + index + " model is missing; skipped");
            return null;
        }
        Key model;
        try {
            model = Key.key(modelText.trim());
        } catch (RuntimeException invalid) {
            diagnostics.add("layer " + index + " model '" + modelText
                    + "' is not a valid key; skipped");
            return null;
        }
        int start = Math.max(0, Math.min(duration - 1, integer(json, "start", 0)));
        int end = Math.max(start + 1, Math.min(duration, integer(json, "end", duration)));
        if (end <= start) {
            diagnostics.add("layer " + index + " has an empty time window; skipped");
            return null;
        }
        Vec3 at = vec3(json.get("at"));
        double scale = clamp(number(json, "scale", 1.0), 0.05, 10);
        double yaw = clamp(number(json, "yaw", 0), -1080, 1080);
        double pitch = clamp(number(json, "pitch", 0), -1080, 1080);
        double roll = clamp(number(json, "roll", 0), -1080, 1080);
        double spin = clamp(number(json, "spin", 0), -180, 180);
        return new Model(model, start, end, at, scale, yaw, pitch, roll, spin);
    }

    private static @NotNull List<Sound> sounds(@NotNull JsonObject root, int duration,
                                               @NotNull List<String> diagnostics) {
        JsonElement element = root.get("sounds");
        if (element == null) {
            element = root.get("sound");
        }
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        List<Sound> sounds = new ArrayList<>();
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                addSound(child, duration, diagnostics, sounds);
            }
        } else {
            addSound(element, duration, diagnostics, sounds);
        }
        if (sounds.size() > 64) {
            diagnostics.add("VFX sound events capped at 64");
            return List.copyOf(sounds.subList(0, 64));
        }
        return List.copyOf(sounds);
    }

    private static void addSound(@NotNull JsonElement element, int duration,
                                 @NotNull List<String> diagnostics,
                                 @NotNull List<Sound> sounds) {
        if (element.isJsonPrimitive()) {
            String name = element.getAsString();
            if (name.matches("[a-z0-9_.:/-]{1,128}")) {
                sounds.add(new Sound(0, name, 1.0, 1.0));
            } else {
                diagnostics.add("ignored invalid VFX sound name '" + name + "'");
            }
            return;
        }
        if (!element.isJsonObject()) {
            diagnostics.add("ignored a non-object VFX sound event");
            return;
        }
        JsonObject json = element.getAsJsonObject();
        int tick = Math.max(0, Math.min(duration - 1, soundTick(json)));
        String name = firstNonBlank(string(json, "sound", ""),
                string(json, "id", ""), string(json, "name", ""));
        if (name.isBlank() || !name.matches("[a-z0-9_.:/-]{1,128}")) {
            diagnostics.add("ignored invalid VFX sound event at tick " + tick);
            return;
        }
        double volume = clamp(number(json, "volume", 1.0), 0, 4);
        double pitch = clamp(number(json, "pitch", 1.0), 0.5, 2);
        sounds.add(new Sound(tick, name, volume, pitch));
    }

    private static int soundTick(@NotNull JsonObject json) {
        for (String key : List.of("tick", "time", "at")) {
            JsonElement value = json.get(key);
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
                continue;
            }
            if (value.getAsJsonPrimitive().isNumber()) {
                return value.getAsInt();
            }
            String text = value.getAsString().trim().toLowerCase(Locale.ROOT);
            try {
                if (text.endsWith("s")) {
                    return (int) Math.round(Double.parseDouble(
                            text.substring(0, text.length() - 1)) * 20);
                }
                if (text.endsWith("t")) {
                    text = text.substring(0, text.length() - 1);
                }
                return (int) Math.round(Double.parseDouble(text));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static @NotNull String firstNonBlank(@NotNull String... values) {
        for (String value : values) {
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    /** Converts a YAML section to the JSON shape Studio exports, so parsing is shared. */
    public static @NotNull JsonObject toJson(@NotNull ConfigurationSection section) {
        JsonObject result = new JsonObject();
        for (String key : section.getKeys(false)) {
            result.add(key, value(section.get(key)));
        }
        return result;
    }

    private static JsonElement value(@Nullable Object raw) {
        if (raw == null) {
            return JsonNull.INSTANCE;
        }
        if (raw instanceof ConfigurationSection section) {
            return toJson(section);
        }
        if (raw instanceof Map<?, ?> map) {
            JsonObject object = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                object.add(String.valueOf(entry.getKey()), value(entry.getValue()));
            }
            return object;
        }
        if (raw instanceof List<?> list) {
            JsonArray array = new JsonArray();
            list.forEach(item -> array.add(value(item)));
            return array;
        }
        if (raw instanceof Number number) {
            return new com.google.gson.JsonPrimitive(number);
        }
        if (raw instanceof Boolean bool) {
            return new com.google.gson.JsonPrimitive(bool);
        }
        if (raw instanceof JsonElement json) {
            return json;
        }
        return new com.google.gson.JsonPrimitive(String.valueOf(raw));
    }

    private static @NotNull Vec3 vec3(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Vec3.ZERO;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            return new Vec3(
                    clamp(array.size() > 0 ? array.get(0).getAsDouble() : 0, -32, 32),
                    clamp(array.size() > 1 ? array.get(1).getAsDouble() : 0, -32, 32),
                    clamp(array.size() > 2 ? array.get(2).getAsDouble() : 0, -32, 32));
        }
        String[] parts = element.getAsString().split("[, ]+");
        return new Vec3(
                clamp(parts.length > 0 ? parseDouble(parts[0]) : 0, -32, 32),
                clamp(parts.length > 1 ? parseDouble(parts[1]) : 0, -32, 32),
                clamp(parts.length > 2 ? parseDouble(parts[2]) : 0, -32, 32));
    }

    private static double[] speed(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new double[]{0.1, 0.3};
        }
        double first;
        double second;
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            first = array.size() > 0 ? array.get(0).getAsDouble() : 0.1;
            second = array.size() > 1 ? array.get(1).getAsDouble() : first;
        } else {
            first = second = element.getAsDouble();
        }
        first = clamp(first, 0, 10);
        second = clamp(second, 0, 10);
        return first <= second ? new double[]{first, second} : new double[]{second, first};
    }

    private static int color(@Nullable JsonElement element, @NotNull List<String> diagnostics,
                             int layer) {
        if (element == null || element.isJsonNull()) {
            return 0xFFFFFF;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt() & 0xFFFFFF;
        }
        try {
            return parseColor(element.getAsString());
        } catch (RuntimeException invalid) {
            diagnostics.add("layer " + layer + " color is invalid; using white");
            return 0xFFFFFF;
        }
    }

    public static int parseColor(@NotNull String value) {
        String color = value.trim();
        Map<String, Integer> dyes = Map.ofEntries(
                Map.entry("white", 0xFFFFFF), Map.entry("orange", 0xFFA500),
                Map.entry("magenta", 0xFF00FF), Map.entry("light_blue", 0x55FFFF),
                Map.entry("yellow", 0xFFFF55), Map.entry("lime", 0x55FF55),
                Map.entry("pink", 0xFFB6C1), Map.entry("gray", 0x808080),
                Map.entry("light_gray", 0xD3D3D3), Map.entry("cyan", 0x00AAAA),
                Map.entry("purple", 0xAA00AA), Map.entry("blue", 0x5555FF),
                Map.entry("brown", 0xAA5500), Map.entry("green", 0x00AA00),
                Map.entry("red", 0xFF5555), Map.entry("black", 0x000000));
        Integer dye = dyes.get(color.toLowerCase(Locale.ROOT));
        if (dye != null) {
            return dye;
        }
        if (color.startsWith("#")) {
            color = color.substring(1);
        } else if (color.startsWith("0x") || color.startsWith("0X")) {
            color = color.substring(2);
        }
        if (!color.matches("[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("color must be a dye name or six hex digits");
        }
        return Integer.parseInt(color, 16);
    }

    private static int[] pair(@NotNull JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            return new int[]{
                    array.size() > 0 ? array.get(0).getAsInt() : 0,
                    array.size() > 1 ? array.get(1).getAsInt() : 0};
        }
        JsonObject object = element.getAsJsonObject();
        return new int[]{
                object.has("start") ? object.get("start").getAsInt() : 0,
                object.has("end") ? object.get("end").getAsInt() : 0};
    }

    private static @NotNull String string(@NotNull JsonObject object, @NotNull String key,
                                          @NotNull String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    private static int integer(@NotNull JsonObject object, @NotNull String key, int fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsInt();
    }

    private static double number(@NotNull JsonObject object, @NotNull String key,
                                 double fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsDouble();
    }

    private static double parseDouble(@Nullable String value) {
        try {
            return value == null ? 0 : Double.parseDouble(value);
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

package io.kalo.platform.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kalo.content.item.definition.ModelDefinition;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Iterator;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts multi-sheet static Java item geometry into a single Bedrock texture atlas.
 * A Bedrock held-item attachable has one texture: each Java face is remapped into a
 * deterministic horizontal atlas with its own absolute UV origin.
 *
 * Restrictions are explicit: no parent-only geometry, no absent face texture,
 * no more than 16 source sheets and 4096px atlas width/height. Original high-resolution
 * textures are preserved. Animated Java strips contribute their first native-size frame
 * until a compatible Bedrock animation controller can reproduce the timing.
 */
final class BedrockItemAtlas {
    private BedrockItemAtlas() {}

    private record Sheet(@NotNull BufferedImage image, int x, int width, int height) {}

    record Result(@NotNull Key iconSource, byte[] image, int sheets) {
        Result {
            image = image.clone();
        }
        @Override public byte[] image() { return image.clone(); }
    }

    static @Nullable Result build(@NotNull ModelDefinition.Custom custom,
                                  @NotNull JsonObject model,
                                  @NotNull JsonObject geometry,
                                  @NotNull ResourcePack javaSource) throws IOException {
        JsonArray elements = model.getAsJsonArray("elements");
        if (elements == null || elements.isEmpty()) return null;
        JsonObject textureAliases = model.getAsJsonObject("textures");
        Map<String,String> aliases = new LinkedHashMap<>();
        if (textureAliases != null) {
            for (var entry : textureAliases.entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) return null;
                aliases.put(entry.getKey(), entry.getValue().getAsString());
            }
        }

        JsonObject geometryRoot = geometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometryRoot.getAsJsonObject("description");
        int tileWidth = description.get("texture_width").getAsInt();
        int tileHeight = description.get("texture_height").getAsInt();
        if (tileWidth < 1 || tileHeight < 1 || tileWidth > 1024 || tileHeight > 1024) return null;
        JsonArray cubes = geometryRoot.getAsJsonArray("bones").get(0).getAsJsonObject()
                .getAsJsonArray("cubes");
        if (cubes.size() != elements.size()) return null;

        Map<Key,Integer> slots = new LinkedHashMap<>();
        // First resolve all face sources. No pack files are written until every face
        // and source image is verified, so failed items cannot leak half a mapping.
        ArrayList<Map<String,Integer>> faceSlots = new ArrayList<>();
        for (JsonElement entry : elements) {
            if (!entry.isJsonObject()) return null;
            JsonObject faces = entry.getAsJsonObject().getAsJsonObject("faces");
            if (faces == null || faces.size() == 0) return null;
            Map<String,Integer> byFace = new LinkedHashMap<>();
            for (var face : faces.entrySet()) {
                if (!face.getValue().isJsonObject()) return null;
                JsonElement texture = face.getValue().getAsJsonObject().get("texture");
                if (texture == null || !texture.isJsonPrimitive()) return null;
                Key key = resolve(texture.getAsString(), custom, aliases);
                if (key == null) return null;
                if (!slots.containsKey(key)) {
                    if (slots.size() >= 16) return null;
                    slots.put(key, slots.size());
                }
                byFace.put(face.getKey(), slots.get(key));
            }
            faceSlots.add(byFace);
        }
        if (slots.size() < 2) return null;

        // A Java model with UVs in 0..16 may reference 512x512 PNGs and even
        // multiple different-sized PNGs. Older Kalo required every PNG to be
        // exactly 16x16, causing the majority of 3D weapons to disappear in
        // Bedrock. Decode with dimensions capped, and preserve source pixels.
        Map<Key, Sheet> sheets = new LinkedHashMap<>();
        int atlasWidth = 0;
        int atlasHeight = 0;
        for (Key key : slots.keySet()) {
            Writable source = javaSource.file("assets/" + key.namespace()
                    + "/textures/" + key.value() + ".png");
            if (source == null) return null;
            byte[] png = source.toByteArray();
            if (png.length > 16 * 1024 * 1024) return null;
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
                if (input == null) return null;
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) return null;
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > 4096 || height > 4096) return null;
                    // Java .mcmeta animation generally stacks square frames
                    // vertically. Render frame zero rather than stretching the
                    // entire strip into a static Bedrock texture.
                    boolean animated = javaSource.file("assets/" + key.namespace()
                            + "/textures/" + key.value() + ".png.mcmeta") != null;
                    int visibleHeight = animated && height > width && height % width == 0
                            ? width : height;
                    if ((long) atlasWidth + width > 4096 || visibleHeight > 4096) return null;
                    BufferedImage image = reader.read(0);
                    sheets.put(key, new Sheet(image, atlasWidth, width, visibleHeight));
                    atlasWidth += width;
                    atlasHeight = Math.max(atlasHeight, visibleHeight);
                } finally {
                    reader.dispose();
                }
            }
        }
        if (atlasWidth < 1 || atlasHeight < 1
                || (long) atlasWidth * atlasHeight > 8L * 1024 * 1024) return null;
        BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas = atlas.createGraphics();
        try {
            for (Sheet sheet : sheets.values()) {
                // Clip Java animation strips to the initial native frame.
                canvas.drawImage(sheet.image(), sheet.x(), 0,
                        sheet.x() + sheet.width(), sheet.height(),
                        0, 0, sheet.width(), sheet.height(), null);
            }
        } finally {
            canvas.dispose();
        }

        // Java model UVs use logical 0..texture_size units. Bedrock instead
        // expects absolute pixel UVs against the atlas. Scale both position and
        // size for the original PNG so a 512px sword stays pixel-identical.
        List<Key> orderedKeys = new ArrayList<>(slots.keySet());
        for (int i = 0; i < cubes.size(); i++) {
            JsonObject bedrockFaces = cubes.get(i).getAsJsonObject().getAsJsonObject("uv");
            if (bedrockFaces == null || bedrockFaces.size() != faceSlots.get(i).size()) return null;
            for (var face : faceSlots.get(i).entrySet()) {
                JsonObject json = bedrockFaces.getAsJsonObject(face.getKey());
                if (json == null) return null;
                Key key = orderedKeys.get(face.getValue());
                Sheet sheet = sheets.get(key);
                JsonArray uv = json.getAsJsonArray("uv");
                JsonArray size = json.getAsJsonArray("uv_size");
                if (uv == null || uv.size() != 2 || size == null || size.size() != 2) return null;
                double scaleX = (double) sheet.width() / tileWidth;
                double scaleY = (double) sheet.height() / tileHeight;
                uv.set(0, new com.google.gson.JsonPrimitive(sheet.x() + uv.get(0).getAsDouble() * scaleX));
                uv.set(1, new com.google.gson.JsonPrimitive(uv.get(1).getAsDouble() * scaleY));
                size.set(0, new com.google.gson.JsonPrimitive(size.get(0).getAsDouble() * scaleX));
                size.set(1, new com.google.gson.JsonPrimitive(size.get(1).getAsDouble() * scaleY));
                json.addProperty("material_instance", "default");
            }
        }
        description.addProperty("texture_width", atlasWidth);
        description.addProperty("texture_height", atlasHeight);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(atlas, "PNG", output)) return null;
        return new Result(slots.keySet().iterator().next(), output.toByteArray(), slots.size());
    }

    private static @Nullable Key resolve(@NotNull String reference,
                                         @NotNull ModelDefinition.Custom custom,
                                         @NotNull Map<String,String> aliases) {
        Set<String> seen = new HashSet<>();
        String value = reference;
        while (value.startsWith("#")) {
            String alias = value.substring(1);
            if (!seen.add(alias)) return null;
            Key override = custom.textures().get(alias);
            if (override != null) return override;
            value = aliases.get(alias);
            if (value == null) return null;
        }
        try {
            return Key.key(value.contains(":") ? value : custom.model().namespace() + ":" + value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}

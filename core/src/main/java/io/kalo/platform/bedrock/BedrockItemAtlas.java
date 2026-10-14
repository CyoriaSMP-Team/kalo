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
import java.util.Map;
import java.util.Set;

/**
 * Converts multi-sheet static Java item geometry into a single Bedrock texture atlas.
 * A Bedrock held-item attachable has one texture: each Java face is remapped into a
 * deterministic horizontal atlas with its own absolute UV origin.
 *
 * Restrictions are explicit: no parent-only geometry, no absent face texture, no
 * mixed-size PNGs, no more than 16 sheets or 4096px combined width. This also avoids
 * decoding arbitrary-size user images during pack generation.
 */
final class BedrockItemAtlas {
    private BedrockItemAtlas() {}

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
        if (slots.size() < 2 || (long)slots.size() * tileWidth > 4096) return null;

        BufferedImage atlas = new BufferedImage(tileWidth * slots.size(), tileHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas = atlas.createGraphics();
        try {
            for (var entry : slots.entrySet()) {
                Key key = entry.getKey();
                Writable source = javaSource.file("assets/" + key.namespace()
                        + "/textures/" + key.value() + ".png");
                if (source == null) return null;
                byte[] png = source.toByteArray();
                if (png.length > 16 * 1024 * 1024) return null;
                // Check dimensions from the PNG header before allocating decoded pixels.
                // A tiny compressed image can declare huge dimensions (decompression bomb).
                try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
                    if (input == null) return null;
                    Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                    if (!readers.hasNext()) return null;
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(input, true, true);
                        if (reader.getWidth(0) != tileWidth || reader.getHeight(0) != tileHeight) return null;
                        BufferedImage image = reader.read(0);
                        canvas.drawImage(image, entry.getValue() * tileWidth, 0, null);
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } finally {
            canvas.dispose();
        }

        // Only mutate the isolated geometry object after all PNGs are loaded.
        for (int i = 0; i < cubes.size(); i++) {
            JsonObject bedrockFaces = cubes.get(i).getAsJsonObject().getAsJsonObject("uv");
            if (bedrockFaces == null || bedrockFaces.size() != faceSlots.get(i).size()) return null;
            for (var face : faceSlots.get(i).entrySet()) {
                JsonObject json = bedrockFaces.getAsJsonObject(face.getKey());
                if (json == null) return null;
                JsonArray uv = json.getAsJsonArray("uv");
                uv.set(0, new com.google.gson.JsonPrimitive(
                        uv.get(0).getAsDouble() + face.getValue() * tileWidth));
                json.addProperty("material_instance", "default");
            }
        }
        description.addProperty("texture_width", tileWidth * slots.size());
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

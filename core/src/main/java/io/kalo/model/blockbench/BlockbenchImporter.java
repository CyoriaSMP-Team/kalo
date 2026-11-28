package io.kalo.model.blockbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.kalo.model.ModelIR;
import io.kalo.model.ModelIR.Animation;
import io.kalo.model.ModelIR.Animator;
import io.kalo.model.ModelIR.AnimationRef;
import io.kalo.model.ModelIR.BezierHandles;
import io.kalo.model.ModelIR.Bone;
import io.kalo.model.ModelIR.Controller;
import io.kalo.model.ModelIR.Cube;
import io.kalo.model.ModelIR.Diagnostic;
import io.kalo.model.ModelIR.DisplayTransform;
import io.kalo.model.ModelIR.Face;
import io.kalo.model.ModelIR.Keyframe;
import io.kalo.model.ModelIR.Severity;
import io.kalo.model.ModelIR.State;
import io.kalo.model.ModelIR.Texture;
import io.kalo.model.ModelIR.Transition;
import io.kalo.model.ModelIR.Vec3;
import io.kalo.model.ModelImportException;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Imports the subset of the Blockbench {@code .bbmodel} format that can be compiled
 * faithfully to Java models and Bedrock geometry.
 *
 * <p>The importer supports Blockbench format 3.2 through 5.0, including the 5.0 split
 * between {@code groups} and {@code outliner}. It keeps the outliner hierarchy instead of
 * flattening it: each group becomes a {@link Bone}, and each cube stays attached to the
 * bone that authored it.</p>
 *
 * <p>Anything the IR cannot represent is reported through {@link ModelIR.Diagnostic}.
 * Malformed JSON, duplicate ids, invalid dimensions, unsafe asset paths, box-UV models and
 * pre-3.2 files are rejected outright. The importer never invents missing geometry.</p>
 */
public final class BlockbenchImporter {

    /** A 64 MiB embedded texture is already far outside a sane resource-pack asset. */
    public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    public static final int MAX_TEXTURES = 256;
    public static final int MAX_EMBEDDED_TEXTURE_BYTES = 16 * 1024 * 1024;
    public static final int MAX_ELEMENTS = 4096;
    public static final int MAX_BONES = 1024;
    public static final int MAX_ANIMATIONS = 256;
    public static final int MAX_KEYFRAMES = 8192;
    public static final int MAX_COORDINATE = 1024;
    public static final int MAX_TEXTURE_DIMENSION = 4096;

    private static final Set<String> FACE_NAMES = Set.of(
            "north", "south", "east", "west", "up", "down");
    private static final Set<String> CHANNELS = Set.of("position", "rotation", "scale");
    private static final Set<String> DISPLAY_SLOTS = Set.of(
            "thirdperson_righthand", "thirdperson_lefthand",
            "firstperson_righthand", "firstperson_lefthand",
            "gui", "ground", "fixed", "head");

    private BlockbenchImporter() {
    }

    /**
     * Reads a model from disk, refusing any file outside {@code packRoot}.
     *
     * @param packRoot the contents pack root; the file must resolve inside it
     * @param file     the {@code .bbmodel} file
     * @param id       the namespaced model id, used for diagnostics and cache identity
     */
    public static @NotNull ModelIR importFile(@NotNull Path packRoot, @NotNull Path file,
                                              @NotNull Key id)
            throws IOException, ModelImportException {
        Path root = packRoot.toAbsolutePath().normalize();
        Path candidate = file.toAbsolutePath().normalize();
        if (!candidate.startsWith(root)) {
            throw new ModelImportException("Model file escapes its pack directory: " + file);
        }
        if (!Files.isRegularFile(candidate)) {
            throw new ModelImportException("Model file does not exist: " + file);
        }
        Path realRoot = root.toRealPath();
        Path realCandidate = candidate.toRealPath();
        if (!realCandidate.startsWith(realRoot)) {
            throw new ModelImportException("Model file resolves outside its pack directory: "
                    + file);
        }
        long size = Files.size(realCandidate);
        if (size > MAX_FILE_BYTES) {
            throw new ModelImportException("Model file is " + size + " bytes; maximum is "
                    + MAX_FILE_BYTES + " bytes: " + file);
        }
        byte[] bytes = Files.readAllBytes(realCandidate);
        return importBytes(bytes, id, realCandidate.toString());
    }

    public static @NotNull ModelIR importBytes(byte @NotNull [] bytes, @NotNull Key id,
                                               @NotNull String sourceLabel)
            throws ModelImportException {
        if (bytes.length > MAX_FILE_BYTES) {
            throw new ModelImportException("Model source is " + bytes.length
                    + " bytes; maximum is " + MAX_FILE_BYTES + " bytes");
        }
        if (bytes.length == 0) {
            throw new ModelImportException("Model source is empty");
        }
        String json = new String(bytes, StandardCharsets.UTF_8);
        return importJson(json, id, sourceLabel);
    }

    public static @NotNull ModelIR importJson(@NotNull String json, @NotNull Key id,
                                              @NotNull String sourceLabel)
            throws ModelImportException {
        if (json.isBlank()) {
            throw new ModelImportException("Model source is empty: " + sourceLabel);
        }
        if (json.length() > MAX_FILE_BYTES) {
            throw new ModelImportException("Model source is " + json.length()
                    + " characters; maximum is " + MAX_FILE_BYTES);
        }

        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new ModelImportException("Model root must be a JSON object: " + sourceLabel);
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException error) {
            throw new ModelImportException("Malformed .bbmodel JSON in " + sourceLabel
                    + ": " + error.getMessage(), error);
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        JsonObject meta = object(root, "meta");
        String formatVersion = meta == null ? null : string(meta, "format_version");
        if (formatVersion == null && meta != null) {
            formatVersion = string(meta, "format");
        }
        if (formatVersion == null) {
            formatVersion = "3.2";
            diagnostics.add(warning("blockbench.missing-format-version",
                    "No meta.format_version; assuming the oldest supported format (3.2)"));
        }
        double version = parseVersion(formatVersion);
        if (version < 3.2) {
            throw new ModelImportException("Blockbench format " + formatVersion
                    + " is older than the supported 3.2 baseline; re-save the model in a"
                    + " current Blockbench version");
        }
        if (version > 5.0) {
            diagnostics.add(warning("blockbench.future-format",
                    "Blockbench format " + formatVersion
                            + " is newer than the tested 5.0 format; only known fields are read"));
        }

        String modelFormat = meta == null ? "java_block" : string(meta, "model_format");
        if (modelFormat == null) {
            modelFormat = "java_block";
        }
        if (meta != null && bool(meta, "box_uv", false)) {
            throw new ModelImportException("Box-UV Blockbench models are not supported yet;"
                    + " export with per-face UVs or open the model with Box UV disabled");
        }
        if (!Set.of("java_block", "bedrock", "bedrock_old", "free").contains(modelFormat)) {
            diagnostics.add(unsupported("blockbench.model-format",
                    "Model format '" + modelFormat + "' is not a Java block, Bedrock"
                            + " geometry or generic cube format; cube/outliner data is"
                            + " imported only where it is unambiguous"));
        }

        int textureWidth = 16;
        int textureHeight = 16;
        boolean resolutionDeclared = false;
        JsonObject resolution = object(root, "resolution");
        if (resolution != null) {
            textureWidth = integer(resolution, "width", 16, "resolution.width");
            textureHeight = integer(resolution, "height", 16, "resolution.height");
            resolutionDeclared = true;
            validateTextureDimension(textureWidth, "resolution.width");
            validateTextureDimension(textureHeight, "resolution.height");
        }

        String name = string(root, "name");
        if (name == null || name.isBlank()) {
            name = id.value();
        }

        List<Texture> textures = parseTextures(root, textureWidth, textureHeight, diagnostics);
        if (!resolutionDeclared && !textures.isEmpty()) {
            textureWidth = textures.getFirst().uvWidth();
            textureHeight = textures.getFirst().uvHeight();
        }

        Map<String, Texture> texturesById = new LinkedHashMap<>();
        for (Texture texture : textures) {
            texturesById.put(texture.id(), texture);
        }

        List<Cube> elements = parseElements(root, textures, texturesById, diagnostics);
        Map<String, Cube> elementsById = new LinkedHashMap<>();
        for (Cube cube : elements) {
            elementsById.put(cube.id(), cube);
        }

        List<Bone> bones = parseHierarchy(root, elementsById, diagnostics, version);
        List<Animation> animations = parseAnimations(root, diagnostics);
        List<Controller> controllers = parseControllers(root, diagnostics);
        Map<String, DisplayTransform> display = parseDisplay(root, diagnostics);

        if (hasNonEmptyArray(root, "meshes")) {
            diagnostics.add(unsupported("blockbench.mesh-elements",
                    "Mesh elements are not convertible to Java/Bedrock cube geometry;"
                            + " they were not imported"));
        }
        if (hasNonEmptyArray(root, "texture_meshes")) {
            diagnostics.add(unsupported("blockbench.texture-meshes",
                    "Texture meshes are not convertible to Java/Bedrock cube geometry;"
                            + " they were not imported"));
        }
        if (hasNonEmptyArray(root, "collections")) {
            diagnostics.add(warning("blockbench.collections",
                    "Blockbench collections are editor organization and were not imported"));
        }

        return new ModelIR(id, name, formatVersion, modelFormat, textureWidth, textureHeight,
                textures, bones, animations, controllers, display, diagnostics);
    }

    // --------------------------------------------------------------------- textures

    private static List<Texture> parseTextures(JsonObject root, int textureWidth,
                                               int textureHeight,
                                               List<Diagnostic> diagnostics)
            throws ModelImportException {
        JsonArray array = array(root, "textures");
        if (array == null) {
            return List.of();
        }
        if (array.size() > MAX_TEXTURES) {
            throw new ModelImportException("Model declares " + array.size()
                    + " textures; maximum is " + MAX_TEXTURES);
        }

        List<Texture> textures = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int index = 0; index < array.size(); index++) {
            JsonElement entry = array.get(index);
            if (!entry.isJsonObject()) {
                throw new ModelImportException("textures[" + index + "] must be an object");
            }
            JsonObject texture = entry.getAsJsonObject();
            String uuid = string(texture, "uuid");
            String id = firstNonBlank(string(texture, "id"), uuid,
                    string(texture, "name"), Integer.toString(index));
            if (!ids.add(id)) {
                throw new ModelImportException("Duplicate texture id '" + id + "'");
            }
            String name = firstNonBlank(string(texture, "name"), id);
            if (!names.add(name)) {
                diagnostics.add(warning("blockbench.duplicate-texture-name",
                        "Duplicate texture name '" + name + "'; texture ids are still unique"));
            }

            int uvWidth = integer(texture, "uv_width", textureWidth, "texture.uv_width");
            int uvHeight = integer(texture, "uv_height", textureHeight, "texture.uv_height");
            validateTextureDimension(uvWidth, "texture.uv_width");
            validateTextureDimension(uvHeight, "texture.uv_height");
            if (uvWidth != textureWidth || uvHeight != textureHeight) {
                diagnostics.add(unsupported("texture.uv-space-mismatch",
                        "Texture '" + name + "' uses a " + uvWidth + "x" + uvHeight
                                + " UV space while the model uses " + textureWidth + "x"
                                + textureHeight + "; static Java/Bedrock models share one"
                                + " UV space, so this texture cannot be compiled faithfully"));
            }

            String relativePath = string(texture, "relative_path");
            String absolutePath = string(texture, "path");
            String sourcePath = null;
            if (relativePath != null && !relativePath.isBlank()) {
                sourcePath = safeRelativePath(relativePath, "texture.relative_path");
            } else if (absolutePath != null && !absolutePath.isBlank()) {
                if (looksAbsolute(absolutePath) || hasTraversal(absolutePath)) {
                    diagnostics.add(warning("texture.absolute-path",
                            "Texture '" + name + "' only has an absolute host path; it is"
                                    + " not read. Add a textures override in the item/block"
                                    + " definition or embed the texture in the model"));
                } else {
                    sourcePath = safeRelativePath(absolutePath, "texture.path");
                }
            }

            String embedded = string(texture, "source");
            byte[] embeddedPng = null;
            if (embedded != null && !embedded.isBlank()) {
                embeddedPng = decodeEmbedded(embedded, name);
            }

            int width = integer(texture, "width", uvWidth, "texture.width");
            int height = integer(texture, "height", uvHeight, "texture.height");
            if (embeddedPng != null) {
                int[] pngSize = pngSize(embeddedPng);
                if (pngSize != null) {
                    if (!texture.has("width")) {
                        width = pngSize[0];
                    }
                    if (!texture.has("height")) {
                        height = pngSize[1];
                    }
                }
            }
            validateTextureDimension(width, "texture.width");
            validateTextureDimension(height, "texture.height");

            String renderMode = firstNonBlank(string(texture, "render_mode"), "default");
            if (!"default".equals(renderMode)) {
                diagnostics.add(unsupported("texture.render-mode",
                        "Texture '" + name + "' uses render_mode '" + renderMode
                                + "'; Java/Bedrock static models only consume the base albedo"));
            }
            if (texture.has("layers") && hasNonEmptyArray(texture, "layers")) {
                diagnostics.add(unsupported("texture.layers",
                        "Texture '" + name + "' uses layered textures; only the base layer"
                                + " can be compiled"));
            }

            textures.add(new Texture(id, uuid, name, sourcePath, width, height, uvWidth,
                    uvHeight, bool(texture, "particle", false), renderMode, embeddedPng));
        }
        return List.copyOf(textures);
    }

    private static byte[] decodeEmbedded(String source, String textureName)
            throws ModelImportException {
        String prefix = "data:image/png;base64,";
        if (!source.regionMatches(true, 0, prefix, 0, prefix.length())) {
            throw new ModelImportException("Texture '" + textureName + "' embeds a non-PNG"
                    + " data URI; only PNG textures are supported");
        }
        String encoded = source.substring(prefix.length());
        long estimated = ((long) encoded.length() * 3) / 4;
        if (estimated > MAX_EMBEDDED_TEXTURE_BYTES) {
            throw new ModelImportException("Embedded texture '" + textureName + "' is "
                    + estimated + " bytes; maximum is " + MAX_EMBEDDED_TEXTURE_BYTES);
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length > MAX_EMBEDDED_TEXTURE_BYTES) {
                throw new ModelImportException("Embedded texture '" + textureName
                        + "' exceeds " + MAX_EMBEDDED_TEXTURE_BYTES + " bytes");
            }
            if (!isPng(decoded)) {
                throw new ModelImportException("Embedded texture '" + textureName
                        + "' is not a valid PNG");
            }
            return decoded;
        } catch (IllegalArgumentException invalid) {
            throw new ModelImportException("Embedded texture '" + textureName
                    + "' is not valid base64", invalid);
        }
    }

    // --------------------------------------------------------------------- elements

    private static List<Cube> parseElements(JsonObject root, List<Texture> textures,
                                            Map<String, Texture> texturesById,
                                            List<Diagnostic> diagnostics)
            throws ModelImportException {
        JsonArray array = array(root, "elements");
        if (array == null) {
            return List.of();
        }
        if (array.size() > MAX_ELEMENTS) {
            throw new ModelImportException("Model declares " + array.size()
                    + " elements; maximum is " + MAX_ELEMENTS);
        }

        List<Cube> elements = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < array.size(); index++) {
            JsonElement entry = array.get(index);
            if (!entry.isJsonObject()) {
                throw new ModelImportException("elements[" + index + "] must be an object");
            }
            JsonObject element = entry.getAsJsonObject();
            String type = firstNonBlank(string(element, "type"), "cube");
            if (!"cube".equalsIgnoreCase(type)) {
                diagnostics.add(unsupported("blockbench.element-type",
                        "Element '" + firstNonBlank(string(element, "name"),
                                "elements[" + index + "]") + "' has unsupported type '"
                                + type + "' and was not imported"));
                continue;
            }
            if (bool(element, "box_uv", false)) {
                throw new ModelImportException("Element '" + firstNonBlank(
                        string(element, "name"), "elements[" + index + "]")
                        + "' uses per-element Box UV, which is not supported yet");
            }

            String id = firstNonBlank(string(element, "uuid"), string(element, "id"));
            if (id == null) {
                id = "element_" + index;
                diagnostics.add(warning("blockbench.missing-uuid",
                        "elements[" + index + "] has no uuid; assigned '" + id + "'"));
            }
            if (!ids.add(id)) {
                throw new ModelImportException("Duplicate element id '" + id + "'");
            }
            String name = firstNonBlank(string(element, "name"), "cube_" + index);

            Vec3 from = requiredVec3(element, "from", name);
            Vec3 to = requiredVec3(element, "to", name);
            validateBounds(from, "from", name);
            validateBounds(to, "to", name);
            if (to.x() < from.x() || to.y() < from.y() || to.z() < from.z()) {
                throw new ModelImportException("Element '" + name + "' has inverted bounds:"
                        + " from " + from + " to " + to);
            }
            Vec3 pivot = optionalVec3(element, "origin", Vec3.ZERO, name);
            Vec3 rotation = optionalVec3(element, "rotation", Vec3.ZERO, name);
            validateRotation(rotation, name);

            double inflate = number(element, "inflate", 0.0, "element.inflate");
            if (!Double.isFinite(inflate) || inflate < -16.0 || inflate > 16.0) {
                throw new ModelImportException("Element '" + name + "' has invalid inflate "
                        + inflate + "; expected -16..16");
            }
            boolean visible = bool(element, "visibility", true);
            boolean export = bool(element, "export", true);
            if (bool(element, "mirror_uv", false)) {
                diagnostics.add(unsupported("blockbench.mirror-uv",
                        "Element '" + name + "' uses mirror_uv; mirrored UVs are not"
                                + " carried into the platform outputs"));
            }

            Map<String, Face> faces = parseFaces(element, name, textures, texturesById,
                    diagnostics);
            elements.add(new Cube(id, name, from, to, pivot, rotation, inflate,
                    visible, export, faces));
        }
        return List.copyOf(elements);
    }

    private static Map<String, Face> parseFaces(JsonObject element, String elementName,
                                                List<Texture> textures,
                                                Map<String, Texture> texturesById,
                                                List<Diagnostic> diagnostics)
            throws ModelImportException {
        JsonObject facesObject = object(element, "faces");
        if (facesObject == null) {
            return Map.of();
        }
        Map<String, Face> faces = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : facesObject.entrySet()) {
            String faceName = entry.getKey().toLowerCase(Locale.ROOT);
            if (!FACE_NAMES.contains(faceName)) {
                diagnostics.add(unsupported("blockbench.face-name",
                        "Element '" + elementName + "' has unknown face '" + entry.getKey()
                                + "'; it was not imported"));
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                throw new ModelImportException("Element '" + elementName + "' face '"
                        + faceName + "' must be an object");
            }
            JsonObject face = entry.getValue().getAsJsonObject();
            JsonArray uv = array(face, "uv");
            double u1;
            double v1;
            double u2;
            double v2;
            if (uv == null) {
                double[] inferred = defaultUv(faceName, element);
                u1 = inferred[0];
                v1 = inferred[1];
                u2 = inferred[2];
                v2 = inferred[3];
                diagnostics.add(warning("blockbench.inferred-uv",
                        "Element '" + elementName + "' face '" + faceName
                                + "' has no UV; Java's inferred face UVs were used"));
            } else {
                if (uv.size() != 4) {
                    throw new ModelImportException("Element '" + elementName + "' face '"
                            + faceName + "' UV must have 4 numbers");
                }
                u1 = finite(uv.get(0), "element.face.uv");
                v1 = finite(uv.get(1), "element.face.uv");
                u2 = finite(uv.get(2), "element.face.uv");
                v2 = finite(uv.get(3), "element.face.uv");
            }

            int rotation = integer(face, "rotation", 0, "element.face.rotation");
            if (rotation % 90 != 0 || rotation < 0 || rotation >= 360) {
                throw new ModelImportException("Element '" + elementName + "' face '"
                        + faceName + "' UV rotation must be 0, 90, 180 or 270; got "
                        + rotation);
            }

            String textureId = null;
            JsonElement texture = face.get("texture");
            if (texture != null && !texture.isJsonNull()) {
                if (!texture.isJsonPrimitive()) {
                    throw new ModelImportException("Element '" + elementName + "' face '"
                            + faceName + "' texture must be an id, name or index");
                }
                String reference = texture.getAsString();
                textureId = resolveTextureReference(reference, textures, texturesById,
                        elementName, faceName);
            }

            faces.put(faceName, new Face(u1, v1, u2, v2, rotation, textureId));
        }
        return Map.copyOf(faces);
    }

    private static String resolveTextureReference(String reference,
                                                  List<Texture> textures,
                                                  Map<String, Texture> texturesById,
                                                  String elementName, String faceName)
            throws ModelImportException {
        String value = reference.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (texturesById.containsKey(value)) {
            return value;
        }
        for (Texture texture : textures) {
            if (texture.name().equals(value) || value.equals(texture.uuid())) {
                return texture.id();
            }
        }
        try {
            int index = Integer.parseInt(value);
            if (index >= 0 && index < textures.size()) {
                return textures.get(index).id();
            }
            for (Texture texture : textures) {
                if (texture.id().equals(Integer.toString(index))) {
                    return texture.id();
                }
            }
        } catch (NumberFormatException ignored) {
            // Named reference, resolved above; fall through to the error below.
        }
        throw new ModelImportException("Element '" + elementName + "' face '" + faceName
                + "' references unknown texture '" + reference + "'");
    }

    // -------------------------------------------------------------------- hierarchy

    private static List<Bone> parseHierarchy(JsonObject root, Map<String, Cube> elements,
                                             List<Diagnostic> diagnostics, double version)
            throws ModelImportException {
        Map<String, JsonObject> groups = new LinkedHashMap<>();
        JsonArray groupArray = array(root, "groups");
        if (groupArray != null) {
            for (int index = 0; index < groupArray.size(); index++) {
                JsonElement entry = groupArray.get(index);
                if (!entry.isJsonObject()) {
                    throw new ModelImportException("groups[" + index + "] must be an object");
                }
                JsonObject group = entry.getAsJsonObject();
                String id = string(group, "uuid");
                if (id == null || id.isBlank()) {
                    throw new ModelImportException("groups[" + index + "] has no uuid");
                }
                if (groups.putIfAbsent(id, group) != null) {
                    throw new ModelImportException("Duplicate group id '" + id + "'");
                }
            }
        }

        HierarchyBuilder builder = new HierarchyBuilder(elements, groups, diagnostics, version);
        JsonArray outliner = array(root, "outliner");
        if (outliner != null) {
            for (JsonElement entry : outliner) {
                builder.process(entry, null);
            }
        }

        builder.attachOrphans();
        return builder.finish();
    }

    private static final class HierarchyBuilder {
        private final Map<String, Cube> elements;
        private final Map<String, JsonObject> groups;
        private final List<Diagnostic> diagnostics;
        private final double version;
        private final Map<String, MutableBone> bones = new LinkedHashMap<>();
        private final Map<String, String> elementParents = new LinkedHashMap<>();
        private final Set<String> visiting = new LinkedHashSet<>();
        private final Set<String> usedIds = new HashSet<>();
        private String rootId;

        private HierarchyBuilder(Map<String, Cube> elements, Map<String, JsonObject> groups,
                                 List<Diagnostic> diagnostics, double version) {
            this.elements = elements;
            this.groups = groups;
            this.diagnostics = diagnostics;
            this.version = version;
            this.usedIds.addAll(elements.keySet());
            for (String groupId : groups.keySet()) {
                this.usedIds.add(groupId);
            }
        }

        private void process(JsonElement entry, String parentId) throws ModelImportException {
            if (entry == null || entry.isJsonNull()) {
                return;
            }
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                reference(entry.getAsString(), parentId);
                return;
            }
            if (!entry.isJsonObject()) {
                throw new ModelImportException("Outliner entries must be element/group ids or groups");
            }
            JsonObject object = entry.getAsJsonObject();
            String id = string(object, "uuid");
            if (id == null || id.isBlank()) {
                throw new ModelImportException("Outliner group is missing its uuid");
            }
            processBone(id, object, parentId);
        }

        private void reference(String id, String parentId) throws ModelImportException {
            if (elements.containsKey(id)) {
                assignElement(id, parentId);
                return;
            }
            processBone(id, groups.get(id), parentId);
        }

        private void processBone(String id, @Nullable JsonObject inline, String parentId)
                throws ModelImportException {
            if (elements.containsKey(id)) {
                throw new ModelImportException("Group id '" + id
                        + "' collides with an element id");
            }
            if (bones.containsKey(id)) {
                throw new ModelImportException("Group '" + id + "' appears more than once"
                        + " in the outliner");
            }
            if (!visiting.add(id)) {
                throw new ModelImportException("Cycle in outliner hierarchy at group '" + id + "'");
            }
            // Format 5.0 stores group transforms in `groups` and only the hierarchy in
            // `outliner`. Pre-5.0 stores everything inline. Prefer the `groups` entry when
            // both exist, and take children from whichever object actually carries them.
            JsonObject groupData = groups.get(id);
            JsonObject data = groupData != null ? groupData : inline;
            if (data == null) {
                throw new ModelImportException("Outliner references unknown group '" + id + "'");
            }
            String name = firstNonBlank(string(data, "name"), id);
            Vec3 pivot = optionalVec3(data, "origin", Vec3.ZERO, name);
            Vec3 rotation = optionalVec3(data, "rotation", Vec3.ZERO, name);
            validateRotation(rotation, name);
            if (version < 4.5 && bool(data, "shade", true) == false) {
                diagnostics.add(unsupported("blockbench.legacy-mirror-uv",
                        "Group '" + name + "' uses the pre-4.5 shade flag; mirrored UVs are"
                                + " not carried into the platform outputs"));
            }
            if (bool(data, "mirror_uv", false)) {
                diagnostics.add(unsupported("blockbench.group-mirror-uv",
                        "Group '" + name + "' uses mirror_uv; mirrored UVs are not carried"
                                + " into the platform outputs"));
            }
            MutableBone bone = new MutableBone(id, name, parentId, pivot, rotation,
                    bool(data, "visibility", true), bool(data, "export", true));
            bones.put(id, bone);

            JsonArray children = array(data, "children");
            if (children == null && inline != null && inline != data) {
                children = array(inline, "children");
            }
            if (children != null) {
                for (JsonElement child : children) {
                    process(child, id);
                }
            }
            visiting.remove(id);
        }

        private void assignElement(String elementId, @Nullable String parentId)
                throws ModelImportException {
            if (elementParents.containsKey(elementId)) {
                throw new ModelImportException("Element '" + elementId
                        + "' appears under more than one outliner node");
            }
            if (parentId == null) {
                parentId = ensureRoot();
            }
            elementParents.put(elementId, parentId);
            bones.get(parentId).cubes.add(elementId);
        }

        private String ensureRoot() {
            if (rootId != null) {
                return rootId;
            }
            String id = "root";
            int suffix = 1;
            while (usedIds.contains(id)) {
                id = "root_" + suffix++;
            }
            usedIds.add(id);
            rootId = id;
            // Blockbench's synthetic main group sits at the block centre, not at the
            // world corner. A centred pivot matters for held-item scale/rotation.
            bones.put(id, new MutableBone(id, "root", null, new Vec3(8, 0, 8),
                    Vec3.ZERO, true, true));
            diagnostics.add(warning("blockbench.synthetic-root",
                    "Created a synthetic 'root' bone for elements outside any outliner group"));
            return id;
        }

        private void attachOrphans() {
            for (String elementId : elements.keySet()) {
                if (elementParents.containsKey(elementId)) {
                    continue;
                }
                String root = ensureRoot();
                elementParents.put(elementId, root);
                bones.get(root).cubes.add(elementId);
            }
        }

        private List<Bone> finish() {
            List<Bone> result = new ArrayList<>();
            for (MutableBone bone : bones.values()) {
                List<Cube> cubes = new ArrayList<>();
                for (String elementId : bone.cubes) {
                    Cube cube = elements.get(elementId);
                    if (cube != null) {
                        cubes.add(cube);
                    }
                }
                List<String> children = new ArrayList<>();
                for (MutableBone candidate : bones.values()) {
                    if (bone.id.equals(candidate.parentId)) {
                        children.add(candidate.id);
                    }
                }
                result.add(new Bone(bone.id, bone.name, bone.parentId, bone.pivot,
                        bone.rotation, bone.visible, bone.export, cubes, children));
            }
            return List.copyOf(result);
        }

        private record MutableBone(String id, String name, @Nullable String parentId,
                                   Vec3 pivot, Vec3 rotation, boolean visible,
                                   boolean export, List<String> cubes) {
            MutableBone(String id, String name, @Nullable String parentId, Vec3 pivot,
                        Vec3 rotation, boolean visible, boolean export) {
                this(id, name, parentId, pivot, rotation, visible, export, new ArrayList<>());
            }
        }
    }

    // ------------------------------------------------------------------- animations

    /**
     * Reads Bedrock animation controllers from either shape a project may use: an
     * object map (the exported Bedrock file) or an array of named controller objects.
     */
    private static List<Controller> parseControllers(JsonObject root,
                                                     List<Diagnostic> diagnostics)
            throws ModelImportException {
        JsonElement raw = root.get("animation_controllers");
        if (raw == null || raw.isJsonNull()) {
            return List.of();
        }
        Map<String, JsonObject> byName = new LinkedHashMap<>();
        if (raw.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : raw.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    byName.put(entry.getKey(), entry.getValue().getAsJsonObject());
                }
            }
        } else if (raw.isJsonArray()) {
            JsonArray array = raw.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                JsonElement entry = array.get(index);
                if (!entry.isJsonObject()) {
                    continue;
                }
                JsonObject object = entry.getAsJsonObject();
                String name = firstNonBlank(string(object, "name"), string(object, "id"),
                        "controller.animation.model_" + index);
                byName.put(name, object);
            }
        } else {
            diagnostics.add(warning("blockbench.animation-controller-shape",
                    "animation_controllers is neither an object nor an array and was skipped"));
            return List.of();
        }

        List<Controller> controllers = new ArrayList<>();
        for (Map.Entry<String, JsonObject> entry : byName.entrySet()) {
            String name = entry.getKey();
            JsonObject controller = entry.getValue();
            JsonObject statesObject = object(controller, "states");
            if (statesObject == null || statesObject.isEmpty()) {
                diagnostics.add(warning("blockbench.animation-controller-empty",
                        "Animation controller '" + name + "' has no states and was skipped"));
                continue;
            }
            List<String> stateNames = List.copyOf(statesObject.keySet());
            String initial = firstNonBlank(string(controller, "initial_state"),
                    stateNames.getFirst());
            if (!statesObject.has(initial)) {
                diagnostics.add(warning("blockbench.animation-controller-initial",
                        "Animation controller '" + name + "' names missing initial state '"
                                + initial + "'; using '" + stateNames.getFirst() + "'"));
                initial = stateNames.getFirst();
            }

            Map<String, State> states = new LinkedHashMap<>();
            for (String stateName : stateNames) {
                JsonElement stateElement = statesObject.get(stateName);
                if (!stateElement.isJsonObject()) {
                    continue;
                }
                JsonObject state = stateElement.getAsJsonObject();
                Map<String, ModelIR.Variable> variables = new LinkedHashMap<>();
                JsonObject variablesObject = state.has("variables")
                        ? object(state, "variables") : null;
                if (variablesObject != null) {
                    for (Map.Entry<String, JsonElement> variable
                            : variablesObject.entrySet()) {
                        if (!variable.getValue().isJsonObject()) {
                            continue;
                        }
                        JsonObject variableObject = variable.getValue().getAsJsonObject();
                        String input = string(variableObject, "input");
                        if (input == null) {
                            continue;
                        }
                        Map<Double, Double> curve = new LinkedHashMap<>();
                        JsonObject remap = object(variableObject, "remap_curve");
                        if (remap != null) {
                            for (Map.Entry<String, JsonElement> point : remap.entrySet()) {
                                try {
                                    curve.put(Double.parseDouble(point.getKey()),
                                            point.getValue().getAsDouble());
                                } catch (RuntimeException ignored) {
                                    // A malformed remap point is not worth dropping the
                                    // whole controller; the input still evaluates.
                                }
                            }
                        }
                        variables.put(variable.getKey(),
                                new ModelIR.Variable(input, curve));
                    }
                }
                List<AnimationRef> animations = new ArrayList<>();
                JsonArray animationArray = array(state, "animations");
                if (animationArray != null) {
                    for (JsonElement animation : animationArray) {
                        if (animation.isJsonPrimitive()) {
                            animations.add(new AnimationRef(animation.getAsString(), null));
                        } else if (animation.isJsonObject()) {
                            for (Map.Entry<String, JsonElement> blend
                                    : animation.getAsJsonObject().entrySet()) {
                                JsonElement value = blend.getValue();
                                animations.add(new AnimationRef(blend.getKey(),
                                        value.isJsonPrimitive()
                                                ? value.getAsString() : value.toString()));
                            }
                        }
                    }
                }
                List<Transition> transitions = new ArrayList<>();
                JsonElement transitionElement = state.get("transitions");
                if (transitionElement != null && transitionElement.isJsonArray()) {
                    for (JsonElement transition : transitionElement.getAsJsonArray()) {
                        if (transition.isJsonObject()) {
                            addTransitions(transition.getAsJsonObject(), statesObject,
                                    name, stateName, transitions, diagnostics);
                        }
                    }
                } else if (transitionElement != null && transitionElement.isJsonObject()) {
                    addTransitions(transitionElement.getAsJsonObject(), statesObject,
                            name, stateName, transitions, diagnostics);
                }
                Double blend = state.has("blend_transition")
                        && state.get("blend_transition").isJsonPrimitive()
                        ? state.get("blend_transition").getAsDouble() : null;
                states.put(stateName, new State(animations, transitions, blend,
                        variables));
            }
            if (!states.isEmpty()) {
                controllers.add(new Controller(name, initial, states));
            }
        }
        if (!controllers.isEmpty()) {
            diagnostics.add(new Diagnostic(Severity.INFO,
                    "blockbench.animation-controllers",
                    "Imported " + controllers.size() + " animation controller(s)"));
        }
        return List.copyOf(controllers);
    }

    private static void addTransitions(JsonObject object, JsonObject states, String controller,
                                       String stateName, List<Transition> transitions,
                                       List<Diagnostic> diagnostics) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!states.has(entry.getKey())) {
                diagnostics.add(warning("blockbench.controller-target",
                        "Animation controller '" + controller + "' state '" + stateName
                                + "' transitions to missing state '" + entry.getKey() + "'"));
                continue;
            }
            transitions.add(new Transition(entry.getKey(),
                    entry.getValue().isJsonPrimitive()
                            ? entry.getValue().getAsString() : entry.getValue().toString()));
        }
    }

    private static List<Animation> parseAnimations(JsonObject root,
                                                   List<Diagnostic> diagnostics)
            throws ModelImportException {
        JsonArray array = array(root, "animations");
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        if (array.size() > MAX_ANIMATIONS) {
            diagnostics.add(unsupported("blockbench.animation-count",
                    "Model declares " + array.size() + " animations; only the first "
                            + MAX_ANIMATIONS + " were read"));
        }

        List<Animation> animations = new ArrayList<>();
        int totalKeyframes = 0;
        boolean reportedRuntime = false;
        for (int index = 0; index < Math.min(array.size(), MAX_ANIMATIONS); index++) {
            JsonElement entry = array.get(index);
            if (!entry.isJsonObject()) {
                diagnostics.add(warning("blockbench.animation-shape",
                        "animations[" + index + "] is not an object and was skipped"));
                continue;
            }
            JsonObject animation = entry.getAsJsonObject();
            String name = firstNonBlank(string(animation, "name"), "animation_" + index);
            String id = firstNonBlank(string(animation, "uuid"), name);
            String loop = firstNonBlank(string(animation, "loop"), "once");
            double length = number(animation, "length", 1.0, "animation.length");
            if (!Double.isFinite(length) || length < 0) {
                diagnostics.add(warning("blockbench.animation-length",
                        "Animation '" + name + "' has invalid length; using 1.0"));
                length = 1.0;
            }

            List<Animator> animators = new ArrayList<>();
            JsonObject animatorObject = object(animation, "animators");
            if (animatorObject != null) {
                for (Map.Entry<String, JsonElement> animatorEntry : animatorObject.entrySet()) {
                    if (!animatorEntry.getValue().isJsonObject()) {
                        continue;
                    }
                    JsonObject animator = animatorEntry.getValue().getAsJsonObject();
                    String type = firstNonBlank(string(animator, "type"), "bone");
                    String animatorName = firstNonBlank(string(animator, "name"),
                            animatorEntry.getKey());
                    if (!"bone".equals(type)) {
                        diagnostics.add(unsupported("blockbench.animator-type",
                                "Animation '" + name + "' animates '" + animatorName
                                        + "' with unsupported type '" + type
                                        + "'; it is kept in the IR but not compiled"));
                    }
                    List<Keyframe> keyframes = new ArrayList<>();
                    JsonArray keyframeArray = array(animator, "keyframes");
                    if (keyframeArray != null) {
                        for (JsonElement keyframeEntry : keyframeArray) {
                            if (++totalKeyframes > MAX_KEYFRAMES) {
                                diagnostics.add(unsupported("blockbench.keyframe-count",
                                        "Model exceeds " + MAX_KEYFRAMES
                                                + " keyframes; later keyframes were skipped"));
                                break;
                            }
                            Keyframe keyframe = parseKeyframe(keyframeEntry, name,
                                    diagnostics);
                            if (keyframe != null) {
                                keyframes.add(keyframe);
                            }
                        }
                    }
                    animators.add(new Animator(animatorEntry.getKey(), animatorName, type,
                            keyframes));
                }
            }
            animations.add(new Animation(id, name, loop, length, animators));
        }

        if (!animations.isEmpty() && !reportedRuntime) {
            diagnostics.add(new Diagnostic(Severity.INFO, "blockbench.animation-runtime",
                    "Animations and keyframes are preserved in the model IR and compiled for"
                            + " held items, placed models and animated blocks. Bedrock"
                            + " animation controllers and Java non-loop/interaction playback"
                            + " are not generated."));
        }
        return List.copyOf(animations);
    }

    private static @Nullable Keyframe parseKeyframe(JsonElement entry, String animationName,
                                                    List<Diagnostic> diagnostics)
            throws ModelImportException {
        if (!entry.isJsonObject()) {
            diagnostics.add(warning("blockbench.keyframe-shape",
                    "Animation '" + animationName + "' contains a non-object keyframe"));
            return null;
        }
        JsonObject keyframe = entry.getAsJsonObject();
        String channel = firstNonBlank(string(keyframe, "channel"), "rotation")
                .toLowerCase(Locale.ROOT);
        if (!CHANNELS.contains(channel)) {
            diagnostics.add(unsupported("blockbench.keyframe-channel",
                    "Animation '" + animationName + "' uses unsupported keyframe channel '"
                            + channel + "'"));
            return null;
        }
        double time = number(keyframe, "time", 0.0, "keyframe.time");
        if (!Double.isFinite(time) || time < 0) {
            diagnostics.add(warning("blockbench.keyframe-time",
                    "Animation '" + animationName + "' has a negative/invalid keyframe time"));
            return null;
        }
        String interpolation = firstNonBlank(string(keyframe, "interpolation"), "linear");
        JsonArray points = array(keyframe, "data_points");
        if (points == null || points.isEmpty()) {
            diagnostics.add(warning("blockbench.keyframe-points",
                    "Animation '" + animationName + "' has a keyframe without data_points"));
            return null;
        }
        List<Vec3> values = new ArrayList<>();
        for (JsonElement point : points) {
            if (!point.isJsonObject()) {
                continue;
            }
            JsonObject value = point.getAsJsonObject();
            values.add(new Vec3(
                    number(value, "x", 0.0, "keyframe.x"),
                    number(value, "y", 0.0, "keyframe.y"),
                    number(value, "z", 0.0, "keyframe.z")));
        }
        if (values.isEmpty()) {
            return null;
        }
        BezierHandles bezier = new BezierHandles(
                vector(keyframe, "bezier_left_time", BezierHandles.DEFAULT.leftTime()),
                vector(keyframe, "bezier_left_value", BezierHandles.DEFAULT.leftValue()),
                vector(keyframe, "bezier_right_time", BezierHandles.DEFAULT.rightTime()),
                vector(keyframe, "bezier_right_value", BezierHandles.DEFAULT.rightValue()));
        return new Keyframe(time, channel, interpolation, values, bezier);
    }

    private static Vec3 vector(JsonObject object, String field, Vec3 fallback) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            if (array.size() < 3) {
                return fallback;
            }
            return new Vec3(
                    number(array.get(0), fallback.x()),
                    number(array.get(1), fallback.y()),
                    number(array.get(2), fallback.z()));
        }
        if (value.isJsonObject()) {
            JsonObject vector = value.getAsJsonObject();
            return new Vec3(
                    number(vector, "x", fallback.x(), field + ".x"),
                    number(vector, "y", fallback.y(), field + ".y"),
                    number(vector, "z", fallback.z(), field + ".z"));
        }
        return fallback;
    }

    // ---------------------------------------------------------------------- display

    private static Map<String, DisplayTransform> parseDisplay(JsonObject root,
                                                               List<Diagnostic> diagnostics)
            throws ModelImportException {
        JsonObject display = object(root, "display");
        if (display == null) {
            return Map.of();
        }
        Map<String, DisplayTransform> transforms = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : display.entrySet()) {
            String slot = entry.getKey();
            if (!DISPLAY_SLOTS.contains(slot)) {
                diagnostics.add(warning("blockbench.display-slot",
                        "Unknown display slot '" + slot + "' was not imported"));
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject transform = entry.getValue().getAsJsonObject();
            transforms.put(slot, new DisplayTransform(
                    optionalVec3(transform, "rotation", Vec3.ZERO, "display." + slot),
                    optionalVec3(transform, "translation", Vec3.ZERO, "display." + slot),
                    optionalVec3(transform, "scale", new Vec3(1, 1, 1),
                            "display." + slot)));
        }
        return Map.copyOf(transforms);
    }

    // ---------------------------------------------------------------------- helpers

    private static double parseVersion(String value) throws ModelImportException {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException invalid) {
            throw new ModelImportException("Invalid Blockbench format_version '" + value + "'");
        }
    }

    private static String safeRelativePath(String value, String field)
            throws ModelImportException {
        String normalized = value.replace('\\', '/').trim();
        if (normalized.isEmpty()) {
            throw new ModelImportException(field + " is empty");
        }
        if (normalized.indexOf('\0') >= 0 || looksAbsolute(normalized)) {
            throw new ModelImportException(field + " must be a relative path: " + value);
        }
        for (String segment : normalized.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new ModelImportException(field + " contains an unsafe path segment: "
                        + value);
            }
        }
        return normalized;
    }

    private static boolean looksAbsolute(String value) {
        return value.startsWith("/") || value.startsWith("\\")
                || value.matches("^[A-Za-z]:.*");
    }

    private static boolean hasTraversal(String value) {
        return value.replace('\\', '/').contains("../");
    }

    private static boolean isPng(byte[] data) {
        return data.length >= 8
                && (data[0] & 0xff) == 0x89 && data[1] == 'P' && data[2] == 'N'
                && data[3] == 'G' && data[4] == 0x0d && data[5] == 0x0a
                && data[6] == 0x1a && data[7] == 0x0a;
    }

    /** Reads the PNG IHDR dimensions without decoding the image. */
    private static int[] pngSize(byte[] data) {
        if (!isPng(data) || data.length < 24 || data[12] != 'I' || data[13] != 'H'
                || data[14] != 'D' || data[15] != 'R') {
            return null;
        }
        int width = ((data[16] & 0xff) << 24) | ((data[17] & 0xff) << 16)
                | ((data[18] & 0xff) << 8) | (data[19] & 0xff);
        int height = ((data[20] & 0xff) << 24) | ((data[21] & 0xff) << 16)
                | ((data[22] & 0xff) << 8) | (data[23] & 0xff);
        return width > 0 && height > 0 ? new int[]{width, height} : null;
    }

    private static void validateTextureDimension(int value, String field)
            throws ModelImportException {
        if (value < 1 || value > MAX_TEXTURE_DIMENSION) {
            throw new ModelImportException(field + " must be within 1.."
                    + MAX_TEXTURE_DIMENSION + "; got " + value);
        }
    }

    private static void validateBounds(Vec3 value, String field, String element)
            throws ModelImportException {
        for (double component : new double[]{value.x(), value.y(), value.z()}) {
            if (!Double.isFinite(component) || Math.abs(component) > MAX_COORDINATE) {
                throw new ModelImportException("Element '" + element + "' " + field
                        + " must be finite and within +/-" + MAX_COORDINATE + "; got "
                        + value);
            }
        }
    }

    private static void validateRotation(Vec3 rotation, String label)
            throws ModelImportException {
        for (double component : new double[]{rotation.x(), rotation.y(), rotation.z()}) {
            if (!Double.isFinite(component)) {
                throw new ModelImportException("'" + label + "' has a non-finite rotation");
            }
        }
    }

    private static Vec3 requiredVec3(JsonObject object, String field, String label)
            throws ModelImportException {
        JsonArray array = array(object, field);
        if (array == null || array.size() != 3) {
            throw new ModelImportException("Element '" + label + "' requires " + field
                    + " as three numbers");
        }
        return new Vec3(finite(array.get(0), "element." + field),
                finite(array.get(1), "element." + field),
                finite(array.get(2), "element." + field));
    }

    private static Vec3 optionalVec3(JsonObject object, String field, Vec3 fallback,
                                     String label) throws ModelImportException {
        JsonArray array = array(object, field);
        if (array == null) {
            return fallback;
        }
        if (array.size() != 3) {
            throw new ModelImportException("'" + label + "' field " + field
                    + " must have three numbers");
        }
        return new Vec3(finite(array.get(0), label + "." + field),
                finite(array.get(1), label + "." + field),
                finite(array.get(2), label + "." + field));
    }

    private static double[] defaultUv(String face, JsonObject element)
            throws ModelImportException {
        Vec3 from = requiredVec3(element, "from", "element");
        Vec3 to = requiredVec3(element, "to", "element");
        return switch (face) {
            case "down" -> new double[]{from.x(), 16 - to.z(), to.x(), 16 - from.z()};
            case "up" -> new double[]{from.x(), from.z(), to.x(), to.z()};
            case "north" -> new double[]{16 - to.x(), 16 - to.y(), 16 - from.x(),
                    16 - from.y()};
            case "south" -> new double[]{from.x(), 16 - to.y(), to.x(), 16 - from.y()};
            case "west" -> new double[]{from.z(), 16 - to.y(), to.z(), 16 - from.y()};
            case "east" -> new double[]{16 - to.z(), 16 - to.y(), 16 - from.z(),
                    16 - from.y()};
            default -> throw new ModelImportException("Unknown cube face '" + face + "'");
        };
    }

    private static double finite(JsonElement element, String field)
            throws ModelImportException {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new ModelImportException(field + " must be a number");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) {
            throw new ModelImportException(field + " must be finite");
        }
        return value;
    }

    private static @Nullable JsonObject object(JsonObject parent, String field)
            throws ModelImportException {
        JsonElement value = parent.get(field);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonObject()) {
            throw new ModelImportException("'" + field + "' must be an object");
        }
        return value.getAsJsonObject();
    }

    private static @Nullable JsonArray array(JsonObject parent, String field)
            throws ModelImportException {
        JsonElement value = parent.get(field);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonArray()) {
            throw new ModelImportException("'" + field + "' must be an array");
        }
        return value.getAsJsonArray();
    }

    private static boolean hasNonEmptyArray(@NotNull JsonObject object,
                                            @NotNull String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonArray() && !value.getAsJsonArray().isEmpty();
    }

    private static @Nullable String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isString()) {
            return primitive.getAsString();
        }
        if (primitive.isNumber() || primitive.isBoolean()) {
            return primitive.getAsString();
        }
        return null;
    }

    private static boolean bool(JsonObject object, String field, boolean fallback) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return value.getAsJsonPrimitive().isBoolean()
                    ? value.getAsBoolean()
                    : Boolean.parseBoolean(value.getAsString());
        } catch (RuntimeException invalid) {
            return fallback;
        }
    }

    private static int integer(JsonObject object, String field, int fallback, String label)
            throws ModelImportException {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new ModelImportException("'" + label + "' must be a whole number");
        }
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || number != Math.rint(number)
                || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new ModelImportException("'" + label + "' must be a whole number");
        }
        return (int) number;
    }

    private static double number(JsonObject object, String field, double fallback,
                                 String label) {
        return number(object.get(field), fallback);
    }

    private static double number(JsonElement value, double fallback) {
        if (value == null || value.isJsonNull()
                || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        double number = value.getAsDouble();
        return Double.isFinite(number) ? number : fallback;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Diagnostic warning(String code, String message) {
        return new Diagnostic(Severity.WARNING, code, message);
    }

    private static Diagnostic unsupported(String code, String message) {
        return new Diagnostic(Severity.UNSUPPORTED, code, message);
    }
}

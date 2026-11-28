package io.kalo.model.java;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.kalo.model.ModelIR;
import io.kalo.model.ModelIR.Bone;
import io.kalo.model.ModelIR.Cube;
import io.kalo.model.ModelIR.Diagnostic;
import io.kalo.model.ModelIR.DisplayTransform;
import io.kalo.model.ModelIR.Face;
import io.kalo.model.ModelIR.Severity;
import io.kalo.model.ModelIR.Vec3;
import io.kalo.model.ModelTextureResolver;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compiles the shared model IR to a vanilla Java {@code models/*.json} file.
 *
 * <p>Java item/block model JSON has no bones, so outliner groups are flattened. That is
 * safe only when a group carries no rotation of its own; a rotated bone is refused rather
 * than baked incorrectly. Everything Java can represent — cube bounds, a single-axis
 * element rotation, per-face UVs, texture size and display transforms — is preserved.</p>
 */
public final class JavaModelCompiler {

    private static final List<String> FACE_ORDER =
            List.of("down", "up", "north", "south", "west", "east");

    private JavaModelCompiler() {
    }

    /**
     * @param model         imported platform-neutral model
     * @param overrides     texture overrides from the item/block definition
     * @param namespace     namespace for embedded/path-derived textures
     * @param assetStem     deterministic stem for extracted embedded textures
     */
    public static @NotNull Result compile(@NotNull ModelIR model,
                                          @NotNull Map<String, Key> overrides,
                                          @NotNull String namespace,
                                          @NotNull String assetStem) {
        List<Diagnostic> diagnostics = new ArrayList<>(model.diagnostics());
        ModelTextureResolver.Result textures = ModelTextureResolver.resolve(model, overrides,
                namespace, assetStem);
        diagnostics.addAll(textures.diagnostics());

        if (!textures.complete()) {
            return failed(diagnostics, "java.textures",
                    "Java model cannot be compiled because one or more textures did not"
                            + " resolve");
        }
        if (textures.textures().isEmpty()) {
            return failed(diagnostics, "java.no-textures",
                    "Java model has no textures to bind");
        }

        JsonObject texturesJson = new JsonObject();
        for (ModelTextureResolver.ResolvedTexture texture : textures.textures().values()) {
            texturesJson.addProperty(texture.material(), texture.key().asString());
        }

        JsonArray elements = new JsonArray();
        boolean flattened = false;
        for (Bone bone : model.bones()) {
            if (!bone.visible() || !bone.export()) {
                diagnostics.add(new Diagnostic(Severity.INFO, "java.bone-hidden",
                        "Bone '" + bone.name() + "' is hidden/not exported; its cubes were"
                                + " omitted from the Java model"));
                continue;
            }
            if (!bone.rotation().equals(Vec3.ZERO)) {
                return failed(diagnostics, "java.bone-rotation",
                        "Bone '" + bone.name() + "' has rotation " + bone.rotation()
                                + "; Java item/block model JSON has no bone hierarchy and"
                                + " baking a rotated bone would change the model");
            }
            if (bone.parentId() != null) {
                flattened = true;
            }
            for (Cube cube : bone.cubes()) {
                if (!cube.visible() || !cube.export()) {
                    diagnostics.add(new Diagnostic(Severity.INFO, "java.cube-hidden",
                            "Cube '" + cube.name() + "' is hidden/not exported and was"
                                    + " omitted from the Java model"));
                    continue;
                }
                if (cube.inflate() != 0.0) {
                    return failed(diagnostics, "java.inflate",
                            "Cube '" + cube.name() + "' has inflate " + cube.inflate()
                                    + "; vanilla Java model JSON has no inflation field");
                }
                int rotatedAxes = cubeRotatedAxes(cube.rotation());
                if (rotatedAxes > 1) {
                    return failed(diagnostics, "java.cube-multi-axis-rotation",
                            "Cube '" + cube.name() + "' is rotated on more than one axis ("
                                    + cube.rotation() + "); Java model elements support only"
                                    + " a single axis");
                }
                elements.add(element(cube, textures));
            }
        }

        if (elements.isEmpty()) {
            return failed(diagnostics, "java.no-elements",
                    "Java model has no visible, exportable cubes");
        }
        if (flattened) {
            diagnostics.add(new Diagnostic(Severity.INFO, "java.flattened-hierarchy",
                    "Outliner parent relationships were flattened for Java model JSON;"
                            + " cubes keep their authored world positions and pivots"));
        }

        JsonObject root = new JsonObject();
        root.add("textures", texturesJson);
        if (model.textureWidth() != 16 || model.textureHeight() != 16) {
            JsonArray size = new JsonArray();
            size.add(model.textureWidth());
            size.add(model.textureHeight());
            root.add("texture_size", size);
        }
        root.add("elements", elements);
        if (!model.display().isEmpty()) {
            root.add("display", display(model.display()));
        }

        return new Result(root, textures.textures().values().stream().toList(),
                List.copyOf(diagnostics));
    }

    private static JsonObject element(Cube cube, ModelTextureResolver.Result textures) {
        JsonObject element = new JsonObject();
        element.add("from", vec(cube.from()));
        element.add("to", vec(cube.to()));

        if (!cube.rotation().equals(Vec3.ZERO)) {
            JsonObject rotation = new JsonObject();
            rotation.add("origin", vec(cube.pivot()));
            if (cube.rotation().x() != 0) {
                rotation.addProperty("axis", "x");
                rotation.addProperty("angle", clean(cube.rotation().x()));
            } else if (cube.rotation().y() != 0) {
                rotation.addProperty("axis", "y");
                rotation.addProperty("angle", clean(cube.rotation().y()));
            } else {
                rotation.addProperty("axis", "z");
                rotation.addProperty("angle", clean(cube.rotation().z()));
            }
            element.add("rotation", rotation);
        }

        JsonObject faces = new JsonObject();
        ModelTextureResolver.ResolvedTexture fallback = textures.first();
        for (String faceName : FACE_ORDER) {
            Face face = cube.faces().get(faceName);
            if (face == null) {
                continue;
            }
            ModelTextureResolver.ResolvedTexture resolved = textures.texture(face.textureId());
            if (resolved == null) {
                resolved = fallback;
            }
            if (resolved == null) {
                continue;
            }
            JsonObject faceJson = new JsonObject();
            JsonArray uv = new JsonArray();
            uv.add(clean(face.u1()));
            uv.add(clean(face.v1()));
            uv.add(clean(face.u2()));
            uv.add(clean(face.v2()));
            faceJson.add("uv", uv);
            faceJson.addProperty("texture", "#" + resolved.material());
            if (face.rotation() != 0) {
                faceJson.addProperty("rotation", face.rotation());
            }
            faces.add(faceName, faceJson);
        }
        element.add("faces", faces);
        return element;
    }

    private static JsonObject display(Map<String, DisplayTransform> transforms) {
        JsonObject display = new JsonObject();
        transforms.forEach((slot, transform) -> {
            JsonObject json = new JsonObject();
            if (!transform.rotation().equals(Vec3.ZERO)) {
                json.add("rotation", vec(transform.rotation()));
            }
            if (!transform.translation().equals(Vec3.ZERO)) {
                json.add("translation", vec(transform.translation()));
            }
            if (!transform.scale().equals(Vec3.ONE)) {
                json.add("scale", vec(transform.scale()));
            }
            if (!json.isEmpty()) {
                display.add(slot, json);
            }
        });
        return display;
    }

    private static int cubeRotatedAxes(Vec3 rotation) {
        int axes = 0;
        if (rotation.x() != 0) {
            axes++;
        }
        if (rotation.y() != 0) {
            axes++;
        }
        if (rotation.z() != 0) {
            axes++;
        }
        return axes;
    }

    private static JsonArray vec(Vec3 value) {
        JsonArray array = new JsonArray();
        array.add(clean(value.x()));
        array.add(clean(value.y()));
        array.add(clean(value.z()));
        return array;
    }

    /** Whole values serialize as integers; fractional values keep their exact value. */
    private static Number clean(double value) {
        return value == Math.rint(value)
                && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                ? (Number) (int) value : (Number) value;
    }

    private static Result failed(List<Diagnostic> diagnostics, String code, String message) {
        diagnostics.add(new Diagnostic(Severity.ERROR, code, message));
        return new Result(null, List.of(), List.copyOf(diagnostics));
    }

    public record Result(
            @Nullable JsonObject model,
            @NotNull List<ModelTextureResolver.ResolvedTexture> textures,
            @NotNull List<Diagnostic> diagnostics
    ) {
        public Result {
            textures = List.copyOf(textures);
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean compiled() {
            return model != null;
        }

        public @NotNull String errorSummary() {
            return diagnostics.stream()
                    .filter(diagnostic -> diagnostic.severity() == Severity.ERROR)
                    .map(Diagnostic::message)
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("unsupported model");
        }
    }
}

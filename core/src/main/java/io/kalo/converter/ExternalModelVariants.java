package io.kalo.converter;

import com.google.gson.*;
import io.kalo.content.item.definition.ItemDefinition;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.Writable;
import net.kyori.adventure.key.Key;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Fail-closed item-model selector conversion: no partial mappings for unsupported branches. */
public final class ExternalModelVariants {
    private static final int MAX_DEPTH = 12, MAX_BRANCHES = 64, MAX_JSON_BYTES = 2 * 1024 * 1024;
    private ExternalModelVariants() {}
    public record Variant(ItemDefinition definition, Key javaModel, List<ExternalModelPredicate> predicates) {
        public Variant { predicates = List.copyOf(predicates); }
    }
    private record Leaf(Key model, List<ExternalModelPredicate> predicates) {}
    private record Threshold(double value, JsonObject model) {}

    public static List<Variant> resolve(ResourcePack pack, ExternalItemBindings.Binding binding) {
        return resolve(pack, binding, false);
    }

    /**
     * For auto-discovered vanilla carriers, unresolved *vanilla fallback* assets
     * are expected: vanilla textures need not be copied into a Java resource pack.
     * Translate only the authored custom models that are really present. Failure
     * to translate one branch never manufactures a replacement for the vanilla item.
     */
    public static List<Variant> resolveAuto(ResourcePack pack, ExternalItemBindings.Binding binding) {
        return resolve(pack, binding, true);
    }

    private static List<Variant> resolve(ResourcePack pack,
            ExternalItemBindings.Binding binding, boolean skipUnresolved) {
        Key model = binding.itemModel();
        JsonObject root = read(pack, "assets/" + model.namespace() + "/items/" + model.value() + ".json");
        if (root == null) throw new IllegalArgumentException("Missing item model " + model);
        List<Leaf> leaves = new ArrayList<>();
        visit(object(root, "model"), List.of(), leaves, 0, skipUnresolved);
        if (leaves.isEmpty()) throw new IllegalArgumentException("No renderable models");
        boolean plain = leaves.size() == 1 && leaves.getFirst().predicates().isEmpty();
        List<Variant> result = new ArrayList<>();
        Set<String> fingerprints = new HashSet<>();
        for (int i = 0; i < leaves.size(); i++) {
            Leaf leaf = leaves.get(i);
            String signature = leaf.predicates().stream().map(p -> p.toJson().toString())
                    .sorted().reduce("", (a,b) -> a + "|" + b);
            if (!fingerprints.add(signature)) throw new IllegalArgumentException("Duplicate predicates");
            Key output = plain ? model : Key.key("kalo_external",
                    digest(model.asString() + "|" + binding.material()) + "_" + i);
            ItemDefinition definition = ExternalItemModelResolver.resolveModel(pack, binding, output, leaf.model());
            if (definition == null) {
                if (skipUnresolved) continue;
                throw new IllegalArgumentException("Unresolved Java item leaf " + leaf.model());
            }
            // An unqualified automatic fallback could override the stock item
            // for every Bedrock player; preserve the actual vanilla renderer.
            if (skipUnresolved && leaf.predicates().isEmpty()) continue;
            result.add(new Variant(definition, model, leaf.predicates()));
        }
        return List.copyOf(result);
    }

    private static void visit(JsonObject node, List<ExternalModelPredicate> predicates,
                              List<Leaf> leaves, int depth, boolean auto) {
        if (depth >= MAX_DEPTH || leaves.size() > MAX_BRANCHES) throw new IllegalArgumentException("Too many selector branches");
        String kind = normalized(str(node, "type"));
        switch (kind) {
            case "model" -> {
                String path = str(node, "model");
                if (path == null) throw new IllegalArgumentException("No model path");
                leaves.add(new Leaf(Key.key(path), predicates));
            }
            case "empty" -> { }
            case "condition" -> {
                String property = normalized(str(node, "property"));
                if (auto && property.equals("using_item")) {
                    // Geyser v2 has no using_item predicate. For auto-discovered
                    // vanilla carriers only, retain the idle appearance rather
                    // than dropping all custom models (bows/shields). This does
                    // NOT claim to track bow draw or shield blocking animation.
                    visit(object(node, "on_false"), predicates, leaves, depth + 1, true);
                } else {
                    if (!Set.of("broken", "damaged", "custom_model_data", "has_component", "fishing_rod_cast")
                            .contains(property)) throw new IllegalArgumentException("Unsupported condition " + property);
                    String component = property.equals("has_component") ? str(node, "component") : null;
                    if (property.equals("has_component") && component == null) throw new IllegalArgumentException("Missing component");
                    ExternalModelPredicate test = new ExternalModelPredicate(
                            ExternalModelPredicate.Kind.CONDITION, property, component, null,
                            integer(node, "index", 0), true, false);
                    visit(object(node, "on_true"), plus(predicates, test), leaves, depth+1, auto);
                    visit(object(node, "on_false"), plus(predicates, test.negated()), leaves, depth+1, auto);
                }
            }
            case "range_dispatch" -> {
                String property = normalized(str(node, "property"));
                if (!Set.of("custom_model_data", "count", "damage", "bundle_fullness").contains(property)) {
                    throw new IllegalArgumentException("Unsupported range " + property);
                }
                int index = integer(node, "index", 0);
                boolean normalize = bool(node, "normalize", false);
                if (normalize && Set.of("custom_model_data","bundle_fullness").contains(property)) {
                    throw new IllegalArgumentException("Cannot normalize this range");
                }
                double scale = number(node, "scale", 1);
                if (!Double.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Invalid range scale");
                JsonArray entries = array(node, "entries");
                if (entries.isEmpty() || entries.size() > MAX_BRANCHES) throw new IllegalArgumentException("Empty/oversize range");
                List<Threshold> ordered = new ArrayList<>();
                for (JsonElement element : entries) {
                    if (!element.isJsonObject()) throw new IllegalArgumentException("Invalid range entry");
                    JsonObject entry = element.getAsJsonObject();
                    double threshold = number(entry, "threshold", Double.NaN) / scale;
                    if (!Double.isFinite(threshold)) throw new IllegalArgumentException("Invalid range threshold");
                    ordered.add(new Threshold(threshold, object(entry, "model")));
                }
                // Stable sort preserves authoring order for equal thresholds.
                // Java chooses the LAST matching entry, even if thresholds are
                // identical. Auto-discovered packs may contain duplicates (e.g.
                // Cyoria crossbow); retain only the last author-specified model.
                // Explicit operator mappings remain strict about ambiguity.
                ordered.sort(Comparator.comparingDouble(Threshold::value));
                for (int i=ordered.size()-2;i>=0;i--) {
                    if (Double.compare(ordered.get(i).value(), ordered.get(i+1).value()) == 0) {
                        if (auto) ordered.remove(i);
                        else throw new IllegalArgumentException("Duplicate threshold");
                    }
                }
                for (int i=0;i<ordered.size();i++) {
                    List<ExternalModelPredicate> path = plus(predicates, range(property, ordered.get(i).value(), index,normalize));
                    if (i+1 < ordered.size()) path = plus(path, range(property,ordered.get(i+1).value(),index,normalize).negated());
                    visit(ordered.get(i).model(), path, leaves, depth+1, auto);
                }
                if (node.has("fallback")) visit(object(node, "fallback"), plus(predicates,
                        range(property, ordered.getFirst().value(), index, normalize).negated()), leaves,depth+1,auto);
            }
            case "select" -> {
                String property = normalized(str(node, "property"));
                if (!Set.of("custom_model_data","charge_type","trim_material","context_dimension").contains(property))
                    throw new IllegalArgumentException("Unsupported select " + property);
                int index = integer(node,"index",0);
                List<ExternalModelPredicate> matches = new ArrayList<>();
                for (JsonElement element : array(node, "cases")) {
                    if (!element.isJsonObject()) throw new IllegalArgumentException("Invalid select case");
                    JsonObject entry = element.getAsJsonObject();
                    JsonElement values = entry.get("when");
                    if (values == null) throw new IllegalArgumentException("Missing case value");
                    List<JsonElement> entries = values.isJsonArray() ? values.getAsJsonArray().asList() : List.of(values);
                    for (JsonElement value : entries) {
                        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Unsupported case value");
                        ExternalModelPredicate match = new ExternalModelPredicate(
                                ExternalModelPredicate.Kind.MATCH, property, value.getAsString(),null,index,true,false);
                        if (matches.stream().anyMatch(x -> x.value().equals(match.value()))) throw new IllegalArgumentException("Duplicate case value");
                        matches.add(match);
                        visit(object(entry,"model"),plus(predicates,match),leaves,depth+1,auto);
                    }
                }
                if (matches.isEmpty()) throw new IllegalArgumentException("No cases");
                if (node.has("fallback")) {
                    List<ExternalModelPredicate> path = predicates;
                    for (var match : matches) path = plus(path, match.negated());
                    visit(object(node,"fallback"),path,leaves,depth+1,auto);
                }
            }
            default -> {
                // Auto-discovered Java items can mix Kalo-renderable custom
                // branches with Java-only fallbacks (notably shield's "special"
                // renderer). Never map an unsupported fallback to a fabricated
                // Bedrock item, but do not discard the supported branches either.
                if (!auto) throw new IllegalArgumentException("Unsupported selector " + kind);
            }
        }
        if (leaves.size() > MAX_BRANCHES) throw new IllegalArgumentException("Too many variants");
    }

    private static ExternalModelPredicate range(String property,double threshold,int index,boolean normalized) {
        return new ExternalModelPredicate(ExternalModelPredicate.Kind.RANGE,property,null,threshold,index,true,normalized);
    }
    private static List<ExternalModelPredicate> plus(List<ExternalModelPredicate> list,ExternalModelPredicate next) {
        if (list.size() > MAX_DEPTH*5) throw new IllegalArgumentException("Too many predicates");
        List<ExternalModelPredicate> result=new ArrayList<>(list);
        result.add(next);return List.copyOf(result);
    }
    private static JsonObject read(ResourcePack pack,String path) {
        Writable writable=pack.file(path);
        if (writable==null) return null;
        try {
            byte[] b=writable.toByteArray();
            if (b.length>MAX_JSON_BYTES)return null;
            JsonElement root=JsonParser.parseString(new String(b,StandardCharsets.UTF_8));
            return root.isJsonObject()?root.getAsJsonObject():null;
        } catch(Exception e){return null;}
    }
    private static JsonObject object(JsonObject o,String key) {
        if (!o.has(key)||!o.get(key).isJsonObject()) throw new IllegalArgumentException("Missing object "+key);
        return o.getAsJsonObject(key);
    }
    private static JsonArray array(JsonObject o,String key) {
        if (!o.has(key)||!o.get(key).isJsonArray()) throw new IllegalArgumentException("Missing array "+key);
        return o.getAsJsonArray(key);
    }
    private static String str(JsonObject o,String key) {
        JsonElement e=o.get(key);
        return e!=null&&e.isJsonPrimitive()&&e.getAsJsonPrimitive().isString()?e.getAsString():null;
    }
    private static String normalized(String s) {
        if (s==null)throw new IllegalArgumentException("Missing selector type/property");
        String key = s.startsWith("minecraft:") ? s.substring(10) : s;
        return switch (key) {
            case "fishing_rod/cast" -> "fishing_rod_cast";
            case "bundle/fullness" -> "bundle_fullness";
            default -> key;
        };
    }
    private static int integer(JsonObject o,String k,int d){return o.has(k)?o.get(k).getAsInt():d;}
    private static double number(JsonObject o,String k,double d){return o.has(k)?o.get(k).getAsDouble():d;}
    private static boolean bool(JsonObject o,String k,boolean d){return o.has(k)?o.get(k).getAsBoolean():d;}
    private static String digest(String s){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(s.getBytes(StandardCharsets.UTF_8)),0,12);}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}

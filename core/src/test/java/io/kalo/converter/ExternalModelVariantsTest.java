package io.kalo.converter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import io.kalo.platform.bedrock.BedrockPackCompiler;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExternalModelVariantsTest {
    private static final String A = "{\"type\":\"minecraft:model\",\"model\":\"test:item/a\"}";
    private static final String B = "{\"type\":\"minecraft:model\",\"model\":\"test:item/b\"}";
    private static final String C = "{\"type\":\"minecraft:model\",\"model\":\"test:item/c\"}";

    private static ResourcePack pack(String selector) {
        ResourcePack source = new ResourcePackImpl(PackMeta.of(46, "selector test"));
        source.file("assets/test/items/example.json", Writable.string("{\"model\":" + selector + "}"));
        for (String name : List.of("a","b","c")) {
            source.file("assets/test/models/item/"+name+".json", Writable.string(
                    "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"test:item/"+name+"\"}}"));
            source.file("assets/test/textures/item/"+name+".png", Writable.bytes(new byte[]{1,2,3}));
        }
        return source;
    }
    private static ExternalItemBindings.Binding observed() {
        return new ExternalItemBindings.Binding(Key.key("test:example"), Material.PAPER, 16);
    }
    private static JsonObject mapping(BedrockPackCompiler.Result result, int index) throws Exception {
        JsonObject json = JsonParser.parseString(
                new String(result.itemMappings().toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
        return json.getAsJsonObject("items").getAsJsonArray("minecraft:paper")
                .get(index).getAsJsonObject();
    }

    @Test
    void conditionGeneratesTwoMutuallyExclusiveGeyserAndNativeRegistrations() throws Exception {
        ResourcePack source = pack("""
                {"type":"minecraft:condition","property":"minecraft:broken",
                 "on_true":%s,"on_false":%s}
                """.formatted(A,B));
        var variants = ExternalModelVariants.resolve(source, observed());
        assertEquals(2, variants.size());
        assertEquals("broken", variants.get(0).predicates().getFirst().property());
        assertTrue(variants.get(0).predicates().getFirst().expected());
        assertFalse(variants.get(1).predicates().getFirst().expected());
        assertNotEquals(variants.get(0).definition().key(), variants.get(1).definition().key());

        BedrockPackCompiler compiler = new BedrockPackCompiler(source,
                new ResourcePackImpl(PackMeta.of(0, "test")));
        assertTrue(compiler.addExternalVariant(variants.get(0)));
        assertTrue(compiler.addExternalVariant(variants.get(1)));
        var result = compiler.finish();
        assertEquals(2, result.itemCount());
        assertEquals("test:example", mapping(result,0).get("model").getAsString());
        assertEquals("test:example", mapping(result,1).get("model").getAsString());
        assertEquals(false, mapping(result,1).getAsJsonObject("predicate").get("expected").getAsBoolean());
        assertEquals(result.itemRegistrations().get(0).predicates(), variants.get(0).predicates());
        assertNotEquals(mapping(result,0).get("bedrock_identifier"),mapping(result,1).get("bedrock_identifier"));
    }

    @Test
    void rangeDispatchBuildsBoundedDisjointIntervalsAndFallback() throws Exception {
        ResourcePack source = pack("""
                {"type":"minecraft:range_dispatch","property":"minecraft:custom_model_data",
                 "index":0,"entries":[{"threshold":2,"model":%s},{"threshold":5,"model":%s}],
                 "fallback":%s}
                """.formatted(A,B,C));
        var variants = ExternalModelVariants.resolve(source, observed());
        assertEquals(3, variants.size());
        assertEquals(2, variants.get(0).predicates().size());
        assertEquals(2.0, variants.get(0).predicates().get(0).threshold());
        assertEquals(5.0, variants.get(0).predicates().get(1).threshold());
        assertFalse(variants.get(0).predicates().get(1).expected());
        assertEquals(1, variants.get(1).predicates().size());
        assertFalse(variants.get(2).predicates().getFirst().expected());
        BedrockPackCompiler compiler = new BedrockPackCompiler(source,
                new ResourcePackImpl(PackMeta.of(0, "test")));
        for (var variant : variants) assertTrue(compiler.addExternalVariant(variant));
        var result=compiler.finish();
        assertEquals(3,result.itemCount());
        assertEquals("and",mapping(result,0).get("predicate_strategy").getAsString());
        assertEquals(2,mapping(result,0).getAsJsonArray("predicate").size());
        assertEquals("range_dispatch",mapping(result,2).getAsJsonObject("predicate")
                .get("type").getAsString());
    }

    @Test
    void selectSupportsCustomModelDataAndExplicitNegativeFallback() {
        ResourcePack source = pack("""
                {"type":"minecraft:select","property":"minecraft:custom_model_data","index":1,
                 "cases":[{"when":["red","blue"],"model":%s}],"fallback":%s}
                """.formatted(A,C));
        var variants = ExternalModelVariants.resolve(source, observed());
        assertEquals(3, variants.size());
        assertEquals("red",variants.get(0).predicates().getFirst().value());
        assertEquals(1,variants.get(1).predicates().getFirst().index());
        assertEquals(2,variants.get(2).predicates().size());
        assertFalse(variants.get(2).predicates().getFirst().expected());
        assertFalse(variants.get(2).predicates().get(1).expected());
    }

    @Test
    void rejectsUnknownPropertiesMissingAssetsDuplicateThresholdsAndLargeExpansion() {
        assertThrows(IllegalArgumentException.class, () -> ExternalModelVariants.resolve(pack("""
                {"type":"minecraft:condition","property":"minecraft:foo",
                 "on_true":%s,"on_false":%s}
                """.formatted(A,B)), observed()));
        assertThrows(IllegalArgumentException.class, () -> ExternalModelVariants.resolve(pack("""
                {"type":"minecraft:range_dispatch","property":"minecraft:damage",
                 "entries":[{"threshold":1,"model":%s},{"threshold":1,"model":%s}]}
                """.formatted(A,B)), observed()));
        assertThrows(IllegalArgumentException.class, () -> ExternalModelVariants.resolve(pack("""
                {"type":"minecraft:condition","property":"minecraft:broken",
                 "on_true":{"type":"minecraft:model","model":"test:item/missing"},"on_false":%s}
                """.formatted(B)), observed()));
    }

    @Test
    void plainModelKeepsItsOriginalIdentityAndDoesNotAddPredicates() throws Exception {
        var source = pack(A);
        var variants = ExternalModelVariants.resolve(source, observed());
        assertEquals(1,variants.size());
        assertEquals(Key.key("test:example"), variants.getFirst().definition().key());
        assertTrue(variants.getFirst().predicates().isEmpty());
        BedrockPackCompiler compiler = new BedrockPackCompiler(source,
                new ResourcePackImpl(PackMeta.of(0, "test")));
        assertTrue(compiler.addExternalVariant(variants.getFirst()));
        var result=compiler.finish();
        assertFalse(mapping(result,0).has("predicate"));
    }
    @Test
    void autoMappedBowKeepsRealJavaIdleModelWithoutPretendingGeyserCanDetectDrawing() throws Exception {
        ResourcePack java = pack("""
                {"type":"minecraft:condition","property":"minecraft:using_item",
                 "on_true":{"type":"minecraft:range_dispatch","property":"minecraft:use_duration",
                            "entries":[{"threshold":1,"model":%s}]},
                 "on_false":{"type":"minecraft:range_dispatch","property":"minecraft:custom_model_data",
                            "entries":[{"threshold":18298,"model":%s}],
                            "fallback":{"type":"minecraft:model","model":"test:item/vanilla_not_in_pack"}}}
                """.formatted(B,A));
        assertThrows(IllegalArgumentException.class, () -> ExternalModelVariants.resolve(java, observed()));
        var variants = ExternalModelVariants.resolveAuto(java, observed());
        assertEquals(1,variants.size(),"Only the custom authored idle model can be mapped safely");
        assertInstanceOf(io.kalo.content.item.definition.ModelDefinition.Sprite.class,
                variants.getFirst().definition().model());
        assertEquals(1,variants.getFirst().predicates().size());
        assertEquals("custom_model_data",variants.getFirst().predicates().getFirst().property());
        assertEquals(18298d,variants.getFirst().predicates().getFirst().threshold());
        assertEquals("test:example", variants.getFirst().javaModel().asString());
        assertFalse(variants.getFirst().predicates().stream()
                .anyMatch(p -> p.property().equals("using_item") || p.property().equals("use_duration")));
    }

    @Test
    void autoMappedCrossbowDuplicateThresholdsUseLastAuthoredJavaModel() {
        ResourcePack java = pack("""
                {"type":"minecraft:range_dispatch","property":"minecraft:custom_model_data",
                 "entries":[{"threshold":24236,"model":%s},
                            {"threshold":24236,"model":%s},
                            {"threshold":24236,"model":%s}],
                 "fallback":{"type":"minecraft:model","model":"test:item/vanilla_not_in_pack"}}
                """.formatted(A,B,C));
        assertThrows(IllegalArgumentException.class, () -> ExternalModelVariants.resolve(java, observed()));
        var variants = ExternalModelVariants.resolveAuto(java,observed());
        assertEquals(1,variants.size(),"Java picks only the last entry at equal thresholds");
        var model = variants.getFirst().definition().model();
        assertInstanceOf(io.kalo.content.item.definition.ModelDefinition.Sprite.class,model);
        var sprite = (io.kalo.content.item.definition.ModelDefinition.Sprite)model;
        assertEquals("test:item/c",sprite.texture().asString());
        assertEquals(24236d,variants.getFirst().predicates().getFirst().threshold());
    }

    @Test
    void autoIdleBranchWithoutItemIdentityDoesNotOverrideVanilla() {
        var java = pack("""
                {"type":"minecraft:condition","property":"minecraft:using_item",
                 "on_true":%s,"on_false":%s}
                """.formatted(B,A));
        assertTrue(ExternalModelVariants.resolveAuto(java,observed()).isEmpty(),
                "Never install a catch-all vanilla replacement without a matching component");
    }

    @Test
    void autoMappedShieldRetainsCustomModelButSkipsUnsupportedVanillaSpecialFallback() {
        ResourcePack source = pack("""
                {"type":"minecraft:condition","property":"minecraft:using_item",
                 "on_true":{"type":"minecraft:special","base":"test:item/vanilla_shield"},
                 "on_false":{"type":"minecraft:range_dispatch","property":"minecraft:custom_model_data",
                             "entries":[{"threshold":20461,"model":%s}],
                             "fallback":{"type":"minecraft:special","base":"test:item/shield"}}}
                """.formatted(A));
        assertThrows(IllegalArgumentException.class, () -> ExternalModelVariants.resolve(source, observed()));
        var variants = ExternalModelVariants.resolveAuto(source, observed());
        assertEquals(1, variants.size());
        assertEquals(20461d, variants.getFirst().predicates().getFirst().threshold());
        assertEquals("test:item/a", ((io.kalo.content.item.definition.ModelDefinition.Sprite)
                variants.getFirst().definition().model()).texture().asString());
    }

    @Test
    void unknownAutoModelCannotCreateAccidentalVanillaCatchAllMapping() {
        ResourcePack source = pack("{\"type\":\"minecraft:special\",\"base\":\"test:item/unknown\"}");
        assertThrows(IllegalArgumentException.class, () -> ExternalModelVariants.resolveAuto(source, observed()));
    }

}

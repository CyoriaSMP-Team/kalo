package io.kalo.converter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kalo.pack.PackMeta;
import io.kalo.pack.ResourcePack;
import io.kalo.pack.ResourcePackImpl;
import io.kalo.pack.Writable;
import io.kalo.platform.bedrock.BedrockPackCompiler;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExternalLegacyItemsTest {
    @TempDir Path temp;

    private ResourcePack pack() {
        ResourcePack source=new ResourcePackImpl(PackMeta.of(46,"legacy test"));
        source.file("assets/minecraft/models/item/paper.json", Writable.string("""
                {"parent":"minecraft:item/generated","overrides":[
                 {"predicate":{"custom_model_data":5},"model":"legacy:item/five"},
                 {"predicate":{"custom_model_data":10},"model":"legacy:item/ten"}
                ]}
                """));
        for(String name:List.of("five","ten")) {
            source.file("assets/legacy/models/item/"+name+".json", Writable.string(
                    "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"legacy:item/"+name+"\"}}"));
            source.file("assets/legacy/textures/item/"+name+".png", Writable.bytes(new byte[]{1,2,3}));
        }
        return source;
    }

    @Test void persistsLegacyJavaMaterialAndCmd() throws IOException {
        Path path=temp.resolve("legacy-items.yml");
        var old=new ExternalLegacyItems.Binding(Material.PAPER,42,64);
        var updated=new ExternalLegacyItems.Binding(Material.PAPER,42,16);
        ExternalLegacyItems.remember(path,old);
        ExternalLegacyItems.remember(path,updated);
        assertEquals(List.of(updated),ExternalLegacyItems.load(path));
        assertThrows(IllegalArgumentException.class,()->
                new ExternalLegacyItems.Binding(Material.AIR,-1,64));
        Files.writeString(path,"items: not-a-list");
        assertThrows(IOException.class,()->ExternalLegacyItems.remember(path,old));
    }

    @Test void selectsHighestLegacyThresholdAndEmitsTrueLegacyGeyserMapping() throws Exception {
        ResourcePack source=pack();
        var observation=new ExternalLegacyItems.Binding(Material.PAPER,12,16);
        var legacy=ExternalLegacyItems.resolve(source,observation);
        assertNotNull(legacy);
        assertEquals(12,legacy.customModelData());
        assertInstanceOf(io.kalo.content.item.definition.ModelDefinition.Sprite.class,
                legacy.definition().model());
        var compiler=new BedrockPackCompiler(source,new ResourcePackImpl(PackMeta.of(0,"test")));
        assertTrue(compiler.addLegacyExternalItem(legacy));
        assertFalse(compiler.addLegacyExternalItem(legacy));
        var result=compiler.finish();
        assertEquals(1,result.itemCount());
        var mapping=JsonParser.parseString(new String(
                result.itemMappings().toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonObject("items").getAsJsonArray("minecraft:paper")
                .get(0).getAsJsonObject();
        assertEquals("legacy",mapping.get("type").getAsString());
        assertEquals(12,mapping.get("custom_model_data").getAsInt());
        assertFalse(mapping.has("model"));
        assertEquals(12,result.itemRegistrations().getFirst().legacyCustomModelData());
        assertEquals("minecraft:paper",result.itemRegistrations().getFirst().javaItem());
    }

    @Test void rejectsUnknownOrAmbiguousOverrides() {
        var source=pack();
        assertNull(ExternalLegacyItems.resolve(source,
                new ExternalLegacyItems.Binding(Material.PAPER,3,64)));
        source.file("assets/minecraft/models/item/paper.json",Writable.string("""
                {"overrides":[{"predicate":{"custom_model_data":1,"damage":0.5},
                "model":"legacy:item/ten"}]}"""));
        assertNull(ExternalLegacyItems.resolve(source,
                new ExternalLegacyItems.Binding(Material.PAPER,12,64)));
    }
}

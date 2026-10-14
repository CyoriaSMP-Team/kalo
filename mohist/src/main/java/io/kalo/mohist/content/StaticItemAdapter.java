package io.kalo.mohist.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Set;

/** Convert a conservative single-sheet static Java cuboid item into Bedrock assets. */
public final class StaticItemAdapter {
    private StaticItemAdapter() {}
    public record Output(String texture,JsonObject geometry,JsonObject attachable) {}

    public static Output adapt(JsonObject javaModel,String bedrockId,String fileId) {
        JsonArray elements=javaModel.getAsJsonArray("elements");
        JsonObject textures=javaModel.getAsJsonObject("textures");
        if(elements==null||elements.isEmpty()||elements.size()>128||textures==null)return null;
        Set<String> sheets=new HashSet<>();
        try {
            for(JsonElement item:elements) {
                if(!item.isJsonObject())return null;
                JsonObject block=item.getAsJsonObject();
                JsonArray from=block.getAsJsonArray("from");
                JsonArray to=block.getAsJsonArray("to");
                if(from==null||to==null||from.size()!=3||to.size()!=3)return null;
                for(int i=0;i<3;i++){
                    double a=from.get(i).getAsDouble(),b=to.get(i).getAsDouble();
                    if(!Double.isFinite(a)||!Double.isFinite(b)||a>=b
                            ||a< -16||b>32)return null;
                }
                if(block.has("rotation")) {
                    if(!block.get("rotation").isJsonObject())return null;
                    JsonObject rotation=block.getAsJsonObject("rotation");
                    JsonArray pivot=rotation.getAsJsonArray("origin");
                    if(pivot==null||pivot.size()!=3)return null;
                    for(JsonElement coordinate:pivot) {
                        if(!Double.isFinite(coordinate.getAsDouble()))return null;
                    }
                    String axis=rotation.get("axis").getAsString();
                    double angle=rotation.get("angle").getAsDouble();
                    if(!java.util.List.of("x","y","z").contains(axis)
                            ||!Double.isFinite(angle)||Math.abs(angle)>45)return null;
                }
                JsonObject faces=block.getAsJsonObject("faces");
                if(faces==null||faces.size()==0)return null;
                for(var face:faces.entrySet()) {
                    if(!face.getValue().isJsonObject())return null;
                    JsonObject description=face.getValue().getAsJsonObject();
                    JsonElement value=description.get("texture");
                    if(value==null||!value.isJsonPrimitive())return null;
                    String texture=value.getAsString();
                    int limit=0;
                    while(texture.startsWith("#")) {
                        if(++limit>16)return null;
                        value=textures.get(texture.substring(1));
                        if(value==null||!value.isJsonPrimitive())return null;
                        texture=value.getAsString();
                    }
                    sheets.add(texture);
                    if(sheets.size()!=1)return null;
                    if(description.has("rotation") && description.get("rotation").getAsInt()!=0)
                        return null;
                }
            }
            if(sheets.size()!=1)return null;
            String texture=sheets.iterator().next();
            if(!texture.matches("([a-z0-9_.-]+:)?[a-z0-9_./-]+")||texture.contains(".."))return null;
            String geoId="geometry.kalo_mohist."+fileId;
            JsonObject geometry=BedrockGeometry.convert(geoId,javaModel);
            if(geometry==null)return null;
            // Bedrock held-item geometry must bind its bone to the carried item slot.
            JsonObject bone=geometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                    .getAsJsonArray("bones").get(0).getAsJsonObject();
            bone.addProperty("binding","q.item_slot_to_bone_name(context.item_slot)");
            for(JsonElement cube:bone.getAsJsonArray("cubes")){
                JsonObject faces=cube.getAsJsonObject().getAsJsonObject("uv");
                if(faces==null)continue;
                for(var entry:faces.entrySet()) {
                    entry.getValue().getAsJsonObject().addProperty("material_instance","default");
                }
            }
            JsonObject materials=new JsonObject();materials.addProperty("default","entity_alphatest");
            JsonObject paths=new JsonObject();paths.addProperty("default","textures/items/"+fileId);
            JsonObject geo=new JsonObject();geo.addProperty("default",geoId);
            JsonObject description=new JsonObject();
            description.addProperty("identifier",bedrockId);
            description.add("materials",materials);
            description.add("textures",paths);
            description.add("geometry",geo);
            JsonArray controllers=new JsonArray();controllers.add("controller.render.item_default");
            description.add("render_controllers",controllers);
            JsonObject def=new JsonObject();def.add("description",description);
            JsonObject attachable=new JsonObject();
            attachable.addProperty("format_version","1.10.0");
            attachable.add("minecraft:attachable",def);
            return new Output(texture,geometry,attachable);
        } catch(RuntimeException unsupported) {return null;}
    }
}

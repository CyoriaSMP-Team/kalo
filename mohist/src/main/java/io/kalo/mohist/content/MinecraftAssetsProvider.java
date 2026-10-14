package io.kalo.mohist.content;

/** Generic namespace-aware layouts used by Java packs, Forge and Fabric mods. */
public final class MinecraftAssetsProvider implements ContentProvider {
    @Override public String id() { return "minecraft-standard-assets"; }

    @Override
    public Kind classify(String path) {
        if (path.equals("META-INF/mods.toml") || path.equals("fabric.mod.json")
                || path.equals("quilt.mod.json") || path.equals("mcmod.info"))
            return Kind.MOD_METADATA;
        if (path.startsWith("data/")) {
            if (path.contains("/recipes/") && path.endsWith(".json")) return Kind.RECIPE;
            if (path.contains("/loot_tables/") && path.endsWith(".json")) return Kind.LOOT_TABLE;
            return null;
        }
        if (!path.startsWith("assets/")) return null;
        String[] pieces = path.split("/",4);
        if (pieces.length < 3 || !pieces[1].matches("[a-z0-9_.-]+")) return null;
        String sub = path.substring(("assets/"+pieces[1]+"/").length());
        if (sub.startsWith("models/item/") && sub.endsWith(".json")) return Kind.ITEM_MODEL;
        if (sub.startsWith("models/block/") && sub.endsWith(".json")) return Kind.BLOCK_MODEL;
        if (sub.startsWith("blockstates/") && sub.endsWith(".json")) return Kind.BLOCK_STATE;
        if (sub.startsWith("textures/") && sub.endsWith(".png.mcmeta")) return Kind.ANIMATION_METADATA;
        if (sub.startsWith("textures/") && sub.endsWith(".png")) return Kind.TEXTURE;
        if ((sub.startsWith("animations/") || sub.startsWith("geo/"))
                && (sub.endsWith(".animation.json") || sub.endsWith(".json")))
            return sub.startsWith("geo/") ? Kind.GEOMETRY : Kind.ENTITY_ANIMATION;
        if (sub.startsWith("sounds/") && (sub.endsWith(".ogg") || sub.endsWith(".wav"))
                || sub.equals("sounds.json")) return Kind.SOUND;
        if (sub.startsWith("lang/") && (sub.endsWith(".json") || sub.endsWith(".lang")))
            return Kind.LANGUAGE;
        if (sub.startsWith("particles/") && sub.endsWith(".json")) return Kind.PARTICLE;
        if (sub.startsWith("font/") && sub.endsWith(".json")) return Kind.FONT;
        if (sub.startsWith("models/") && sub.endsWith(".json")) return Kind.ENTITY_MODEL;
        // Keep a record of opaque mod assets rather than discarding unknown layouts.
        return Kind.OTHER_ASSET;
    }
}

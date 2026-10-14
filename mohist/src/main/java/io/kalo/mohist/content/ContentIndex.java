package io.kalo.mohist.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.GsonBuilder;
import io.kalo.mohist.PackSources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Provider-independent read-only model of discovered installed content.
 * Used as a stable hand-off document, NOT a protocol translator or server
 * registry mutator. Third-party providers can implement ContentProvider.
 */
public final class ContentIndex {
    private static final int MAX_INDEXED_ASSETS = 40_000;
    private static final int MAX_ARCHIVE_ASSETS = 20_000;
    private static final int MAX_WARNINGS = 120;
    private static final long MAX_ARCHIVE_BYTES = 256L * 1024 * 1024;
    private static final long MAX_ASSET_BYTES = 32L * 1024 * 1024;
    private static final long MAX_SUM_EXPANDED = 512L * 1024 * 1024;
    private static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private ContentIndex() {}

    public record Asset(String source, String id, String path, ContentProvider.Kind kind, long bytes) {}
    public record RegistryAsset(ForgeRegistrySnapshot.Entry entry, boolean hasJavaModel,
                                boolean hasBlockState, boolean hasTexture) {}
    public record Result(List<Asset> assets, List<RegistryAsset> registryLinks,
                         List<String> warnings, List<String> providers, boolean complete) {
        public Result {
            assets=List.copyOf(assets);registryLinks=List.copyOf(registryLinks);
            warnings=List.copyOf(warnings);providers=List.copyOf(providers);
        }

        public Map<ContentProvider.Kind, Long> counts() {
            Map<ContentProvider.Kind,Long> result=new EnumMap<>(ContentProvider.Kind.class);
            for(Asset asset:assets) result.merge(asset.kind(),1L,Long::sum);
            return result;
        }

        public JsonObject json() {
            JsonObject root=new JsonObject();
            root.addProperty("schema","kalo.content-index/1");
            root.addProperty("complete",complete);
            root.addProperty("purpose","asset-and-registry-candidates-only");
            root.addProperty("mod_protocol_translation",false);
            root.addProperty("generated_automatically",true);
            JsonArray providersList=new JsonArray();
            providers.forEach(providersList::add);
            root.add("providers",providersList);
            JsonObject counts=new JsonObject();
            counts().forEach((key,num)->counts.addProperty(key.jsonName(),num));
            root.add("counts",counts);
            JsonArray sources=new JsonArray();
            Set<String> added=new HashSet<>();
            for(var asset:assets)if(added.add(asset.source())) sources.add(asset.source());
            root.add("sources",sources);
            JsonArray content=new JsonArray();
            for(Asset asset:assets) {
                JsonObject entry=new JsonObject();
                entry.addProperty("source",asset.source());
                entry.addProperty("id",asset.id());
                entry.addProperty("path",asset.path());
                entry.addProperty("type",asset.kind().jsonName());
                entry.addProperty("size",asset.bytes());
                content.add(entry);
            }
            root.add("assets",content);
            JsonArray registry=new JsonArray();
            for(var linked:registryLinks) {
                JsonObject entry=new JsonObject();
                entry.addProperty("registry",linked.entry().kind());
                entry.addProperty("id",linked.entry().id());
                entry.addProperty("candidate_item_model",linked.hasJavaModel());
                entry.addProperty("candidate_blockstate",linked.hasBlockState());
                entry.addProperty("candidate_texture",linked.hasTexture());
                registry.add(entry);
            }
            root.add("forge_registry",registry);
            JsonArray warningsList=new JsonArray();
            warnings.forEach(warningsList::add);
            root.add("warnings",warningsList);
            return root;
        }
    }

    public static Result build(Path serverRoot, PackSources.Report report,
                               ForgeRegistrySnapshot.Result registry, ClassLoader extensions)
            throws IOException {
        Path root=serverRoot.toRealPath();
        List<ContentProvider> providers=new ArrayList<>();
        providers.add(new MinecraftAssetsProvider());
        try {
            for (ContentProvider extension : ServiceLoader.load(ContentProvider.class,extensions)) {
                if (providers.stream().noneMatch(p->p.id().equals(extension.id()))) providers.add(extension);
            }
        } catch (java.util.ServiceConfigurationError invalid) {
            // Unavailable optional extension must not break the built-in catalog.
        }
        providers.sort(Comparator.comparing(ContentProvider::id));

        List<Asset> assets=new ArrayList<>();
        List<String> warnings=new ArrayList<>(report.warnings());
        warnings.addAll(registry.warnings());
        boolean complete=report.warnings().stream().noneMatch(w ->
                w.contains("Too many archives") || w.contains("too many"));

        List<PackSources.Source> sources=new ArrayList<>();
        sources.addAll(report.packs());
        sources.addAll(report.mods());
        sources.sort(Comparator.comparing(x->x.path().toString()));
        for(PackSources.Source source:sources) {
            if(assets.size()>=MAX_INDEXED_ASSETS) {
                warn(warnings,"Global indexed asset limit reached; catalog is incomplete");
                complete=false;break;
            }
            Path archive=source.path();
            if(Files.isSymbolicLink(archive) || !archive.toRealPath().startsWith(root)
                    || Files.size(archive)>MAX_ARCHIVE_BYTES) {
                warn(warnings,"Rejected unsafe archive "+archive.getFileName());
                complete=false;continue;
            }
            String name=root.relativize(archive.toRealPath()).toString().replace('\\','/');
            int perArchive=0;
            long expanded=0;
            try(ZipFile zip=new ZipFile(archive.toFile())) {
                Enumeration<? extends ZipEntry> all=zip.entries();
                while(all.hasMoreElements()) {
                    ZipEntry entry=all.nextElement();
                    if(entry.isDirectory())continue;
                    String path=entry.getName();
                    long size=entry.getSize();
                    if(!safePath(path)||size<0||size>MAX_ASSET_BYTES
                            ||expanded>MAX_SUM_EXPANDED-size) {
                        warn(warnings,"Unsupported or unsafe archive entry in "+name);
                        complete=false;break;
                    }
                    expanded+=size;
                    if(++perArchive>MAX_ARCHIVE_ASSETS) {
                        warn(warnings,"Too many archive files in "+name);
                        complete=false;break;
                    }
                    if(assets.size()>=MAX_INDEXED_ASSETS) {
                        warn(warnings,"Global indexed asset limit");
                        complete=false;break;
                    }
                    ContentProvider.Kind kind=null;
                    // Unknown providers are free to support custom layouts;
                    // standard generic provider is fallback.
                    for(ContentProvider provider:providers) {
                        ContentProvider.Kind found=provider.classify(path);
                        if(found!=null) {
                            kind=found;
                            if(found!=ContentProvider.Kind.OTHER_ASSET)break;
                        }
                    }
                    if(kind==null)continue;
                    String id=id(path);
                    assets.add(new Asset(name,id,path,kind,size));
                }
            } catch (RuntimeException bad) {
                warn(warnings,"Asset indexing failed for "+name+" ("+bad.getClass().getSimpleName()+")");
                complete=false;
            }
        }
        assets.sort(Comparator.comparing(Asset::source).thenComparing(Asset::path));
        Map<String,ContentProvider.Kind> identities=new HashMap<>();
        for(Asset asset:assets) identities.putIfAbsent(asset.path(),asset.kind());
        Set<String> textureIdentities=new HashSet<>();
        for(Asset asset:assets) {
            if(asset.kind()!=ContentProvider.Kind.TEXTURE)continue;
            String value=asset.id();
            textureIdentities.add(value);
            int colon=value.indexOf(':');
            if(colon<0)continue;
            for(String folder:List.of("item/","block/","entity/")) {
                if(value.substring(colon+1).startsWith(folder))
                    textureIdentities.add(value.substring(0,colon+1)+value.substring(colon+1+folder.length()));
            }
        }
        List<RegistryAsset> linked=new ArrayList<>();
        for(var entry:registry.entries()) {
            String[] segments=entry.id().split(":",2);
            if(segments.length!=2)continue;
            String ns=segments[0],local=segments[1];
            linked.add(new RegistryAsset(entry,
                    identities.containsKey("assets/"+ns+"/models/item/"+local+".json"),
                    identities.containsKey("assets/"+ns+"/blockstates/"+local+".json"),
                    textureIdentities.contains(entry.id())));
        }
        linked.sort(Comparator.comparing((RegistryAsset a)->a.entry().kind())
                .thenComparing(a->a.entry().id()));
        List<String> providerIds=providers.stream().map(ContentProvider::id).toList();
        return new Result(assets,linked,warnings,providerIds,complete);
    }

    private static String id(String path) {
        String[] parts=path.split("/",4);
        if(parts.length>=4&&(parts[0].equals("assets")||parts[0].equals("data"))) {
            String local=parts[3];
            int dot=local.lastIndexOf('.');
            if(dot>0)local=local.substring(0,dot);
            return parts[1]+":"+local;
        }
        return "unknown:"+path.replace('/', '_');
    }
    private static boolean safePath(String path) {
        if(path.startsWith("/")||path.contains("\\")||path.equals("..")
                ||path.startsWith("../")||path.contains("/../")||path.contains("/./")
                ||path.contains("//")) return false;
        return true;
    }
    private static void warn(List<String> warnings,String msg) {
        if(warnings.size()<MAX_WARNINGS)warnings.add(msg);
    }
    public static void write(Path path,Result index) throws IOException {
        if(!index.complete())throw new IOException("Incomplete content inventory cannot be published");
        Files.createDirectories(path.getParent());
        if(Files.isSymbolicLink(path))throw new IOException("Refusing symlink handoff path");
        Path staged=Files.createTempFile(path.getParent(),".kalo-index-",".tmp");
        try {
            Files.writeString(staged,GSON.toJson(index.json()),StandardCharsets.UTF_8);
            try {
                Files.move(staged,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            } catch(java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(staged,path,StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {Files.deleteIfExists(staged);}
    }
}

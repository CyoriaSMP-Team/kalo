package io.kalo.mohist.content;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reflective probe for Forge 1.20.1 identifiers, executed only on the Bukkit
 * server thread. Never instantiates, registers, or changes any Forge object.
 */
public final class ForgeRegistrySnapshot {
    private static final int MAX_IDS = 20_000;
    private ForgeRegistrySnapshot() {}

    public record Entry(String kind, String id) {}
    public record Result(List<Entry> entries, List<String> warnings) {
        public Result { entries = List.copyOf(entries); warnings = List.copyOf(warnings); }
    }

    public static Result probe(ClassLoader loader) {
        List<Entry> records = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            Class<?> forge = Class.forName("net.minecraftforge.registries.ForgeRegistries",false,loader);
            for (String fieldName : List.of("ITEMS","BLOCKS","ENTITY_TYPES","FLUIDS","SOUND_EVENTS")) {
                try {
                    Field field = forge.getField(fieldName);
                    Object registry = field.get(null);
                    Method keys = registry.getClass().getMethod("getKeys");
                    Object values = keys.invoke(registry);
                    if (!(values instanceof Collection<?> ids)) {
                        warnings.add(fieldName+" registry returned no collection");
                        continue;
                    }
                    if (ids.size() > MAX_IDS) {
                        warnings.add(fieldName+" registry has too many identifiers");
                        continue;
                    }
                    for (Object id : ids) {
                        String key=String.valueOf(id).toLowerCase(Locale.ROOT);
                        if (!key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                                || key.contains("..")) continue;
                        records.add(new Entry(fieldName.toLowerCase(Locale.ROOT),key));
                    }
                } catch (ReflectiveOperationException|RuntimeException failure) {
                    warnings.add(fieldName+": cannot access registry via Forge reflection ("
                            + failure.getClass().getSimpleName()+")");
                }
            }
        } catch (ClassNotFoundException absent) {
            warnings.add("ForgeRegistries is unavailable; asset indexing still works");
        } catch (LinkageError failure) {
            warnings.add("Forge API linkage unavailable: "+failure.getClass().getSimpleName());
        }
        records.sort(Comparator.comparing(Entry::kind).thenComparing(Entry::id));
        return new Result(records,warnings);
    }
}

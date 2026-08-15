package io.kalo.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import org.jetbrains.annotations.NotNull;

public final class Json {
    /**
     * Not pretty-printed: generated pack JSON is machine-written and machine-read, and
     * compact output keeps the pack smaller for every client that has to download it.
     */
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Json() {
    }

    public static @NotNull String write(@NotNull JsonElement element) {
        return GSON.toJson(element);
    }

    public static @NotNull Writable writable(@NotNull JsonElement element) {
        return Writable.string(write(element));
    }
}

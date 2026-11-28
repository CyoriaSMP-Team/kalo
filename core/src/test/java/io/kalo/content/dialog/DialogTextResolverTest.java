package io.kalo.content.dialog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogTextResolverTest {

    @Test
    void placeholdersResolveInEveryStringValueAndLeaveUnknownNames() {
        JsonObject source = JsonParser.parseString("""
                {
                  "title": "Hello {player}",
                  "body": [
                    {"type": "minecraft:plain_message", "contents": "For {target}"}
                  ],
                  "actions": [
                    {
                      "label": "Give {player} a sword",
                      "tooltip": "{target}'s kit",
                      "action": {"type": "minecraft:run_command", "command": "/give {player} sword"}
                    }
                  ]
                }
                """).getAsJsonObject();

        JsonObject resolved = (JsonObject) DialogTextResolver.resolve(source,
                Map.of("player", "Steve", "target", "Alex"));

        assertEquals("Hello Steve", resolved.get("title").getAsString());
        assertEquals("For Alex", resolved.getAsJsonArray("body").get(0)
                .getAsJsonObject().get("contents").getAsString());
        JsonObject action = resolved.getAsJsonArray("actions").get(0).getAsJsonObject();
        assertEquals("Give Steve a sword", action.get("label").getAsString());
        assertEquals("Alex's kit", action.get("tooltip").getAsString());
        assertEquals("/give Steve sword", action.getAsJsonObject("action")
                .get("command").getAsString());

        assertEquals("Hello {player}", source.get("title").getAsString(),
                "resolving must not mutate the generated data-pack JSON");
        assertEquals("{unknown}",
                DialogTextResolver.render("{unknown}", Map.of("player", "Steve")));
    }

    @Test
    void generatedDialogJsonCanBeResolvedForAnInlineOpen() {
        JsonObject source = JsonParser.parseString("""
                {"type":"minecraft:multi_action","title":"{player}","actions":[
                  {"label":"{target}","action":{"type":"minecraft:run_command","command":"/kalo click abc"}}
                ]}
                """).getAsJsonObject();

        JsonObject resolved = (JsonObject) DialogTextResolver.resolve(source,
                Map.of("player", "Steve", "target", "Alex"));

        assertFalse(resolved.toString().contains("{player}"));
        assertFalse(resolved.toString().contains("{target}"));
        assertTrue(resolved.toString().contains("Steve"));
        assertTrue(resolved.toString().contains("Alex"));
    }
}

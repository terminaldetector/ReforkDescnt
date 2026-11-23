package com.terminaldetector.drmd.world.profile;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldProfileTest {
    @Test
    void savesTheSelectedPresetAndSeed() {
        WorldProfile profile = new WorldProfile(WorldProfile.CURRENT_SCHEMA, WorldProfile.Mode.UNGRAVITY,
                WorldProfile.Preset.INFINITY_PSYCHEDELIC, WorldProfile.Scale.HUGE,
                WorldProfile.Performance.COMPACT, WorldProfile.Start.GLIDER_DOCK, Long.MIN_VALUE + 35);
        JsonObject encoded = encode(profile);

        assertEquals("INFINITY_PSYCHEDELIC", encoded.get("preset").getAsString());
        assertEquals(profile, WorldProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void vanillaProfileUsesStockScale() {
        WorldProfile profile = WorldProfile.vanilla(271026);
        JsonObject encoded = encode(profile);

        assertEquals("STOCK", encoded.get("scale").getAsString());
        assertEquals(profile, WorldProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void rejectsUnknownSchemasAndIncompatibleGeneratorSettings() {
        JsonObject encoded = encode(WorldProfile.vanilla(4));
        encoded.addProperty("schema", WorldProfile.CURRENT_SCHEMA + 1);
        assertTrue(WorldProfile.CODEC.parse(JsonOps.INSTANCE, encoded).error().isPresent());

        encoded.addProperty("schema", WorldProfile.CURRENT_SCHEMA);
        encoded.addProperty("scale", "HUGE");
        assertTrue(WorldProfile.CODEC.parse(JsonOps.INSTANCE, encoded).error().isPresent());

        encoded.addProperty("mode", "UNGRAVITY");
        encoded.addProperty("preset", "PSYCHEDELIC");
        assertTrue(WorldProfile.CODEC.parse(JsonOps.INSTANCE, encoded).error().isPresent());
    }

    private static JsonObject encode(WorldProfile profile) {
        return WorldProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile).getOrThrow().getAsJsonObject();
    }
}

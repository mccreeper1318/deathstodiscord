package com.pinnacle.deathstodiscord;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.math.BigDecimal;
import java.util.Optional;

final class DiscordJson {

    private DiscordJson() {
    }

    static String messagePayload(String content) {
        JsonObject payload = new JsonObject();
        payload.addProperty("content", content);
        return payload.toString();
    }

    static Optional<String> stringField(String json, String field) {
        return field(json, field)
                .filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsString)
                .filter(value -> !value.isBlank());
    }

    static Optional<BigDecimal> decimalField(String json, String field) {
        Optional<JsonElement> value = field(json, field);
        if (value.isEmpty() || !value.get().isJsonPrimitive()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new BigDecimal(value.get().getAsString()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<JsonElement> field(String json, String field) {
        if (json == null || json.isBlank() || field == null || field.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return Optional.empty();
            }

            JsonElement value = root.getAsJsonObject().get(field);
            return value == null || value.isJsonNull() ? Optional.empty() : Optional.of(value);
        } catch (JsonSyntaxException | IllegalStateException ignored) {
            return Optional.empty();
        }
    }
}

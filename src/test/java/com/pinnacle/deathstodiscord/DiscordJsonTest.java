package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordJsonTest {

    @Test
    void readsMessageIdRegardlessOfWhitespaceAndFieldOrder() {
        String response = "{ \"content\": \"ready\", \"id\" : \"123456789\" }";

        assertEquals("123456789", DiscordJson.stringField(response, "id").orElseThrow());
    }

    @Test
    void safelyRejectsMalformedJson() {
        assertTrue(DiscordJson.stringField("{not-json", "id").isEmpty());
    }

    @Test
    void payloadEscapesContentThroughJsonLibrary() {
        String payload = DiscordJson.messagePayload("line one\n\"quoted\"");

        assertEquals("line one\n\"quoted\"",
                DiscordJson.stringField(payload, "content").orElseThrow());
    }
}

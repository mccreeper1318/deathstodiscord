package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordHttpExceptionTest {

    @Test
    void identifiesDeletedDiscordMessagesByStatus() {
        assertTrue(new DiscordHttpException(404, "{\"message\":\"Unknown Message\"}").isMissingMessage());
        assertFalse(new DiscordHttpException(401, "{\"message\":\"Unauthorized\"}").isMissingMessage());
    }
}

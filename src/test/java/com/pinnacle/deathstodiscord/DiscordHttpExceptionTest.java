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

    @Test
    void retriesRequestTimeoutsAndServerFailuresOnly() {
        assertTrue(new DiscordHttpException(408, "").isRetryable());
        assertTrue(new DiscordHttpException(500, "").isRetryable());
        assertTrue(new DiscordHttpException(599, "").isRetryable());
        assertFalse(new DiscordHttpException(400, "").isRetryable());
        assertFalse(new DiscordHttpException(404, "").isRetryable());
    }
}

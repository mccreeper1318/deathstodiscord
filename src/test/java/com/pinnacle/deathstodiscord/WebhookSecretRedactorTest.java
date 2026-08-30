package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSecretRedactorTest {

    @Test
    void removesConfiguredWebhookUrlFromExceptionMessage() {
        String webhook = "https://discord.com/api/webhooks/123456/super-secret-token";
        RuntimeException error = new RuntimeException("Illegal character in URI: " + webhook);

        String sanitized = WebhookSecretRedactor.safeExceptionMessage(error, webhook);

        assertFalse(sanitized.contains("super-secret-token"));
        assertFalse(sanitized.contains(webhook));
        assertTrue(sanitized.contains("[REDACTED_WEBHOOK]"));
    }

    @Test
    void removesConfiguredWebhookWhenExceptionAddsQueryParameters() {
        String webhook = "https://discord.com/api/webhooks/123456/super-secret-token";
        RuntimeException error = new RuntimeException("Invalid URI " + webhook + "?wait=true");

        String sanitized = WebhookSecretRedactor.safeExceptionMessage(error, webhook);

        assertFalse(sanitized.contains("super-secret-token"));
        assertTrue(sanitized.contains("[REDACTED_WEBHOOK]?wait=true"));
    }

    @Test
    void redactsDiscordWebhookFoundOutsideConfiguredValue() {
        RuntimeException error = new RuntimeException(
                "Request failed for https://canary.discord.com/api/webhooks/999999/another-secret-token");

        String sanitized = WebhookSecretRedactor.safeExceptionMessage(error, "https://example.invalid/webhook");

        assertFalse(sanitized.contains("another-secret-token"));
        assertTrue(sanitized.contains("https://canary.discord.com/api/webhooks/999999/[REDACTED_WEBHOOK]"));
    }

    @Test
    void preservesUsefulNonSecretErrorDetails() {
        RuntimeException error = new RuntimeException("Discord HTTP 429 response: rate limited");

        String sanitized = WebhookSecretRedactor.safeExceptionMessage(error, "https://discord.com/api/webhooks/1/token");

        assertTrue(sanitized.contains("Discord HTTP 429"));
        assertTrue(sanitized.contains("rate limited"));
    }

    @Test
    void handlesMissingExceptionMessageSafely() {
        RuntimeException error = new RuntimeException();

        String sanitized = WebhookSecretRedactor.safeExceptionMessage(error, "https://discord.com/api/webhooks/1/token");

        assertTrue(sanitized.contains("Discord request failed"));
    }
}

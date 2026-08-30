package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DiscordMessageStateStoreTest {

    @Test
    void webhookFingerprintIsStableWithoutPersistingTheSecret() {
        String webhook = "https://discord.com/api/webhooks/123/super-secret-token";

        String first = WebhookIdentity.fingerprint(webhook);
        String second = WebhookIdentity.fingerprint(webhook);

        assertEquals(first, second);
        assertFalse(first.contains("super-secret-token"));
    }

    @Test
    void differentWebhooksHaveDifferentStateIdentities() {
        assertNotEquals(
                WebhookIdentity.fingerprint("https://discord.com/api/webhooks/1/one"),
                WebhookIdentity.fingerprint("https://discord.com/api/webhooks/2/two"));
    }
}

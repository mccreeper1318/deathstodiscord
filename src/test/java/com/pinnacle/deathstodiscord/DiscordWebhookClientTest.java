package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscordWebhookClientTest {

    @Test
    void cancelledCreationCleanupDeletesTheCreatedMessageThroughItsOriginalWebhook() {
        HttpRequest request = DiscordWebhookClient.buildDeleteRequest(
                "https://discord.com/api/webhooks/123/token?thread_id=456", "789");

        assertEquals("DELETE", request.method());
        assertEquals(
                "https://discord.com/api/webhooks/123/token/messages/789?thread_id=456",
                request.uri().toString());
    }
}

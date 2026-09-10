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

    @Test
    void messageRequestsNormalizeATrailingWebhookSlash() {
        HttpRequest request = DiscordWebhookClient.buildDeleteRequest(
                "https://discord.com/api/webhooks/123/token/?thread_id=456", "789");

        assertEquals(
                "https://discord.com/api/webhooks/123/token/messages/789?thread_id=456",
                request.uri().toString());
    }

    @Test
    void creationAddsWaitWhenTheWebhookHasNoQuery() {
        assertEquals(
                "https://discord.com/api/webhooks/123/token?wait=true",
                DiscordWebhookClient.createMessageUri(
                        "https://discord.com/api/webhooks/123/token").toString());
    }

    @Test
    void creationPreservesOtherQueryParametersAndReplacesWait() {
        assertEquals(
                "https://discord.com/api/webhooks/123/token?thread_id=456&wait=true",
                DiscordWebhookClient.createMessageUri(
                        "https://discord.com/api/webhooks/123/token?wait=false&thread_id=456&wait=false").toString());
    }
}

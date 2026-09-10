package com.pinnacle.deathstodiscord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class DiscordWebhookClient {

    private static final int DISCORD_MAX_CONTENT_LENGTH = 2000;

    private final HttpClient http;

    DiscordWebhookClient(HttpClient http) {
        this.http = http;
    }

    String createMessage(String webhookUrl, String content) throws Exception {
        HttpResponse<String> response = send(
                HttpRequest.newBuilder()
                        .uri(createMessageUri(webhookUrl))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                DiscordJson.messagePayload(hardLimit(content)), StandardCharsets.UTF_8))
                        .build());

        return DiscordJson.stringField(response.body(), "id")
                .orElseThrow(() -> new IllegalStateException("Discord response did not contain a message id."));
    }

    void patchMessage(String webhookUrl, String messageId, String content) throws Exception {
        String patchUrl = messageUrl(webhookUrl, messageId);
        send(HttpRequest.newBuilder()
                .uri(URI.create(patchUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(
                        DiscordJson.messagePayload(hardLimit(content)), StandardCharsets.UTF_8))
                .build());
    }

    void deleteMessage(String webhookUrl, String messageId) throws Exception {
        send(buildDeleteRequest(webhookUrl, messageId));
    }

    static HttpRequest buildDeleteRequest(String webhookUrl, String messageId) {
        return HttpRequest.newBuilder()
                .uri(URI.create(messageUrl(webhookUrl, messageId)))
                .timeout(Duration.ofSeconds(10))
                .DELETE()
                .build();
    }

    static URI createMessageUri(String webhookUrl) {
        return URI.create(withQueryParameter(webhookUrl, "wait", "true"));
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() == 429) {
            throw DiscordRateLimitException.fromResponse(response);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new DiscordHttpException(response.statusCode(), response.body());
        }
        return response;
    }

    private static String hardLimit(String content) {
        if (content.length() <= DISCORD_MAX_CONTENT_LENGTH) {
            return content;
        }
        return content.substring(0, DISCORD_MAX_CONTENT_LENGTH);
    }

    private static String withQueryParameter(String url, String name, String value) {
        int queryIndex = url.indexOf('?');
        String base = queryIndex < 0 ? url : url.substring(0, queryIndex);
        String query = queryIndex < 0 ? "" : url.substring(queryIndex + 1);

        StringBuilder updatedQuery = new StringBuilder();
        if (!query.isBlank()) {
            for (String parameter : query.split("&")) {
                if (parameter.isBlank()) {
                    continue;
                }
                int equalsIndex = parameter.indexOf('=');
                String parameterName = equalsIndex < 0 ? parameter : parameter.substring(0, equalsIndex);
                if (parameterName.equals(name)) {
                    continue;
                }
                if (!updatedQuery.isEmpty()) {
                    updatedQuery.append('&');
                }
                updatedQuery.append(parameter);
            }
        }

        if (!updatedQuery.isEmpty()) {
            updatedQuery.append('&');
        }
        updatedQuery.append(name).append('=').append(value);
        return base + "?" + updatedQuery;
    }

    private static String messageUrl(String webhookUrl, String messageId) {
        int queryIndex = webhookUrl.indexOf('?');
        String base = queryIndex < 0 ? webhookUrl : webhookUrl.substring(0, queryIndex);
        String query = queryIndex < 0 ? "" : webhookUrl.substring(queryIndex);
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/messages/" + messageId + query;
    }
}

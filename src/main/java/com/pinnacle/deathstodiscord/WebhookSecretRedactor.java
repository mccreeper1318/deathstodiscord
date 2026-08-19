package com.pinnacle.deathstodiscord;

import java.util.regex.Pattern;

/**
 * Removes Discord webhook secrets from messages before they are shown to an
 * administrator or written to the server log.
 */
final class WebhookSecretRedactor {

    private static final String REDACTED_WEBHOOK = "[REDACTED_WEBHOOK]";
    private static final Pattern DISCORD_WEBHOOK_PATTERN = Pattern.compile(
            "(?i)(https?://(?:(?:canary|ptb)\\.)?discord(?:app)?\\.com/api(?:/v\\d+)?/webhooks/\\d+/)[^\\s?&#]+"
    );

    private WebhookSecretRedactor() {
    }

    static String safeExceptionMessage(Throwable error, String configuredWebhookUrl) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.isBlank()) {
            return "Discord request failed without additional details.";
        }

        String sanitized = message;
        if (configuredWebhookUrl != null && !configuredWebhookUrl.isBlank()) {
            sanitized = sanitized.replace(configuredWebhookUrl, REDACTED_WEBHOOK);
        }

        sanitized = DISCORD_WEBHOOK_PATTERN.matcher(sanitized).replaceAll("$1" + REDACTED_WEBHOOK);
        return sanitized;
    }
}

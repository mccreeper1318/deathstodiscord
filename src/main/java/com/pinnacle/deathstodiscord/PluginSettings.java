package com.pinnacle.deathstodiscord;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

record PluginSettings(
        String webhookUrl,
        String objectiveName,
        String mode,
        int top,
        boolean showZeroDeaths,
        int updateDelaySeconds,
        int maxDiscordContentCharacters
) {

    private static final int MIN_DISCORD_CONTENT_LENGTH = 500;
    private static final int MAX_DISCORD_CONTENT_LENGTH = 2000;

    static LoadResult validate(String webhookUrl, String objectiveName, Object modeValue, Object topValue,
                               Object showZeroDeathsValue, Object updateDelayValue, Object contentLimitValue) {
        List<String> errors = new ArrayList<>();

        String normalizedWebhook = webhookUrl == null ? "" : webhookUrl.trim();
        if (isWebhookConfigured(normalizedWebhook) && !isValidHttpUri(normalizedWebhook)) {
            errors.add("webhook-url must be a valid HTTP or HTTPS URL.");
        }

        String normalizedObjective = objectiveName == null ? "" : objectiveName.trim();
        if (normalizedObjective.isBlank()) {
            errors.add("objective-name must not be blank.");
        }

        String mode = modeValue instanceof String value ? value.trim().toUpperCase(Locale.ROOT) : "";
        if (!mode.equals("ALL") && !mode.equals("TOP")) {
            errors.add("mode must be either ALL or TOP.");
        }

        Integer top = integerValue(topValue);
        if (top == null || top < 1) {
            errors.add("top must be an integer of at least 1.");
        }

        Boolean showZeroDeaths = booleanValue(showZeroDeathsValue);
        if (showZeroDeaths == null) {
            errors.add("show-zero-deaths must be true or false.");
        }

        Integer updateDelay = integerValue(updateDelayValue);
        if (updateDelay == null || updateDelay < 0) {
            errors.add("update-delay-seconds must be a non-negative integer.");
        }

        Integer contentLimit = integerValue(contentLimitValue);
        if (contentLimit == null || contentLimit < MIN_DISCORD_CONTENT_LENGTH
                || contentLimit > MAX_DISCORD_CONTENT_LENGTH) {
            errors.add("max-discord-content-characters must be an integer from 500 through 2000.");
        }

        if (!errors.isEmpty()) {
            return new LoadResult(null, List.copyOf(errors));
        }

        return new LoadResult(new PluginSettings(
                normalizedWebhook,
                normalizedObjective,
                mode,
                top,
                showZeroDeaths,
                updateDelay,
                contentLimit), List.of());
    }

    boolean webhookConfigured() {
        return isWebhookConfigured(webhookUrl);
    }

    long updateDelayTicks() {
        return updateDelaySeconds * 20L;
    }

    static boolean isWebhookConfigured(String webhookUrl) {
        return webhookUrl != null
                && !webhookUrl.isBlank()
                && !webhookUrl.contains("PASTE_WEBHOOK_URL_HERE");
    }

    private static boolean isValidHttpUri(String value) {
        try {
            URI uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            long longValue = number.longValue();
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE
                    && number.doubleValue() == longValue) {
                return (int) longValue;
            }
        }
        return null;
    }

    private static Boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : null;
    }

    record LoadResult(PluginSettings settings, List<String> errors) {
        boolean valid() {
            return settings != null;
        }
    }
}

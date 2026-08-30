package com.pinnacle.deathstodiscord;

final class DiscordHttpException extends Exception {

    private final int statusCode;

    DiscordHttpException(int statusCode, String responseBody) {
        super(buildMessage(statusCode, responseBody));
        this.statusCode = statusCode;
    }

    int statusCode() {
        return statusCode;
    }

    boolean isMissingMessage() {
        return statusCode == 404;
    }

    private static String buildMessage(int statusCode, String responseBody) {
        String discordMessage = DiscordJson.stringField(responseBody, "message").orElse("");
        if (discordMessage.isBlank()) {
            return "Discord HTTP " + statusCode + ".";
        }
        return "Discord HTTP " + statusCode + ": " + discordMessage;
    }
}

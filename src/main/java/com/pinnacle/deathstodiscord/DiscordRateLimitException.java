package com.pinnacle.deathstodiscord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpResponse;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Describes a temporary Discord rate limit without exposing response details.
 */
final class DiscordRateLimitException extends Exception {

    private static final long DEFAULT_RETRY_DELAY_TICKS = 20L;
    private static final long MAX_RETRY_DELAY_TICKS = Integer.MAX_VALUE;
    private static final BigDecimal TICKS_PER_SECOND = BigDecimal.valueOf(20L);
    private static final Pattern RETRY_AFTER_FIELD = Pattern.compile(
            "\"retry_after\"\\s*:\\s*\"?([+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:[eE][+-]?\\d+)?)\"?"
    );

    private final long retryDelayTicks;

    private DiscordRateLimitException(long retryDelayTicks) {
        super("Discord HTTP 429 rate limit.");
        this.retryDelayTicks = retryDelayTicks;
    }

    static DiscordRateLimitException fromResponse(HttpResponse<String> response) {
        String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
        String resetAfter = response.headers().firstValue("X-RateLimit-Reset-After").orElse(null);
        return new DiscordRateLimitException(retryDelayTicks(retryAfter, response.body(), resetAfter));
    }

    static long retryDelayTicks(String retryAfterHeader, String responseBody, String resetAfterHeader) {
        OptionalLong headerDelay = parseRetryDelay(retryAfterHeader);
        if (headerDelay.isPresent()) {
            return headerDelay.getAsLong();
        }

        if (responseBody != null) {
            Matcher matcher = RETRY_AFTER_FIELD.matcher(responseBody);
            if (matcher.find()) {
                OptionalLong bodyDelay = parseRetryDelay(matcher.group(1));
                if (bodyDelay.isPresent()) {
                    return bodyDelay.getAsLong();
                }
            }
        }

        OptionalLong resetDelay = parseRetryDelay(resetAfterHeader);
        return resetDelay.orElse(DEFAULT_RETRY_DELAY_TICKS);
    }

    long retryDelayTicks() {
        return retryDelayTicks;
    }

    private static OptionalLong parseRetryDelay(String value) {
        if (value == null || value.isBlank()) {
            return OptionalLong.empty();
        }

        try {
            BigDecimal seconds = new BigDecimal(value.trim());
            if (seconds.signum() < 0) {
                return OptionalLong.empty();
            }

            BigDecimal ticks = seconds.multiply(TICKS_PER_SECOND).setScale(0, RoundingMode.CEILING);
            if (ticks.compareTo(BigDecimal.valueOf(MAX_RETRY_DELAY_TICKS)) > 0) {
                return OptionalLong.of(MAX_RETRY_DELAY_TICKS);
            }

            return OptionalLong.of(Math.max(1L, ticks.longValueExact()));
        } catch (ArithmeticException | NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }
}

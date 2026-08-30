package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscordRateLimitExceptionTest {

    @Test
    void honorsRetryAfterHeaderBeforeOtherDiscordValues() {
        long delayTicks = DiscordRateLimitException.retryDelayTicks(
                "2.5", "{\"retry_after\":7.0}", "9.0");

        assertEquals(50L, delayTicks);
    }

    @Test
    void fallsBackToDiscordJsonRetryAfterWhenHeaderIsMissing() {
        long delayTicks = DiscordRateLimitException.retryDelayTicks(
                null, "{\"message\":\"You are being rate limited.\",\"retry_after\":1.25}", null);

        assertEquals(25L, delayTicks);
    }

    @Test
    void acceptsQuotedDiscordJsonRetryAfterValues() {
        long delayTicks = DiscordRateLimitException.retryDelayTicks(
                null, "{\"retry_after\":\"0.75\"}", null);

        assertEquals(15L, delayTicks);
    }

    @Test
    void fallsBackToResetAfterHeaderWhenOtherValuesAreUnavailable() {
        long delayTicks = DiscordRateLimitException.retryDelayTicks(
                "not-a-number", "{\"message\":\"rate limited\"}", "3.5");

        assertEquals(70L, delayTicks);
    }

    @Test
    void roundsFractionalTicksUpToAvoidRetryingEarly() {
        long delayTicks = DiscordRateLimitException.retryDelayTicks("0.051", null, null);

        assertEquals(2L, delayTicks);
    }

    @Test
    void waitsAtLeastOneTickForZeroRetryAfter() {
        long delayTicks = DiscordRateLimitException.retryDelayTicks("0", null, null);

        assertEquals(1L, delayTicks);
    }

    @Test
    void rejectsNegativeHeaderAndUsesDiscordJsonFallback() {
        long delayTicks = DiscordRateLimitException.retryDelayTicks(
                "-1", "{\"retry_after\":2}", null);

        assertEquals(40L, delayTicks);
    }

    @Test
    void usesSafeDefaultWhenDiscordProvidesNoUsableDelay() {
        long delayTicks = DiscordRateLimitException.retryDelayTicks(
                "NaN", "{\"retry_after\":-3}", "Infinity");

        assertEquals(20L, delayTicks);
    }

    @Test
    void boundsExtremelyLargeRetryAfterValues() {
        long delayTicks = DiscordRateLimitException.retryDelayTicks(
                "999999999999999999999999999999", null, null);

        assertEquals(Integer.MAX_VALUE, delayTicks);
    }
}

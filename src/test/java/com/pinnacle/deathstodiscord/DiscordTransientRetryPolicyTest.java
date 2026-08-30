package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordTransientRetryPolicyTest {

    @Test
    void retriesThreeTimesWithBoundedExponentialBackoff() {
        assertTrue(DiscordTransientRetryPolicy.canRetry(0));
        assertEquals(1, DiscordTransientRetryPolicy.nextRetryNumber(0));
        assertEquals(20L, DiscordTransientRetryPolicy.retryDelayTicks(1));
        assertEquals(40L, DiscordTransientRetryPolicy.retryDelayTicks(2));
        assertEquals(80L, DiscordTransientRetryPolicy.retryDelayTicks(3));
        assertFalse(DiscordTransientRetryPolicy.canRetry(3));
    }
}

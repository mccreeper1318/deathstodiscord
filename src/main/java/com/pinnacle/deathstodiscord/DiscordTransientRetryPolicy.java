package com.pinnacle.deathstodiscord;

final class DiscordTransientRetryPolicy {

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_TICKS = 20L;

    private DiscordTransientRetryPolicy() {
    }

    static boolean canRetry(int retriesCompleted) {
        return retriesCompleted >= 0 && retriesCompleted < MAX_RETRIES;
    }

    static int nextRetryNumber(int retriesCompleted) {
        if (!canRetry(retriesCompleted)) {
            throw new IllegalArgumentException("No transient Discord retries remain.");
        }
        return retriesCompleted + 1;
    }

    static long retryDelayTicks(int retryNumber) {
        if (retryNumber < 1 || retryNumber > MAX_RETRIES) {
            throw new IllegalArgumentException("Invalid transient Discord retry number: " + retryNumber);
        }
        return INITIAL_RETRY_DELAY_TICKS << (retryNumber - 1);
    }

    static int maximumRetries() {
        return MAX_RETRIES;
    }
}

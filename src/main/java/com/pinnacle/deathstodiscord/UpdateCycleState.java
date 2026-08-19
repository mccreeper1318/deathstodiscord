package com.pinnacle.deathstodiscord;

/**
 * Tracks the lifecycle of death-triggered leaderboard updates.
 *
 * <p>Deaths that happen while an update is only waiting for its debounce delay
 * are already covered by the upcoming snapshot, so they do not need another
 * update. Deaths that happen after the snapshot has started are marked pending
 * so one fresh follow-up snapshot is scheduled when the current update ends.</p>
 */
final class UpdateCycleState {

    private Phase phase = Phase.IDLE;
    private boolean pending;

    synchronized boolean requestUpdate() {
        return switch (phase) {
            case IDLE -> {
                phase = Phase.SCHEDULED;
                yield true;
            }
            case SCHEDULED -> false;
            case IN_FLIGHT -> {
                pending = true;
                yield false;
            }
        };
    }

    synchronized void markUpdateStarted() {
        if (phase != Phase.SCHEDULED) {
            throw new IllegalStateException("Cannot start an update while state is " + phase);
        }
        phase = Phase.IN_FLIGHT;
    }

    synchronized boolean completeUpdateAndShouldScheduleAgain() {
        if (phase != Phase.IN_FLIGHT) {
            throw new IllegalStateException("Cannot complete an update while state is " + phase);
        }

        if (pending) {
            pending = false;
            phase = Phase.SCHEDULED;
            return true;
        }

        phase = Phase.IDLE;
        return false;
    }

    private enum Phase {
        IDLE,
        SCHEDULED,
        IN_FLIGHT
    }
}

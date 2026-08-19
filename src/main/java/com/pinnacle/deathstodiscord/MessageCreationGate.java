package com.pinnacle.deathstodiscord;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Coordinates creation of the initial Discord leaderboard message.
 *
 * <p>All access is expected from the Bukkit primary thread. The first caller
 * claims message creation; later callers are queued until that creation either
 * succeeds or fails.</p>
 */
final class MessageCreationGate {

    private boolean creationInProgress;
    private final List<WaitingUpdate> waitingUpdates = new ArrayList<>();

    boolean beginOrQueue(Runnable retryAfterCreation, Consumer<String> failAfterCreation) {
        if (!creationInProgress) {
            creationInProgress = true;
            return true;
        }

        waitingUpdates.add(new WaitingUpdate(retryAfterCreation, failAfterCreation));
        return false;
    }

    void creationSucceeded() {
        creationInProgress = false;
        List<WaitingUpdate> waiting = drainWaitingUpdates();
        waiting.forEach(update -> update.retryAfterCreation().run());
    }

    void creationFailed(String reason) {
        creationInProgress = false;
        List<WaitingUpdate> waiting = drainWaitingUpdates();
        waiting.forEach(update -> update.failAfterCreation().accept(reason));
    }

    boolean isCreationInProgress() {
        return creationInProgress;
    }

    int queuedUpdateCount() {
        return waitingUpdates.size();
    }

    private List<WaitingUpdate> drainWaitingUpdates() {
        if (waitingUpdates.isEmpty()) {
            return List.of();
        }

        List<WaitingUpdate> waiting = List.copyOf(waitingUpdates);
        waitingUpdates.clear();
        return waiting;
    }

    private record WaitingUpdate(Runnable retryAfterCreation, Consumer<String> failAfterCreation) {
    }
}

package com.pinnacle.deathstodiscord;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Serializes Discord message PATCH requests in submission order.
 *
 * <p>All access is expected from the Bukkit primary thread. A submitted patch
 * starts immediately when the queue is idle; later submissions wait until the
 * current patch reports completion.</p>
 */
final class PatchRequestQueue {

    private boolean patchInProgress;
    private final Deque<Runnable> waitingPatches = new ArrayDeque<>();

    void submit(Runnable startPatch) {
        Objects.requireNonNull(startPatch, "startPatch");

        if (!patchInProgress) {
            patchInProgress = true;
            startPatch.run();
            return;
        }

        waitingPatches.addLast(startPatch);
    }

    void completeCurrent() {
        if (!patchInProgress) {
            return;
        }

        Runnable next = waitingPatches.pollFirst();
        if (next == null) {
            patchInProgress = false;
            return;
        }

        next.run();
    }

    boolean isPatchInProgress() {
        return patchInProgress;
    }

    int queuedPatchCount() {
        return waitingPatches.size();
    }
}

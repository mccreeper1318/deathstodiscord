package com.pinnacle.deathstodiscord;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Serializes every Discord update for one webhook/configuration session. */
final class DiscordRequestQueue {

    private final Deque<Request> waiting = new ArrayDeque<>();
    private boolean active = true;
    private boolean requestInProgress;

    void submit(Runnable start, Runnable cancel) {
        Request request = new Request(
                Objects.requireNonNull(start, "start"),
                Objects.requireNonNull(cancel, "cancel"));

        if (!active) {
            request.cancel().run();
            return;
        }
        if (!requestInProgress) {
            requestInProgress = true;
            request.start().run();
            return;
        }
        waiting.addLast(request);
    }

    void completeCurrent() {
        if (!requestInProgress) {
            return;
        }

        if (!active) {
            requestInProgress = false;
            return;
        }

        Request next = waiting.pollFirst();
        if (next == null) {
            requestInProgress = false;
            return;
        }
        next.start().run();
    }

    void deactivate() {
        if (!active) {
            return;
        }
        active = false;
        Request request;
        while ((request = waiting.pollFirst()) != null) {
            request.cancel().run();
        }
    }

    boolean isRequestInProgress() {
        return requestInProgress;
    }

    int queuedRequestCount() {
        return waiting.size();
    }

    private record Request(Runnable start, Runnable cancel) {
    }
}

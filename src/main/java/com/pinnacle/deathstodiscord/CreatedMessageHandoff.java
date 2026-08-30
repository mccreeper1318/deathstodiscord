package com.pinnacle.deathstodiscord;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

final class CreatedMessageHandoff {

    private final AtomicReference<Outcome> outcome = new AtomicReference<>(Outcome.PENDING);
    private final CountDownLatch resolved = new CountDownLatch(1);

    boolean claimForMainThread() {
        return resolve(Outcome.MAIN_THREAD);
    }

    boolean cancel() {
        return resolve(Outcome.CANCELLED);
    }

    Outcome awaitResolution() throws InterruptedException {
        resolved.await();
        return outcome.get();
    }

    Outcome outcome() {
        return outcome.get();
    }

    private boolean resolve(Outcome completedOutcome) {
        if (!outcome.compareAndSet(Outcome.PENDING, completedOutcome)) {
            return false;
        }
        resolved.countDown();
        return true;
    }

    enum Outcome {
        PENDING,
        MAIN_THREAD,
        CANCELLED
    }
}

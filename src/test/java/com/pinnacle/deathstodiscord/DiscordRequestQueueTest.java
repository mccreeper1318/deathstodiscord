package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordRequestQueueTest {

    @Test
    void serializesCreationAndUpdatesInSubmissionOrder() {
        DiscordRequestQueue queue = new DiscordRequestQueue();
        List<String> events = new ArrayList<>();

        queue.submit(() -> events.add("create-and-patch"), () -> events.add("cancel-create"));
        queue.submit(() -> events.add("fresh-patch"), () -> events.add("cancel-fresh"));

        assertEquals(List.of("create-and-patch"), events);
        assertTrue(queue.isRequestInProgress());
        assertEquals(1, queue.queuedRequestCount());

        queue.completeCurrent();
        assertEquals(List.of("create-and-patch", "fresh-patch"), events);
        queue.completeCurrent();
        assertFalse(queue.isRequestInProgress());
    }

    @Test
    void deactivationCancelsQueuedWorkWithoutStartingIt() {
        DiscordRequestQueue queue = new DiscordRequestQueue();
        List<String> events = new ArrayList<>();

        queue.submit(() -> events.add("old-in-flight"), () -> events.add("cancel-old"));
        queue.submit(() -> events.add("should-not-start"), () -> events.add("cancel-queued"));

        queue.deactivate();

        assertEquals(List.of("old-in-flight", "cancel-queued"), events);
        assertEquals(0, queue.queuedRequestCount());
        queue.completeCurrent();
        assertFalse(queue.isRequestInProgress());
    }
}

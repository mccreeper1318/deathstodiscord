package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchRequestQueueTest {

    @Test
    void serializesPatchesInSubmissionOrder() {
        PatchRequestQueue queue = new PatchRequestQueue();
        List<String> started = new ArrayList<>();

        queue.submit(() -> started.add("first"));
        queue.submit(() -> started.add("second"));
        queue.submit(() -> started.add("third"));

        assertEquals(List.of("first"), started);
        assertTrue(queue.isPatchInProgress());
        assertEquals(2, queue.queuedPatchCount());

        queue.completeCurrent();
        assertEquals(List.of("first", "second"), started);
        assertEquals(1, queue.queuedPatchCount());

        queue.completeCurrent();
        assertEquals(List.of("first", "second", "third"), started);
        assertEquals(0, queue.queuedPatchCount());

        queue.completeCurrent();
        assertFalse(queue.isPatchInProgress());
    }

    @Test
    void freshCreationUpdateWaitsBehindCreatorPatch() {
        PatchRequestQueue patchQueue = new PatchRequestQueue();
        MessageCreationGate creationGate = new MessageCreationGate();
        List<String> started = new ArrayList<>();

        assertTrue(creationGate.beginOrQueue(
                () -> started.add("unexpected-creator-retry"),
                failure -> started.add("unexpected-creator-failure")));

        assertFalse(creationGate.beginOrQueue(
                () -> patchQueue.submit(() -> started.add("fresh")),
                failure -> started.add("queued-failure")));

        patchQueue.submit(() -> started.add("creator"));
        assertEquals(List.of("creator"), started);

        // The POST has succeeded and the creator PATCH has completed its HTTP
        // request, but the serialized queue has not released the slot yet.
        // Releasing the creation gate must therefore queue the fresh PATCH.
        creationGate.creationSucceeded();
        assertEquals(List.of("creator"), started);
        assertEquals(1, patchQueue.queuedPatchCount());

        patchQueue.completeCurrent();
        assertEquals(List.of("creator", "fresh"), started);
    }

    @Test
    void queueCanBeReusedAfterItDrains() {
        PatchRequestQueue queue = new PatchRequestQueue();
        List<String> started = new ArrayList<>();

        queue.submit(() -> started.add("first"));
        queue.completeCurrent();
        queue.submit(() -> started.add("second"));

        assertEquals(List.of("first", "second"), started);
        assertTrue(queue.isPatchInProgress());
    }
}

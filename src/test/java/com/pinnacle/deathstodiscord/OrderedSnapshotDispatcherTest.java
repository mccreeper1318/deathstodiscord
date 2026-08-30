package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderedSnapshotDispatcherTest {

    @Test
    void asynchronouslyCompletedSnapshotsDispatchInInvocationOrder() {
        OrderedSnapshotDispatcher dispatcher = new OrderedSnapshotDispatcher();
        OrderedSnapshotDispatcher.Reservation older = dispatcher.reserve();
        OrderedSnapshotDispatcher.Reservation newer = dispatcher.reserve();
        List<String> dispatched = new ArrayList<>();

        assertTrue(dispatcher.complete(newer, () -> dispatched.add("newer")));
        assertEquals(List.of(), dispatched);

        assertTrue(dispatcher.complete(older, () -> dispatched.add("older")));
        assertEquals(List.of("older", "newer"), dispatched);
    }

    @Test
    void resetRejectsCompletionsFromAnEarlierPluginLifecycle() {
        OrderedSnapshotDispatcher dispatcher = new OrderedSnapshotDispatcher();
        OrderedSnapshotDispatcher.Reservation stale = dispatcher.reserve();
        List<String> dispatched = new ArrayList<>();

        dispatcher.reset();

        assertFalse(dispatcher.complete(stale, () -> dispatched.add("stale")));
        assertEquals(List.of(), dispatched);
    }
}

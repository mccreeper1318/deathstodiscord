package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCreationGateTest {

    @Test
    void overlappingUpdatesWaitForTheFirstMessageCreation() {
        MessageCreationGate gate = new MessageCreationGate();
        List<String> events = new ArrayList<>();

        assertTrue(gate.beginOrQueue(
                () -> events.add("first-retry"),
                reason -> events.add("first-failed:" + reason)));

        assertFalse(gate.beginOrQueue(
                () -> events.add("second-retry"),
                reason -> events.add("second-failed:" + reason)));
        assertFalse(gate.beginOrQueue(
                () -> events.add("third-retry"),
                reason -> events.add("third-failed:" + reason)));

        assertTrue(gate.isCreationInProgress());
        assertEquals(2, gate.queuedUpdateCount());

        gate.creationSucceeded();

        assertFalse(gate.isCreationInProgress());
        assertEquals(0, gate.queuedUpdateCount());
        assertEquals(List.of("second-retry", "third-retry"), events);
    }

    @Test
    void queuedUpdatesCompleteWithoutRetryingWhenCreationFails() {
        MessageCreationGate gate = new MessageCreationGate();
        List<String> events = new ArrayList<>();

        assertTrue(gate.beginOrQueue(
                () -> events.add("first-retry"),
                reason -> events.add("first-failed:" + reason)));
        assertFalse(gate.beginOrQueue(
                () -> events.add("second-retry"),
                reason -> events.add("second-failed:" + reason)));

        gate.creationFailed("creation failed");

        assertFalse(gate.isCreationInProgress());
        assertEquals(0, gate.queuedUpdateCount());
        assertEquals(List.of("second-failed:creation failed"), events);
    }

    @Test
    void gateCanBeClaimedAgainAfterCompletion() {
        MessageCreationGate gate = new MessageCreationGate();

        assertTrue(gate.beginOrQueue(() -> { }, reason -> { }));
        gate.creationSucceeded();
        assertTrue(gate.beginOrQueue(() -> { }, reason -> { }));
    }
}

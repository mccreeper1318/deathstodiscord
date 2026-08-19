package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCycleStateTest {

    @Test
    void deathsDuringDebounceDoNotScheduleRedundantFollowUp() {
        UpdateCycleState state = new UpdateCycleState();

        assertTrue(state.requestUpdate());
        assertFalse(state.requestUpdate());
        assertFalse(state.requestUpdate());

        state.markUpdateStarted();

        assertFalse(state.completeUpdateAndShouldScheduleAgain());
        assertTrue(state.requestUpdate());
    }

    @Test
    void deathDuringInFlightUpdateSchedulesFreshFollowUp() {
        UpdateCycleState state = new UpdateCycleState();

        assertTrue(state.requestUpdate());
        state.markUpdateStarted();

        assertFalse(state.requestUpdate());
        assertTrue(state.completeUpdateAndShouldScheduleAgain());

        state.markUpdateStarted();
        assertFalse(state.completeUpdateAndShouldScheduleAgain());
        assertTrue(state.requestUpdate());
    }

    @Test
    void multipleDeathsDuringInFlightUpdateCoalesceIntoOneFollowUp() {
        UpdateCycleState state = new UpdateCycleState();

        assertTrue(state.requestUpdate());
        state.markUpdateStarted();

        assertFalse(state.requestUpdate());
        assertFalse(state.requestUpdate());
        assertFalse(state.requestUpdate());

        assertTrue(state.completeUpdateAndShouldScheduleAgain());
        state.markUpdateStarted();
        assertFalse(state.completeUpdateAndShouldScheduleAgain());
    }

    @Test
    void deathDuringFollowUpCanQueueAnotherFreshSnapshot() {
        UpdateCycleState state = new UpdateCycleState();

        assertTrue(state.requestUpdate());
        state.markUpdateStarted();
        assertFalse(state.requestUpdate());
        assertTrue(state.completeUpdateAndShouldScheduleAgain());

        state.markUpdateStarted();
        assertFalse(state.requestUpdate());
        assertTrue(state.completeUpdateAndShouldScheduleAgain());

        state.markUpdateStarted();
        assertFalse(state.completeUpdateAndShouldScheduleAgain());
    }
}

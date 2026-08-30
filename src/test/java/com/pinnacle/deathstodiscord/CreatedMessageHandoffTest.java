package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatedMessageHandoffTest {

    @Test
    void cancellationOwnsCleanupWhenThePluginDisablesBeforeMainThreadHandling() throws Exception {
        CreatedMessageHandoff handoff = new CreatedMessageHandoff();

        assertTrue(handoff.cancel());
        assertFalse(handoff.claimForMainThread());
        assertEquals(CreatedMessageHandoff.Outcome.CANCELLED, handoff.awaitResolution());
    }

    @Test
    void mainThreadHandlingPreventsDuplicateCleanup() throws Exception {
        CreatedMessageHandoff handoff = new CreatedMessageHandoff();

        assertTrue(handoff.claimForMainThread());
        assertFalse(handoff.cancel());
        assertEquals(CreatedMessageHandoff.Outcome.MAIN_THREAD, handoff.awaitResolution());
    }
}

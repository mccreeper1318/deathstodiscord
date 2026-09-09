package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnownPlayerDirectoryTest {

    @Test
    void parsesOnlyValidPlayerDataFilesDuringTheOneTimeScan() {
        UUID first = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID second = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");

        List<UUID> result = PlayerDataFileParser.parseUuids(List.of(
                first + ".dat",
                second + ".DAT",
                "not-a-uuid.dat",
                "session.lock"));

        assertEquals(List.of(first, second), result);
    }

    @Test
    void mergesBukkitKnownPlayersWithFirstWorldPlayerData() {
        UUID bukkitOnly = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID shared = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
        UUID playerDataOnly = UUID.fromString("123e4567-e89b-12d3-a456-426614174002");

        List<UUID> result = KnownPlayerDirectory.mergeHistoricalPlayerUuids(
                List.of(bukkitOnly, shared),
                List.of(shared, playerDataOnly));

        assertEquals(List.of(bukkitOnly, shared, playerDataOnly), result);
    }

    @Test
    void checkedFileScanFailureFallsBackToNoPlayerData() {
        AtomicInteger failures = new AtomicInteger();

        List<UUID> result = KnownPlayerDirectory.safelyScanPlayerData(
                () -> {
                    throw new IOException("scan failed");
                },
                error -> failures.incrementAndGet());

        assertTrue(result.isEmpty());
        assertEquals(1, failures.get());
    }

    @Test
    void uncheckedFileScanFailureFallsBackToNoPlayerData() {
        AtomicInteger failures = new AtomicInteger();

        List<UUID> result = KnownPlayerDirectory.safelyScanPlayerData(
                () -> {
                    throw new IllegalStateException("filesystem failed");
                },
                error -> failures.incrementAndGet());

        assertTrue(result.isEmpty());
        assertEquals(1, failures.get());
    }

    @Test
    void failedProfileLookupDoesNotAbortTheRemainingBatch() {
        UUID failing = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID successful = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
        Deque<UUID> remaining = new ArrayDeque<>(List.of(failing, successful));
        List<UUID> remembered = new ArrayList<>();
        AtomicInteger failures = new AtomicInteger();

        boolean complete = KnownPlayerDirectory.resolveNamesBatch(
                remaining,
                50,
                uuid -> {
                    if (uuid.equals(failing)) {
                        throw new IllegalStateException("profile failed");
                    }
                    return "PlayerTwo";
                },
                (uuid, name) -> remembered.add(uuid),
                error -> failures.incrementAndGet());

        assertTrue(complete);
        assertTrue(remaining.isEmpty());
        assertEquals(List.of(successful), remembered);
        assertEquals(1, failures.get());
    }
}

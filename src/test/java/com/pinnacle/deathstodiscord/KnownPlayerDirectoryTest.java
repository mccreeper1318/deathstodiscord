package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

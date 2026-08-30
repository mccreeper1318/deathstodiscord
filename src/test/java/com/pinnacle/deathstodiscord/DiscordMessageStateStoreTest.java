package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DiscordMessageStateStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void webhookFingerprintIsStableWithoutPersistingTheSecret() {
        String webhook = "https://discord.com/api/webhooks/123/super-secret-token";

        String first = WebhookIdentity.fingerprint(webhook);
        String second = WebhookIdentity.fingerprint(webhook);

        assertEquals(first, second);
        assertFalse(first.contains("super-secret-token"));
    }

    @Test
    void differentWebhooksHaveDifferentStateIdentities() {
        assertNotEquals(
                WebhookIdentity.fingerprint("https://discord.com/api/webhooks/1/one"),
                WebhookIdentity.fingerprint("https://discord.com/api/webhooks/2/two"));
    }

    @Test
    void failedPersistenceIsReportedAndTheInMemoryMessageIdIsRolledBack() throws Exception {
        Path fileUsedAsDataFolder = Files.createFile(temporaryDirectory.resolve("not-a-directory"));
        DiscordMessageStateStore store = new DiscordMessageStateStore(
                fileUsedAsDataFolder.toFile(), Logger.getLogger("state-store-test"));
        String fingerprint = store.activateWebhook("https://discord.com/api/webhooks/1/token");

        assertFalse(store.saveMessageId(fingerprint, "123456789"));
        assertEquals("", store.messageId(fingerprint));
    }

    @Test
    void legacyMessageIdCannotMigrateWithoutAConfiguredWebhookIdentity() {
        DiscordMessageStateStore store = new DiscordMessageStateStore(
                temporaryDirectory.toFile(), Logger.getLogger("state-store-test"));

        assertFalse(store.migrateLegacyMessageId("", "123456789"));
        assertEquals("", store.messageId(""));
    }
}

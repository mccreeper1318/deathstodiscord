package com.pinnacle.deathstodiscord;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Logger;

final class DiscordMessageStateStore {

    private static final String FINGERPRINT_KEY = "webhook-fingerprint";
    private static final String MESSAGE_ID_KEY = "message-id";

    private final File stateFile;
    private final Logger logger;
    private final YamlConfiguration state;

    DiscordMessageStateStore(File dataFolder, Logger logger) {
        this.stateFile = new File(dataFolder, "state.yml");
        this.logger = logger;
        this.state = YamlConfiguration.loadConfiguration(stateFile);
    }

    String activateWebhook(String webhookUrl) {
        String fingerprint = WebhookIdentity.fingerprint(webhookUrl);
        String storedFingerprint = state.getString(FINGERPRINT_KEY, "");
        if (!fingerprint.equals(storedFingerprint)) {
            state.set(FINGERPRINT_KEY, fingerprint);
            state.set(MESSAGE_ID_KEY, null);
            save();
        }
        return fingerprint;
    }

    void migrateLegacyMessageId(String fingerprint, String legacyMessageId) {
        if (legacyMessageId == null || legacyMessageId.isBlank()) {
            return;
        }
        if (!fingerprint.equals(state.getString(FINGERPRINT_KEY, ""))) {
            return;
        }
        if (messageId(fingerprint).isBlank()) {
            state.set(MESSAGE_ID_KEY, legacyMessageId.trim());
            save();
        }
    }

    String messageId(String fingerprint) {
        if (!fingerprint.equals(state.getString(FINGERPRINT_KEY, ""))) {
            return "";
        }
        String messageId = state.getString(MESSAGE_ID_KEY, "");
        return messageId == null ? "" : messageId.trim();
    }

    boolean saveMessageId(String fingerprint, String messageId) {
        if (!fingerprint.equals(state.getString(FINGERPRINT_KEY, ""))) {
            return false;
        }
        state.set(MESSAGE_ID_KEY, messageId);
        save();
        return true;
    }

    boolean clearMessageId(String fingerprint, String expectedMessageId) {
        String current = messageId(fingerprint);
        if (!Objects.equals(current, expectedMessageId)) {
            return false;
        }
        state.set(MESSAGE_ID_KEY, null);
        save();
        return true;
    }

    private void save() {
        try {
            state.save(stateFile);
        } catch (IOException e) {
            logger.severe("Could not save Discord message state: " + e.getMessage());
        }
    }
}

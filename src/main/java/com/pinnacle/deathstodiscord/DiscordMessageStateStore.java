package com.pinnacle.deathstodiscord;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.logging.Logger;

final class DiscordMessageStateStore {

    private static final String FINGERPRINT_KEY = "webhook-fingerprint";
    private static final String MESSAGE_ID_KEY = "message-id";

    private final File stateFile;
    private final File backupFile;
    private final Logger logger;
    private final YamlConfiguration state;

    DiscordMessageStateStore(File dataFolder, Logger logger) {
        this.stateFile = new File(dataFolder, "state.yml");
        this.backupFile = new File(dataFolder, "state.yml.bak");
        this.logger = logger;
        this.state = loadState(stateFile, backupFile, logger);
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

    boolean migrateLegacyMessageId(String fingerprint, String legacyMessageId) {
        if (legacyMessageId == null || legacyMessageId.isBlank()) {
            return true;
        }
        if (fingerprint == null || fingerprint.isBlank()) {
            return false;
        }
        if (!fingerprint.equals(state.getString(FINGERPRINT_KEY, ""))) {
            return false;
        }
        if (messageId(fingerprint).isBlank()) {
            Object previousMessageId = state.get(MESSAGE_ID_KEY);
            state.set(MESSAGE_ID_KEY, legacyMessageId.trim());
            if (!save()) {
                state.set(MESSAGE_ID_KEY, previousMessageId);
                return false;
            }
        }
        return true;
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
        Object previousMessageId = state.get(MESSAGE_ID_KEY);
        state.set(MESSAGE_ID_KEY, messageId);
        if (save()) {
            return true;
        }
        state.set(MESSAGE_ID_KEY, previousMessageId);
        return false;
    }

    boolean clearMessageId(String fingerprint, String expectedMessageId) {
        String current = messageId(fingerprint);
        if (!Objects.equals(current, expectedMessageId)) {
            return false;
        }
        Object previousMessageId = state.get(MESSAGE_ID_KEY);
        state.set(MESSAGE_ID_KEY, null);
        if (save()) {
            return true;
        }
        state.set(MESSAGE_ID_KEY, previousMessageId);
        return false;
    }

    private boolean save() {
        try {
            String serializedState = state.saveToString();
            writeAtomically(stateFile.toPath(), serializedState);
            try {
                writeAtomically(backupFile.toPath(), serializedState);
            } catch (IOException backupError) {
                logger.warning("Could not update the Discord message state backup: "
                        + backupError.getMessage());
            }
            return true;
        } catch (IOException e) {
            logger.severe("Could not save Discord message state: " + e.getMessage());
            return false;
        }
    }

    private static YamlConfiguration loadState(File stateFile, File backupFile, Logger logger) {
        YamlConfiguration primary = loadStateFile(stateFile, logger, "Discord message state");
        if (hasWebhookIdentity(primary)) {
            return primary;
        }

        YamlConfiguration backup = loadStateFile(backupFile, logger, "Discord message state backup");
        if (hasWebhookIdentity(backup)) {
            if (stateFile.isFile()) {
                logger.warning("Discord message state was empty or invalid; recovered state.yml.bak.");
            } else {
                logger.warning("Discord message state was missing; recovered state.yml.bak.");
            }
            return backup;
        }

        return primary == null ? new YamlConfiguration() : primary;
    }

    private static YamlConfiguration loadStateFile(File file, Logger logger, String description) {
        if (!file.isFile()) {
            return null;
        }

        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
            return loaded;
        } catch (IOException | InvalidConfigurationException error) {
            logger.severe("Could not load " + description + ": " + error.getMessage());
            return null;
        }
    }

    private static boolean hasWebhookIdentity(YamlConfiguration state) {
        if (state == null) {
            return false;
        }
        String fingerprint = state.getString(FINGERPRINT_KEY, "");
        return fingerprint != null && !fingerprint.isBlank();
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path absoluteTarget = target.toAbsolutePath();
        Path directory = absoluteTarget.getParent();
        if (directory == null) {
            throw new IOException("State file does not have a parent directory.");
        }

        Files.createDirectories(directory);
        Path temporaryFile = Files.createTempFile(directory, target.getFileName() + ".", ".tmp");
        try {
            Files.writeString(
                    temporaryFile,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            moveReplacing(temporaryFile, absoluteTarget);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

package com.pinnacle.deathstodiscord;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

final class DiscordUpdateService {

    private static final String INITIAL_MESSAGE = "**💀 Death Leaderboard**\nInitializing…";

    private final DeathsToDiscordPlugin plugin;
    private final DiscordWebhookClient client;
    private final DiscordMessageStateStore stateStore;
    private volatile WebhookSession activeSession;

    DiscordUpdateService(DeathsToDiscordPlugin plugin, DiscordWebhookClient client,
                         DiscordMessageStateStore stateStore) {
        this.plugin = plugin;
        this.client = client;
        this.stateStore = stateStore;
    }

    String configureWebhook(String webhookUrl) {
        if (!PluginSettings.isWebhookConfigured(webhookUrl)) {
            deactivateCurrentSession();
            return "";
        }

        String fingerprint = WebhookIdentity.fingerprint(webhookUrl);
        if (activeSession != null && activeSession.active
                && activeSession.fingerprint.equals(fingerprint)) {
            return fingerprint;
        }

        deactivateCurrentSession();
        fingerprint = stateStore.activateWebhook(webhookUrl);
        activeSession = new WebhookSession(webhookUrl, fingerprint);
        return fingerprint;
    }

    boolean migrateLegacyMessageId(String fingerprint, String legacyMessageId) {
        return stateStore.migrateLegacyMessageId(fingerprint, legacyMessageId);
    }

    void submit(String webhookUrl, String content, CommandSender sender, Runnable onComplete) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> submit(webhookUrl, content, sender, onComplete));
            return;
        }

        WebhookSession session = activeSession;
        String fingerprint = WebhookIdentity.fingerprint(webhookUrl);
        if (!isCurrent(session) || !session.fingerprint.equals(fingerprint)) {
            onComplete.run();
            return;
        }

        session.queue.submit(
                () -> executeUpdate(session, content, sender, onComplete),
                onComplete);
    }

    void shutdown() {
        deactivateCurrentSession();
    }

    private void executeUpdate(WebhookSession session, String content, CommandSender sender,
                               Runnable onComplete) {
        if (!isCurrent(session)) {
            finishCancelled(session, onComplete);
            return;
        }

        String messageId = stateStore.messageId(session.fingerprint);
        if (messageId.isBlank()) {
            createThenPatch(session, content, sender, onComplete);
        } else {
            patch(session, messageId, content, sender, onComplete, true);
        }
    }

    private void createThenPatch(WebhookSession session, String content, CommandSender sender,
                                 Runnable onComplete) {
        if (!isCurrent(session)) {
            finishCancelled(session, onComplete);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String createdMessageId = client.createMessage(session.webhookUrl, INITIAL_MESSAGE);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!isCurrent(session)) {
                        finishCancelled(session, onComplete);
                        cleanUpCreatedMessage(session, createdMessageId);
                        return;
                    }
                    if (!stateStore.saveMessageId(session.fingerprint, createdMessageId)) {
                        finishFailure(
                                session,
                                sender,
                                "Discord message creation failed because state.yml could not be saved.",
                                onComplete);
                        cleanUpCreatedMessage(session, createdMessageId);
                        return;
                    }

                    plugin.getLogger().info("Created a Discord leaderboard message and saved its state.");
                    patch(session, createdMessageId, content, sender, onComplete, false);
                });
            } catch (DiscordRateLimitException rateLimit) {
                scheduleRateLimitedRetry(session, "Discord message creation", rateLimit,
                        () -> createThenPatch(session, content, sender, onComplete), onComplete);
            } catch (Exception error) {
                finishFailureOnMainThread(session, sender,
                        "Discord message creation failed: "
                                + WebhookSecretRedactor.safeExceptionMessage(error, session.webhookUrl),
                        onComplete);
            }
        });
    }

    private void cleanUpCreatedMessage(WebhookSession session, String messageId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                client.deleteMessage(session.webhookUrl, messageId);
            } catch (DiscordRateLimitException rateLimit) {
                scheduleCreatedMessageCleanupRetry(session, messageId, rateLimit);
            } catch (DiscordHttpException error) {
                if (!error.isMissingMessage()) {
                    logCreatedMessageCleanupFailure(session, error);
                }
            } catch (Exception error) {
                logCreatedMessageCleanupFailure(session, error);
            }
        });
    }

    private void scheduleCreatedMessageCleanupRetry(WebhookSession session, String messageId,
                                                    DiscordRateLimitException rateLimit) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            long delayTicks = rateLimit.retryDelayTicks();
            plugin.getLogger().warning(
                    "Cleanup of a newly created Discord message was rate limited; retrying in "
                            + (delayTicks / 20.0) + " seconds.");
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> cleanUpCreatedMessage(session, messageId), delayTicks);
        });
    }

    private void logCreatedMessageCleanupFailure(WebhookSession session, Exception error) {
        plugin.getLogger().warning(
                "Could not delete a newly created Discord message after its state was rejected: "
                        + WebhookSecretRedactor.safeExceptionMessage(error, session.webhookUrl));
    }

    private void patch(WebhookSession session, String messageId, String content, CommandSender sender,
                       Runnable onComplete, boolean recoverMissingMessage) {
        if (!isCurrent(session)) {
            finishCancelled(session, onComplete);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                client.patchMessage(session.webhookUrl, messageId, content);
                finishSuccessOnMainThread(session, sender, onComplete);
            } catch (DiscordRateLimitException rateLimit) {
                scheduleRateLimitedRetry(session, "Discord leaderboard update", rateLimit,
                        () -> patch(session, messageId, content, sender, onComplete, recoverMissingMessage),
                        onComplete);
            } catch (DiscordHttpException error) {
                if (recoverMissingMessage && error.isMissingMessage()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!isCurrent(session)) {
                            finishCancelled(session, onComplete);
                            return;
                        }
                        stateStore.clearMessageId(session.fingerprint, messageId);
                        plugin.getLogger().warning(
                                "The saved Discord leaderboard message no longer exists; creating a replacement.");
                        createThenPatch(session, content, sender, onComplete);
                    });
                    return;
                }

                if (error.isMissingMessage()) {
                    Bukkit.getScheduler().runTask(plugin,
                            () -> stateStore.clearMessageId(session.fingerprint, messageId));
                }
                finishFailureOnMainThread(session, sender,
                        "Discord leaderboard update failed: "
                                + WebhookSecretRedactor.safeExceptionMessage(error, session.webhookUrl),
                        onComplete);
            } catch (Exception error) {
                finishFailureOnMainThread(session, sender,
                        "Discord leaderboard update failed: "
                                + WebhookSecretRedactor.safeExceptionMessage(error, session.webhookUrl),
                        onComplete);
            }
        });
    }

    private void scheduleRateLimitedRetry(WebhookSession session, String description,
                                          DiscordRateLimitException rateLimit, Runnable retry,
                                          Runnable onComplete) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!isCurrent(session)) {
                finishCancelled(session, onComplete);
                return;
            }

            long delayTicks = rateLimit.retryDelayTicks();
            plugin.getLogger().warning(description + " was rate limited by Discord (HTTP 429); retrying in "
                    + (delayTicks / 20.0) + " seconds.");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isCurrent(session)) {
                    retry.run();
                } else {
                    finishCancelled(session, onComplete);
                }
            }, delayTicks);
        });
    }

    private void finishSuccessOnMainThread(WebhookSession session, CommandSender sender, Runnable onComplete) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!isCurrent(session)) {
                finishCancelled(session, onComplete);
                return;
            }
            if (sender != null) {
                sender.sendMessage(Component.text(
                        "DeathsToDiscord Discord message updated.", NamedTextColor.GREEN));
            }
            finish(session, onComplete);
        });
    }

    private void finishFailureOnMainThread(WebhookSession session, CommandSender sender,
                                           String failureMessage, Runnable onComplete) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!isCurrent(session)) {
                finishCancelled(session, onComplete);
                return;
            }
            finishFailure(session, sender, failureMessage, onComplete);
        });
    }

    private void finishFailure(WebhookSession session, CommandSender sender,
                               String failureMessage, Runnable onComplete) {
        if (sender != null) {
            sender.sendMessage(Component.text("Update failed: " + failureMessage, NamedTextColor.RED));
        }
        plugin.getLogger().warning(failureMessage);
        finish(session, onComplete);
    }

    private void finishCancelled(WebhookSession session, Runnable onComplete) {
        finish(session, onComplete);
    }

    private void finish(WebhookSession session, Runnable onComplete) {
        try {
            onComplete.run();
        } finally {
            session.queue.completeCurrent();
        }
    }

    private boolean isCurrent(WebhookSession session) {
        return session != null && session.active && activeSession == session;
    }

    private void deactivateCurrentSession() {
        if (activeSession == null) {
            return;
        }
        activeSession.active = false;
        activeSession.queue.deactivate();
        activeSession = null;
    }

    private static final class WebhookSession {
        private final String webhookUrl;
        private final String fingerprint;
        private final DiscordRequestQueue queue = new DiscordRequestQueue();
        private volatile boolean active = true;

        private WebhookSession(String webhookUrl, String fingerprint) {
            this.webhookUrl = webhookUrl;
            this.fingerprint = fingerprint;
        }
    }
}

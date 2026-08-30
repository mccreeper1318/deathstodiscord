package com.pinnacle.deathstodiscord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class WebhookIdentity {

    private WebhookIdentity() {
    }

    static String fingerprint(String webhookUrl) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(webhookUrl.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available.", impossible);
        }
    }
}

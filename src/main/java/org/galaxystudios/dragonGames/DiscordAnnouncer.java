package org.galaxystudios.dragonGames;

import org.bukkit.Bukkit;

import javax.net.ssl.HttpsURLConnection;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Sends game updates to Discord.
 *
 * Note: Discord doesn't use WebSockets for user bots/webhooks in the way most people expect.
 * The stable, safe way for a Minecraft plugin to "announce" is a Discord webhook.
 */
public final class DiscordAnnouncer {

    private final DragonGames plugin;

    public DiscordAnnouncer(DragonGames plugin) {
        this.plugin = plugin;
    }

    public void announceAsync(String message) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false)) return;
        String url = plugin.getConfig().getString("discord.webhook-url", "");
        if (url == null || url.isBlank()) return;

        // Never block the main thread with I/O.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                postWebhook(url, message);
            } catch (Exception ex) {
                plugin.getLogger().warning("Discord announce failed: " + ex.getMessage());
            }
        });
    }

    private void postWebhook(String webhookUrl, String content) throws Exception {
        URL url = new URL(webhookUrl);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        String safe = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");

        // minimal webhook payload
        String json = "{\"content\":\"" + safe + "\"}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        conn.setFixedLengthStreamingMode(bytes.length);
        conn.connect();

        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Discord webhook returned HTTP " + code);
        }

        conn.disconnect();
    }
}


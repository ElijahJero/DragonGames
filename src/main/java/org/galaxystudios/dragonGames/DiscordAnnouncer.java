package org.galaxystudios.dragonGames;

import org.bukkit.Bukkit;

import javax.net.ssl.HttpsURLConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

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
        conn.setRequestProperty("User-Agent", "DragonGames/DiscordWebhook");

        String stripped = stripPrefix(content, plugin.getConfig().getString("discord.prefix", "[DragonGames] "));
        String escaped = escapeJson(stripped);
        String description = trimToLength(escaped, 4096);
        String json = buildEmbedPayload(description);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        conn.setFixedLengthStreamingMode(bytes.length);
        conn.connect();

        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            String errorBody = readBody(conn);
            throw new IllegalStateException("Discord webhook returned HTTP " + code + (errorBody.isEmpty() ? "" : ": " + errorBody));
        }

        conn.disconnect();
    }

    private String buildEmbedPayload(String description) {
        String safeDescription = description == null ? "" : description;
        return "{\"embeds\":[{" +
                "\"title\":\"Dragon Games Update\"," +
                "\"description\":\"" + safeDescription + "\"," +
                "\"color\":" + 0x9b59b6 + ',' +
                "\"timestamp\":\"" + Instant.now().toString() + "\"" +
                "}]}";
    }

    private String stripPrefix(String content, String prefix) {
        if (content == null) return "";
        if (prefix == null || prefix.isBlank()) return content;
        return content.startsWith(prefix) ? content.substring(prefix.length()) : content;
    }

    private String trimToLength(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        plugin.getLogger().warning("Discord message truncated to " + max + " characters for embed.");
        return value.substring(0, max - 1) + "\u2026"; // ellipsis
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

    private String readBody(HttpsURLConnection conn) {
        try (InputStream in = conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream()) {
            if (in == null) return "";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            return "";
        }
    }
}

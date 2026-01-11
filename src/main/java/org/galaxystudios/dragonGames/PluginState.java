package org.galaxystudios.dragonGames;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Persists holder + absolute timestamps and weekly activity accounting.
 */
public final class PluginState {

    private final DragonGames plugin;
    private final File file;

    private UUID holder;

    // Game lifecycle
    private boolean gameEnabled;
    private long gameStartEpochSeconds;

    // Weekly accounting
    private long weekStartEpochSeconds;
    private long holderWeeklyPlaySeconds;
    private long holderLastSeenEpochSeconds;

    // For accurate online-time accounting across restarts
    private long lastTickEpochSeconds;

    public PluginState(DragonGames plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "state.yml");
    }

    public void load() {
        if (!file.exists()) {
            resetWeek();
            lastTickEpochSeconds = now();
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String holderStr = cfg.getString("holder");
        holder = (holderStr == null || holderStr.isBlank()) ? null : UUID.fromString(holderStr);

        gameEnabled = cfg.getBoolean("gameEnabled", false);
        gameStartEpochSeconds = cfg.getLong("gameStartEpochSeconds", 0L);

        weekStartEpochSeconds = cfg.getLong("weekStartEpochSeconds", 0L);
        holderWeeklyPlaySeconds = cfg.getLong("holderWeeklyPlaySeconds", 0L);
        holderLastSeenEpochSeconds = cfg.getLong("holderLastSeenEpochSeconds", 0L);

        lastTickEpochSeconds = cfg.getLong("lastTickEpochSeconds", 0L);

        if (weekStartEpochSeconds <= 0) resetWeek();
        if (lastTickEpochSeconds <= 0) lastTickEpochSeconds = now();

        resetWeeklyIfNeeded();
    }

    public void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Failed to create plugin data folder.");
        }
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("holder", holder == null ? null : holder.toString());

        cfg.set("gameEnabled", gameEnabled);
        cfg.set("gameStartEpochSeconds", gameStartEpochSeconds);

        cfg.set("weekStartEpochSeconds", weekStartEpochSeconds);
        cfg.set("holderWeeklyPlaySeconds", holderWeeklyPlaySeconds);
        cfg.set("holderLastSeenEpochSeconds", holderLastSeenEpochSeconds);
        cfg.set("lastTickEpochSeconds", lastTickEpochSeconds);
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save state.yml: " + e.getMessage());
        }
    }

    public UUID getHolder() {
        return holder;
    }

    public void setHolder(UUID holder) {
        this.holder = holder;
        // when holder changes, reset weekly tracking for new holder
        resetWeek();
        holderLastSeenEpochSeconds = now();
    }

    public boolean isGameEnabled() {
        return gameEnabled;
    }

    public void setGameEnabled(boolean enabled) {
        if (this.gameEnabled == enabled) return;
        this.gameEnabled = enabled;
        if (enabled && gameStartEpochSeconds <= 0) {
            gameStartEpochSeconds = now();
        }
    }

    public long getGameStartEpochSeconds() {
        return gameStartEpochSeconds;
    }

    public void resetWeek() {
        weekStartEpochSeconds = startOfWeekEpochSeconds();
        holderWeeklyPlaySeconds = 0L;
    }

    public void resetWeeklyIfNeeded() {
        long current = startOfWeekEpochSeconds();
        if (weekStartEpochSeconds != current) {
            resetWeek();
        }
    }

    public long getHolderWeeklyPlaySeconds() {
        return holderWeeklyPlaySeconds;
    }

    public void addPlaySeconds(long seconds) {
        holderWeeklyPlaySeconds += Math.max(0L, seconds);
    }

    public void touchActivity(UUID uuid) {
        if (uuid != null && uuid.equals(holder)) {
            holderLastSeenEpochSeconds = now();
        }
    }

    public long getHolderLastSeenEpochSeconds() {
        return holderLastSeenEpochSeconds;
    }

    public long getLastTickEpochSeconds() {
        return lastTickEpochSeconds;
    }

    public void setLastTickEpochSeconds(long epochSeconds) {
        this.lastTickEpochSeconds = epochSeconds;
    }

    public boolean isWeekExpired() {
        return startOfWeekEpochSeconds() != weekStartEpochSeconds;
    }

    private long now() {
        return Instant.now().getEpochSecond();
    }

    private long startOfWeekEpochSeconds() {
        // week starts Monday 00:00 UTC
        ZonedDateTime z = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime monday = z.with(java.time.DayOfWeek.MONDAY).toLocalDate().atStartOfDay(ZoneOffset.UTC);
        if (z.getDayOfWeek().getValue() < java.time.DayOfWeek.MONDAY.getValue()) {
            monday = monday.minusWeeks(1);
        }
        // if today isn't Monday but we used with(MONDAY) it might point to next Monday; adjust if so
        if (monday.isAfter(z)) monday = monday.minusWeeks(1);
        return monday.toEpochSecond();
    }
}

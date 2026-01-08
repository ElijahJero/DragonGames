package org.galaxystudios.dragonGames;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class TickTasks {

    private final DragonGames plugin;
    private final EggManager eggs;

    public TickTasks(DragonGames plugin, EggManager eggs) {
        this.plugin = plugin;
        this.eggs = eggs;
    }

    public void start() {
        long intervalTicks = Math.max(20L, plugin.getConfig().getLong("check-interval-seconds", 300L) * 20L);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            plugin.getState().resetWeeklyIfNeeded();

            // Persist absolute time marker for accurate delta across restarts.
            long now = System.currentTimeMillis() / 1000L;
            long lastTick = plugin.getState().getLastTickEpochSeconds();
            // clamp delta in case system clock changes wildly
            long delta = Math.max(0L, Math.min(3600L, now - lastTick));
            plugin.getState().setLastTickEpochSeconds(now);

            if (!plugin.isGameEnabled()) {
                plugin.getState().save();
                return;
            }

            UUID holder = eggs.getHolder();
            if (holder == null) {
                plugin.getDynmap().clearMarker();
                plugin.getState().save();
                return;
            }

            Player hp = Bukkit.getPlayer(holder);

            if (hp != null && hp.isOnline()) {
                plugin.getState().addPlaySeconds(delta);
                plugin.getState().touchActivity(holder);
                eggs.ensureEggInInventory(hp);
                eggs.applyEggBuffs(hp);
                plugin.getDynmap().updateMarker(hp);
            }

            long required = eggs.getRequiredWeeklySeconds();
            if (plugin.getState().getHolderWeeklyPlaySeconds() < required) {
                long lastSeen = plugin.getState().getHolderLastSeenEpochSeconds();
                if (now - lastSeen > required) {
                    eggs.returnEggToReturnLocation("The Dragon Egg returned because its holder was inactive!");
                    plugin.getDiscord().announceAsync(plugin.getConfig().getString("discord.prefix", "[DragonEgg] ") +
                            "The Dragon Egg returned to home due to inactivity.");
                }
            }

            plugin.getState().save();
        }, intervalTicks, intervalTicks);

        long dynUpdate = Math.max(20L, plugin.getConfig().getLong("dynmap.update-seconds", 5L) * 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getDynmap().isAvailable()) return;
            Player hp = eggs.getOnlineHolder();
            if (hp != null) plugin.getDynmap().updateMarker(hp);
        }, dynUpdate, dynUpdate);
    }
}

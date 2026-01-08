package org.galaxystudios.dragonGames;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class DragonGames extends JavaPlugin {

    private PluginState state;
    private EggManager eggManager;
    private DynmapHook dynmap;
    private DiscordAnnouncer discord;

    private List<PotionEffect> configuredBuffs = Collections.emptyList();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.discord = new DiscordAnnouncer(this);

        this.state = new PluginState(this);
        this.state.load();

        this.eggManager = new EggManager(this);
        this.configuredBuffs = eggManager.parseBuffsFromConfig();

        this.dynmap = new DynmapHook(this);
        this.dynmap.init();

        Bukkit.getPluginManager().registerEvents(new DragonEggListener(this, eggManager), this);

        if (getCommand("dragongames") != null) {
            DragonGamesCommand cmd = new DragonGamesCommand(this);
            getCommand("dragongames").setExecutor(cmd);
            getCommand("dragongames").setTabCompleter(cmd);
        }

        new TickTasks(this, eggManager).start();

        Player holder = eggManager.getOnlineHolder();
        if (holder != null) {
            Bukkit.getScheduler().runTask(this, () -> {
                eggManager.ensureEggInInventory(holder);
                eggManager.applyEggBuffs(holder);
                dynmap.updateMarker(holder);
            });
        }

        getLogger().info("DragonGames enabled. gameEnabled=" + state.isGameEnabled() + " holder=" + state.getHolder());
        logDebug("Debug mode is ON");
    }

    @Override
    public void onDisable() {
        if (state != null) state.save();
        if (dynmap != null) dynmap.clearMarker();
    }

    public PluginState getState() {
        return state;
    }

    public EggManager getEggManager() {
        return eggManager;
    }

    public DynmapHook getDynmap() {
        return dynmap;
    }

    public DiscordAnnouncer getDiscord() {
        return discord;
    }

    public List<PotionEffect> getConfiguredBuffs() {
        return configuredBuffs;
    }

    public boolean isPvpOverrideEnabled() {
        return getConfig().getBoolean("pvp-override-enabled", true);
    }

    public boolean isGameEnabled() {
        // persisted state is the source of truth
        return state.isGameEnabled();
    }

    public void setGameEnabled(boolean enabled) {
        state.setGameEnabled(enabled);
        state.save();
    }

    public void startDragonGames(Location home) {
        setGameEnabled(true);

        if (home != null) {
            discord.announceAsync(getConfig().getString("discord.prefix", "[DragonEgg] ") +
                    "Dragon Games started! Home set at " + home.getWorld().getName() + " " +
                    (int) home.getX() + "," + (int) home.getY() + "," + (int) home.getZ());
            eggManager.placeEggAtHomeIfNoHolder();
        } else {
            discord.announceAsync(getConfig().getString("discord.prefix", "[DragonEgg] ") + "Dragon Games started!");
            eggManager.placeEggAtHomeIfNoHolder();
        }
    }

    public void stopDragonGames() {
        setGameEnabled(false);
        discord.announceAsync(getConfig().getString("discord.prefix", "[DragonEgg] ") + "Dragon Games stopped.");
    }

    public void clearHolderAndReturnEgg() {
        eggManager.returnEggToReturnLocation("The Dragon Egg was returned by an admin.");
        discord.announceAsync(getConfig().getString("discord.prefix", "[DragonEgg] ") + "An admin returned the Dragon Egg to home.");
    }

    public void setHolderByAdmin(Player player) {
        if (player == null) return;
        eggManager.setHolder(player, EggManager.EggEventReason.ADMIN);
        discord.announceAsync(getConfig().getString("discord.prefix", "[DragonEgg] ") + "Admin set holder to " + player.getName() + ".");
    }

    public void clearHolderOnly() {
        UUID holder = state.getHolder();
        if (holder != null) {
            Player oldOnline = Bukkit.getPlayer(holder);
            if (oldOnline != null) {
                eggManager.clearEggs(oldOnline);
                eggManager.removeEggBuffs(oldOnline);
            }
        }
        state.setHolder(null);
        state.save();
        dynmap.clearMarker();
    }

    public void setReturnLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        getConfig().set("return-location.world", loc.getWorld().getName());
        getConfig().set("return-location.x", loc.getX());
        getConfig().set("return-location.y", loc.getY());
        getConfig().set("return-location.z", loc.getZ());
        getConfig().set("return-location.yaw", loc.getYaw());
        getConfig().set("return-location.pitch", loc.getPitch());
        saveConfig();
    }

    public Location getReturnLocation() {
        ConfigurationSection s = getConfig().getConfigurationSection("return-location");
        if (s == null) return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();

        String worldName = s.getString("world", "world");
        World w = Bukkit.getWorld(worldName);
        double x = s.getDouble("x", 0.5);
        double y = s.getDouble("y", 100.0);
        double z = s.getDouble("z", 0.5);
        float yaw = (float) s.getDouble("yaw", 0.0);
        float pitch = (float) s.getDouble("pitch", 0.0);
        return new Location(w, x, y, z, yaw, pitch);
    }

    public boolean isDebugEnabled() {
        return getConfig().getBoolean("debug.enabled", false);
    }

    public void logDebug(String msg) {
        if (isDebugEnabled()) {
            getLogger().info("[DEBUG] " + msg);
        }
    }
}

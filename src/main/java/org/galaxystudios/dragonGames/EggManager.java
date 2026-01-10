package org.galaxystudios.dragonGames;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Owns the "who holds the egg" state and the rules for transferring/returning it.
 */
public final class EggManager {

    public static final Material EGG_MATERIAL = Material.DRAGON_EGG;

    private final DragonGames plugin;

    public EggManager(DragonGames plugin) {
        this.plugin = plugin;
    }

    public UUID getHolder() {
        return plugin.getState().getHolder();
    }

    public Player getOnlineHolder() {
        UUID holder = getHolder();
        return holder == null ? null : Bukkit.getPlayer(holder);
    }

    public boolean playerHasEgg(Player player) {
        return countEggs(player.getInventory()) > 0;
    }

    public int countEggs(PlayerInventory inv) {
        int c = 0;
        for (ItemStack it : inv.getContents()) {
            if (it != null && it.getType() == EGG_MATERIAL) c += it.getAmount();
        }
        ItemStack off = inv.getItemInOffHand();
        if (off != null && off.getType() == EGG_MATERIAL) c += off.getAmount();
        return c;
    }

    public void ensureEggInInventory(Player player) {
        normalizeEggInventory(player);
    }

    public void giveEggToPlayer(Player player) {
        normalizeEggInventory(player);
        PlayerInventory inv = player.getInventory();
        // If player already has an egg, don't add more
        if (countEggs(inv) >= 1) return;

        HashMap<Integer, ItemStack> leftover = inv.addItem(new ItemStack(EGG_MATERIAL, 1));
        if (!leftover.isEmpty()) {
            ItemStack off = inv.getItemInOffHand();
            if (off == null || off.getType() == Material.AIR) {
                inv.setItemInOffHand(new ItemStack(EGG_MATERIAL, 1));
            } else {
                // inventory is full; drop currently held item and replace it with the egg
                ItemStack inHand = inv.getItemInMainHand();
                if (inHand != null && inHand.getType() != Material.AIR) {
                    player.getWorld().dropItemNaturally(player.getLocation(), inHand.clone());
                }
                inv.setItemInMainHand(new ItemStack(EGG_MATERIAL, 1));
            }
        }
    }

    public void clearEggs(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.getType() == EGG_MATERIAL) inv.setItem(i, null);
        }
        ItemStack off = inv.getItemInOffHand();
        if (off != null && off.getType() == EGG_MATERIAL) inv.setItemInOffHand(null);
    }

    public void normalizeEggInventory(Player player) {
        PlayerInventory inv = player.getInventory();
        int eggsFound = countEggs(inv);
        if (eggsFound > 1) {
            clearEggs(player);
            eggsFound = 0;
        }
        if (eggsFound == 0 && plugin.getState().getHolder() != null && plugin.getState().getHolder().equals(player.getUniqueId())) {
            // holder missing egg: add one respecting full-inventory rules
            HashMap<Integer, ItemStack> leftover = inv.addItem(new ItemStack(EGG_MATERIAL, 1));
            if (!leftover.isEmpty()) {
                ItemStack off = inv.getItemInOffHand();
                if (off == null || off.getType() == Material.AIR) {
                    inv.setItemInOffHand(new ItemStack(EGG_MATERIAL, 1));
                } else {
                    ItemStack inHand = inv.getItemInMainHand();
                    if (inHand != null && inHand.getType() != Material.AIR) {
                        player.getWorld().dropItemNaturally(player.getLocation(), inHand.clone());
                    }
                    inv.setItemInMainHand(new ItemStack(EGG_MATERIAL, 1));
                }
            }
        }
        if (eggsFound >= 1 && (plugin.getState().getHolder() == null || !plugin.getState().getHolder().equals(player.getUniqueId()))) {
            // non-holder has egg: remove it
            clearEggs(player);
        }
    }

    public void setHolder(Player player, EggEventReason reason) {
        UUID old = getHolder();
        UUID now = player.getUniqueId();

        if (Objects.equals(old, now)) {
            ensureEggInInventory(player);
            applyEggBuffs(player);
            plugin.getState().touchActivity(now);
            plugin.getState().save();
            plugin.getDynmap().updateMarker(player);
            return;
        }

        if (old != null) {
            Player oldOnline = Bukkit.getPlayer(old);
            if (oldOnline != null) {
                clearEggs(oldOnline);
                removeEggBuffs(oldOnline);
            }
        }

        plugin.getState().setHolder(now);
        plugin.getState().resetWeeklyIfNeeded();
        plugin.getState().touchActivity(now);
        plugin.getState().save();

        ensureEggInInventory(player);
        applyEggBuffs(player);
        plugin.getDynmap().updateMarker(player);

        announceCapture(player, reason);
        playCaptureEffects(player.getLocation());
    }

    public void returnEggToReturnLocation(String message) {
        UUID holder = getHolder();
        if (holder != null) {
            Player oldOnline = Bukkit.getPlayer(holder);
            if (oldOnline != null) {
                clearEggs(oldOnline);
                removeEggBuffs(oldOnline);
            }
        }

        plugin.getState().setHolder(null);
        plugin.getState().save();
        plugin.getDynmap().clearMarker();

        Location loc = plugin.getReturnLocation();
        if (loc == null || loc.getWorld() == null) {
            plugin.getLogger().warning("Return location world is missing; cannot return egg.");
            return;
        }

        // place the egg as a block at the altar instead of dropping an item
        World w = loc.getWorld();
        w.getBlockAt(loc).setType(EGG_MATERIAL, false);

        if (message != null && !message.isBlank()) {
            Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "[DragonGames] " + ChatColor.LIGHT_PURPLE + message);
        }

        // DRAGON_BREATH on 1.21+ requires a Float data parameter; use 0f for default
        w.spawnParticle(Particle.DRAGON_BREATH, loc, 200, 1.0, 1.0, 1.0, 0.02, 0f);
        w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
        w.strikeLightningEffect(loc);
    }

    public void applyEggBuffs(Player player) {
        for (PotionEffect e : plugin.getConfiguredBuffs()) {
            player.addPotionEffect(e);
        }
    }

    public void removeEggBuffs(Player player) {
        for (PotionEffect e : plugin.getConfiguredBuffs()) {
            PotionEffectType t = e.getType();
            if (t != null) player.removePotionEffect(t);
        }
    }

    private void announceCapture(Player player, EggEventReason reason) {
        if (!plugin.getConfig().getBoolean("announce.capture", true)) return;

        String msg;
        switch (reason) {
            case PICKUP -> msg = player.getName() + " has captured the Dragon Egg! A bounty is on their head.";
            case TRANSFER_ON_DEATH -> msg = player.getName() + " now holds the Dragon Egg! The hunt continues.";
            case ADMIN -> msg = player.getName() + " was chosen as Dragon Egg holder.";
            default -> msg = player.getName() + " is now the Dragon Egg holder.";
        }
        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "[DragonGames] " + ChatColor.LIGHT_PURPLE + msg);
    }

    private void playCaptureEffects(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        World w = loc.getWorld();
        // DRAGON_BREATH on 1.21+ requires a Float data parameter; use 0f for default
        w.spawnParticle(Particle.DRAGON_BREATH, loc, 150, 0.8, 0.8, 0.8, 0.02, 0f);
        w.spawnParticle(Particle.PORTAL, loc, 300, 1.2, 1.2, 1.2, 0.1);
        w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 1.0f, 1.0f);
    }

    public long getRequiredWeeklySeconds() {
        return plugin.getConfig().getLong("required-playtime-per-week-seconds", 7200L);
    }

    public List<PotionEffect> parseBuffsFromConfig() {
        List<PotionEffect> out = new ArrayList<>();
        List<Map<?, ?>> list = plugin.getConfig().getMapList("buffs");
        for (Map<?, ?> m : list) {
            Object typeObj = m.get("type");
            if (typeObj == null) continue;
            PotionEffectType type = PotionEffectType.getByName(String.valueOf(typeObj));
            if (type == null) {
                plugin.getLogger().warning("Unknown potion type in config: " + typeObj);
                continue;
            }

            int amp = 0;
            Object ampObj = m.get("amplifier");
            if (ampObj != null) {
                try {
                    amp = Integer.parseInt(String.valueOf(ampObj));
                } catch (NumberFormatException ignored) {
                }
            }

            out.add(new PotionEffect(type, 20 * 60 * 60, amp, true, false, true));
        }
        return out;
    }

    public void placeEggAtHomeIfNoHolder() {
        if (plugin.getState().getHolder() != null) return;
        Location loc = plugin.getReturnLocation();
        if (loc == null || loc.getWorld() == null) {
            plugin.getLogger().warning("Cannot place egg: return location is not set or world missing");
            return;
        }
        World w = loc.getWorld();
        w.getBlockAt(loc).setType(EGG_MATERIAL, false);
        plugin.logDebug("Placed dragon egg block at home: " + loc);
    }

    public enum EggEventReason {
        PICKUP,
        TRANSFER_ON_DEATH,
        ADMIN,
        UNKNOWN
    }
}

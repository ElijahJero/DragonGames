package org.galaxystudios.dragonGames;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class DragonEggListener implements Listener {

    private final DragonGames plugin;
    private final EggManager eggs;

    public DragonEggListener(DragonGames plugin, EggManager eggs) {
        this.plugin = plugin;
        this.eggs = eggs;
    }

    private void pickupEggBlock(Player player, org.bukkit.block.Block block) {
        block.setType(Material.AIR, false);
        eggs.setHolder(player, EggManager.EggEventReason.PICKUP);
        plugin.getDiscord().announceAsync(plugin.getConfig().getString("discord.prefix", "[DragonEgg] ") +
                player.getName() + " captured the Dragon Egg!");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent e) {
        if (!plugin.isGameEnabled()) return;
        if (e.getClickedBlock() == null) return;
        if (e.getClickedBlock().getType() != EggManager.EGG_MATERIAL) return;
        if (!(e.getPlayer() instanceof Player p)) return;

        e.setCancelled(true);
        pickupEggBlock(p, e.getClickedBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent e) {
        if (e.getBlock().getType() != EggManager.EGG_MATERIAL) return;
        if (!(e.getPlayer() instanceof Player p)) return;

        e.setCancelled(true);
        pickupEggBlock(p, e.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent e) {
        if (!plugin.isGameEnabled()) return;
        if (e.getItem().getItemStack().getType() != EggManager.EGG_MATERIAL) return;

        e.setCancelled(true); // prevent vanilla pickup flow/teleport issues
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            eggs.setHolder(p, EggManager.EggEventReason.PICKUP);
            plugin.getDiscord().announceAsync(plugin.getConfig().getString("discord.prefix", "[DragonEgg] ") +
                    p.getName() + " captured the Dragon Egg!");
            e.getItem().remove();
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack stack = e.getItemDrop().getItemStack();
        if (stack.getType() != EggManager.EGG_MATERIAL) return;

        UUID holder = eggs.getHolder();
        if (holder != null && holder.equals(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.DARK_PURPLE + "[DragonEgg] " + ChatColor.LIGHT_PURPLE + "You can't drop the Dragon Egg.");
            Bukkit.getScheduler().runTask(plugin, () -> eggs.ensureEggInInventory(e.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        if (e.getBlockPlaced().getType() != EggManager.EGG_MATERIAL) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(ChatColor.DARK_PURPLE + "[DragonEgg] " + ChatColor.LIGHT_PURPLE + "You can't place the Dragon Egg.");
        Bukkit.getScheduler().runTask(plugin, () -> eggs.ensureEggInInventory(e.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        UUID holder = eggs.getHolder();
        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();

        boolean involvesEgg = (current != null && current.getType() == EggManager.EGG_MATERIAL) ||
                (cursor != null && cursor.getType() == EggManager.EGG_MATERIAL);

        if (involvesEgg) {
            // Prevent putting egg into containers; ensure only the holder keeps it
            if (holder == null || !holder.equals(p.getUniqueId())) {
                e.setCancelled(true);
                return;
            }
            // Cancel attempts to move egg into other inventories (e.g., chests)
            if (e.getClickedInventory() != p.getInventory()) {
                e.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> eggs.ensureEggInInventory(p));
                return;
            }
            // Prevent moving within inventory too; just keep it with player
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> eggs.ensureEggInInventory(p));
        }

        if (holder == null || !holder.equals(p.getUniqueId())) return;

        // ...existing code handling for holder movements...
        if ((current != null && current.getType() == EggManager.EGG_MATERIAL) ||
                (cursor != null && cursor.getType() == EggManager.EGG_MATERIAL)) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> eggs.ensureEggInInventory(p));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack old = e.getOldCursor();
        for (ItemStack it : e.getNewItems().values()) {
            if ((old != null && old.getType() == EggManager.EGG_MATERIAL) || (it != null && it.getType() == EggManager.EGG_MATERIAL)) {
                e.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> eggs.ensureEggInInventory(p));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (eggs.getHolder() != null && eggs.getHolder().equals(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                eggs.ensureEggInInventory(p);
                eggs.applyEggBuffs(p);
                plugin.getDynmap().updateMarker(p);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID holder = eggs.getHolder();
        if (holder != null && holder.equals(e.getPlayer().getUniqueId())) {
            plugin.getState().touchActivity(holder);
            plugin.getState().save();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        UUID holder = eggs.getHolder();
        if (holder == null || !holder.equals(dead.getUniqueId())) return;

        e.getDrops().removeIf(i -> i != null && i.getType() == EggManager.EGG_MATERIAL);

        Player killer = dead.getKiller();
        if (killer != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                eggs.setHolder(killer, EggManager.EggEventReason.TRANSFER_ON_DEATH);
                plugin.getDiscord().announceAsync(plugin.getConfig().getString("discord.prefix", "[DragonEgg] ") +
                        killer.getName() + " claimed the Dragon Egg by killing " + dead.getName() + "!");
            });
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                eggs.returnEggToReturnLocation(dead.getName() + " died, and the Dragon Egg returned to the altar!");
                plugin.getDiscord().announceAsync(plugin.getConfig().getString("discord.prefix", "[DragonEgg] ") +
                        "The Dragon Egg returned to home because " + dead.getName() + " died.");
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!plugin.isPvpOverrideEnabled()) return;

        Entity victim = e.getEntity();
        if (!(victim instanceof Player vp)) return;

        UUID holder = eggs.getHolder();
        if (holder == null || !holder.equals(vp.getUniqueId())) return;

        if (e.isCancelled()) {
            e.setCancelled(false);
        }
    }
}

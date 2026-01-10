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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import com.sk89q.worldguard.bukkit.protection.events.DisallowedPVPEvent;

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

        if (!involvesEgg) return;

        // Only the current holder can move the egg at all
        if (holder == null || !holder.equals(p.getUniqueId())) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> eggs.ensureEggInInventory(p));
            return;
        }

        Inventory clicked = e.getClickedInventory();
        PlayerInventory pinv = p.getInventory();

        // Block moving the egg to any non-player inventory (chests, anvils, etc.)
        if (clicked != null && clicked != pinv) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> eggs.ensureEggInInventory(p));
            return;
        }

        // Allow rearranging inside the player's own inventory/offhand
        // but disallow dropping/taking out via hotbar swap/number keys to container
        if (e.getAction().name().contains("DROP")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> eggs.ensureEggInInventory(p));
            return;
        }

        // If the click targets outside slots (e.g. creative drop), cancel
        if (clicked == null) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> eggs.ensureEggInInventory(p));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        UUID holder = eggs.getHolder();
        ItemStack cursor = e.getOldCursor();
        if (cursor == null || cursor.getType() != EggManager.EGG_MATERIAL) return;

        // Only holder can drag the egg; only within their own inventory slots
        if (holder == null || !holder.equals(p.getUniqueId())) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> eggs.ensureEggInInventory(p));
            return;
        }

        // If any target slot is outside player inventory, block
        for (int rawSlot : e.getRawSlots()) {
            if (rawSlot >= p.getInventory().getSize() + 5) { // includes crafting grid + armor/offhand margin
                e.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> eggs.ensureEggInInventory(p));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID holder = eggs.getHolder();

        // If player is supposed to be holder, ensure exactly one egg + buffs
        if (holder != null && holder.equals(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                eggs.clearEggs(p);
                eggs.giveEggToPlayer(p);
                eggs.applyEggBuffs(p);
                plugin.getDynmap().updateMarker(p);
            });
            return;
        }

        // If player is not holder, strip any eggs they may have
        Bukkit.getScheduler().runTask(plugin, () -> eggs.clearEggs(p));
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

    @EventHandler(priority = EventPriority.HIGHEST)
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldGuardDisallowedPvp(DisallowedPVPEvent e) {
        if (!plugin.isPvpOverrideEnabled()) return;
        Player vp = e.getDefender();
        if (vp == null) return;

        UUID holder = eggs.getHolder();
        if (holder != null && holder.equals(vp.getUniqueId())) {
            plugin.logDebug("Allowing PVP on egg holder via WorldGuard hook for " + vp.getName());
            e.setCancelled(true);
        }
    }
}

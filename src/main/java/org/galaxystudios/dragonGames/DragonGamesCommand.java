package org.galaxystudios.dragonGames;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DragonGamesCommand implements CommandExecutor, TabCompleter {

    private final DragonGames plugin;

    public DragonGamesCommand(DragonGames plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        if (!sender.hasPermission("dragongames.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "sethome" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("This command must be used by a player.");
                    return true;
                }
                Location loc = p.getLocation();
                plugin.setReturnLocation(loc);
                plugin.startDragonGames(loc);
                sender.sendMessage(ChatColor.DARK_PURPLE + "[DragonGames] " + ChatColor.LIGHT_PURPLE + "Home set and Dragon Games started.");
                return true;
            }
            case "start" -> {
                plugin.startDragonGames(plugin.getReturnLocation());
                sender.sendMessage(ChatColor.DARK_PURPLE + "[DragonGames] " + ChatColor.LIGHT_PURPLE + "Dragon Games started.");
                return true;
            }
            case "stop" -> {
                plugin.stopDragonGames();
                sender.sendMessage(ChatColor.DARK_PURPLE + "[DragonGames] " + ChatColor.LIGHT_PURPLE + "Dragon Games stopped.");
                return true;
            }
            case "status" -> {
                UUID holder = plugin.getState().getHolder();
                String holderName = holder == null ? "none" : holder.toString();
                Player online = holder == null ? null : Bukkit.getPlayer(holder);
                if (online != null) holderName = online.getName() + " (online)";

                sender.sendMessage(ChatColor.DARK_PURPLE + "[DragonGames] " + ChatColor.LIGHT_PURPLE +
                        "enabled=" + plugin.isGameEnabled() + ", holder=" + holderName +
                        ", weeklyPlaySeconds=" + plugin.getState().getHolderWeeklyPlaySeconds());
                if (plugin.isDebugEnabled()) {
                    sender.sendMessage(ChatColor.GRAY + "debug=true, returnLocation=" + plugin.getReturnLocation());
                }
                return true;
            }
            case "returnegg" -> {
                plugin.clearHolderAndReturnEgg();
                sender.sendMessage(ChatColor.DARK_PURPLE + "[DragonGames] " + ChatColor.LIGHT_PURPLE + "Egg returned to home.");
                return true;
            }
            case "setholder" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " setholder <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found (must be online): " + args[1]);
                    return true;
                }
                plugin.setHolderByAdmin(target);
                sender.sendMessage(ChatColor.DARK_PURPLE + "[DragonGames] " + ChatColor.LIGHT_PURPLE + "Holder set to " + target.getName());
                return true;
            }
            case "clearholder" -> {
                plugin.clearHolderOnly();
                sender.sendMessage(ChatColor.DARK_PURPLE + "[DragonGames] " + ChatColor.LIGHT_PURPLE + "Cleared holder (no egg spawned).");
                return true;
            }
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(ChatColor.DARK_PURPLE + "[DragonGames] " + ChatColor.LIGHT_PURPLE + "Reloaded config.yml (state.yml unchanged).");
                return true;
            }
            default -> {
                sendHelp(sender, label);
                return true;
            }
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.DARK_PURPLE + "[DragonGames] " + ChatColor.LIGHT_PURPLE + "Commands:");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/" + label + " sethome" + ChatColor.GRAY + " - set egg home here and start games");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/" + label + " start" + ChatColor.GRAY + " - start games");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/" + label + " stop" + ChatColor.GRAY + " - stop games");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/" + label + " status" + ChatColor.GRAY + " - show current state");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/" + label + " returnegg" + ChatColor.GRAY + " - return egg to home");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/" + label + " setholder <player>" + ChatColor.GRAY + " - make player holder");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/" + label + " clearholder" + ChatColor.GRAY + " - clear holder without spawning egg");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/" + label + " reload" + ChatColor.GRAY + " - reload config.yml");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> subcommands = List.of("sethome", "start", "stop", "status", "returnegg", "setholder", "clearholder", "reload");
            for (String sub : subcommands) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && "setholder".equalsIgnoreCase(args[0])) {
            String partial = args[1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                String name = player.getName();
                if (name.toLowerCase().startsWith(partial)) {
                    completions.add(name);
                }
            }
        }
        return completions;
    }
}

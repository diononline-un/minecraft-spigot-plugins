package me.diono.chestmanager.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import me.diono.chestmanager.ChestManagerPlugin;
import me.diono.chestmanager.task.SortTask;
import me.diono.chestmanager.trait.ChestManagerTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;

public class ChestManagerCommand implements CommandExecutor, TabCompleter {

    private final ChestManagerPlugin plugin;

    public ChestManagerCommand(ChestManagerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "scan" -> handleScan(sender, args);
            case "home" -> handleHome(sender, args);
            case "status" -> handleStatus(sender, args);
            case "radius" -> handleRadius(sender, args);
            case "interval" -> handleInterval(sender, args);
            case "sortnow" -> handleSortNow(sender, args);
            case "list" -> handleList(sender);
            case "setup" -> handleSetup(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return;
        }
        if (!player.hasPermission("chestmanager.use")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /chestmanager create <name>");
            return;
        }

        String name = args[1];
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER, name);
        npc.getOrAddTrait(ChestManagerTrait.class);
        npc.spawn(player.getLocation());
        ChestManagerTrait trait = npc.getOrAddTrait(ChestManagerTrait.class);
        trait.setHome(player.getLocation());
        trait.onServerStart();
        trait.getChestArea().scan();

        player.sendMessage(ChatColor.GREEN + "ChestManager NPC '" + name + "' created!");
        player.sendMessage(ChatColor.GRAY + "Home set to your location, area scanned.");
        player.sendMessage("");
        player.sendMessage(trait.getChestArea().getStatus());
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Type /cm setup for full setup guide.");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chestmanager.use")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /chestmanager delete <name>");
            return;
        }

        NPC npc = findNpc(args[1]);
        if (npc == null || !npc.hasTrait(ChestManagerTrait.class)) {
            sender.sendMessage(ChatColor.RED + "ChestManager NPC not found.");
            return;
        }
        CitizensAPI.getNPCRegistry().deregister(npc);
        npc.destroy();
        sender.sendMessage(ChatColor.GREEN + "ChestManager NPC deleted.");
    }

    private void handleScan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chestmanager.use")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return;
        }

        ChestManagerTrait trait = findTrait(sender, args);
        if (trait == null) return;

        trait.getChestArea().scan();
        sender.sendMessage(ChatColor.GREEN + "Area rescanned for " + trait.getNPC().getName());
        sender.sendMessage(trait.getChestArea().getStatus());
    }

    private void handleHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return;
        }
        if (!player.hasPermission("chestmanager.use")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return;
        }

        ChestManagerTrait trait = findTrait(sender, args);
        if (trait == null) return;

        trait.setHome(player.getLocation());
        trait.getChestArea().scan();
        player.sendMessage(ChatColor.GREEN + "Home set for " + trait.getNPC().getName()
                + ". Scan complete.");
        player.sendMessage(trait.getChestArea().getStatus());
    }

    private void handleStatus(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chestmanager.use")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return;
        }

        ChestManagerTrait trait = findTrait(sender, args);
        if (trait == null) return;

        sender.sendMessage(ChatColor.GOLD + "=== " + trait.getNPC().getName() + " Status ===");
        sender.sendMessage("Home: " + formatLoc(trait.getHome()));
        sender.sendMessage("Scan radius: " + trait.getScanRadius() + " blocks");
        sender.sendMessage("Sort interval: " + (trait.getSortIntervalTicks() / 20) + " seconds");
        sender.sendMessage("Current task: " + (trait.getCurrentTask() != null
                ? trait.getCurrentTask().status() : "none"));
        sender.sendMessage("");
        sender.sendMessage(trait.getChestArea().getStatus());
    }

    private void handleRadius(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chestmanager.use")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /chestmanager radius <name> <value>");
            return;
        }

        ChestManagerTrait trait = findTrait(sender, args);
        if (trait == null) return;

        try {
            int radius = Integer.parseInt(args[2]);
            if (radius < 5 || radius > 100) {
                sender.sendMessage(ChatColor.RED + "Radius must be between 5 and 100.");
                return;
            }
            trait.setScanRadius(radius);
            trait.getChestArea().scan();
            sender.sendMessage(ChatColor.GREEN + "Scan radius set to " + radius + ".");
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid number: " + args[2]);
        }
    }

    private void handleInterval(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chestmanager.use")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /chestmanager interval <name> <seconds>");
            return;
        }

        ChestManagerTrait trait = findTrait(sender, args);
        if (trait == null) return;

        try {
            int seconds = Integer.parseInt(args[2]);
            if (seconds < 5 || seconds > 3600) {
                sender.sendMessage(ChatColor.RED + "Interval must be between 5 and 3600 seconds.");
                return;
            }
            trait.setSortIntervalTicks(seconds * 20);
            sender.sendMessage(ChatColor.GREEN + "Sort interval set to " + seconds + " seconds.");
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid number: " + args[2]);
        }
    }

    private void handleSortNow(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chestmanager.use")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return;
        }

        ChestManagerTrait trait = findTrait(sender, args);
        if (trait == null) return;

        trait.setTask(new SortTask());
        sender.sendMessage(ChatColor.GREEN + trait.getNPC().getName() + " is now sorting.");
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== ChestManager NPCs ===");
        boolean found = false;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (!npc.hasTrait(ChestManagerTrait.class)) continue;
            ChestManagerTrait trait = npc.getTraitNullable(ChestManagerTrait.class);
            if (trait == null) continue;
            found = true;
            sender.sendMessage(ChatColor.WHITE + " - " + npc.getName()
                    + ChatColor.GRAY + " [" + (trait.getCurrentTask() != null
                    ? trait.getCurrentTask().status() : "none") + "]");
        }
        if (!found) {
            sender.sendMessage(ChatColor.GRAY + "No ChestManager NPCs found.");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== ChestManager Commands ===");
        sender.sendMessage("/cm create <name> - Create a ChestManager Villager NPC");
        sender.sendMessage("/cm delete <name> - Delete a ChestManager NPC");
        sender.sendMessage("/cm home [name] - Set NPC home (center of 40x40 area)");
        sender.sendMessage("/cm scan [name] - Rescan area for chests");
        sender.sendMessage("/cm status [name] - Show NPC and chest area status");
        sender.sendMessage("/cm list - List all ChestManager NPCs");
        sender.sendMessage("/cm radius <name> <5-100> - Set scan radius (default 40)");
        sender.sendMessage("/cm interval <name> <5-3600> - Set sort interval in seconds (default 30)");
        sender.sendMessage("/cm sortnow [name] - Force immediate sorting");
        sender.sendMessage(ChatColor.GRAY + "Type /cm setup for usage instructions");
    }

    private void handleSetup(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "ChestManager Setup Guide");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "1. Create the NPC:");
        sender.sendMessage(ChatColor.GRAY + "   /cm create Sorter");
        sender.sendMessage(ChatColor.GRAY + "   (Home + scan are automatic - stand in the center!)");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "2. Place chests with signs:");
        sender.sendMessage(ChatColor.GRAY + "   Special chests - sign ON the chest:");
        sender.sendMessage(ChatColor.WHITE + "     [INPUT]      " + ChatColor.GRAY + "- sign directly attached to input chest");
        sender.sendMessage(ChatColor.WHITE + "     [REQUEST]    " + ChatColor.GRAY + "- sign directly attached to request chest");
        sender.sendMessage(ChatColor.WHITE + "     [UNSORTABLE] " + ChatColor.GRAY + "- sign directly attached to fallback chest");
        sender.sendMessage(ChatColor.GRAY + "   (also accepts [unsortet] or [sonstige])");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "   Category chests - sign on block NEXT TO chest:");
        sender.sendMessage(ChatColor.WHITE + "     [ORES]       " + ChatColor.GRAY + "- Ores & Gems");
        sender.sendMessage(ChatColor.WHITE + "     [BLOCKS]     " + ChatColor.GRAY + "- Building blocks");
        sender.sendMessage(ChatColor.WHITE + "     [WOOD]       " + ChatColor.GRAY + "- Wood & planks");
        sender.sendMessage(ChatColor.WHITE + "     [TOOLS]      " + ChatColor.GRAY + "- Tools & Weapons");
        sender.sendMessage(ChatColor.WHITE + "     [ARMOR]      " + ChatColor.GRAY + "- Armor");
        sender.sendMessage(ChatColor.WHITE + "     [FOOD]       " + ChatColor.GRAY + "- Food");
        sender.sendMessage(ChatColor.WHITE + "     [REDSTONE]   " + ChatColor.GRAY + "- Redstone");
        sender.sendMessage(ChatColor.WHITE + "     [NATURAL]    " + ChatColor.GRAY + "- Plants & natural");
        sender.sendMessage(ChatColor.WHITE + "     [MOB_DROPS]  " + ChatColor.GRAY + "- Mob drops");
        sender.sendMessage(ChatColor.WHITE + "     [MISC]       " + ChatColor.GRAY + "- Everything else");
        sender.sendMessage(ChatColor.GRAY + "     Example: [Block] [Chest]  <- sign on block, chest next to it");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "3. Scan the area:");
        sender.sendMessage(ChatColor.GRAY + "   /cm scan Sorter  (or /cm home Sorter to move + rescan)");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "4. Start using it:");
        sender.sendMessage(ChatColor.GRAY + "   - Dump items into [INPUT] chest - NPC notices immediately");
        sender.sendMessage(ChatColor.GRAY + "   - Alternatively NPC auto-sorts every 30s");
        sender.sendMessage(ChatColor.GRAY + "   - Right-click [REQUEST] chest to browse & request items");
        sender.sendMessage(ChatColor.GRAY + "   - /cm sortnow Sorter to force immediate sorting");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "NPC scans " + ChatColor.WHITE + "40 blocks horizontal"
                + ChatColor.GRAY + ", from home Y level " + ChatColor.WHITE + "down to bedrock"
                + ChatColor.GRAY + ".");
    }

    private ChestManagerTrait findTrait(CommandSender sender, String[] args) {
        ChestManagerTrait trait;

        if (args.length >= 2) {
            NPC npc = findNpc(args[1]);
            if (npc == null || !npc.hasTrait(ChestManagerTrait.class)) {
                sender.sendMessage(ChatColor.RED + "ChestManager NPC not found.");
                return null;
            }
            trait = npc.getTraitNullable(ChestManagerTrait.class);
        } else if (sender instanceof Player player) {
            trait = findNearestTrait(player);
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /chestmanager <subcommand> <name>");
            return null;
        }

        if (trait == null) {
            sender.sendMessage(ChatColor.RED + "No ChestManager NPC found.");
        }
        return trait;
    }

    private ChestManagerTrait findNearestTrait(Player player) {
        ChestManagerTrait nearest = null;
        double best = Double.MAX_VALUE;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (!npc.isSpawned() || npc.getEntity() == null) continue;
            if (!npc.hasTrait(ChestManagerTrait.class)) continue;
            ChestManagerTrait trait = npc.getTraitNullable(ChestManagerTrait.class);
            if (trait == null) continue;
            double dist = npc.getEntity().getLocation().distanceSquared(player.getLocation());
            if (dist < best && dist < 400) {
                best = dist;
                nearest = trait;
            }
        }
        return nearest;
    }

    private NPC findNpc(String name) {
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.getName() != null && npc.getName().equalsIgnoreCase(name)) {
                return npc;
            }
        }
        return null;
    }

    private String formatLoc(Location loc) {
        if (loc == null) return "not set";
        return String.format("%.0f %.0f %.0f %s",
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getWorld() != null ? loc.getWorld().getName() : "");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        String[] subcommands = {"create", "delete", "scan", "home", "status", "radius",
                "interval", "sortnow", "list", "setup"};

        if (args.length == 1) {
            for (String sub : subcommands) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    suggestions.add(sub);
                }
            }
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("create")
                && !args[0].equalsIgnoreCase("list")) {
            for (NPC npc : CitizensAPI.getNPCRegistry()) {
                if (npc.hasTrait(ChestManagerTrait.class) && npc.getName() != null) {
                    if (npc.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        suggestions.add(npc.getName());
                    }
                }
            }
        }
        return suggestions;
    }
}

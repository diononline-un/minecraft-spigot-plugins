package me.diono.workernpcs.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import me.diono.workernpcs.WorkerNPCPlugin;
import me.diono.workernpcs.task.*;
import me.diono.workernpcs.trait.AiWorkerTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;

public class WorkerCommand implements CommandExecutor, TabCompleter {

    private final WorkerNPCPlugin plugin;

    public WorkerCommand(WorkerNPCPlugin plugin) {
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
            case "home" -> handleHome(sender, args);
            case "list" -> handleList(sender);
            case "task" -> handleTask(sender, args);
            case "config" -> handleConfig(sender);
            case "inventory" -> handleInventory(sender, args);
            case "teach" -> handleTeach(sender, args);
            case "forget" -> handleForget(sender, args);
            case "memory" -> handleMemory(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return;
        }
        if (!sender.hasPermission("workernpcs.use")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /worker create <name>");
            return;
        }
        if (!plugin.canCreateMore()) {
            sender.sendMessage(ChatColor.RED + "Max NPC limit reached (" + plugin.getMaxNpcs() + ").");
            return;
        }

        String name = args[1];
        NPC npc = plugin.createWorker(name);
        npc.spawn(player.getLocation());
        npc.getOrAddTrait(AiWorkerTrait.class).onServerStart();
        sender.sendMessage(ChatColor.GREEN + "Worker NPC '" + name + "' created! Use @Name to chat.");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("workernpcs.use")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /worker delete <name>");
            return;
        }

        NPC npc = findNpc(args[1]);
        if (npc == null) {
            sender.sendMessage(ChatColor.RED + "NPC not found.");
            return;
        }
        CitizensAPI.getNPCRegistry().deregister(npc);
        npc.destroy();
        sender.sendMessage(ChatColor.GREEN + "Worker NPC deleted.");
    }

    private void handleHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return;
        }
        if (!sender.hasPermission("workernpcs.use")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return;
        }

        NPC npc;
        if (args.length >= 2) {
            npc = findNpc(args[1]);
        } else {
            npc = findNearestWorker(player);
        }

        if (npc == null) {
            sender.sendMessage(ChatColor.RED + "No worker NPC found. Usage: /worker home [name]");
            return;
        }

        AiWorkerTrait trait = npc.getTraitNullable(AiWorkerTrait.class);
        if (trait == null) {
            sender.sendMessage(ChatColor.RED + "That NPC is not a worker.");
            return;
        }

        trait.setHome(player.getLocation());
        sender.sendMessage(ChatColor.GREEN + "Home set for " + npc.getName() + ".");
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Worker NPCs (" + plugin.getMaxNpcs() + " max) ===");
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            AiWorkerTrait trait = npc.getTraitNullable(AiWorkerTrait.class);
            if (trait == null) continue;
            String task = trait.getCurrentTask() != null
                ? trait.getCurrentTask().status() : "unknown";
            sender.sendMessage(ChatColor.WHITE + " - " + npc.getName()
                + ChatColor.GRAY + " [" + task + "]");
        }
    }

    private void handleTask(CommandSender sender, String[] args) {
        if (!sender.hasPermission("workernpcs.use")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /worker task <name> <task>");
            sender.sendMessage(ChatColor.GRAY + "Tasks: follow, farm, breed, gohome, idle");
            return;
        }

        NPC npc = findNpc(args[1]);
        if (npc == null) {
            sender.sendMessage(ChatColor.RED + "NPC not found.");
            return;
        }

        AiWorkerTrait trait = npc.getTraitNullable(AiWorkerTrait.class);
        if (trait == null) {
            sender.sendMessage(ChatColor.RED + "That NPC is not a worker.");
            return;
        }

        String taskName = args[2].toLowerCase();
        switch (taskName) {
            case "follow":
                if (sender instanceof Player player) {
                    trait.setFollowTarget(player);
                }
                break;
            case "farm":
                trait.setTask(new FarmTask(trait));
                break;
            case "breed":
                trait.setTask(new BreedTask(trait));
                break;
            case "gohome":
                trait.setTask(new GoHomeTask(trait.getHome()));
                break;
            case "idle":
                trait.setTask(new IdleTask());
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown task: " + taskName);
                return;
        }
        sender.sendMessage(ChatColor.GREEN + npc.getName() + " → " + taskName);
    }

    private void handleConfig(CommandSender sender) {
        if (!sender.hasPermission("workernpcs.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "=== WorkerNPCs Config ===");
        sender.sendMessage("Model: " + plugin.getModel());
        sender.sendMessage("Max NPCs: " + plugin.getMaxNpcs());
        sender.sendMessage("API Key: " + (plugin.getApiKey().isEmpty() ? "not set"
            : plugin.getApiKey().substring(0, 5) + "..." + plugin.getApiKey().substring(
                plugin.getApiKey().length() - 4)));
    }

    private void handleInventory(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return;
        }
        if (!sender.hasPermission("workernpcs.use")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return;
        }

        NPC npc;
        if (args.length >= 2) {
            npc = findNpc(args[1]);
        } else {
            npc = findNearestWorker(player);
        }

        if (npc == null) {
            sender.sendMessage(ChatColor.RED + "No worker NPC found.");
            return;
        }

        AiWorkerTrait trait = npc.getTraitNullable(AiWorkerTrait.class);
        if (trait == null) {
            sender.sendMessage(ChatColor.RED + "That NPC is not a worker.");
            return;
        }

        player.openInventory(trait.createInventory());
    }

    private void handleTeach(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return;
        }
        if (!sender.hasPermission("workernpcs.use")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /worker teach <name> <label>");
            sender.sendMessage(ChatColor.GRAY + "Look at a block and the NPC remembers it as <label>");
            return;
        }

        NPC npc = findNpc(args[1]);
        if (npc == null) {
            sender.sendMessage(ChatColor.RED + "NPC not found.");
            return;
        }

        AiWorkerTrait trait = npc.getTraitNullable(AiWorkerTrait.class);
        if (trait == null) {
            sender.sendMessage(ChatColor.RED + "That NPC is not a worker.");
            return;
        }

        Location target = player.getTargetBlock(null, 10).getLocation();
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "No block in sight.");
            return;
        }

        String label = args[2].toLowerCase();
        trait.remember(label, target);
        sender.sendMessage(ChatColor.GREEN + npc.getName() + " remembers '" + label
            + "' at " + target.getBlockX() + " " + target.getBlockY() + " " + target.getBlockZ());
    }

    private void handleForget(CommandSender sender, String[] args) {
        if (!sender.hasPermission("workernpcs.use")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /worker forget <name> <label>");
            return;
        }

        NPC npc = findNpc(args[1]);
        if (npc == null) {
            sender.sendMessage(ChatColor.RED + "NPC not found.");
            return;
        }

        AiWorkerTrait trait = npc.getTraitNullable(AiWorkerTrait.class);
        if (trait == null) {
            sender.sendMessage(ChatColor.RED + "That NPC is not a worker.");
            return;
        }

        trait.forget(args[2].toLowerCase());
        sender.sendMessage(ChatColor.GREEN + npc.getName() + " forgot '" + args[2] + "'.");
    }

    private void handleMemory(CommandSender sender, String[] args) {
        if (!sender.hasPermission("workernpcs.use")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return;
        }

        NPC npc;
        if (args.length >= 2) {
            npc = findNpc(args[1]);
        } else {
            if (sender instanceof Player player) {
                npc = findNearestWorker(player);
            } else {
                sender.sendMessage("Usage: /worker memory <name>");
                return;
            }
        }

        if (npc == null) {
            sender.sendMessage(ChatColor.RED + "No worker NPC found.");
            return;
        }

        AiWorkerTrait trait = npc.getTraitNullable(AiWorkerTrait.class);
        if (trait == null) {
            sender.sendMessage(ChatColor.RED + "That NPC is not a worker.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== " + npc.getName() + "'s Memory ===");
        sender.sendMessage(ChatColor.GRAY + "Home: " + formatLoc(trait.getHome()));
        for (Map.Entry<String, Location> entry : trait.getKnownLocations().entrySet()) {
            sender.sendMessage(ChatColor.WHITE + "  " + entry.getKey()
                + ChatColor.GRAY + " → " + formatLoc(entry.getValue()));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== WorkerNPCs Commands ===");
        sender.sendMessage("/worker create <name> - Create worker NPC");
        sender.sendMessage("/worker delete <name> - Delete worker NPC");
        sender.sendMessage("/worker home [name] - Set/view NPC home");
        sender.sendMessage("/worker list - List all worker NPCs");
        sender.sendMessage("/worker task <name> <task> - Assign task");
        sender.sendMessage("/worker inventory [name] - Open NPC inventory");
        sender.sendMessage("/worker teach <name> <label> - Teach NPC a location");
        sender.sendMessage("/worker forget <name> <label> - Remove from memory");
        sender.sendMessage("/worker memory [name] - Show NPC's known locations");
        sender.sendMessage("/worker config - View config (admin)");
        sender.sendMessage(ChatColor.GRAY + "Chat: @Name <message> to talk to NPC");
    }

    private String formatLoc(Location loc) {
        if (loc == null) return "none";
        return String.format("%.0f %.0f %.0f %s",
            loc.getX(), loc.getY(), loc.getZ(),
            loc.getWorld() != null ? loc.getWorld().getName() : "");
    }

    private NPC findNpc(String name) {
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.getName() != null && npc.getName().equalsIgnoreCase(name)) {
                return npc;
            }
        }
        return null;
    }

    private NPC findNearestWorker(Player player) {
        NPC nearest = null;
        double best = Double.MAX_VALUE;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (!npc.hasTrait(AiWorkerTrait.class)) continue;
            if (!npc.isSpawned()) continue;
            if (npc.getEntity() == null) continue;
            double dist = npc.getEntity().getLocation().distanceSquared(player.getLocation());
            if (dist < best) {
                best = dist;
                nearest = npc;
            }
        }
        return best < 400 ? nearest : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.addAll(List.of("create", "delete", "home", "list", "task",
                "config", "inventory", "teach", "forget", "memory"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("delete")
            || args[0].equalsIgnoreCase("task") || args[0].equalsIgnoreCase("home")
            || args[0].equalsIgnoreCase("inventory") || args[0].equalsIgnoreCase("teach")
            || args[0].equalsIgnoreCase("forget") || args[0].equalsIgnoreCase("memory"))) {
            for (NPC npc : CitizensAPI.getNPCRegistry()) {
                if (npc.hasTrait(AiWorkerTrait.class)) {
                    suggestions.add(npc.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("task")) {
            suggestions.addAll(List.of("follow", "farm", "breed", "gohome", "idle"));
        }
        return suggestions.stream()
            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
            .toList();
    }
}

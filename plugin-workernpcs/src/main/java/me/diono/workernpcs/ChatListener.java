package me.diono.workernpcs;

import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import me.diono.workernpcs.trait.AiWorkerTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;

public class ChatListener implements Listener {

    private final WorkerNPCPlugin plugin;

    public ChatListener(WorkerNPCPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String msg = event.getMessage();
        if (!msg.startsWith("@")) return;

        int spaceIdx = msg.indexOf(' ');
        if (spaceIdx == -1) return;

        String name = msg.substring(1, spaceIdx);
        String command = msg.substring(spaceIdx + 1).trim();
        if (command.isEmpty()) return;

        NPC npc = findNpc(name);
        if (npc == null) return;

        AiWorkerTrait trait = npc.getTraitNullable(AiWorkerTrait.class);
        if (trait == null) return;

        Player player = event.getPlayer();
        event.setCancelled(true);

        Bukkit.getScheduler().runTask(plugin, () -> {
            player.sendMessage(ChatColor.GRAY + "[to " + npc.getName() + "] " + ChatColor.WHITE + command);

            CompletableFuture<String> future = trait.chat(player, command);
            future.thenAccept(response -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String reply = ChatColor.GRAY + "[" + npc.getName() + " → you] " + ChatColor.WHITE + response;
                    player.sendMessage(reply);

                    for (Player nearby : Bukkit.getOnlinePlayers()) {
                        if (nearby.equals(player)) continue;
                        if (nearby.getLocation().distanceSquared(
                                npc.getStoredLocation()) < 100) {
                            nearby.sendMessage(ChatColor.GRAY + "[" + npc.getName() + "] "
                                + ChatColor.WHITE + response);
                        }
                    }
                });
            });
        });
    }

    private NPC findNpc(String name) {
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.getName() != null && npc.getName().equalsIgnoreCase(name)) {
                return npc;
            }
        }
        return null;
    }
}

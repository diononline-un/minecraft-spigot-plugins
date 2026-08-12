package me.diono.chestmanager.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import me.diono.chestmanager.ChestManagerPlugin;
import me.diono.chestmanager.gui.RequestGui;
import me.diono.chestmanager.trait.ChestManagerTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;

public class RequestChestListener implements Listener {

    private final ChestManagerPlugin plugin;

    public RequestChestListener(ChestManagerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) return;

        String role = readSign(block);
        if (!"request".equals(role)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (!player.hasPermission("chestmanager.request")) {
            player.sendMessage("§cNo permission to use the request chest.");
            return;
        }

        ChestManagerTrait trait = findNearestTrait(block.getLocation());
        if (trait == null) {
            player.sendMessage("§cNo ChestManager NPC found nearby.");
            return;
        }

        new RequestGui(trait, player).open(player);
    }

    @SuppressWarnings("deprecation")
    private String readSign(Block chestBlock) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

        for (BlockFace face : faces) {
            Block adj = chestBlock.getRelative(face);
            if (adj.getState() instanceof Sign sign) {
                String role = sign.getLine(0);
                if (role != null && !role.isEmpty()) {
                    return role.trim().replaceAll("\\[|\\]", "").toLowerCase();
                }
            }
        }

        for (BlockFace face : faces) {
            Block neighbor = chestBlock.getRelative(face);
            for (BlockFace signFace : faces) {
                Block signBlock = neighbor.getRelative(signFace);
                if (signBlock.getState() instanceof Sign sign) {
                    String role = sign.getLine(0);
                    if (role != null && !role.isEmpty()) {
                        return role.trim().replaceAll("\\[|\\]", "").toLowerCase();
                    }
                }
            }
        }

        return null;
    }

    private ChestManagerTrait findNearestTrait(Location loc) {
        ChestManagerTrait nearest = null;
        double best = Double.MAX_VALUE;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (!npc.isSpawned() || npc.getEntity() == null) continue;
            if (!npc.hasTrait(ChestManagerTrait.class)) continue;
            ChestManagerTrait trait = npc.getTraitNullable(ChestManagerTrait.class);
            if (trait == null) continue;
            Location home = trait.getHome();
            if (home == null || !home.getWorld().equals(loc.getWorld())) continue;
            double dist = home.distanceSquared(loc);
            if (dist < best && dist <= (trait.getScanRadius() * trait.getScanRadius())) {
                best = dist;
                nearest = trait;
            }
        }
        return nearest;
    }
}

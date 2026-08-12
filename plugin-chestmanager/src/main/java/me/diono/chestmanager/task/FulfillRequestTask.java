package me.diono.chestmanager.task;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import me.diono.chestmanager.chest.ChestArea;
import me.diono.chestmanager.trait.ChestManagerTrait;
import net.citizensnpcs.api.npc.NPC;

public class FulfillRequestTask extends ChestTask {

    private enum State {
        MOVING_TO_CHEST, WITHDRAWING, MOVING_TO_REQUEST, DEPOSITING, DONE
    }

    private final UUID playerUuid;
    private final Material material;
    private final int amount;
    private final String category;
    private final Location requestChest;

    private State state = State.MOVING_TO_CHEST;
    private Location currentCategoryChest;

    public FulfillRequestTask(UUID playerUuid, Material material, int amount,
                              String category, Location requestChest) {
        this.playerUuid = playerUuid;
        this.material = material;
        this.amount = amount;
        this.category = category;
        this.requestChest = requestChest;
    }

    @Override
    public void tick(ChestManagerTrait trait) {
        NPC npc = trait.getNPC();
        ChestArea area = trait.getChestArea();

        switch (state) {
            case MOVING_TO_CHEST -> {
                if (npc.getNavigator().isNavigating()) return;

                if (currentCategoryChest == null) {
                    Location chest = area.getCategoryChests(category).stream()
                            .filter(loc -> area.getChestInventory(loc) != null
                                    && hasItem(area.getChestInventory(loc), material))
                            .findFirst().orElse(null);

                    if (chest == null) {
                        fail(trait, "No chest found with " + material.name());
                        return;
                    }
                    currentCategoryChest = chest;
                    npc.getNavigator().setTarget(chest.clone().add(0.5, 1, 0.5));
                    npc.getNavigator().getLocalParameters().speedModifier(1.0f).range(2.0f);
                } else {
                    state = State.WITHDRAWING;
                }
            }

            case WITHDRAWING -> {
                if (currentCategoryChest == null) {
                    fail(trait, "Chest location lost");
                    return;
                }

                boolean withdrawn = area.withdrawFromCategory(category, material, amount);
                if (withdrawn) {
                    ItemStack item = new ItemStack(material, Math.min(amount,
                            material.getMaxStackSize()));
                    trait.getCarryInventory().clear();
                    trait.getCarryInventory().add(item);

                    state = State.MOVING_TO_REQUEST;
                } else {
                    fail(trait, "Could not withdraw items");
                }
            }

            case MOVING_TO_REQUEST -> {
                if (npc.getNavigator().isNavigating()) return;

                if (requestChest == null) {
                    fail(trait, "Request chest not found");
                    return;
                }
                npc.getNavigator().setTarget(requestChest.clone().add(0.5, 1, 0.5));
                npc.getNavigator().getLocalParameters().speedModifier(1.0f).range(2.0f);
                state = State.DEPOSITING;
            }

            case DEPOSITING -> {
                if (npc.getNavigator().isNavigating()) return;

                Inventory reqInv = area.getChestInventory(requestChest);
                if (reqInv == null) {
                    fail(trait, "Request chest inaccessible");
                    return;
                }

                for (ItemStack stack : trait.getCarryInventory()) {
                    if (stack != null && stack.getType() != Material.AIR) {
                        reqInv.addItem(stack.clone());
                    }
                }
                trait.getCarryInventory().clear();

                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null) {
                    player.sendMessage("§a" + trait.getNPC().getName() + " §7delivered §f"
                            + amount + "x " + material.name().toLowerCase()
                            + " §7to the request chest.");
                }

                state = State.DONE;
            }

            case DONE -> {
                trait.setTask(new SortTask());
            }
        }
    }

    private void fail(ChestManagerTrait trait, String reason) {
        trait.getNPC().getNavigator().cancelNavigation();
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            player.sendMessage("§c" + trait.getNPC().getName() + " could not fulfill request: " + reason);
        }
        trait.setTask(new SortTask());
    }

    private boolean hasItem(Inventory inv, Material mat) {
        if (inv == null) return false;
        for (ItemStack stack : inv.getContents()) {
            if (stack != null && stack.getType() == mat) return true;
        }
        return false;
    }

    @Override
    public void stop(ChestManagerTrait trait) {
        trait.getNPC().getNavigator().cancelNavigation();
    }

    @Override
    public int tickInterval() {
        return 10;
    }

    @Override
    public String status() {
        return "Fulfilling request";
    }
}

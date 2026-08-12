package me.diono.chestmanager.task;

import java.util.*;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import me.diono.chestmanager.ChestManagerPlugin;
import me.diono.chestmanager.chest.ChestArea;
import me.diono.chestmanager.trait.ChestManagerTrait;
import net.citizensnpcs.api.npc.NPC;

public class SortTask extends ChestTask {

    private enum State {
        IDLE, GATHERING, SORTING, RETURNING
    }

    private State state = State.IDLE;
    private int idleCounter;
    private int sortInterval;
    private Iterator<SortAction> sortQueue;
    private SortAction currentAction;
    private boolean forced;

    private record SortAction(Material material, int amount, Location targetChest) {}

    @Override
    public void start(ChestManagerTrait trait) {
        state = State.IDLE;
        idleCounter = 0;
        sortInterval = trait.getSortIntervalTicks();
        sortQueue = null;
        currentAction = null;
        forced = false;
    }

    @Override
    public void tick(ChestManagerTrait trait) {
        switch (state) {
            case IDLE -> tickIdle(trait);
            case GATHERING -> tickGathering(trait);
            case SORTING -> tickSorting(trait);
            case RETURNING -> tickReturning(trait);
        }
    }

    private void tickIdle(ChestManagerTrait trait) {
        idleCounter++;

        ChestArea area = trait.getChestArea();
        Location inputLoc = area.getInputChest();

        if (inputLoc != null && hasItems(area, inputLoc)) {
            state = State.GATHERING;
            forced = true;
            idleCounter = 0;
            return;
        }

        if (idleCounter >= sortInterval) {
            idleCounter = 0;
            if (inputLoc != null && hasItems(area, inputLoc)) {
                state = State.GATHERING;
                forced = true;
            }
        }
    }

    private boolean hasItems(ChestArea area, Location chestLoc) {
        Inventory inv = area.getChestInventory(chestLoc);
        if (inv == null) return false;
        for (ItemStack stack : inv.getContents()) {
            if (stack != null && stack.getType() != Material.AIR) return true;
        }
        return false;
    }

    private void tickGathering(ChestManagerTrait trait) {
        ChestArea area = trait.getChestArea();
        NPC npc = trait.getNPC();
        Location inputLoc = area.getInputChest();

        if (inputLoc == null) {
            state = State.IDLE;
            currentAction = null;
            forced = false;
            return;
        }

        if (npc.getNavigator().isNavigating()) return;

        if (currentAction == null) {
            ensureChunkLoaded(inputLoc);
            npc.getNavigator().setTarget(inputLoc.clone().add(0.5, 1, 0.5));
            npc.getNavigator().getLocalParameters().speedModifier(1.0f).range(2.0f);
            currentAction = new SortAction(null, 0, inputLoc);
            return;
        }

        ensureChunkLoaded(inputLoc);
        Inventory inputInv = area.getChestInventory(inputLoc);
        if (inputInv == null) {
            state = State.IDLE;
            currentAction = null;
            forced = false;
            return;
        }

        List<SortAction> actions = new ArrayList<>();
        List<ItemStack> carryItems = new ArrayList<>();

        for (int slot = 0; slot < inputInv.getSize(); slot++) {
            ItemStack stack = inputInv.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) continue;

            Material mat = stack.getType();
            Optional<String> category = trait.getCategoryConfig().getCategory(mat);

            Location target;
            if (category.isPresent()) {
                List<Location> chests = area.getCategoryChests(category.get());
                if (chests.isEmpty()) {
                    target = area.getUnsortableChest();
                } else {
                    target = nearestChest(inputLoc, chests);
                }
            } else {
                target = area.getUnsortableChest();
            }

            if (target == null) {
                ChestManagerPlugin.getInstance().getLogger().warning(
                        "No target chest for " + mat.name() + " (no category, no unsortable)");
                continue;
            }

            int amount = stack.getAmount();
            actions.add(new SortAction(mat, amount, target));
            carryItems.add(stack.clone());
            inputInv.setItem(slot, null);

            if (carryItems.size() >= 27) break;
        }

        trait.getCarryInventory().clear();
        trait.getCarryInventory().addAll(carryItems);

        if (actions.isEmpty()) {
            state = State.RETURNING;
            currentAction = null;
            forced = false;
            return;
        }

        sortQueue = actions.iterator();
        currentAction = null;
        state = State.SORTING;
    }

    private Location nearestChest(Location origin, List<Location> chests) {
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (Location loc : chests) {
            double dist = origin.distanceSquared(loc);
            if (dist < bestDist) {
                bestDist = dist;
                best = loc;
            }
        }
        return best;
    }

    private void tickSorting(ChestManagerTrait trait) {
        NPC npc = trait.getNPC();
        ChestArea area = trait.getChestArea();

        if (npc.getNavigator().isNavigating()) return;

        if (currentAction == null) {
            if (sortQueue == null || !sortQueue.hasNext()) {
                state = State.RETURNING;
                forced = false;
                return;
            }
            currentAction = sortQueue.next();
            ensureChunkLoaded(currentAction.targetChest());
            npc.getNavigator().setTarget(currentAction.targetChest().clone().add(0.5, 1, 0.5));
            npc.getNavigator().getLocalParameters().speedModifier(1.0f).range(2.0f);
            return;
        }

        ensureChunkLoaded(currentAction.targetChest());
        Inventory targetInv = area.getChestInventory(currentAction.targetChest());
        if (targetInv == null) {
            currentAction = null;
            return;
        }

        Material mat = currentAction.material();
        int remaining = currentAction.amount();

        Iterator<ItemStack> carryIt = trait.getCarryInventory().iterator();
        while (carryIt.hasNext() && remaining > 0) {
            ItemStack stack = carryIt.next();
            if (stack.getType() == mat) {
                int toDeposit = Math.min(stack.getAmount(), remaining);
                ItemStack depositStack = new ItemStack(mat, toDeposit);
                Map<Integer, ItemStack> leftovers = targetInv.addItem(depositStack);
                int deposited = toDeposit;
                if (!leftovers.isEmpty()) {
                    deposited -= leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
                }
                remaining -= deposited;
                if (deposited > 0 && trait.getNPC().getEntity() instanceof LivingEntity entity) {
                    entity.swingMainHand();
                }
                if (stack.getAmount() <= deposited) {
                    carryIt.remove();
                } else {
                    stack.setAmount(stack.getAmount() - deposited);
                }
            }
        }

        currentAction = null;
    }

    private void tickReturning(ChestManagerTrait trait) {
        NPC npc = trait.getNPC();
        ChestArea area = trait.getChestArea();

        if (npc.getNavigator().isNavigating()) return;

        Location inputLoc = area.getInputChest();
        if (inputLoc != null) {
            ensureChunkLoaded(inputLoc);
            npc.getNavigator().setTarget(inputLoc.clone().add(0.5, 1, 0.5));
            npc.getNavigator().getLocalParameters().speedModifier(1.0f).range(2.0f);
        }

        state = State.IDLE;
        currentAction = null;
        forced = false;
    }

    private void ensureChunkLoaded(Location loc) {
        if (loc != null && loc.getWorld() != null && !loc.getChunk().isLoaded()) {
            loc.getChunk().load();
        }
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
        return "Sorting (" + state.toString().toLowerCase() + ")";
    }
}

package me.diono.chestmanager.bukkit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import me.diono.chestmanager.core.BlockPos;
import me.diono.chestmanager.core.ItemKey;
import me.diono.chestmanager.core.port.CarryPort;

/**
 * Der Träger des Sorters: die Items, die er zwischen zwei Truhen bei sich führt.
 *
 * <p>Der Inhalt ist als serialisierter Blob darstellbar, damit der Kern ihn vor
 * der Entnahme ins Journal schreiben kann. Nur so lässt er sich nach einem
 * Absturz wiederherstellen.
 */
public final class BukkitCarryPort implements CarryPort {

    private final World world;
    private final BukkitChestPort chests;
    private final List<ItemStack> carried = new ArrayList<>();
    private Location dropLocation;

    public BukkitCarryPort(World world, BukkitChestPort chests) {
        this.world = world;
        this.chests = chests;
    }

    @Override
    public Optional<String> preview(BlockPos pos, ItemKey key, int amount) {
        Optional<Inventory> inventory = chests.inventoryAt(pos);
        if (inventory.isEmpty()) {
            return Optional.empty();
        }
        List<ItemStack> preview = new ArrayList<>();
        int remaining = amount;
        for (ItemStack stack : inventory.get().getContents()) {
            if (remaining <= 0) break;
            if (!matches(stack, key)) continue;
            int take = Math.min(stack.getAmount(), remaining);
            ItemStack copy = stack.clone();
            copy.setAmount(take);
            preview.add(copy);
            remaining -= take;
        }
        return preview.isEmpty() ? Optional.empty() : Optional.of(serialize(preview));
    }

    @Override
    public int extract(BlockPos pos, ItemKey key, int amount) {
        Optional<Inventory> inventory = chests.inventoryAt(pos);
        if (inventory.isEmpty()) {
            return 0;
        }
        Inventory inv = inventory.get();
        int remaining = amount;
        List<ItemStack> taken = new ArrayList<>();

        ItemStack[] contents = inv.getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (!matches(stack, key)) continue;
            int take = Math.min(stack.getAmount(), remaining);
            ItemStack copy = stack.clone();
            copy.setAmount(take);
            taken.add(copy);

            int left = stack.getAmount() - take;
            if (left <= 0) {
                inv.setItem(slot, null);
            } else {
                stack.setAmount(left);
                inv.setItem(slot, stack);
            }
            remaining -= take;
        }

        carried.addAll(taken);
        return amount - remaining;
    }

    @Override
    public int deposit(BlockPos pos) {
        if (carried.isEmpty()) {
            return 0;
        }
        Optional<Inventory> inventory = chests.inventoryAt(pos);
        if (inventory.isEmpty()) {
            return totalCarried();
        }
        Map<Integer, ItemStack> leftover = inventory.get().addItem(carried.toArray(new ItemStack[0]));
        carried.clear();
        for (ItemStack rest : leftover.values()) {
            if (rest != null && rest.getType() != Material.AIR) {
                carried.add(rest);
            }
        }
        return totalCarried();
    }

    @Override
    public String carriedPayload() {
        return carried.isEmpty() ? "" : serialize(carried);
    }

    @Override
    public void loadCarried(String payload) {
        carried.clear();
        carried.addAll(deserialize(payload));
    }

    @Override
    public int dropCarried() {
        if (carried.isEmpty()) {
            return 0;
        }
        Location dropAt = dropLocation();
        int total = totalCarried();
        for (ItemStack stack : carried) {
            world.dropItemNaturally(dropAt, stack);
        }
        carried.clear();
        return total;
    }

    @Override
    public boolean isCarryEmpty() {
        return carried.isEmpty();
    }

    /** Position, an der notgelandete Items abgelegt werden. */
    private Location dropLocation() {
        return dropLocation != null ? dropLocation : world.getSpawnLocation();
    }

    /** Setzt die Position, an der {@link #dropCarried()} ablegt. */
    public void setDropLocation(Location location) {
        this.dropLocation = location == null ? null : location.clone();
    }

    public int totalCarried() {
        return carried.stream().mapToInt(ItemStack::getAmount).sum();
    }

    public List<ItemStack> carriedItems() {
        return List.copyOf(carried);
    }

    private static boolean matches(ItemStack stack, ItemKey key) {
        return stack != null
                && stack.getType() != Material.AIR
                && BukkitChestPort.keyOf(stack).equals(key);
    }

    // --- Serialisierung --------------------------------------------------

    static String serialize(List<ItemStack> stacks) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeInt(stacks.size());
            for (ItemStack stack : stacks) {
                out.writeObject(stack);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Trägerinhalt konnte nicht gesichert werden", failure);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    static List<ItemStack> deserialize(String payload) {
        List<ItemStack> result = new ArrayList<>();
        if (payload == null || payload.isEmpty()) {
            return result;
        }
        byte[] bytes = Base64.getDecoder().decode(payload);
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            int size = in.readInt();
            for (int i = 0; i < size; i++) {
                Object read = in.readObject();
                if (read instanceof ItemStack stack) {
                    result.add(stack);
                }
            }
        } catch (IOException | ClassNotFoundException failure) {
            throw new IllegalStateException("Trägerinhalt konnte nicht gelesen werden", failure);
        }
        return result;
    }

    /** Nur für Tests: Anzahl der Stapel im Träger. */
    public int stackCount() {
        return carried.size();
    }
}

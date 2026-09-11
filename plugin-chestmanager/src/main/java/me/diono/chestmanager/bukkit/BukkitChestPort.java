package me.diono.chestmanager.bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.diono.chestmanager.core.BlockPos;
import me.diono.chestmanager.core.ChestSnapshot;
import me.diono.chestmanager.core.ItemKey;
import me.diono.chestmanager.core.port.ChestPort;

/**
 * Lesender Zugriff auf Truhen. Eine Doppeltruhe erscheint als ein Inventar mit
 * 54 Plätzen, weil beide Hälften dasselbe Inventar teilen.
 */
public final class BukkitChestPort implements ChestPort {

    private final World world;
    private final int fallbackStackSize;

    public BukkitChestPort(World world) {
        this.world = world;
        this.fallbackStackSize = 64;
    }

    /** Inventar an dieser Position, leer wenn dort keine Truhe steht. */
    public Optional<Inventory> inventoryAt(BlockPos pos) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Material type = block.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) {
            return Optional.empty();
        }
        if (block.getState() instanceof Chest chest) {
            return Optional.of(chest.getBlockInventory());
        }
        return Optional.empty();
    }

    @Override
    public Optional<ChestSnapshot> read(BlockPos pos) {
        return inventoryAt(pos).map(this::snapshotOf);
    }

    private ChestSnapshot snapshotOf(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        List<ChestSnapshot.StackView> slots = new ArrayList<>(contents.length);
        int stackSize = fallbackStackSize;

        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() == Material.AIR) {
                slots.add(null);
                continue;
            }
            stackSize = Math.max(1, stack.getMaxStackSize());
            slots.add(new ChestSnapshot.StackView(keyOf(stack), stack.getAmount()));
        }
        return new ChestSnapshot(slots, stackSize);
    }

    /**
     * Bildet den Item-Schlüssel eines Stapels. Für Items ohne besondere Metadaten
     * genügt der Typ; sonst wird eine Kennung der Metadaten angehängt, damit
     * verzauberte Werkzeuge nicht mit unverzauberten vermischt werden.
     */
    public static ItemKey keyOf(ItemStack stack) {
        String typeId = stack.getType().getKey().toString();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || isPlain(meta)) {
            return ItemKey.of(typeId);
        }
        return new ItemKey(typeId, Integer.toHexString(meta.serialize().hashCode()));
    }

    private static boolean isPlain(ItemMeta meta) {
        return !meta.hasDisplayName()
                && !meta.hasLore()
                && !meta.hasEnchants()
                && !meta.hasCustomModelData()
                && !meta.hasAttributeModifiers()
                && !meta.hasCustomModelData()
                && meta.getPersistentDataContainer().isEmpty();
    }
}

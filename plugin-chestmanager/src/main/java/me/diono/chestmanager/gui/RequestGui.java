package me.diono.chestmanager.gui;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.diono.chestmanager.ChestManagerPlugin;
import me.diono.chestmanager.chest.ChestArea;
import me.diono.chestmanager.config.CategoryConfig;
import me.diono.chestmanager.trait.ChestManagerTrait;
import org.bukkit.ChatColor;

public class RequestGui implements InventoryHolder, Listener {

    private static final int ROWS = 6;
    private static final int BACK_SLOT = ROWS * 9 - 9;
    private static final int CLOSE_SLOT = ROWS * 9 - 1;

    private final ChestManagerTrait trait;
    private final Player player;
    private final Inventory inventory;
    private String selectedCategory;

    public RequestGui(ChestManagerTrait trait, Player player) {
        this.trait = trait;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, ROWS * 9, "ChestManager - Request");
    }

    public void open(Player player) {
        showCategories();
        player.openInventory(inventory);
        Bukkit.getPluginManager().registerEvents(this, ChestManagerPlugin.getInstance());
    }

    private void showCategories() {
        inventory.clear();
        selectedCategory = null;

        ChestArea area = trait.getChestArea();
        area.scan();
        CategoryConfig config = trait.getCategoryConfig();

        int slot = 0;
        for (String catKey : config.getCategories()) {
            if (slot >= BACK_SLOT) break;

            int itemCount = area.getTotalItemsForCategory(catKey);
            Material icon = config.getCategoryIcon(catKey);
            String display = config.getCategoryDisplay(catKey);

            ItemStack item = new ItemStack(icon != null ? icon : Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + display);
                meta.setLore(List.of(
                        ChatColor.GRAY + "Total items: " + ChatColor.WHITE + itemCount,
                        "",
                        ChatColor.YELLOW + "Click to browse"
                ));
                item.setItemMeta(meta);
            }
            inventory.setItem(slot++, item);
        }

        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName(ChatColor.RED + "Close");
            closeItem.setItemMeta(closeMeta);
        }
        inventory.setItem(CLOSE_SLOT, closeItem);
    }

    private void showCategoryItems(String categoryKey) {
        inventory.clear();
        selectedCategory = categoryKey;

        ChestArea area = trait.getChestArea();
        CategoryConfig config = trait.getCategoryConfig();
        Map<Material, Integer> counts = area.getItemCountsByCategory(categoryKey);

        int slot = 0;
        for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
            if (slot >= BACK_SLOT) break;

            Material mat = entry.getKey();
            int amount = entry.getValue();

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.WHITE + formatMaterialName(mat));
                meta.setLore(List.of(
                        ChatColor.GRAY + "Available: " + ChatColor.WHITE + amount,
                        "",
                        ChatColor.YELLOW + "Left click: §f1 stack (64)",
                        ChatColor.YELLOW + "Right click: §fhalf stack (32)",
                        ChatColor.YELLOW + "Shift click: §f16"
                ));
                item.setItemMeta(meta);
            }
            inventory.setItem(slot++, item);
        }

        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.GRAY + "<- Back to Categories");
            backItem.setItemMeta(backMeta);
        }
        inventory.setItem(BACK_SLOT, backItem);

        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName(ChatColor.RED + "Close");
            closeItem.setItemMeta(closeMeta);
        }
        inventory.setItem(CLOSE_SLOT, closeItem);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getSlot();

        if (slot == CLOSE_SLOT) {
            event.getWhoClicked().closeInventory();
            return;
        }

        if (slot == BACK_SLOT) {
            showCategories();
            return;
        }

        if (selectedCategory == null) {
            CategoryConfig config = trait.getCategoryConfig();
            List<String> categories = new ArrayList<>(config.getCategories());
            if (slot < categories.size()) {
                String catKey = categories.get(slot);
                showCategoryItems(catKey);
            }
        } else {
            Material mat = clicked.getType();
            int amount;
            if (event.isShiftClick()) {
                amount = 16;
            } else if (event.isRightClick()) {
                amount = 32;
            } else {
                amount = 64;
            }

            amount = Math.min(amount,
                    trait.getChestArea().getItemCountsByCategory(selectedCategory)
                            .getOrDefault(mat, 0));

            if (amount > 0) {
                event.getWhoClicked().closeInventory();
                trait.requestItems((Player) event.getWhoClicked(), mat, amount, selectedCategory);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() == this) {
            InventoryClickEvent.getHandlerList().unregister(this);
            InventoryCloseEvent.getHandlerList().unregister(this);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static String formatMaterialName(Material mat) {
        String name = mat.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : name.toCharArray()) {
            if (Character.isSpaceChar(c)) {
                capitalize = true;
                sb.append(c);
            } else if (capitalize) {
                sb.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

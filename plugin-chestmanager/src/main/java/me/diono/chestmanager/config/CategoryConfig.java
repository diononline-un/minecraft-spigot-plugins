package me.diono.chestmanager.config;

import java.util.*;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public class CategoryConfig {

    private final JavaPlugin plugin;
    private final Map<Material, String> itemToCategory = new EnumMap<>(Material.class);
    private final Map<String, CategoryData> categories = new LinkedHashMap<>();

    private static class CategoryData {
        final String display;
        final Material icon;
        final Set<Material> items = EnumSet.noneOf(Material.class);

        CategoryData(String display, Material icon) {
            this.display = display;
            this.icon = icon;
        }
    }

    public CategoryConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        itemToCategory.clear();
        categories.clear();

        ConfigurationSection catSection = plugin.getConfig().getConfigurationSection("categories");
        if (catSection == null) {
            plugin.getLogger().warning("No categories defined in config.yml");
            return;
        }

        for (String catKey : catSection.getKeys(false)) {
            String display = catSection.getString(catKey + ".display", catKey);
            String iconName = catSection.getString(catKey + ".icon", "CHEST");
            Material icon = Material.matchMaterial(iconName);
            if (icon == null) icon = Material.CHEST;

            CategoryData data = new CategoryData(display, icon);
            List<String> itemNames = catSection.getStringList(catKey + ".items");
            for (String name : itemNames) {
                Material mat = Material.matchMaterial(name);
                if (mat != null) {
                    data.items.add(mat);
                    itemToCategory.put(mat, catKey);
                }
            }
            categories.put(catKey, data);
        }
    }

    public Optional<String> getCategory(Material material) {
        return Optional.ofNullable(itemToCategory.get(material));
    }

    public String getCategoryDisplay(String categoryKey) {
        CategoryData data = categories.get(categoryKey);
        return data != null ? data.display : categoryKey;
    }

    public Material getCategoryIcon(String categoryKey) {
        CategoryData data = categories.get(categoryKey);
        return data != null ? data.icon : Material.CHEST;
    }

    public Set<String> getCategories() {
        return Collections.unmodifiableSet(categories.keySet());
    }

    public boolean isKnownItem(Material material) {
        return itemToCategory.containsKey(material);
    }

    public boolean isUnsortable(Material material) {
        return !itemToCategory.containsKey(material);
    }
}

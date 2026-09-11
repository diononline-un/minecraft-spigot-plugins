package me.diono.chestmanager.bukkit;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import me.diono.chestmanager.core.CategoryTable;
import me.diono.chestmanager.core.SorterSettings;

/**
 * Liest Kategorien und Sorter-Einstellungen aus der Konfiguration und meldet
 * fehlerhafte Einträge mit ihrer Position in der Datei, statt sie stillschweigend
 * zu übergehen.
 */
public final class PluginConfig {

    /** Ergebnis eines Ladevorgangs. */
    public record Loaded(CategoryTable categories, SorterSettings settings, List<String> problems) {}

    private PluginConfig() {
    }

    public static Loaded load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        List<String> problems = new ArrayList<>();

        CategoryTable.LoadResult categories = CategoryTable.load(readCategories(config, problems));
        problems.addAll(categories.warnings());

        if (categories.table().categoryKeys().isEmpty()) {
            problems.add("Es ist keine einzige Kategorie definiert; der Sorter kann nichts einsortieren. "
                    + "Vorlage: plugins/ChestManager/config.yml.example");
        }

        SorterSettings settings = readSettings(config, problems);
        return new Loaded(categories.table(), settings, problems);
    }

    private static List<CategoryTable.Definition> readCategories(FileConfiguration config, List<String> problems) {
        List<CategoryTable.Definition> definitions = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("categories");
        if (section == null) {
            return definitions;
        }

        for (String key : section.getKeys(false)) {
            String path = "categories." + key;
            String display = config.getString(path + ".display", key);
            String iconName = config.getString(path + ".icon", "CHEST");
            Material icon = Material.matchMaterial(iconName);
            if (icon == null) {
                problems.add(path + ".icon: unbekannter Item-Typ '" + iconName + "'; es wird CHEST verwendet");
                icon = Material.CHEST;
            }

            List<String> typeIds = new ArrayList<>();
            for (String rawName : config.getStringList(path + ".items")) {
                Material material = Material.matchMaterial(rawName);
                if (material == null || !material.isItem()) {
                    problems.add(path + ".items: unbekannter Item-Typ '" + rawName + "'");
                    continue;
                }
                typeIds.add(material.getKey().toString());
            }
            if (typeIds.isEmpty()) {
                problems.add(path + ".items ist leer; die Kategorie wird nie greifen");
            }
            definitions.add(new CategoryTable.Definition(key, display, icon.getKey().toString(), typeIds));
        }
        return definitions;
    }

    private static SorterSettings readSettings(FileConfiguration config, List<String> problems) {
        SorterSettings defaults = SorterSettings.defaults();
        String path = "sorter.";

        int searchRadius = config.getInt(path + "search-radius", defaults.searchRadius());
        int maxChests = config.getInt(path + "max-chests", defaults.maxChests());
        int maxHeight = config.getInt(path + "max-height-difference", defaults.maxHeightDifference());
        int linkDistance = config.getInt(path + "link-distance", defaults.linkDistance());
        int blocksPerTick = config.getInt(path + "blocks-per-tick", defaults.blocksPerTick());
        double reach = config.getDouble(path + "reach", defaults.reach());
        long navTimeout = config.getLong(path + "nav-timeout-ms", defaults.navTimeoutMillis());
        int maxAttempts = config.getInt(path + "max-nav-attempts", defaults.maxNavAttempts());
        long retryBackoff = config.getLong(path + "retry-backoff-ms", defaults.retryBackoffMillis());
        long blockDuration = config.getLong(path + "block-duration-ms", defaults.blockDurationMillis());
        int carryLimit = config.getInt(path + "carry-limit", defaults.carryLimit());
        long sortInterval = config.getLong(path + "sort-interval-ms", defaults.sortIntervalMillis());
        long rescanInterval = config.getLong(path + "rescan-interval-ms", defaults.rescanIntervalMillis());
        int opsPerTick = config.getInt(path + "ops-per-tick", defaults.opsPerTick());
        int maxTickets = config.getInt(path + "max-chunk-tickets", defaults.maxChunkTickets());
        boolean debug = config.getBoolean("debug", defaults.debug());

        try {
            return new SorterSettings(searchRadius, maxChests, maxHeight, linkDistance, blocksPerTick,
                    reach, navTimeout, maxAttempts, retryBackoff, blockDuration, carryLimit,
                    sortInterval, rescanInterval, opsPerTick, maxTickets, debug);
        } catch (IllegalArgumentException invalid) {
            problems.add("sorter: ungültiger Wert (" + invalid.getMessage()
                    + "); es gelten die Voreinstellungen");
            return defaults;
        }
    }

    /** Takt des Schedulers in Server-Ticks. */
    public static int tickPeriodTicks(JavaPlugin plugin) {
        return Math.max(1, plugin.getConfig().getInt("sorter.tick-period-ticks", 5));
    }

    /** Anzahl der im Transportprotokoll gehaltenen Vorgänge. */
    public static int journalCapacity(JavaPlugin plugin) {
        return Math.max(10, plugin.getConfig().getInt("sorter.log-capacity", 200));
    }
}

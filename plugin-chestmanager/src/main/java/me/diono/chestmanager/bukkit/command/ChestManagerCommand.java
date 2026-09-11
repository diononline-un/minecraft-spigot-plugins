package me.diono.chestmanager.bukkit.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import me.diono.chestmanager.bukkit.ChestManagerPlugin;
import me.diono.chestmanager.bukkit.ChestManagerTrait;
import me.diono.chestmanager.core.BlockedChests;
import me.diono.chestmanager.core.BlockPos;
import me.diono.chestmanager.core.ChestArea;
import me.diono.chestmanager.core.RoleKind;
import me.diono.chestmanager.core.Sorter;
import me.diono.chestmanager.core.SorterState;
import me.diono.chestmanager.core.TransportLog;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;

/** Bedienoberfläche für Betreiber. */
public final class ChestManagerCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION_ADMIN = "chestmanager.admin";
    private static final String PERMISSION_OPERATE = "chestmanager.operate";
    private static final String PERMISSION_VIEW = "chestmanager.view";

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "delete", "list", "scan", "status", "sortnow", "selftest",
            "blocked", "unblock", "log", "pause", "resume", "home", "reload", "setup");

    private final ChestManagerPlugin plugin;

    public ChestManagerCommand(ChestManagerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, args);
            case "delete" -> delete(sender, args);
            case "list" -> list(sender);
            case "scan" -> scan(sender, args);
            case "status" -> status(sender, args);
            case "sortnow" -> sortNow(sender, args);
            case "selftest" -> selfTest(sender, args);
            case "blocked" -> blocked(sender, args);
            case "unblock" -> unblock(sender, args);
            case "log" -> log(sender, args);
            case "pause" -> pause(sender, args, true);
            case "resume" -> pause(sender, args, false);
            case "home" -> home(sender, args);
            case "reload" -> reload(sender);
            case "setup" -> setup(sender);
            default -> help(sender);
        }
        return true;
    }

    // --- Verwaltung -------------------------------------------------------

    private void create(CommandSender sender, String[] args) {
        if (!require(sender, PERMISSION_ADMIN)) return;
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Verwendung: /cm create <Name> [x y z]");
            return;
        }
        String name = args[1];
        if (findNpcByName(name) != null) {
            sender.sendMessage(ChatColor.RED + "Es gibt bereits einen NPC mit dem Namen '" + name + "'.");
            return;
        }

        Location where = resolveSpawnLocation(sender, args);
        if (where == null) {
            return;
        }

        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER, name);
        ChestManagerTrait trait = npc.getOrAddTrait(ChestManagerTrait.class);
        npc.spawn(where);
        trait.setHome(where);
        plugin.registerTrait(trait);
        trait.start();

        sender.sendMessage(ChatColor.GREEN + "Sorter '" + name + "' angelegt bei "
                + describeHome(where) + ".");
        sender.sendMessage(ChatColor.GRAY + "Markiere die Truhen mit Schildern, dann /cm scan " + name);
    }

    /**
     * Position für den neuen Sorter: entweder die angegebenen Koordinaten (auch von
     * der Konsole aus) oder die Position des Spielers.
     */
    private Location resolveSpawnLocation(CommandSender sender, String[] args) {
        if (args.length >= 5) {
            try {
                int x = Integer.parseInt(args[2]);
                int y = Integer.parseInt(args[3]);
                int z = Integer.parseInt(args[4]);
                Location base = sender instanceof Player player
                        ? player.getLocation()
                        : plugin.getServer().getWorlds().get(0).getSpawnLocation();
                return new Location(base.getWorld(), x + 0.5, y, z + 0.5,
                        base.getYaw(), base.getPitch());
            } catch (NumberFormatException invalid) {
                sender.sendMessage(ChatColor.RED + "Koordinaten müssen ganze Zahlen sein.");
                return null;
            }
        }
        if (sender instanceof Player player) {
            return player.getLocation();
        }
        if (args.length >= 3 && args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Von der Konsole bitte x y z angeben:"
                    + " /cm create <Name> <x> <y> <z>");
            return null;
        }
        sender.sendMessage(ChatColor.RED + "Von der Konsole bitte x y z angeben:"
                + " /cm create <Name> <x> <y> <z>");
        return null;
    }

    private void delete(CommandSender sender, String[] args) {
        if (!require(sender, PERMISSION_ADMIN)) return;
        ChestManagerTrait trait = requireTrait(sender, args);
        if (trait == null) return;
        NPC npc = trait.getNPC();
        String name = npc.getName();

        if (trait.sorter() != null && trait.carry() != null && !trait.carry().isCarryEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Getragene Items werden zuerst zurückgelegt …");
            int rounds = 0;
            while (!trait.carry().isCarryEmpty() && rounds++ < 40) {
                trait.sorter().tick(4);
            }
            if (!trait.carry().isCarryEmpty()) {
                sender.sendMessage(ChatColor.RED + "Es konnten nicht alle Items zurückgelegt werden."
                        + " Der NPC wird nicht entfernt.");
                return;
            }
        }
        plugin.unregisterTrait(trait);
        CitizensAPI.getNPCRegistry().deregister(npc);
        npc.destroy();
        sender.sendMessage(ChatColor.GREEN + "Sorter '" + name + "' entfernt.");
    }

    private void home(CommandSender sender, String[] args) {
        if (!require(sender, PERMISSION_ADMIN)) return;
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können einen Heimatpunkt setzen.");
            return;
        }
        ChestManagerTrait trait = requireTrait(sender, args);
        if (trait == null) return;
        trait.setHome(player.getLocation());
        sender.sendMessage(ChatColor.GREEN + "Heimatpunkt gesetzt, Bereich wird neu ermittelt.");
    }

    private void reload(CommandSender sender) {
        if (!require(sender, PERMISSION_ADMIN)) return;
        List<String> problems = plugin.reloadConfiguration();
        plugin.restartScheduler();
        if (problems.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "Konfiguration geladen, keine Auffälligkeiten.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Konfiguration geladen mit "
                    + problems.size() + " Auffälligkeit(en):");
            for (String problem : problems) {
                sender.sendMessage(ChatColor.GRAY + " - " + problem);
            }
        }
        sender.sendMessage(ChatColor.GRAY + "Kategorien: " + plugin.categories().categoryKeys());
    }

    private void pause(CommandSender sender, String[] args, boolean pause) {
        if (!require(sender, PERMISSION_OPERATE)) return;
        ChestManagerTrait trait = requireTrait(sender, args);
        if (trait == null) return;
        trait.setPaused(pause);
        sender.sendMessage(ChatColor.GREEN + (pause ? "Angehalten: " : "Weitergemacht: ")
                + trait.getNPC().getName());
    }

    // --- Betrieb ----------------------------------------------------------

    private void scan(CommandSender sender, String[] args) {
        if (!require(sender, PERMISSION_OPERATE)) return;
        ChestManagerTrait trait = requireTrait(sender, args);
        if (trait == null) return;
        if (trait.sorter() == null) {
            sender.sendMessage(ChatColor.RED + "Der Sorter ist noch nicht bereit (NPC nicht gespawnt?)");
            return;
        }
        trait.sorter().requestScan();
        sender.sendMessage(ChatColor.GREEN + "Bereich wird neu ermittelt; der Fortschritt steht in"
                + " /cm status " + trait.getNPC().getName());
    }

    private void sortNow(CommandSender sender, String[] args) {
        if (!require(sender, PERMISSION_OPERATE)) return;
        ChestManagerTrait trait = requireTrait(sender, args);
        if (trait == null) return;
        Sorter sorter = trait.sorter();
        if (sorter == null) {
            sender.sendMessage(ChatColor.RED + "Der Sorter ist noch nicht bereit.");
            return;
        }
        if (isBusy(sorter.state())) {
            sender.sendMessage(ChatColor.YELLOW + "Es läuft bereits ein Sortierlauf ("
                    + sorter.state() + "); er wird nicht unterbrochen.");
            return;
        }
        sorter.sortNow();
        sender.sendMessage(ChatColor.GREEN + trait.getNPC().getName() + " sortiert jetzt.");
    }

    private void unblock(CommandSender sender, String[] args) {
        if (!require(sender, PERMISSION_OPERATE)) return;
        ChestManagerTrait trait = requireTrait(sender, args);
        if (trait == null) return;
        if (trait.sorter() == null) {
            sender.sendMessage(ChatColor.RED + "Der Sorter ist noch nicht bereit.");
            return;
        }
        if (args.length >= 5) {
            try {
                BlockPos pos = new BlockPos(Integer.parseInt(args[2]), Integer.parseInt(args[3]),
                        Integer.parseInt(args[4]));
                trait.sorter().blockedChests().unblock(pos);
                sender.sendMessage(ChatColor.GREEN + "Sperre für " + pos + " aufgehoben.");
            } catch (NumberFormatException invalid) {
                sender.sendMessage(ChatColor.RED + "Verwendung: /cm unblock <Name> <x> <y> <z>");
            }
            return;
        }
        // Ohne Koordinaten: alle Sperren dieses Sorters aufheben.
        trait.sorter().blockedChests().clear();
        sender.sendMessage(ChatColor.GREEN + "Alle Sperren von " + trait.getNPC().getName()
                + " aufgehoben.");
    }

    // --- Einsicht ---------------------------------------------------------

    private void list(CommandSender sender) {
        if (!require(sender, PERMISSION_VIEW)) return;
        List<ChestManagerTrait> traits = plugin.traits();
        if (traits.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Keine Sorter vorhanden.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "=== Sorter (" + traits.size() + ") ===");
        for (ChestManagerTrait trait : traits) {
            Sorter sorter = trait.sorter();
            String state = sorter == null ? "nicht bereit" : sorter.state().toString();
            sender.sendMessage(ChatColor.WHITE + " - " + trait.getNPC().getName()
                    + ChatColor.GRAY + " [" + state + "] " + describeHome(trait.home()));
        }
    }

    private void status(CommandSender sender, String[] args) {
        if (!require(sender, PERMISSION_VIEW)) return;
        if (args.length >= 2 && !"*".equals(args[1])) {
            ChestManagerTrait trait = requireTrait(sender, args);
            if (trait == null) return;
            printStatus(sender, trait);
            return;
        }
        List<ChestManagerTrait> traits = plugin.traits();
        if (traits.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Keine Sorter vorhanden.");
            return;
        }
        for (ChestManagerTrait trait : traits) {
            printStatus(sender, trait);
        }
        sender.sendMessage(ChatColor.GRAY + "Gehaltene Chunk-Tickets: " + plugin.heldTicketCount());
    }

    private void printStatus(CommandSender sender, ChestManagerTrait trait) {
        Sorter sorter = trait.sorter();
        sender.sendMessage(ChatColor.GOLD + "=== " + trait.getNPC().getName() + " ===");
        if (sorter == null) {
            sender.sendMessage(ChatColor.RED + "Sorter nicht aufgebaut. Heimatpunkt: "
                    + describeHome(trait.home()));
            return;
        }
        sender.sendMessage(ChatColor.GRAY + "Zustand: " + ChatColor.WHITE + sorter.state()
                + ChatColor.GRAY + " — " + sorter.describe().lines().findFirst().orElse(""));
        sender.sendMessage(ChatColor.GRAY + "Heimat: " + ChatColor.WHITE + describeHome(trait.home()));

        ChestArea area = sorter.area();
        sender.sendMessage(ChatColor.GRAY + "Anker: " + ChatColor.WHITE
                + (area.anchor() == null ? "keiner" : area.anchor().toString()));
        sender.sendMessage(ChatColor.GRAY + "Truhen: " + ChatColor.WHITE + area.marked().size()
                + ChatColor.GRAY + " markiert, " + ChatColor.WHITE + area.unmarked().size()
                + ChatColor.GRAY + " ohne Rolle");

        for (Map.Entry<RoleKind, Integer> entry : area.countByRole().entrySet()) {
            sender.sendMessage(ChatColor.GRAY + "  " + entry.getKey() + ": " + entry.getValue());
        }
        if (!area.countByCategory().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "  Kategorien: " + area.countByCategory());
        }
        if (!sorter.lastScanWarnings().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Letzte Scan-Warnungen:");
            for (String warning : sorter.lastScanWarnings()) {
                sender.sendMessage(ChatColor.GRAY + "  - " + warning);
            }
        }
        sender.sendMessage(ChatColor.GRAY + "Offene Transportvorgänge: "
                + ChatColor.WHITE + sorter.journal().size());
        sender.sendMessage(ChatColor.GRAY + "Letzter Scan: " + ChatColor.WHITE
                + sorter.status().lastScanDurationMillis() + " ms");
    }

    private void blocked(CommandSender sender, String[] args) {
        if (!require(sender, PERMISSION_VIEW)) return;
        ChestManagerTrait trait = requireTrait(sender, args);
        if (trait == null) return;
        if (trait.sorter() == null) {
            sender.sendMessage(ChatColor.RED + "Der Sorter ist noch nicht bereit.");
            return;
        }
        List<BlockedChests.Entry> entries = trait.sorter().blockedChests()
                .active(System.currentTimeMillis());
        if (entries.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "Keine gesperrten Truhen.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "=== Gesperrte Truhen ===");
        long now = System.currentTimeMillis();
        for (BlockedChests.Entry entry : entries) {
            sender.sendMessage(ChatColor.WHITE + " - " + entry.pos() + ChatColor.GRAY
                    + " (" + entry.reason() + ", noch " + (entry.remainingMillis(now) / 1000)
                    + " s, " + entry.attempts() + " Versuche)");
        }
    }

    private void log(CommandSender sender, String[] args) {
        if (!require(sender, PERMISSION_VIEW)) return;
        ChestManagerTrait trait = requireTrait(sender, args);
        if (trait == null) return;
        if (trait.sorter() == null) {
            sender.sendMessage(ChatColor.RED + "Der Sorter ist noch nicht bereit.");
            return;
        }
        String filter = null;
        if (args.length >= 3) {
            Material material = Material.matchMaterial(args[2]);
            if (material == null) {
                sender.sendMessage(ChatColor.RED + "Unbekannter Item-Typ: " + args[2]);
                return;
            }
            filter = material.getKey().toString();
        }
        List<TransportLog.Record> records = trait.sorter().transportLog().recent(15, filter);
        if (records.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Keine Vorgänge protokolliert.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "=== Letzte Transporte ===");
        for (TransportLog.Record record : records) {
            sender.sendMessage(ChatColor.GRAY + " - " + record.amount() + "x "
                    + ChatColor.WHITE + record.key().typeId() + ChatColor.GRAY
                    + " " + record.source() + " -> "
                    + (record.target() == null ? "?" : record.target().toString())
                    + " (" + record.outcome() + ")");
        }
    }

    private void selfTest(CommandSender sender, String[] args) {
        if (!require(sender, PERMISSION_VIEW)) return;
        ChestManagerTrait trait = requireTrait(sender, args);
        if (trait == null) return;
        if (trait.sorter() == null) {
            sender.sendMessage(ChatColor.RED + "Der Sorter ist noch nicht bereit.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "=== Selbsttest: " + trait.getNPC().getName() + " ===");
        int errors = 0;
        for (Sorter.Finding finding : trait.sorter().selfTest()) {
            ChatColor color = switch (finding.severity()) {
                case ERROR -> ChatColor.RED;
                case WARNING -> ChatColor.YELLOW;
                case INFO -> ChatColor.GRAY;
            };
            if (finding.severity() == Sorter.Finding.Severity.ERROR) {
                errors++;
            }
            sender.sendMessage(color + " [" + finding.severity() + "] " + finding.message());
        }
        sender.sendMessage(errors == 0
                ? ChatColor.GREEN + "Selbsttest ohne Fehler beendet."
                : ChatColor.RED + "Selbsttest mit " + errors + " Fehler(n) beendet.");
    }

    private boolean isBusy(SorterState state) {
        return state != SorterState.IDLE && state != SorterState.STOPPED;
    }

    // --- Hilfe ------------------------------------------------------------

    private void help(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== ChestManager ===");
        sender.sendMessage(ChatColor.YELLOW + "Einrichtung");
        sender.sendMessage(ChatColor.GRAY + " /cm create <Name>" + ChatColor.DARK_GRAY + "  Sorter an deiner Position anlegen");
        sender.sendMessage(ChatColor.GRAY + " /cm home [Name]" + ChatColor.DARK_GRAY + "  Heimatpunkt verschieben");
        sender.sendMessage(ChatColor.GRAY + " /cm scan [Name]" + ChatColor.DARK_GRAY + "  Bereich neu ermitteln");
        sender.sendMessage(ChatColor.GRAY + " /cm delete <Name>" + ChatColor.DARK_GRAY + "  Sorter entfernen");
        sender.sendMessage(ChatColor.YELLOW + "Betrieb");
        sender.sendMessage(ChatColor.GRAY + " /cm sortnow [Name]" + ChatColor.DARK_GRAY + "  sofort sortieren");
        sender.sendMessage(ChatColor.GRAY + " /cm pause|resume [Name]" + ChatColor.DARK_GRAY + "  anhalten und fortsetzen");
        sender.sendMessage(ChatColor.GRAY + " /cm unblock <Name> [x y z]" + ChatColor.DARK_GRAY + "  Sperren aufheben");
        sender.sendMessage(ChatColor.YELLOW + "Einsicht");
        sender.sendMessage(ChatColor.GRAY + " /cm list | status [Name] | blocked [Name]" + ChatColor.DARK_GRAY);
        sender.sendMessage(ChatColor.GRAY + " /cm selftest [Name]" + ChatColor.DARK_GRAY + "  Einrichtung prüfen");
        sender.sendMessage(ChatColor.GRAY + " /cm log [Name] [Item]" + ChatColor.DARK_GRAY + "  Transporte ansehen");
        sender.sendMessage(ChatColor.YELLOW + "Konfiguration");
        sender.sendMessage(ChatColor.GRAY + " /cm reload" + ChatColor.DARK_GRAY + "  config.yml neu laden");
        sender.sendMessage(ChatColor.GRAY + " /cm setup" + ChatColor.DARK_GRAY + "  Anleitung mit Schildnamen");
    }

    private void setup(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "ChestManager einrichten");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "1. Truhen markieren");
        sender.sendMessage(ChatColor.GRAY + "   Schild auf den Block über der Truhe oder daneben.");
        sender.sendMessage(ChatColor.WHITE + "   [INPUT]      " + ChatColor.GRAY + "Ankertruhe. Genau eine, sie legt den Bereich fest");
        sender.sendMessage(ChatColor.WHITE + "   [ORES] [FOOD] " + ChatColor.GRAY + "Zieltruhe einer Kategorie aus config.yml");
        sender.sendMessage(ChatColor.WHITE + "   [UNSORTABLE] " + ChatColor.GRAY + "Auffangtruhe für Items ohne Kategorie");
        sender.sendMessage(ChatColor.WHITE + "   [IGNORE]     " + ChatColor.GRAY + "Truhe im Bereich, die der Sorter nicht anfasst");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "2. Sorter anlegen");
        sender.sendMessage(ChatColor.GRAY + "   /cm create Sorter");
        sender.sendMessage(ChatColor.GRAY + "   /cm scan Sorter");
        sender.sendMessage(ChatColor.GRAY + "   /cm selftest Sorter");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "3. Verhalten");
        sender.sendMessage(ChatColor.GRAY + "   Der Sorter läuft den Bereich ab und holt aus jeder markierten");
        sender.sendMessage(ChatColor.GRAY + "   Truhe die Items heraus, die dort nicht hingehören.");
        sender.sendMessage(ChatColor.GRAY + "   Die Ankertruhe wirkt als Eingangspuffer: alles mit bekannter");
        sender.sendMessage(ChatColor.GRAY + "   Kategorie wird von dort wegsortiert.");
    }

    // --- Hilfsmittel ------------------------------------------------------

    private boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("chestmanager.admin")) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Dazu fehlt dir die Berechtigung (" + permission + ").");
        return false;
    }

    private ChestManagerTrait requireTrait(CommandSender sender, String[] args) {
        if (args.length >= 2 && !"*".equals(args[1])) {
            ChestManagerTrait trait = plugin.findTrait(args[1]);
            if (trait == null) {
                sender.sendMessage(ChatColor.RED + "Kein Sorter mit dem Namen '" + args[1] + "'.");
            }
            return trait;
        }
        if (sender instanceof Player player) {
            ChestManagerTrait near = findNearest(player);
            if (near == null) {
                sender.sendMessage(ChatColor.RED + "Kein Sorter in der Nähe. Gib einen Namen an.");
            }
            return near;
        }
        List<ChestManagerTrait> traits = plugin.traits();
        if (traits.size() == 1) {
            return traits.get(0);
        }
        sender.sendMessage(ChatColor.RED + "Bitte einen Namen angeben.");
        return null;
    }

    private ChestManagerTrait findNearest(Player player) {
        ChestManagerTrait best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ChestManagerTrait trait : plugin.traits()) {
            Location home = trait.home();
            if (home == null || home.getWorld() == null
                    || !home.getWorld().equals(player.getWorld())) {
                continue;
            }
            double distance = home.distanceSquared(player.getLocation());
            if (distance < bestDistance && distance <= 400) {
                bestDistance = distance;
                best = trait;
            }
        }
        return best;
    }

    private NPC findNpcByName(String name) {
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.getName() != null && npc.getName().equalsIgnoreCase(name)) {
                return npc;
            }
        }
        return null;
    }

    private String describeHome(Location home) {
        if (home == null) {
            return "nicht gesetzt";
        }
        // Blockkoordinaten, nicht gerundete Weltkoordinaten.
        return home.getBlockX() + " " + home.getBlockY() + " " + home.getBlockZ() + " "
                + (home.getWorld() == null ? "?" : home.getWorld().getName());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    suggestions.add(sub);
                }
            }
            return suggestions;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && !sub.equals("create") && !sub.equals("reload") && !sub.equals("setup")) {
            for (ChestManagerTrait trait : plugin.traits()) {
                if (trait.getNPC() == null || trait.getNPC().getName() == null) {
                    continue;
                }
                String name = trait.getNPC().getName();
                if (name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    suggestions.add(name);
                }
            }
            return suggestions;
        }

        if (args.length == 3 && sub.equals("log")) {
            for (Material material : Material.values()) {
                if (!material.isItem()) {
                    continue;
                }
                String name = material.name();
                if (name.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    suggestions.add(name);
                }
                if (suggestions.size() >= 40) {
                    break;
                }
            }
        }
        return suggestions;
    }
}

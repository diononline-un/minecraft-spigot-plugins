package me.diono.chestmanager.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import me.diono.chestmanager.core.SignRoleParser.Interpretation;
import me.diono.chestmanager.core.port.LogPort;
import me.diono.chestmanager.core.port.WorldPort;

/**
 * Ermittelt den Sortierbereich in zwei Phasen, die beide über mehrere Ticks
 * laufen können.
 *
 * <p>Phase {@code COLLECT} tastet den Suchquader blockweise ab und sammelt jeden
 * Truhenblock mit seiner Schildrolle. Phase {@code LINK} bildet daraus einen
 * Graphen, in dem zwei Truhen verbunden sind, wenn ihr Chebyshev-Abstand höchstens
 * {@code linkDistance + 1} beträgt, und nimmt die Zusammenhangskomponente der
 * Ankertruhe als Bereich.
 */
public final class AreaScanner {

    /** Eine Steuereinheit, die über mehrere Ticks hinweg abgearbeitet wird. */
    public static final class Session {

        private final BlockPos center;
        private final SorterSettings settings;
        private final WorldPort world;
        private final SignRoleParser parser;
        private final LogPort log;

        private final int xMin;
        private final int xMax;
        private final int yMin;
        private final int yMax;
        private final int zMin;
        private final int zMax;
        private final long totalBlocks;

        private long visited;
        private boolean finished;
        private int skippedUnloaded;
        private boolean limitReported;

        private final Map<BlockPos, Interpretation> chests = new LinkedHashMap<>();
        private final Set<BlockPos> unmarkedChests = new LinkedHashSet<>();
        private final List<String> warnings = new ArrayList<>();

        private ChestArea result;

        private Session(BlockPos center, SorterSettings settings, WorldPort world,
                        SignRoleParser parser, LogPort log) {
            this.center = center;
            this.settings = settings;
            this.world = world;
            this.parser = parser;
            this.log = log;

            int radius = settings.searchRadius();
            int vertical = settings.maxHeightDifference();
            this.xMin = center.x() - radius;
            this.xMax = center.x() + radius;
            this.zMin = center.z() - radius;
            this.zMax = center.z() + radius;
            this.yMin = Math.max(world.minY(), center.y() - vertical);
            this.yMax = Math.min(world.maxY(), center.y() + vertical);

            long width = (long) xMax - xMin + 1;
            long depth = (long) zMax - zMin + 1;
            long height = (long) yMax - yMin + 1;
            this.totalBlocks = width * depth * height;
        }

        public boolean isFinished() {
            return finished;
        }

        /** Fortschritt in Prozent, 100 sobald die Ermittlung abgeschlossen ist. */
        public int progressPercent() {
            if (finished) return 100;
            if (totalBlocks <= 0) return 100;
            return (int) Math.min(99, visited * 100 / totalBlocks);
        }

        public long scannedBlocks() {
            return visited;
        }

        public ChestArea area() {
            return result == null ? ChestArea.empty(null) : result;
        }

        public List<String> warnings() {
            return List.copyOf(warnings);
        }

        /**
         * Arbeitet höchstens {@code blocksPerTick} Blockzugriffe ab.
         *
         * @return {@code true}, sobald die Ermittlung abgeschlossen ist
         */
        public boolean step() {
            if (finished) {
                return true;
            }
            int budget = settings.blocksPerTick();
            while (budget > 0 && visited < totalBlocks) {
                scanBlockAt(visited);
                visited++;
                budget--;
            }
            if (visited >= totalBlocks) {
                link();
                finished = true;
            }
            return finished;
        }

        private void scanBlockAt(long index) {
            int height = yMax - yMin + 1;
            int depth = zMax - zMin + 1;
            long perLayer = (long) depth * height;

            long layerIndex = index / perLayer;
            long rest = index % perLayer;
            int z = zMin + (int) (rest / height);
            int y = yMin + (int) (rest % height);
            int x = xMin + (int) layerIndex;

            BlockPos pos = new BlockPos(x, y, z);
            if (!world.isLoaded(pos)) {
                skippedUnloaded++;
                return;
            }
            if (!world.isChest(pos)) {
                return;
            }
            if (chests.size() + unmarkedChests.size() >= settings.maxChests()) {
                if (!limitReported) {
                    limitReported = true;
                    warnings.add("Höchstzahl von " + settings.maxChests()
                            + " Truhen erreicht; weitere Truhen werden nicht erfasst");
                }
                return;
            }

            Optional<Interpretation> role = roleAt(pos);
            if (role.isPresent()) {
                Interpretation interpretation = role.get();
                if (interpretation instanceof Interpretation.UnknownCategory unknown) {
                    warnings.add("Unbekannte Kategorie auf Schild '" + unknown.header()
                            + "' bei " + pos + "; Truhe gilt als nicht markiert");
                    unmarkedChests.add(pos);
                } else {
                    chests.put(pos, interpretation);
                }
            } else {
                unmarkedChests.add(pos);
            }
        }

        /**
         * Sucht das Rollenschild an einem der sechs Nachbarn der Truhe. Ein Schild
         * kann nicht im Truhenblock selbst stehen; ein Schild auf der Truhe ist der
         * Block darüber und damit einer dieser Nachbarn.
         */
        private Optional<Interpretation> roleAt(BlockPos chest) {
            for (BlockPos neighbour : neighbours(chest)) {
                Optional<String> header = world.signHeader(neighbour);
                if (header.isEmpty()) {
                    continue;
                }
                Optional<Interpretation> parsed = parser.parse(header.get());
                if (parsed.isPresent()) {
                    return parsed;
                }
            }
            return Optional.empty();
        }

        private void link() {
            BlockPos anchor = null;
            int anchorCount = 0;
            for (Map.Entry<BlockPos, Interpretation> entry : chests.entrySet()) {
                Interpretation interpretation = entry.getValue();
                if (interpretation instanceof Interpretation.Assignment assignment
                        && assignment.kind() == RoleKind.ANCHOR) {
                    anchorCount++;
                    if (anchor == null) {
                        anchor = entry.getKey();
                    }
                }
            }
            if (anchorCount > 1) {
                warnings.add(anchorCount + " Ankertruhen gefunden; verwendet wird " + anchor
                        + ". Im Sortierbetrieb gilt genau eine Ankertruhe.");
            }
            if (anchor == null) {
                warnings.add("Keine Ankertruhe gefunden; erwartet wird ein Schild mit [INPUT]");
                result = new ChestArea(null, List.of(), List.copyOf(unmarkedChests));
                return;
            }

            Set<BlockPos> component = connectedComponent(anchor);
            List<ChestRef> marked = new ArrayList<>();
            List<BlockPos> unmarked = new ArrayList<>();

            for (BlockPos pos : component) {
                Interpretation interpretation = chests.get(pos);
                if (interpretation instanceof Interpretation.Assignment assignment) {
                    marked.add(new ChestRef(pos, assignment.kind(), assignment.categoryKey(), null));
                } else {
                    unmarked.add(pos);
                }
            }
            for (BlockPos pos : unmarkedChests) {
                if (!component.contains(pos)) {
                    continue;
                }
                if (!unmarked.contains(pos)) {
                    unmarked.add(pos);
                }
            }

            if (component.size() >= settings.maxChests()) {
                warnings.add("Bereich stösst an die Höchstzahl von " + settings.maxChests() + " Truhen");
            }
            if (skippedUnloaded > 0) {
                warnings.add(skippedUnloaded + " Blöcke lagen in nicht geladenen Chunks und wurden übersprungen");
            }

            result = new ChestArea(anchor, marked, unmarked);
        }

        /** Breitensuche über den Truhenverbund ab dem Anker. */
        private Set<BlockPos> connectedComponent(BlockPos anchor) {
            int maxGap = settings.linkDistance() + 1;
            Set<BlockPos> visitedPositions = new LinkedHashSet<>();
            Deque<BlockPos> queue = new ArrayDeque<>();

            if (!allChestPositions().contains(anchor)) {
                return visitedPositions;
            }
            queue.add(anchor);
            visitedPositions.add(anchor);

            while (!queue.isEmpty()) {
                BlockPos current = queue.poll();
                for (BlockPos candidate : allChestPositions()) {
                    if (visitedPositions.contains(candidate)) {
                        continue;
                    }
                    if (current.chebyshevDistance(candidate) <= maxGap) {
                        visitedPositions.add(candidate);
                        queue.add(candidate);
                    }
                }
            }
            return visitedPositions;
        }

        /**
         * Alle Truhenblöcke des Suchquaders, unabhängig von ihrer Markierung. Der
         * Verbund wird über sie gebildet, damit eine Truhe ohne Schild den Bereich
         * nicht zerschneidet.
         */
        private Set<BlockPos> allChestPositions() {
            Set<BlockPos> all = new LinkedHashSet<>(chests.keySet());
            all.addAll(unmarkedChests);
            return all;
        }

        private BlockPos center() {
            return center;
        }
    }

    private static final int[][] NEIGHBOUR_OFFSETS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private final WorldPort world;
    private final CategoryTable categories;
    private final LogPort log;

    public AreaScanner(WorldPort world, CategoryTable categories, LogPort log) {
        this.world = world;
        this.categories = categories;
        this.log = log;
    }

    /**
     * Startet eine Ermittlung um den angegebenen Mittelpunkt. Der Mittelpunkt ist
     * üblicherweise der Heimatpunkt des Sorters; die Ankertruhe wird im Quader gesucht.
     */
    public Session start(BlockPos center, SorterSettings settings) {
        if (center == null) {
            throw new IllegalArgumentException("Mittelpunkt für die Bereichsermittlung fehlt");
        }
        return new Session(center, settings, world, new SignRoleParser(categories), log);
    }

    /** Führt eine vollständige Ermittlung ohne Tick-Grenze aus, für Tests und Selbsttest. */
    public ChestArea scanNow(BlockPos center, SorterSettings settings) {
        Session session = start(center, settings);
        while (!session.step()) {
            // step() arbeitet je Aufruf ein Blockkontingent ab
        }
        return session.area();
    }

    static List<BlockPos> neighbours(BlockPos pos) {
        List<BlockPos> result = new ArrayList<>(NEIGHBOUR_OFFSETS.length);
        for (int[] offset : NEIGHBOUR_OFFSETS) {
            result.add(pos.offset(offset[0], offset[1], offset[2]));
        }
        return result;
    }
}

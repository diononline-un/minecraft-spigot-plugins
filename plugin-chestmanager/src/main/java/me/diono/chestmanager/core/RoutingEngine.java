package me.diono.chestmanager.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Entscheidet, welche Items fehlplatziert sind und wohin sie gehören.
 *
 * <p>Die Engine ist zustandsarm bis auf die Liste der bereits gemeldeten Item-Typen
 * ohne Kategorie: der Betreiber soll je Typ einmal lesen, dass eine Zuordnung fehlt,
 * nicht bei jedem Durchlauf erneut.
 */
public final class RoutingEngine {

    /** Ein fehlplatzierter Stapel in einer Quelltruhe. */
    public record MisplacedStack(ItemKey key, int amount, String categoryKey) {

        /** Ob für diesen Stapel überhaupt eine Kategorie bekannt ist. */
        public boolean hasCategory() {
            return categoryKey != null;
        }
    }

    /** Grund, aus dem kein Ziel gefunden wurde. */
    public enum Rejection {
        /** Kein Item-Typ dieses Namens ist einer Kategorie zugeordnet und es gibt keine Auffangtruhe. */
        NO_CATEGORY_AND_NO_FALLBACK,
        /** Die Kategorie hat keine markierte Zieltruhe. */
        NO_TARGET_CHEST,
        /** Alle Zieltruhen der Kategorie sind voll. */
        CATEGORY_FULL,
        /** Zieltruhen existieren, sind aber als unerreichbar gesperrt. */
        ALL_UNREACHABLE
    }

    /** Ergebnis der Zielsuche. */
    public sealed interface TargetChoice {
        record Found(ChestRef chest, int amount) implements TargetChoice {}
        record Rejected(Rejection reason, String categoryKey) implements TargetChoice {}
    }

    /** Auskunft über Erreichbarkeit und Inhalt, die die Engine nicht selbst ermitteln kann. */
    public interface TargetContext {
        Optional<ChestSnapshot> snapshot(BlockPos pos);

        boolean isReachable(BlockPos pos);
    }

    private final CategoryTable categories;
    private final Set<String> reportedUnknownTypes = new HashSet<>();

    public RoutingEngine(CategoryTable categories) {
        this.categories = categories;
    }

    /**
     * Ob ein Item in dieser Truhe fehlplatziert ist.
     *
     * <p>Die Ankertruhe führt keine Kategorie und wirkt als Eingangspuffer: alles mit
     * bekannter Kategorie ist darin fehlplatziert. Die Auffangtruhe ist das Ziel der
     * Items ohne Kategorie. In einer Kategorietruhe ist ein Item ohne Kategorie nur
     * dann fehlplatziert, wenn es überhaupt eine Auffangtruhe gibt.
     */
    public boolean isMisplaced(ChestRef chest, ItemKey key, ChestArea area) {
        if (chest.kind() == RoleKind.IGNORED) {
            return false;
        }
        Optional<String> category = categories.categoryOf(key);

        return switch (chest.kind()) {
            // Die Ankertruhe ist Eingangspuffer: alles mit Kategorie gehört woanders hin.
            // Items ohne Kategorie gehören in die Auffangtruhe, sofern es eine gibt.
            case ANCHOR -> category.isPresent() || area.fallback().isPresent();
            case FALLBACK -> category.isPresent();
            case CATEGORY -> category
                    .map(found -> !found.equals(chest.categoryKey()))
                    .orElseGet(() -> area.fallback().isPresent());
            case IGNORED -> false;
        };
    }

    /** Alle fehlplatzierten Stapel einer Truhe, in Slotreihenfolge. */
    public List<MisplacedStack> misplacedIn(ChestRef chest, ChestSnapshot snapshot, ChestArea area) {
        if (!chest.kind().isReadable()) {
            return List.of();
        }
        List<MisplacedStack> result = new ArrayList<>();
        for (int slot = 0; slot < snapshot.size(); slot++) {
            ChestSnapshot.StackView stack = snapshot.slot(slot);
            if (stack == null) continue;
            if (!isMisplaced(chest, stack.key(), area)) continue;
            result.add(new MisplacedStack(stack.key(), stack.amount(),
                    categories.categoryOf(stack.key()).orElse(null)));
        }
        return result;
    }

    /**
     * Wählt die Zieltruhe für einen Stapel.
     *
     * <p>Reihenfolge: zuerst eine Truhe, die denselben {@link ItemKey} führt und dort
     * noch Platz hat; danach die erreichbare Truhe mit dem meisten freien Platz für
     * diesen Schlüssel. Bei Gleichstand entscheidet die Blockposition, damit
     * wiederholte Läufe dasselbe Ergebnis liefern.
     */
    public TargetChoice chooseTarget(ItemKey key, int amount, ChestArea area, TargetContext context) {
        Optional<String> category = categories.categoryOf(key);

        List<ChestRef> candidates = new ArrayList<>();
        String categoryLabel;
        if (category.isPresent()) {
            categoryLabel = category.get();
            candidates.addAll(area.targets(categoryLabel));
        } else {
            categoryLabel = null;
            area.fallback().ifPresent(candidates::add);
        }

        if (candidates.isEmpty()) {
            if (category.isEmpty()) {
                reportUnknownType(key);
                return new TargetChoice.Rejected(Rejection.NO_CATEGORY_AND_NO_FALLBACK, null);
            }
            return new TargetChoice.Rejected(Rejection.NO_TARGET_CHEST, categoryLabel);
        }

        List<ChestRef> reachable = candidates.stream().filter(ref -> context.isReachable(ref.pos())).toList();
        if (reachable.isEmpty()) {
            return new TargetChoice.Rejected(Rejection.ALL_UNREACHABLE, categoryLabel);
        }

        record Candidate(ChestRef ref, boolean hasStack, int free) {}

        List<Candidate> viable = new ArrayList<>();
        boolean anyCapacity = false;
        for (ChestRef ref : reachable) {
            Optional<ChestSnapshot> snapshot = context.snapshot(ref.pos());
            if (snapshot.isEmpty()) continue;
            int free = snapshot.get().freeCapacityFor(key);
            if (free <= 0) continue;
            anyCapacity = true;
            viable.add(new Candidate(ref, snapshot.get().hasRoomInExistingStack(key), free));
        }

        if (!anyCapacity) {
            return new TargetChoice.Rejected(Rejection.CATEGORY_FULL, categoryLabel);
        }

        viable.sort(Comparator
                .comparing(Candidate::hasStack, Comparator.reverseOrder())
                .thenComparing(Candidate::free, Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.ref().pos().x())
                .thenComparing(candidate -> candidate.ref().pos().y())
                .thenComparing(candidate -> candidate.ref().pos().z()));

        Candidate best = viable.get(0);
        int room = context.snapshot(best.ref().pos())
                .map(snapshot -> snapshot.freeCapacityFor(key))
                .orElse(0);
        return new TargetChoice.Found(best.ref(), Math.min(amount, room));
    }

    /** Ob dieser Item-Typ dem Betreiber bereits als kategorielos gemeldet wurde. */
    public boolean hasReportedUnknownType(ItemKey key) {
        return reportedUnknownTypes.contains(key.typeId());
    }

    private void reportUnknownType(ItemKey key) {
        reportedUnknownTypes.add(key.typeId());
    }

    /** Nach einem Konfigurationswechsel dürfen fehlende Zuordnungen erneut gemeldet werden. */
    public void clearReportedTypes() {
        reportedUnknownTypes.clear();
    }

    public CategoryTable categories() {
        return categories;
    }
}

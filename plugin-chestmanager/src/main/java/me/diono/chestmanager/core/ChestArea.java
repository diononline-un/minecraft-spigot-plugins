package me.diono.chestmanager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Der erkannte Sortierbereich: alle Truhen, die über den Anker zusammenhängen,
 * mit ihrer jeweiligen Rolle.
 *
 * <p>Truhen ohne Rollenschild gehören zum Verbund, sind aber weder Quelle noch
 * Ziel. Sie werden getrennt geführt, damit die Bereichsübersicht sie ausweisen kann.
 */
public final class ChestArea {

    private final BlockPos anchor;
    private final List<ChestRef> marked;
    private final List<BlockPos> unmarked;
    private final Map<BlockPos, ChestRef> byPosition;

    public ChestArea(BlockPos anchor, List<ChestRef> marked, List<BlockPos> unmarked) {
        this.anchor = anchor;
        this.marked = List.copyOf(marked);
        this.unmarked = List.copyOf(unmarked);

        Map<BlockPos, ChestRef> index = new LinkedHashMap<>();
        for (ChestRef ref : this.marked) {
            index.put(ref.pos(), ref);
        }
        this.byPosition = Collections.unmodifiableMap(index);
    }

    public static ChestArea empty(BlockPos anchor) {
        return new ChestArea(anchor, List.of(), List.of());
    }

    /** Ankerblock des Bereichs; {@code null}, wenn kein Anker gefunden wurde. */
    public BlockPos anchor() {
        return anchor;
    }

    public boolean hasAnchor() {
        return anchor != null;
    }

    /** Alle markierten Truhen, einschliesslich der ignorierten. */
    public List<ChestRef> marked() {
        return marked;
    }

    /** Truhen im Verbund ohne Rollenschild. */
    public List<BlockPos> unmarked() {
        return unmarked;
    }

    /** Truhen, aus denen der Sorter Items entnehmen darf. */
    public List<ChestRef> sources() {
        List<ChestRef> result = new ArrayList<>();
        for (ChestRef ref : marked) {
            if (ref.kind().isReadable()) {
                result.add(ref);
            }
        }
        return result;
    }

    /** Zieltruhen einer Kategorie. */
    public List<ChestRef> targets(String categoryKey) {
        List<ChestRef> result = new ArrayList<>();
        for (ChestRef ref : marked) {
            if (ref.targets(categoryKey)) {
                result.add(ref);
            }
        }
        return result;
    }

    /** Auffangtruhe, falls markiert. */
    public Optional<ChestRef> fallback() {
        return marked.stream().filter(ref -> ref.kind() == RoleKind.FALLBACK).findFirst();
    }

    public Optional<ChestRef> at(BlockPos pos) {
        return Optional.ofNullable(byPosition.get(pos));
    }

    public Set<BlockPos> positions() {
        return byPosition.keySet();
    }

    public int size() {
        return marked.size();
    }

    public boolean isEmpty() {
        return marked.isEmpty();
    }

    /** Anzahl markierter Truhen je Rolle, für Status- und Scanausgaben. */
    public Map<RoleKind, Integer> countByRole() {
        Map<RoleKind, Integer> counts = new LinkedHashMap<>();
        for (RoleKind kind : RoleKind.values()) {
            int count = (int) marked.stream().filter(ref -> ref.kind() == kind).count();
            if (count > 0 || kind != RoleKind.IGNORED) {
                counts.put(kind, count);
            }
        }
        return counts;
    }

    /** Anzahl Zieltruhen je Kategorie. */
    public Map<String, Integer> countByCategory() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ChestRef ref : marked) {
            if (ref.kind() == RoleKind.CATEGORY && ref.categoryKey() != null) {
                counts.merge(ref.categoryKey(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * Ob sich zwei Bereiche eine Truhe teilen. Zwei Sorter dürfen nicht dieselbe
     * Truhe bearbeiten, deshalb meldet der Betreiber einen solchen Konflikt.
     */
    public boolean overlaps(ChestArea other) {
        if (other == null) {
            return false;
        }
        for (BlockPos pos : byPosition.keySet()) {
            if (other.byPosition.containsKey(pos)) {
                return true;
            }
        }
        return false;
    }

    /** Die gemeinsamen Truhen zweier Bereiche. */
    public List<BlockPos> sharedWith(ChestArea other) {
        if (other == null) {
            return List.of();
        }
        List<BlockPos> shared = new ArrayList<>();
        for (BlockPos pos : byPosition.keySet()) {
            if (other.byPosition.containsKey(pos)) {
                shared.add(pos);
            }
        }
        return shared;
    }
}

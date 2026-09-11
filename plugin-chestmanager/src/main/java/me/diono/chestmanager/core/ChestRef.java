package me.diono.chestmanager.core;

/**
 * Eine Truhe des Sortierbereichs mit ihrer Rolle. Doppeltruhen werden als eine
 * Einheit geführt; {@link #pos()} ist dann die linke Hälfte.
 *
 * @param pos         Ankerblock der Truhe
 * @param kind        Rolle im Sortierbetrieb
 * @param categoryKey Kategorieschlüssel, nur bei {@link RoleKind#CATEGORY} gesetzt
 * @param accessPoint begehbare Position neben der Truhe, {@code null} solange noch
 *                    kein Zugangspunkt aufgelöst wurde
 */
public record ChestRef(BlockPos pos, RoleKind kind, String categoryKey, BlockPos accessPoint) {

    public ChestRef {
        if (kind == RoleKind.CATEGORY && (categoryKey == null || categoryKey.isEmpty())) {
            throw new IllegalArgumentException("CATEGORY-Truhe ohne Kategorieschlüssel: " + pos);
        }
    }

    public static ChestRef category(BlockPos pos, String categoryKey) {
        return new ChestRef(pos, RoleKind.CATEGORY, categoryKey, null);
    }

    public static ChestRef of(BlockPos pos, RoleKind kind) {
        return new ChestRef(pos, kind, null, null);
    }

    public ChestRef withAccessPoint(BlockPos point) {
        return new ChestRef(pos, kind, categoryKey, point);
    }

    /** Ob diese Truhe Zieltruhe für die angegebene Kategorie ist. */
    public boolean targets(String wantedCategory) {
        return kind == RoleKind.CATEGORY && categoryKey != null && categoryKey.equals(wantedCategory);
    }

    public boolean hasAccessPoint() {
        return accessPoint != null;
    }

    @Override
    public String toString() {
        return kind + (categoryKey != null ? "[" + categoryKey + "]" : "") + "@" + pos;
    }
}

package me.diono.chestmanager.core;

import java.util.List;
import java.util.Optional;

/**
 * Lesbare Sicht auf ein Truheninventar. Ein Stapel entspricht genau einem Slot,
 * damit der Kern Mengen und Stapelgrenzen nachrechnen kann, ohne Bukkit zu kennen.
 */
public record ChestSnapshot(List<StackView> slots, int stackSize) {

    public record StackView(ItemKey key, int amount) {
        public StackView {
            if (amount < 0) throw new IllegalArgumentException("Menge darf nicht negativ sein");
        }
    }

    public static ChestSnapshot empty(int size, int stackSize) {
        return new ChestSnapshot(java.util.Collections.nCopies(size, null), stackSize);
    }

    public int size() {
        return slots.size();
    }

    public StackView slot(int index) {
        return slots.get(index);
    }

    public boolean isEmpty() {
        return slots.stream().allMatch(java.util.Objects::isNull);
    }

    /**
     * Freie Aufnahmekapazität für einen bestimmten Item-Schlüssel: Restplätze in
     * passenden Stapeln plus volle Stapel in leeren Slots.
     */
    public int freeCapacityFor(ItemKey key) {
        int free = 0;
        for (StackView slot : slots) {
            if (slot == null) {
                free += stackSize;
            } else if (slot.key().equals(key)) {
                free += Math.max(0, stackSize - slot.amount());
            }
        }
        return free;
    }

    /** Gesamtzahl freier Stapelplätze, unabhängig vom Item-Typ. */
    public int freeSlots() {
        return (int) slots.stream().filter(java.util.Objects::isNull).count();
    }

    public int totalAmount() {
        return slots.stream().filter(java.util.Objects::nonNull)
                .mapToInt(StackView::amount).sum();
    }

    /** Ob ein Stapel mit diesem Schlüssel bereits existiert und noch Platz hat. */
    public boolean hasRoomInExistingStack(ItemKey key) {
        return slots.stream().anyMatch(slot -> slot != null
                && slot.key().equals(key)
                && slot.amount() < stackSize);
    }

    public Optional<StackView> firstStackOf(ItemKey key) {
        return slots.stream().filter(java.util.Objects::nonNull)
                .filter(slot -> slot.key().equals(key))
                .findFirst();
    }
}

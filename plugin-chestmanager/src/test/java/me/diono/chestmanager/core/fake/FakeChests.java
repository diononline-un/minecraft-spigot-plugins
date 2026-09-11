package me.diono.chestmanager.core.fake;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import me.diono.chestmanager.core.BlockPos;
import me.diono.chestmanager.core.ChestSnapshot;
import me.diono.chestmanager.core.ItemKey;
import me.diono.chestmanager.core.port.CarryPort;
import me.diono.chestmanager.core.port.ChestPort;

/**
 * Truhen- und Träger-Attrappe mit echter Stapelarithmetik, damit Tests Mengen
 * prüfen können statt nur Aufrufe zu zählen.
 *
 * <p>Der Träger ist ein eigenes kleines Inventar, das der Sorter bei sich führt.
 * Sein serialisierter Inhalt ist bewusst als Text dargestellt: Der Kern behandelt
 * ihn undurchsichtig, genau wie im Bukkit-Adapter.
 */
public final class FakeChests implements ChestPort, CarryPort {

    private static final String ENTRY_SEPARATOR = "\u0002";
    private static final String FIELD_SEPARATOR = "\u0001";

    private record Carried(ItemKey key, int amount) {}

    private final Map<BlockPos, List<ItemKey>> slots = new HashMap<>();
    private final Map<BlockPos, int[]> amounts = new HashMap<>();
    private final Map<BlockPos, Integer> sizes = new HashMap<>();
    private final Map<BlockPos, Integer> stackSizes = new HashMap<>();

    private final List<Carried> carried = new ArrayList<>();
    private int carriedCapacity = 54;
    private boolean dropAtPosition;
    private int droppedAmount;

    private int extractCalls;
    private int depositCalls;
    private int previewCalls;

    public FakeChests define(BlockPos pos, int size) {
        return define(pos, size, 64);
    }

    public FakeChests define(BlockPos pos, int size, int stackSize) {
        List<ItemKey> row = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            row.add(null);
        }
        slots.put(pos, row);
        amounts.put(pos, new int[size]);
        sizes.put(pos, size);
        stackSizes.put(pos, stackSize);
        return this;
    }

    public FakeChests remove(BlockPos pos) {
        slots.remove(pos);
        amounts.remove(pos);
        sizes.remove(pos);
        stackSizes.remove(pos);
        return this;
    }

    /** Setzt die Aufnahmefähigkeit des Trägers, um Rückgabefälle zu prüfen. */
    public FakeChests carryCapacity(int capacity) {
        this.carriedCapacity = capacity;
        return this;
    }

    /** Lässt das Ablegen an der Position fehlschlagen. */
    public FakeChests failDrop() {
        this.dropAtPosition = false;
        return this;
    }

    /** Setzt einen Stapel in die ersten passenden Slots. */
    public FakeChests put(BlockPos pos, ItemKey key, int amount) {
        if (!slots.containsKey(pos)) {
            define(pos, 27);
        }
        List<ItemKey> row = slots.get(pos);
        int[] counts = amounts.get(pos);
        int stackSize = stackSizes.get(pos);
        int remaining = amount;

        for (int i = 0; i < row.size() && remaining > 0; i++) {
            if (key.equals(row.get(i)) && counts[i] < stackSize) {
                int add = Math.min(stackSize - counts[i], remaining);
                counts[i] += add;
                remaining -= add;
            }
        }
        for (int i = 0; i < row.size() && remaining > 0; i++) {
            if (row.get(i) == null) {
                int add = Math.min(stackSize, remaining);
                row.set(i, key);
                counts[i] = add;
                remaining -= add;
            }
        }
        if (remaining > 0) {
            throw new IllegalStateException("Testtruhe " + pos + " ist voll");
        }
        return this;
    }

    public int countOf(BlockPos pos, ItemKey key) {
        List<ItemKey> row = slots.get(pos);
        if (row == null) return 0;
        int[] counts = amounts.get(pos);
        int total = 0;
        for (int i = 0; i < row.size(); i++) {
            if (key.equals(row.get(i))) {
                total += counts[i];
            }
        }
        return total;
    }

    public int countOfType(BlockPos pos, String typeId) {
        List<ItemKey> row = slots.get(pos);
        if (row == null) return 0;
        int[] counts = amounts.get(pos);
        int total = 0;
        for (int i = 0; i < row.size(); i++) {
            ItemKey key = row.get(i);
            if (key != null && key.typeId().equals(typeId)) {
                total += counts[i];
            }
        }
        return total;
    }

    public int totalItems(BlockPos pos) {
        List<ItemKey> row = slots.get(pos);
        if (row == null) return 0;
        int[] counts = amounts.get(pos);
        int total = 0;
        for (int i = 0; i < row.size(); i++) {
            if (row.get(i) != null) {
                total += counts[i];
            }
        }
        return total;
    }

    public int extractCalls() {
        return extractCalls;
    }

    public int depositCalls() {
        return depositCalls;
    }

    public int previewCalls() {
        return previewCalls;
    }

    public int sizeOf(BlockPos pos) {
        Integer size = sizes.get(pos);
        return size == null ? 0 : size;
    }

    // --- Träger ----------------------------------------------------------

    public int carriedTotal() {
        return carried.stream().mapToInt(Carried::amount).sum();
    }

    public int droppedTotal() {
        return droppedAmount;
    }

    public boolean isDropAtPosition() {
        return dropAtPosition;
    }

    /** Setzt den Trägerinhalt direkt, für Wiederherstellungstests. */
    public FakeChests carry(ItemKey key, int amount) {
        carried.add(new Carried(key, amount));
        return this;
    }

    @Override
    public Optional<String> preview(BlockPos pos, ItemKey key, int amount) {
        previewCalls++;
        int available = countOf(pos, key);
        if (available <= 0) {
            return Optional.empty();
        }
        int take = Math.min(available, amount);
        return Optional.of(serialize(List.of(new Carried(key, take))));
    }

    @Override
    public int extract(BlockPos pos, ItemKey key, int amount) {
        extractCalls++;
        List<ItemKey> row = slots.get(pos);
        if (row == null) return 0;
        int[] counts = amounts.get(pos);
        int remaining = amount;

        for (int i = 0; i < row.size() && remaining > 0; i++) {
            if (!key.equals(row.get(i))) continue;
            int take = Math.min(counts[i], remaining);
            counts[i] -= take;
            remaining -= take;
            if (counts[i] == 0) {
                row.set(i, null);
            }
        }
        int taken = amount - remaining;
        if (taken > 0) {
            carry(key, taken);
        }
        return taken;
    }

    @Override
    public int deposit(BlockPos pos) {
        depositCalls++;
        List<ItemKey> row = slots.get(pos);
        if (row == null) {
            return carriedTotal();
        }
        int[] counts = amounts.get(pos);
        int stackSize = stackSizes.get(pos);
        int remainingTotal = 0;

        List<Carried> leftovers = new ArrayList<>();
        for (Carried stack : carried) {
            int remaining = stack.amount();
            for (int i = 0; i < row.size() && remaining > 0; i++) {
                if (stack.key().equals(row.get(i)) && counts[i] < stackSize) {
                    int add = Math.min(stackSize - counts[i], remaining);
                    counts[i] += add;
                    remaining -= add;
                }
            }
            for (int i = 0; i < row.size() && remaining > 0; i++) {
                if (row.get(i) == null) {
                    int add = Math.min(stackSize, remaining);
                    row.set(i, stack.key());
                    counts[i] = add;
                    remaining -= add;
                }
            }
            if (remaining > 0) {
                leftovers.add(new Carried(stack.key(), remaining));
                remainingTotal += remaining;
            }
        }
        carried.clear();
        carried.addAll(leftovers);
        return remainingTotal;
    }

    @Override
    public String carriedPayload() {
        return serialize(carried);
    }

    @Override
    public void loadCarried(String payload) {
        carried.clear();
        carried.addAll(deserialize(payload));
    }

    @Override
    public int dropCarried() {
        int total = carriedTotal();
        if (total > carriedCapacity) {
            return 0;
        }
        dropAtPosition = true;
        droppedAmount += total;
        carried.clear();
        return total;
    }

    @Override
    public boolean isCarryEmpty() {
        return carried.isEmpty();
    }

    // --- Serialisierung --------------------------------------------------

    private static String serialize(List<Carried> stacks) {
        StringBuilder builder = new StringBuilder();
        for (Carried stack : stacks) {
            if (builder.length() > 0) {
                builder.append(ENTRY_SEPARATOR);
            }
            builder.append(stack.key().typeId()).append(FIELD_SEPARATOR)
                    .append(stack.key().variantKey()).append(FIELD_SEPARATOR)
                    .append(stack.amount());
        }
        return builder.toString();
    }

    private static List<Carried> deserialize(String payload) {
        List<Carried> result = new ArrayList<>();
        if (payload == null || payload.isEmpty()) {
            return result;
        }
        for (String entry : payload.split(ENTRY_SEPARATOR)) {
            String[] fields = entry.split(FIELD_SEPARATOR);
            result.add(new Carried(ItemKey.of(fields[0], fields[1]), Integer.parseInt(fields[2])));
        }
        return result;
    }

    @Override
    public Optional<ChestSnapshot> read(BlockPos pos) {
        List<ItemKey> row = slots.get(pos);
        if (row == null) return Optional.empty();
        int[] counts = amounts.get(pos);
        List<ChestSnapshot.StackView> views = new ArrayList<>(row.size());
        for (int i = 0; i < row.size(); i++) {
            views.add(row.get(i) == null ? null : new ChestSnapshot.StackView(row.get(i), counts[i]));
        }
        return Optional.of(new ChestSnapshot(views, stackSizes.get(pos)));
    }
}

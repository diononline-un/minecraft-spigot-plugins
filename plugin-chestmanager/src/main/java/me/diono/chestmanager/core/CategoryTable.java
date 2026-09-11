package me.diono.chestmanager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Kategorien und ihre Item-Zuordnung. Die Reihenfolge der Definitionen entscheidet
 * bei mehrfach genannten Item-Typen: die zuerst definierte Kategorie gewinnt.
 */
public final class CategoryTable {

    /** Eine Kategorie, wie sie aus der Konfiguration gelesen wurde. */
    public record Definition(String key, String display, String iconTypeId, List<String> itemTypeIds) {
        public Definition {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("Kategorieschlüssel fehlt");
            itemTypeIds = itemTypeIds == null ? List.of() : List.copyOf(itemTypeIds);
        }
    }

    /**
     * Ergebnis des Ladens. {@code warnings} nennt jede Doppelnennung mit beiden
     * Kategorienamen, damit der Betreiber sie korrigieren kann.
     */
    public record LoadResult(CategoryTable table, List<String> warnings) {}

    private final Map<String, String> itemToCategory;
    private final Map<String, Definition> definitions;

    private CategoryTable(Map<String, String> itemToCategory, Map<String, Definition> definitions) {
        this.itemToCategory = itemToCategory;
        this.definitions = definitions;
    }

    public static LoadResult load(List<Definition> source) {
        Map<String, String> itemToCategory = new LinkedHashMap<>();
        Map<String, Definition> definitions = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        for (Definition definition : source) {
            if (definitions.containsKey(definition.key())) {
                warnings.add("Kategorie '" + definition.key() + "' ist mehrfach definiert; "
                        + "die spätere Definition wird ignoriert");
                continue;
            }
            definitions.put(definition.key(), definition);

            for (String typeId : definition.itemTypeIds()) {
                String previous = itemToCategory.putIfAbsent(typeId, definition.key());
                if (previous != null) {
                    warnings.add("Item '" + typeId + "' ist in '" + previous + "' und '"
                            + definition.key() + "' genannt; es gilt '" + previous + "'");
                }
            }
        }

        return new LoadResult(new CategoryTable(itemToCategory, definitions), List.copyOf(warnings));
    }

    public static CategoryTable empty() {
        return new CategoryTable(Map.of(), Map.of());
    }

    /** Kategorie eines Items, leer wenn der Typ in keiner Kategorie vorkommt. */
    public Optional<String> categoryOf(ItemKey key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(itemToCategory.get(key.typeId()));
    }

    public boolean hasCategory(String categoryKey) {
        return categoryKey != null && definitions.containsKey(categoryKey);
    }

    public Set<String> categoryKeys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(definitions.keySet()));
    }

    public List<Definition> definitions() {
        return List.copyOf(definitions.values());
    }

    public String display(String categoryKey) {
        Definition definition = definitions.get(categoryKey);
        return definition != null ? definition.display() : categoryKey;
    }

    public String iconTypeId(String categoryKey) {
        Definition definition = definitions.get(categoryKey);
        return definition != null ? definition.iconTypeId() : "minecraft:chest";
    }

    public int knownItemCount() {
        return itemToCategory.size();
    }
}

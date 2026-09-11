package me.diono.chestmanager.core;

import java.util.Objects;

/**
 * Identität eines Item-Stapels. {@code typeId} ist der Material-Schlüssel
 * ({@code minecraft:iron_ore}), {@code variantKey} fasst alles zusammen, was den
 * Stapel von einem gleichartigen unterscheidet: Enchantments, Anzeigename,
 * CustomModelData und persistente Datacontainer.
 *
 * <p>Kategorien ordnen ausschliesslich über {@link #typeId()} zu. Zwei Stapel
 * gelten nur dann als stapelbar, wenn beide Teile gleich sind.
 */
public record ItemKey(String typeId, String variantKey) {

    /** Variante für Items ohne Metadaten. */
    public static final String PLAIN = "-";

    public ItemKey {
        Objects.requireNonNull(typeId, "typeId");
        if (variantKey == null || variantKey.isEmpty()) {
            variantKey = PLAIN;
        }
    }

    public static ItemKey of(String typeId) {
        return new ItemKey(typeId, PLAIN);
    }

    public static ItemKey of(String typeId, String variantKey) {
        return new ItemKey(typeId, variantKey);
    }

    public boolean sameType(ItemKey other) {
        return other != null && typeId.equals(other.typeId);
    }

    @Override
    public String toString() {
        return variantKey.equals(PLAIN) ? typeId : typeId + "{" + variantKey + "}";
    }
}

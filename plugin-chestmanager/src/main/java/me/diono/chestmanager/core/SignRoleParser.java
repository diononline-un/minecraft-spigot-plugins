package me.diono.chestmanager.core;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest die Rolle einer Truhe aus der ersten Zeile eines Schildes. Der Vergleich
 * ignoriert Gross- und Kleinschreibung, eckige Klammern und überzählige Leerzeichen,
 * damit die vorhandenen Schilder auf dem Server unverändert weiter funktionieren.
 */
public final class SignRoleParser {

    /** Ergebnis der Auswertung eines Schildtextes. */
    public sealed interface Interpretation {

        /** Das Schild nennt eine bekannte Rolle. */
        record Assignment(RoleKind kind, String categoryKey) implements Interpretation {}

        /** Das Schild sieht wie eine Rolle aus, die Kategorie ist aber unbekannt. */
        record UnknownCategory(String header) implements Interpretation {}
    }

    private static final Set<String> ANCHOR_TOKENS = Set.of("input", "anchor", "anker", "eingang");
    private static final Set<String> FALLBACK_TOKENS =
            Set.of("unsortable", "unsortet", "unsortiert", "sonstige", "rest", "other", "fallback");
    private static final Set<String> IGNORE_TOKENS =
            Set.of("ignore", "ignorieren", "skip", "none", "keine");

    /** Findet das Textfeld in einer JSON-Komponente, auch mit Escapes. */
    private static final Pattern JSON_TEXT =
            Pattern.compile("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final CategoryTable categories;

    public SignRoleParser(CategoryTable categories) {
        this.categories = categories;
    }

    /**
     * Wertet einen Schildtext aus.
     *
     * @return leer, wenn das Schild keine Rolle nennt oder leer ist
     */
    public Optional<Interpretation> parse(String header) {
        String token = normalize(header);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        if (ANCHOR_TOKENS.contains(token)) {
            return Optional.of(new Interpretation.Assignment(RoleKind.ANCHOR, null));
        }
        if (FALLBACK_TOKENS.contains(token)) {
            return Optional.of(new Interpretation.Assignment(RoleKind.FALLBACK, null));
        }
        if (IGNORE_TOKENS.contains(token)) {
            return Optional.of(new Interpretation.Assignment(RoleKind.IGNORED, null));
        }
        if (categories.hasCategory(token)) {
            return Optional.of(new Interpretation.Assignment(RoleKind.CATEGORY, token));
        }
        return Optional.of(new Interpretation.UnknownCategory(header == null ? "" : header.trim()));
    }

    /** Entfernt Klammern, vereinheitlicht Schreibweise und Leerraum. */
    static String normalize(String header) {
        String text = unwrapJson(header);
        if (text == null) {
            return "";
        }
        String token = text.replace("[", "").replace("]", "");
        token = token.trim().toLowerCase(Locale.ROOT);
        return token.replaceAll("\\s+", " ");
    }

    /**
     * Schilder, die per Befehl gesetzt wurden, tragen ihren Text als JSON-Komponente
     * ({@code {"text":"[INPUT]"}}). Ein Spieler beschriftet sie dagegen im Klartext.
     * Beide Formen müssen dieselbe Rolle ergeben.
     */
    static String unwrapJson(String header) {
        if (header == null) {
            return "";
        }
        String trimmed = header.trim();
        if (trimmed.startsWith("{")) {
            Matcher matcher = JSON_TEXT.matcher(trimmed);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return header;
    }
}

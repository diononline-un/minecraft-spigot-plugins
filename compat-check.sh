#!/usr/bin/env bash
set -euo pipefail

# ============================================================
#  compat-check.sh
#  Prüft alle Plugin-Module auf deprecated API-Usage und
#  potenzielle Breaking Changes beim Spigot-Versionsupdate.
#
#  Nutzung:
#    ./compat-check.sh              – Check gegen Baseline
#    ./compat-check.sh --baseline   – Neuen Baseline-Snapshot anlegen
#    ./compat-check.sh --diff       – Diff zwischen Baseline und aktuellem Stand
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASELINE_DIR="$SCRIPT_DIR/compat-baseline"
CURRENT_DIR="$SCRIPT_DIR/compat-current"
REPORT_FILE="$SCRIPT_DIR/compat-report.txt"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

command_help() {
    echo "Kompatibilitäts-Checker für Spigot-Plugin-Updates"
    echo ""
    echo "  --baseline    Erstelle neuen Baseline-Snapshot (nach Versionsupdate)"
    echo "  --diff        Zeige Unterschiede zwischen Baseline und aktuellem Build"
    echo "  --help        Diese Hilfe"
    echo ""
    echo "  Ohne Flag: Führt Check aus und zeigt neue Fehler/Warnungen"
}

# ── Build ausführen ──────────────────────────────────────────
run_build() {
    local outdir="$1"
    rm -rf "$outdir"
    mkdir -p "$outdir"

    echo -e "${CYAN}▶ Baue alle Module (Kompilierung erzwingen)...${NC}"
    mvn clean compile -f "$SCRIPT_DIR/pom.xml" 2>&1 | tee "$outdir/build.log"

    # Extrahiere nur relevante Warnungen und Fehler
    grep -E '(deprecation|unchecked|^\[ERROR\]|warning)' "$outdir/build.log" \
        | grep -v 'BUILD SUCCESS' \
        | grep -v 'BUILD FAILURE' \
        | sort -u > "$outdir/warnings.log" 2>/dev/null || true
}

# ── Analyse ──────────────────────────────────────────────────
analyze() {
    if [ ! -d "$BASELINE_DIR" ] || [ ! -f "$BASELINE_DIR/warnings.log" ]; then
        echo -e "${YELLOW}⚠ Keine Baseline vorhanden. Erstelle sie mit:${NC}"
        echo "  ./compat-check.sh --baseline"
        exit 1
    fi

    echo -e "${CYAN}▶ Analysiere Unterschiede...${NC}"

    local new_count=0
    local fixed_count=0
    local errors_count=0

    comm -13 "$BASELINE_DIR/warnings.log" "$CURRENT_DIR/warnings.log" > "$REPORT_FILE.new" 2>/dev/null || true
    comm -23 "$BASELINE_DIR/warnings.log" "$CURRENT_DIR/warnings.log" > "$REPORT_FILE.fixed" 2>/dev/null || true

    new_count=$(wc -l < "$REPORT_FILE.new" 2>/dev/null || echo 0)
    fixed_count=$(wc -l < "$REPORT_FILE.fixed" 2>/dev/null || echo 0)
    errors_count=$(grep -c '^\[ERROR\]' "$CURRENT_DIR/warnings.log" 2>/dev/null || echo 0)

    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo -e "  ${GREEN}Gelöscht/Fixed:  $fixed_count${NC}"
    echo -e "  ${RED}NEU (Breaking!): $new_count${NC}"
    echo -e "  ${YELLOW}Errors gesamt:   $errors_count${NC}"
    echo "═══════════════════════════════════════════════════════════"
    echo ""

    if [ "$new_count" -gt 0 ]; then
        echo -e "${RED}═══ POTENZIELLE BREAKING CHANGES ═══${NC}"
        while IFS= read -r line; do
            echo -e "${RED}  ► $line${NC}"
        done < "$REPORT_FILE.new"
        echo ""
        echo -e "${RED}⚠ BREAKING CHANGES ERKANNT!${NC}"
        echo "  Neue deprecation/error-Meldungen seit dem letzten Baseline-Snapshot."
        echo "  Bitte prüfen und Code an die neue Spigot-Version anpassen."
        echo ""
        exit 2
    else
        echo -e "${GREEN}✓ Keine neuen Deprecations/Fehler. Code ist kompatibel.${NC}"
    fi

    if [ "$fixed_count" -gt 0 ]; then
        echo -e "${GREEN}═══ BEHOBENE WARNUNGEN (entfernt) ═══${NC}"
        cat "$REPORT_FILE.fixed"
    fi
}

# ── Main ─────────────────────────────────────────────────────
cd "$SCRIPT_DIR"

case "${1:-}" in
    --baseline)
        run_build "$BASELINE_DIR"
        echo ""
        echo -e "${GREEN}✓ Baseline-Snapshot aktualisiert.${NC}"
        echo "  Datei: $BASELINE_DIR/warnings.log"
        ;;
    --diff)
        if [ ! -d "$BASELINE_DIR" ]; then
            echo -e "${RED}Keine Baseline vorhanden.${NC}"
            exit 1
        fi
        run_build "$CURRENT_DIR"
        echo ""
        echo -e "${CYAN}═══ DIFF (Baseline → Aktuell) ═══${NC}"
        echo -e "${RED}── NEU: ──${NC}"
        comm -13 "$BASELINE_DIR/warnings.log" "$CURRENT_DIR/warnings.log" 2>/dev/null || echo "  (keine)"
        echo -e "${GREEN}── WEG: ──${NC}"
        comm -23 "$BASELINE_DIR/warnings.log" "$CURRENT_DIR/warnings.log" 2>/dev/null || echo "  (keine)"
        ;;
    --help|"-h")
        command_help
        ;;
    *)
        run_build "$CURRENT_DIR"
        analyze
        ;;
esac

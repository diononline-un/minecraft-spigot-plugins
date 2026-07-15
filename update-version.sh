#!/usr/bin/env bash
set -euo pipefail

# ============================================================
#  update-version.sh
#  Aktualisiert die Spigot-Version im gesamten Projekt und
#  führt einen Kompatibilitäts-Check durch.
#
#  Nutzung:
#    ./update-version.sh <NEUE_SPIGOT_VERSION>
#
#  Beispiel:
#    ./update-version.sh 1.22-R0.1-SNAPSHOT
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

if [ $# -ne 1 ]; then
    echo "Nutzung: $0 <NEUE_SPIGOT_VERSION>"
    echo "Beispiel: $0 1.22-R0.1-SNAPSHOT"
    exit 1
fi

NEW_VERSION="$1"

# Aktuelle Version auslesen
OLD_VERSION=$(grep -oP '(?<=<spigot.version>)[^<]+' "$SCRIPT_DIR/pom.xml" | head -1)

if [ -z "$OLD_VERSION" ]; then
    echo -e "${RED}Konnte aktuelle Spigot-Version nicht aus pom.xml lesen.${NC}"
    exit 1
fi

echo -e "${CYAN}Aktuelle Version : $OLD_VERSION${NC}"
echo -e "${CYAN}Neue Version     : $NEW_VERSION${NC}"
echo ""

# Bestätigung
read -rp "Version aktualisieren und Kompatibilitäts-Check starten? [j/N] " confirm
if [[ ! "$confirm" =~ ^[jJyY] ]]; then
    echo "Abgebrochen."
    exit 0
fi

# ── 1. Version in pom.xml aktualisieren ──
sed -i "s|<spigot.version>$OLD_VERSION</spigot.version>|<spigot.version>$NEW_VERSION</spigot.version>|" "$SCRIPT_DIR/pom.xml"
echo -e "${GREEN}✓ pom.xml aktualisiert${NC}"

# ── 2. VERSION-Datei aktualisieren ──
echo "spigot.version=$NEW_VERSION" > "$SCRIPT_DIR/VERSION"
echo "# Zuletzt aktualisiert: $(date -Iseconds)" >> "$SCRIPT_DIR/VERSION"
echo -e "${GREEN}✓ VERSION-Datei aktualisiert${NC}"

# ── 3. Kompatibilitäts-Check ausführen ──
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  Führe Kompatibilitäts-Check durch...${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
echo ""

if "$SCRIPT_DIR/compat-check.sh" --diff; then
    echo ""
    echo -e "${GREEN}✓ Keine Breaking Changes – Baseline wird aktualisiert.${NC}"
    "$SCRIPT_DIR/compat-check.sh" --baseline
    echo ""
    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}  Update auf $NEW_VERSION erfolgreich!${NC}"
    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
else
    echo ""
    echo -e "${RED}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${RED}  Breaking Changes erkannt!${NC}"
    echo -e "${RED}  Behebe die Issues und führe dann:${NC}"
    echo -e "${RED}    ./compat-check.sh --baseline${NC}"
    echo -e "${RED}═══════════════════════════════════════════════════════════${NC}"
    exit 2
fi

#!/usr/bin/env bash
# Fährt alle Container-Tests nacheinander auf einem frisch zurückgesetzten Server.
#
# Aufruf: ./docker/run-all-tests.sh
set -uo pipefail

cd "$(dirname "$0")"
REPO="$(cd .. && pwd)"

declare -a NAMES=(test-sorter test-crash-recovery test-edge-cases diagnose-sealed)
FAILED=()

for name in "${NAMES[@]}"; do
    echo
    echo "=============================================================="
    echo "  $name"
    echo "=============================================================="
    "$REPO/docker/reset-server.sh" >/dev/null 2>&1
    # Auf den Serverstart warten.
    sleep 55
    if python3 "$name.py"; then
        echo "--> $name: bestanden"
    else
        echo "--> $name: FEHLGESCHLAGEN"
        FAILED+=("$name")
    fi
done

echo
echo "=============================================================="
if [ ${#FAILED[@]} -eq 0 ]; then
    echo "  Alle ${#NAMES[@]} Testskripte bestanden."
    exit 0
fi
echo "  Fehlgeschlagen: ${FAILED[*]}"
exit 1

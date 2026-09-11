#!/usr/bin/env bash
# Setzt den Testserver auf einen sauberen Stand zurück.
#
# Räumt Welt, Logs, Plugin-Daten und Absturzberichte weg. Nötig, weil Paper
# Weltverzeichnisse aus älteren Läufen migriert und dabei abbrechen kann.
#
# Aufruf: ./docker/reset-server.sh [--plugins]
#   --plugins  kopiert das gebaute Plugin-JAR und Citizens neu hinein
set -euo pipefail

cd "$(dirname "$0")"
REPO="$(cd .. && pwd)"

docker compose down >/dev/null 2>&1 || true

docker run --rm -v "$PWD/server:/server" alpine sh -c "
    rm -rf /server/world /server/world_nether /server/world_the_end \
           /server/logs /server/crash-reports /server/config \
           /server/plugins/ChestManager /server/config.yml \
           /server/libraries /server/versions /server/cache \
           /server/usercache.json
    chown -R 1000:1000 /server
"

if [ "${1:-}" = "--plugins" ]; then
    cp "$REPO/plugin-chestmanager/target/chestmanager-plugin-1.0.0-SNAPSHOT.jar" server/plugins/
    curl -fsSL -A "Mozilla/5.0" -o server/plugins/Citizens.jar \
        "https://ci.citizensnpcs.co/job/Citizens2/lastSuccessfulBuild/artifact/dist/target/Citizens-2.0.43-b4249.jar"
    echo "Plugin und Citizens neu kopiert."
fi

docker compose up -d >/dev/null
echo "Testserver läuft. Logs: docker logs -f chestmanager-test"

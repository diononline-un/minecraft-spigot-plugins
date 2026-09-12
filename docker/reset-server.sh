#!/usr/bin/env bash
# Setzt den Testserver auf einen sauberen Stand zurück.
#
# Räumt Welt, Logs, Plugin-Daten und Absturzberichte weg. Nötig, weil Paper
# Weltverzeichnisse aus älteren Läufen migriert und dabei abbrechen kann.
#
# Aufruf: ./docker/reset-server.sh [--plugins] [--citizens <pfad>]
#   --plugins           kopiert das gebaute Plugin-JAR neu hinein
#   --citizens <pfad>   nutzt dieses Citizens-JAR statt des Jenkins-Builds
set -euo pipefail

cd "$(dirname "$0")"
REPO="$(cd .. && pwd)"
CHEST_PLUGIN="$REPO/plugin-chestmanager/target/chestmanager-plugin-1.0.0-SNAPSHOT.jar"
CITIZENS_URL="https://ci.citizensnpcs.co/job/Citizens2/lastSuccessfulBuild/artifact/dist/target/Citizens-2.0.43-b4249.jar"
CITIZENS_LOCAL=""

shift_args=()
while [ $# -gt 0 ]; do
    case "$1" in
        --citizens)
            CITIZENS_LOCAL="${2:-}"
            shift 2
            ;;
        *)
            shift_args+=("$1")
            shift
            ;;
    esac
done

docker compose down >/dev/null 2>&1 || true

docker run --rm -v "$PWD/server:/server" alpine sh -c "
    rm -rf /server/world /server/world_nether /server/world_the_end \
           /server/logs /server/crash-reports /server/config \
           /server/plugins/ChestManager /server/plugins/Citizens /server/config.yml \
           /server/libraries /server/versions /server/cache \
           /server/usercache.json
    chown -R 1000:1000 /server
"

if [ "${shift_args[0]:-}" = "--plugins" ]; then
    cp "$CHEST_PLUGIN" server/plugins/
    if [ -n "$CITIZENS_LOCAL" ]; then
        cp "$CITIZENS_LOCAL" server/plugins/Citizens.jar
        echo "Plugin und Citizens aus $CITIZENS_LOCAL kopiert."
    else
        curl -fsSL -A "Mozilla/5.0" -o server/plugins/Citizens.jar "$CITIZENS_URL"
        echo "Plugin und Citizens vom Jenkins kopiert."
    fi
fi

docker compose up -d >/dev/null
echo "Testserver läuft. Logs: docker logs -f chestmanager-test"

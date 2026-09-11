#!/usr/bin/env bash
# Baut das ChestManager-Modul mit der user-space Toolchain.
# Aufruf: ./build.sh [maven-argumente...]
set -euo pipefail

TOOLCHAIN="${HOME}/.local/share/toolchain"
export JAVA_HOME="${TOOLCHAIN}/jdk21"
export PATH="${JAVA_HOME}/bin:${TOOLCHAIN}/maven/bin:${PATH}"

cd "$(dirname "$0")"

if [ ! -x "${JAVA_HOME}/bin/java" ]; then
    echo "JDK 21 fehlt unter ${JAVA_HOME}" >&2
    echo "Entweder 'sudo pacman -S jdk21-openjdk maven' installieren oder" >&2
    echo "JDK 21 und Maven nach ${TOOLCHAIN} entpacken." >&2
    exit 1
fi

exec mvn -B "$@"

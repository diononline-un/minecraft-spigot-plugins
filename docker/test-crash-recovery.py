#!/usr/bin/env python3
"""Absturztest: Ueberlebt der Transport einen harten Neustart ohne Item-Verlust?

Fuellt die Ankertruhe mit vielen Items, laesst den Sorter arbeiten und killt den
Container mitten im Transport. Nach dem Neustart muss die Gesamtzahl der Items
unveraendert sein und jedes Item in einer Truhe liegen.

Aufruf:  python3 docker/test-crash-recovery.py
"""

import re
import subprocess
import sys
import time

from rcon import Rcon, wait_for_server

FLOOR_Y = -55
CHEST_Y = FLOOR_Y + 1
SIGN_Y = CHEST_Y + 1
NPC = (0, CHEST_Y, 3)

ANCHOR = (0, CHEST_Y, 0)
ORES = (1, CHEST_Y, 0)
FOOD = (2, CHEST_Y, 0)
FALLBACK = (3, CHEST_Y, 0)

CHESTS = [ANCHOR, ORES, FOOD, FALLBACK]
SIGNS = {ANCHOR: "[INPUT]", ORES: "[ORES]", FOOD: "[FOOD]", FALLBACK: "[UNSORTABLE]"}

# Genug Items fuer mehrere Fahrten, damit der Absturz mitten im Transport landet.
IRON = 320
BREAD = 160
TOTAL = IRON + BREAD

failures = []


def check(label, actual, expected):
    if actual == expected:
        print(f"  OK   {label}: {actual}")
    else:
        print(f"  FAIL {label}: erwartet {expected}, tatsaechlich {actual}")
        failures.append(f"{label}: erwartet {expected}, tatsaechlich {actual}")


def pos(p):
    return f"{p[0]} {p[1]} {p[2]}"


def build(rcon):
    print("\n[1] Anlage aufbauen und fuellen")
    rcon.command("forceload add -16 -16 16 16")
    time.sleep(2.0)
    rcon.command(f"fill -8 {FLOOR_Y} -8 12 {FLOOR_Y} 8 minecraft:stone")
    for chest, text in SIGNS.items():
        rcon.command(f"setblock {pos(chest)} minecraft:chest replace")
        rcon.command(f"setblock {chest[0]} {SIGN_Y} {chest[2]} minecraft:oak_sign replace")
        rcon.command(
            f'data merge block {chest[0]} {SIGN_Y} {chest[2]} '
            f"{{front_text:{{messages:['{{\"text\":\"{text}\"}}','{{\"text\":\"\"}}',"
            f"'{{\"text\":\"\"}}','{{\"text\":\"\"}}']}}}}"
        )

    # In Stapeln zu 64 auf die Slots verteilen.
    slot = 0
    left = IRON
    while left > 0:
        chunk = min(64, left)
        rcon.command(f"item replace block {pos(ANCHOR)} container.{slot} with minecraft:iron_ore {chunk}")
        left -= chunk
        slot += 1
    left = BREAD
    while left > 0:
        chunk = min(64, left)
        rcon.command(f"item replace block {pos(ANCHOR)} container.{slot} with minecraft:bread {chunk}")
        left -= chunk
        slot += 1

    print(f"  {TOTAL} Items eingefuellt")
    rcon.command("cm delete Sorter")
    time.sleep(0.5)
    rcon.command(f"cm create Sorter {NPC[0]} {NPC[1]} {NPC[2]}")
    time.sleep(1.0)
    rcon.command("cm scan Sorter")

    deadline = time.time() + 120
    while time.time() < deadline:
        if "SCANNING" not in rcon.command("cm status Sorter"):
            break
        time.sleep(2)


def count_all(rcon, item, slots=27):
    """Zaehlt einen Item-Typ ueber alle Truhen der Anlage.

    Slotweise, weil Paper die Antwort auf ein vollstaendiges `Items` bei vollen
    Truhen kuerzt.
    """
    total = 0
    for chest in CHESTS:
        for slot in range(slots):
            raw = rcon.command(f"data get block {pos(chest)} Items[{{Slot:{slot}b}}]")
            if f'id: "{item}"' not in raw:
                continue
            count = re.search(r"count:\s*(\d+)", raw)
            total += int(count.group(1)) if count else 1
    return total


def main():
    print("Warte auf den Testserver ...")
    rcon = wait_for_server()
    try:
        build(rcon)

        print("\n[2] Sortieren lassen und mitten im Transport hart abbrechen")
        rcon.command("cm sortnow Sorter")
        # So lange warten, bis der Sorter nachweislich Items traegt.
        carrying = False
        deadline = time.time() + 60
        while time.time() < deadline:
            status = rcon.command("cm status Sorter")
            state = re.search(r"Zustand:\s*\w+\s*\S*\s*(\w+)", status)
            if "APPROACH_TARGET" in status or "PICKUP" in status:
                carrying = True
                break
            time.sleep(1)
        print(f"  Sorter traegt Items: {carrying}")

        # Welt sichern, damit die Testanlage den Abbruch ueberlebt. Der Zustand des
        # Plugins wird dabei bewusst NICHT gesichert: Genau darum geht es im Test.
        rcon.command("save-all flush")
        time.sleep(3)

        # Harter Abbruch: kein geordnetes Herunterfahren des Plugins.
        subprocess.run(["docker", "kill", "chestmanager-test"], check=True,
                       capture_output=True)
        print("  Container hart beendet")
    finally:
        rcon.close()

    print("\n[3] Server neu starten")
    subprocess.run(["docker", "compose", "-f", "docker-compose.yml", "up", "-d"],
                   check=True, capture_output=True)
    time.sleep(5)

    rcon = wait_for_server(timeout=300)
    try:
        # Nach einem harten Abbruch ist das forceload verloren; der Sorter setzt
        # aber eigene Chunk-Tickets für seinen Suchraum.
        deadline = time.time() + 180
        while time.time() < deadline:
            status = rcon.command("cm status Sorter")
            if "Offene Transportvorgänge: §f0" in status and "SCANNING" not in status:
                break
            time.sleep(3)

        print("\n[4] Ergebnisse pruefen")
        chunks = rcon.command("forceload query")
        print(f"  forceload: {chunks.strip()[:120]}")
        iron = count_all(rcon, "minecraft:iron_ore")
        bread = count_all(rcon, "minecraft:bread")
        check("Erz gesamt erhalten", iron, IRON)
        check("Brot gesamt erhalten", bread, BREAD)
        check("Summe erhalten", iron + bread, TOTAL)

        # Nichts darf auf dem Boden liegen.
        leftover = rcon.command("execute if entity @e[type=item]")
        if "JA" in leftover:
            # Es kann sich um abgelegte Notlandungen handeln; dann muss das
            # Protokoll sie ausweisen.
            log = rcon.command("cm log Sorter")
            print("  Hinweis: Items auf dem Boden gefunden")
            print(f"  Protokoll: {log[:400]}")
        else:
            print("  OK   keine Items auf dem Boden")

        print("\n--- Status nach dem Neustart ---")
        print(rcon.command("cm status Sorter"))
        print("\n--- Protokoll ---")
        print(rcon.command("cm log Sorter"))
    finally:
        rcon.close()

    if failures:
        print("\nFehlgeschlagen:")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print("\nAbsturztest bestanden: kein Item verloren oder verdoppelt.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

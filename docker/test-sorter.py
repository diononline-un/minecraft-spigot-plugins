#!/usr/bin/env python3
"""Ende-zu-Ende-Test des ChestManagers gegen den Testserver.

Baut eine Testanlage auf, lässt den Sorter laufen und prüft die Truheninhalte
per RCON. Voraussetzung: `docker compose -f docker/docker-compose.yml up -d`.

Aufruf:  python3 docker/test-sorter.py
"""

import json
import re
import sys
import time

from rcon import Rcon, wait_for_server

# Testanlage: eine flache Plattform, darauf eine zusammenhaengende Truhenreihe.
# Alle Koordinaten liegen in den Chunks, die per /forceload geladen sind.
FLOOR_Y = -55
CHEST_Y = FLOOR_Y + 1
SIGN_Y = CHEST_Y + 1
NPC = (0, CHEST_Y, 3)

ANCHOR = (0, CHEST_Y, 0)
ORES = (1, CHEST_Y, 0)
FOOD = (2, CHEST_Y, 0)
FALLBACK = (3, CHEST_Y, 0)
IGNORED = (4, CHEST_Y, 0)

SIGNS = {
    ANCHOR: "[INPUT]",
    ORES: "[ORES]",
    FOOD: "[FOOD]",
    FALLBACK: "[UNSORTABLE]",
    IGNORED: "[IGNORE]",
}

failures = []
checks = 0


def check(label, actual, expected):
    global checks
    checks += 1
    if actual == expected:
        print(f"  OK   {label}: {actual}")
    else:
        print(f"  FAIL {label}: erwartet {expected}, tatsaechlich {actual}")
        failures.append(f"{label}: erwartet {expected}, tatsaechlich {actual}")


def check_true(label, condition, detail=""):
    global checks
    checks += 1
    if condition:
        print(f"  OK   {label}")
    else:
        print(f"  FAIL {label} {detail}")
        failures.append(f"{label} {detail}".strip())


def pos(p):
    return f"{p[0]} {p[1]} {p[2]}"


def build_scenario(rcon):
    print("\n[1] Testanlage aufbauen")
    # Ohne Spieler sind keine Chunks geladen; ohne geladene Chunks koennen weder
    # setblock noch der Sorter etwas ausrichten.
    rcon.command("forceload add -16 -16 16 16")
    time.sleep(2.0)
    rcon.command(f"fill -8 {FLOOR_Y} -8 12 {FLOOR_Y} 8 minecraft:stone")
    answer = rcon.command(f"data get block 0 {FLOOR_Y} 0")
    check_true("Plattform steht", "not a block entity" in answer, answer)
    rcon.command("kill @e[type=item]")

    for chest, sign_text in SIGNS.items():
        answer = rcon.command(f"setblock {pos(chest)} minecraft:chest replace")
        check_true(f"Truhe bei {chest} gesetzt", "Changed the block" in answer, answer)
        # Das Schild sitzt auf dem Block ueber der Truhe.
        rcon.command(f"setblock {chest[0]} {SIGN_Y} {chest[2]} minecraft:oak_sign replace")
        message = json.dumps({"text": sign_text})
        rcon.command(
            f'data merge block {chest[0]} {SIGN_Y} {chest[2]} '
            f"{{front_text:{{messages:['{message}','{{\"text\":\"\"}}',"
            f"'{{\"text\":\"\"}}','{{\"text\":\"\"}}']}}}}"
        )

    # Fehlplatzierte Items: Erz und Brot in der Ankertruhe, Brot zusaetzlich
    # in der Erztruhe, ein Item ohne Kategorie ebenfalls in die Ankertruhe.
    for chest, slot, item, amount in [
        (ANCHOR, 0, "minecraft:iron_ore", 32),
        (ANCHOR, 1, "minecraft:bread", 16),
        (ANCHOR, 2, "minecraft:nether_star", 5),
        (ORES, 0, "minecraft:bread", 7),
        # Die ignorierte Truhe enthaelt etwas, das der Sorter nicht anfassen darf.
        (IGNORED, 0, "minecraft:diamond", 3),
    ]:
        answer = rcon.command(f"item replace block {pos(chest)} container.{slot} with {item} {amount}")
        check_true(f"{amount}x {item} in {chest}", "Replaced" in answer, answer)
    return True


def start_sorter(rcon, name="Sorter", timeout=120):
    print("\n[2] Sorter anlegen und Bereich ermitteln")
    rcon.command(f"cm delete {name}")
    time.sleep(0.5)
    rcon.command(f"cm create {name} {NPC[0]} {NPC[1]} {NPC[2]}")
    time.sleep(1.0)
    rcon.command(f"cm scan {name}")

    # Der Scan laeuft budgetiert ueber viele Ticks; auf das Ende warten.
    deadline = time.time() + timeout
    while time.time() < deadline:
        status = rcon.command(f"cm status {name}")
        if "SCANNING" not in status:
            return status
        time.sleep(2)
    return rcon.command(f"cm status {name}")


def chest_contents(rcon, chest, slots=27):
    """Liest den Truheninhalt slotweise.

    Paper kürzt die Antwort auf `data get block ... Items` bei vollen Truhen, eine
    Zählung darauf wäre stillschweigend falsch. Einzelne Slots liefern kurze,
    vollständige Antworten.
    """
    contents = {}
    for slot in range(slots):
        raw = rcon.command(f"data get block {pos(chest)} Items[{{Slot:{slot}b}}]")
        item = re.search(r'id:\s*"([^"]+)"', raw)
        if not item:
            continue
        count = re.search(r"count:\s*(\d+)", raw)
        contents[item.group(1)] = contents.get(item.group(1), 0) + (
            int(count.group(1)) if count else 1)
    return contents


def total_in_chest(rcon, chest, item):
    return chest_contents(rcon, chest).get(item, 0)


def run_until_sorted(rcon, name="Sorter", timeout=90):
    """Wartet, bis der Sorter nichts mehr zu tun hat."""
    print("\n[3] Sortieren lassen")
    rcon.command(f"cm sortnow {name}")
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        status = rcon.command(f"cm status {name}")
        state = re.search(r"Zustand:\s*(\w+)", status)
        current = state.group(1) if state else "?"
        if current != last:
            print(f"  ... Zustand {current}")
            last = current
        if current == "IDLE":
            return True
        time.sleep(2)
    return False


def verify(rcon):
    print("\n[4] Ergebnisse pruefen")
    check("Erz in der Erztruhe", total_in_chest(rcon, ORES, "minecraft:iron_ore"), 32)
    check("Ankertruhe frei von Erz", total_in_chest(rcon, ANCHOR, "minecraft:iron_ore"), 0)
    check("Brot in der Esstruhe", total_in_chest(rcon, FOOD, "minecraft:bread"), 23)
    check("Ankertruhe frei von Brot", total_in_chest(rcon, ANCHOR, "minecraft:bread"), 0)
    check("Erztruhe frei von Brot", total_in_chest(rcon, ORES, "minecraft:bread"), 0)
    check("Item ohne Kategorie in der Auffangtruhe",
          total_in_chest(rcon, FALLBACK, "minecraft:nether_star"), 5)
    check("Ignorierte Truhe unangetastet", total_in_chest(rcon, IGNORED, "minecraft:diamond"), 3)

    items_on_ground = rcon.command("execute if entity @e[type=item]")
    check_true("Keine Items auf dem Boden", "JA" not in items_on_ground, items_on_ground)


def main():
    print("Warte auf den Testserver ...")
    rcon = wait_for_server()
    try:
        build_scenario(rcon)
        status = start_sorter(rcon)
        print("\n--- Status nach dem Scan ---")
        print(status)

        self_test = rcon.command("cm selftest Sorter")
        print("\n--- Selbsttest ---")
        print(self_test)

        finished = run_until_sorted(rcon)
        check_true("Sorter kehrt in den Ruhezustand zurueck", finished)

        verify(rcon)
    finally:
        rcon.close()

    print(f"\n{checks - len(failures)}/{checks} Prüfungen bestanden")
    if failures:
        print("\nFehlgeschlagen:")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print("Alle Prüfungen bestanden.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

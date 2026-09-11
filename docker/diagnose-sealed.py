#!/usr/bin/env python3
"""Prueft gezielt, ob eine unerreichbare Truhe erkannt und gesperrt wird.

Baut eine Truhenreihe, mauert eine Zieltruhe darin vollstaendig ein und prueft
nach dem Scan, ob der Sorter sie als unerreichbar fuehrt. Blockzustaende werden
mit `execute if block` geprueft, weil `execute if ... run say` ueber RCON keine
Ausgabe liefert.

Aufruf:  python3 docker/diagnose-sealed.py
"""

import re
import sys
import time

from rcon import Rcon, wait_for_server

FLOOR_Y = -55
CHEST_Y = FLOOR_Y + 1
SIGN_Y = CHEST_Y + 1
NPC = (0, CHEST_Y, 3)

ANCHOR = (0, CHEST_Y, 0)
ORES = (1, CHEST_Y, 0)
SEALED = (2, CHEST_Y, 0)

# Eine echte Einmauerung. Ein Schild ist in Minecraft passabel; steht es auf der
# Truhe und ist die Luft darueber frei, kann der NPC auf die Truhe steigen und sie
# erreichen. Damit die Truhe wirklich unerreichbar ist, sitzt das Schild in der
# Decke und ueber ihm liegt ebenfalls ein Block.
WALLS = [
    (SEALED[0] + 1, SEALED[1], SEALED[2]),
    (SEALED[0], SEALED[1], SEALED[2] + 1),
    (SEALED[0], SEALED[1], SEALED[2] - 1),
    # Deckel ueber dem Schild: sonst waere der Schildblock ein gueltiger Standort.
    (SEALED[0], SEALED[1] + 2, SEALED[2]),
]

failures = []


def pos(p):
    return f"{p[0]} {p[1]} {p[2]}"


def check(label, actual, expected):
    if actual == expected:
        print(f"  OK   {label}: {actual}")
    else:
        print(f"  FAIL {label}: erwartet {expected}, tatsaechlich {actual}")
        failures.append(f"{label}: erwartet {expected}, tatsaechlich {actual}")


def check_true(label, condition, detail=""):
    if condition:
        print(f"  OK   {label}")
    else:
        print(f"  FAIL {label} {detail}")
        failures.append(f"{label} {detail}".strip())


def block_is(rcon, p, material):
    return "Test passed" in rcon.command(f"execute if block {pos(p)} {material}")


def setup(rcon):
    print("\n[1] Anlage mit eingemauerter Truhe aufbauen")
    rcon.command("forceload add -16 -16 16 16")
    time.sleep(2.0)
    rcon.command(f"fill -8 {FLOOR_Y} -8 12 {FLOOR_Y} 8 minecraft:stone")

    for chest, text in [(ANCHOR, "[INPUT]"), (ORES, "[ORES]"), (SEALED, "[ORES]")]:
        rcon.command(f"setblock {pos(chest)} minecraft:chest replace")
        rcon.command(f"setblock {chest[0]} {SIGN_Y} {chest[2]} minecraft:oak_sign replace")
        rcon.command(
            f'data merge block {chest[0]} {SIGN_Y} {chest[2]} '
            f"{{front_text:{{messages:['{{\"text\":\"{text}\"}}','{{\"text\":\"\"}}',"
            f"'{{\"text\":\"\"}}','{{\"text\":\"\"}}']}}}}"
        )
    for wall in WALLS:
        rcon.command(f"setblock {pos(wall)} minecraft:obsidian replace")

    # Zustand pruefen, bevor der Sorter laeuft.
    check_true("Ankertruhe steht", block_is(rcon, ANCHOR, "minecraft:chest"))
    check_true("Erztruhe steht", block_is(rcon, ORES, "minecraft:chest"))
    check_true("Eingemauerte Truhe steht", block_is(rcon, SEALED, "minecraft:chest"))
    check_true("Rollenschild der eingemauerten Truhe sitzt",
               block_is(rcon, (SEALED[0], SIGN_Y, SEALED[2]), "minecraft:oak_sign"))
    walls_ok = all(block_is(rcon, w, "minecraft:obsidian") for w in WALLS)
    check_true("Alle Zugangswege zugemauert", walls_ok)

    # Die regulaere Erztruhe voll fuellen, damit nur die eingemauerte Platz hat.
    for slot in range(27):
        rcon.command(f"item replace block {pos(ORES)} container.{slot} with minecraft:iron_ore 64")
    rcon.command(f"item replace block {pos(ANCHOR)} container.0 with minecraft:gold_ore 16")


def run_sorter(rcon):
    print("\n[2] Scan und Diagnose")
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

    status = rcon.command("cm status Sorter").replace("§", "")
    print("\n--- Status ---")
    print(status)
    print("\n--- Selbsttest ---")
    print(rcon.command("cm selftest Sorter").replace("§", ""))

    deadline = time.time() + 60
    blocked = ""
    while time.time() < deadline:
        blocked = rcon.command("cm blocked Sorter").replace("§", "")
        if "2/-54/0" in blocked:
            break
        time.sleep(3)
    print("\n--- Sperrliste ---")
    print(blocked)

    return status, blocked


def main():
    print("Warte auf den Testserver ...")
    rcon = wait_for_server()
    try:
        setup(rcon)
        status, blocked = run_sorter(rcon)

        print("\n[3] Bewertung")
        check_true("Eingemauerte Truhe ist im Bereich",
                   re.search(r"CATEGORY:\s*2", status) is not None, status)
        check_true("Eingemauerte Truhe gilt als unerreichbar",
                   "2/-54/0" in blocked, blocked)
    finally:
        rcon.close()

    if failures:
        print("\nFehlgeschlagen:")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print("\nErkennung unerreichbarer Truhen bestanden.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

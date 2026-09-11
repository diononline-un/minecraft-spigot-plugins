#!/usr/bin/env python3
"""Randfälle auf dem Testserver: unerreichbare Truhe, Scan-Zeit, geordnetes Anhalten.

Aufruf:  python3 docker/test-edge-cases.py
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
SEALED = (4, CHEST_Y, 0)

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


def total_in_chest(rcon, chest, item, slots=27):
    total = 0
    for slot in range(slots):
        raw = rcon.command(f"data get block {pos(chest)} Items[{{Slot:{slot}b}}]")
        if f'id: "{item}"' not in raw:
            continue
        count = re.search(r"count:\s*(\d+)", raw)
        total += int(count.group(1)) if count else 1
    return total


def build(rcon, with_sealed=False):
    rcon.command("forceload add -16 -16 16 16")
    time.sleep(2.0)
    rcon.command(f"fill -8 {FLOOR_Y} -8 12 {FLOOR_Y} 8 minecraft:stone")
    for chest, text in [(ANCHOR, "[INPUT]"), (ORES, "[ORES]"),
                        (FOOD, "[FOOD]"), (FALLBACK, "[UNSORTABLE]")]:
        rcon.command(f"setblock {pos(chest)} minecraft:chest replace")
        rcon.command(f"setblock {chest[0]} {SIGN_Y} {chest[2]} minecraft:oak_sign replace")
        rcon.command(
            f'data merge block {chest[0]} {SIGN_Y} {chest[2]} '
            f"{{front_text:{{messages:['{{\"text\":\"{text}\"}}','{{\"text\":\"\"}}',"
            f"'{{\"text\":\"\"}}','{{\"text\":\"\"}}']}}}}"
        )

    if with_sealed:
        # Eine weitere Zieltruhe, die rundherum eingemauert ist: Der Sorter kommt
        # nicht heran und muss sie sperren, ohne die uebrigen Truhen zu blockieren.
        rcon.command(f"setblock {pos(SEALED)} minecraft:chest replace")
        rcon.command(f"setblock {SEALED[0]} {SIGN_Y} {SEALED[2]} minecraft:oak_sign replace")
        rcon.command(
            f'data merge block {SEALED[0]} {SIGN_Y} {SEALED[2]} '
            f"{{front_text:{{messages:['{{\"text\":\"[ORES]\"}}','{{\"text\":\"\"}}',"
            f"'{{\"text\":\"\"}}','{{\"text\":\"\"}}']}}}}"
        )
        # Ein Schild ist passabel; ohne Deckel darueber koennte der Sorter auf die
        # Truhe steigen. Die Einmauerung braucht deshalb den Block ueber dem Schild.
        for dx, dy, dz in [(1, 0, 0),
                           (0, 0, 1),
                           (0, 0, -1),
                           (0, 2, 0)]:
            rcon.command(
                f"setblock {SEALED[0] + dx} {SEALED[1] + dy} {SEALED[2] + dz} "
                f"minecraft:obsidian replace")


def start_sorter(rcon, name="Sorter", timeout=120):
    rcon.command(f"cm delete {name}")
    time.sleep(0.5)
    rcon.command(f"cm create {name} {NPC[0]} {NPC[1]} {NPC[2]}")
    time.sleep(1.0)
    rcon.command(f"cm scan {name}")
    deadline = time.time() + timeout
    while time.time() < deadline:
        status = rcon.command(f"cm status {name}")
        if "SCANNING" not in status:
            return status
        time.sleep(2)
    return rcon.command(f"cm status {name}")


def test_sealed_chest(rcon):
    print("\n[A] Unerreichbare Truhe sperren, Rest weitersortieren")
    build(rcon, with_sealed=True)
    rcon.command(f"item replace block {pos(ANCHOR)} container.0 with minecraft:iron_ore 24")
    rcon.command(f"item replace block {pos(ANCHOR)} container.1 with minecraft:bread 12")
    # Die regulaere Erztruhe voll fuellen: Nur die eingemauerte hat noch Platz,
    # also muss der Sorter sie anlaufen und dabei scheitern.
    for slot in range(27):
        rcon.command(f"item replace block {pos(ORES)} container.{slot} with minecraft:iron_ore 64")
    start_sorter(rcon)

    rcon.command("cm sortnow Sorter")
    deadline = time.time() + 150
    while time.time() < deadline:
        if "IDLE" in rcon.command("cm status Sorter"):
            break
        time.sleep(3)

    deadline = time.time() + 60
    blocked = ""
    while time.time() < deadline:
        blocked = rcon.command("cm blocked Sorter")
        if "4/-54/0" in blocked:
            break
        time.sleep(3)
    check_true("Eingemauerte Truhe ist gesperrt", "4/-54/0" in blocked, blocked)
    # Eine volle Kategorie darf nur die betroffenen Stapel liegen lassen.
    check("Brot trotzdem sortiert", total_in_chest(rcon, FOOD, "minecraft:bread"), 12)
    check("Erz bleibt liegen, weil seine Kategorie voll ist",
          total_in_chest(rcon, ANCHOR, "minecraft:iron_ore"), 24)


def test_scan_time(rcon):
    print("\n[B] Scan-Zeit im Status")
    status = rcon.command("cm status Sorter").replace("§", "")
    match = re.search(r"Letzter Scan:\s*\w*\s*(\d+)\s*ms", status)
    check_true("Status weist die Scan-Dauer aus", match is not None, status[:200])
    if match:
        millis = int(match.group(1))
        # Der Scan laeuft budgetiert; ein Wert im Sekundenbereich waere ein
        # Durchlauf ohne Tickspreizung.
        check_true(f"Scan-Dauer bleibt unter 30 s ({millis} ms)", millis < 30_000)


def test_graceful_stop(rcon):
    print("\n[C] Geordnetes Anhalten waehrend einer Fahrt")
    build(rcon)
    for chest in (ANCHOR, ORES, FOOD, FALLBACK, SEALED):
        rcon.command(f"data modify block {pos(chest)} Items set value []")
    time.sleep(0.5)
    slot = 0
    left = 400
    while left > 0 and slot < 27:
        chunk = min(64, left)
        rcon.command(f"item replace block {pos(ANCHOR)} container.{slot} "
                     f"with minecraft:iron_ore {chunk}")
        left -= chunk
        slot += 1
    start_sorter(rcon)
    rcon.command("cm sortnow Sorter")

    # Kurz laufen lassen, dann geordnet stoppen. Ob er in genau diesem Moment
    # traegt, ist nicht vorhersagbar; entscheidend ist, dass nach dem Anhalten
    # kein Item verloren ist.
    time.sleep(12)
    status = rcon.command("cm status Sorter")
    print(f"  Zustand beim Stoppen: {status.splitlines()[1][:60] if len(status.splitlines()) > 1 else '?'}")
    rcon.close()

    # Geordnetes Herunterfahren: Das Plugin gibt getragene Items zurueck.
    subprocess.run(["docker", "stop", "chestmanager-test"], check=True, capture_output=True,
                   timeout=180)
    print("  Container geordnet gestoppt")


def verify_after_stop():
    subprocess.run(["docker", "compose", "-f", "docker-compose.yml", "up", "-d"],
                   check=True, capture_output=True)
    time.sleep(5)
    rcon = wait_for_server(timeout=300)
    try:
        time.sleep(20)
        iron = 0
        for chest in (ANCHOR, ORES, FOOD, FALLBACK, SEALED):
            iron += total_in_chest(rcon, chest, "minecraft:iron_ore")
        check("Alle 400 Erz liegen in Truhen", iron, 400)

        on_ground = rcon.command("execute if entity @e[type=item]")
        check_true("Nichts liegt auf dem Boden", "JA" not in on_ground)
    finally:
        rcon.close()


def main():
    print("Warte auf den Testserver ...")
    rcon = wait_for_server()
    try:
        test_sealed_chest(rcon)
        test_scan_time(rcon)
        test_graceful_stop(rcon)
    finally:
        try:
            rcon.close()
        except Exception:
            pass

    verify_after_stop()

    if failures:
        print("\nFehlgeschlagen:")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print("\nAlle Randfaelle bestanden.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

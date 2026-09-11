---
status: in-development
progress: 85
version: "1.0.0-SNAPSHOT"
tags: [minecraft, spigot, maven, plugins, openspec, chestmanager]
scope: "Multi-Modul-Maven-Monorepo für Diono-Spigot-Plugins (Spigot 26.2, Java 21)"
last_updated: "2026-09-11"
next_steps:
  - ChestManager: unerreichbare Truhe wird im Selbsttest nicht als Fehler gemeldet (offener Punkt aus dem Container-Test)
  - ChestManager auf dem Live-Server ausrollen (alte NPCs löschen, config.yml aus Vorlage, /cm selftest)
  - Citizens-Version im Parent-POM auf 2.0.43-SNAPSHOT heben (2.0.35 läuft nicht auf Spigot 26.2)
  - WorkerNPCs DeepSeek-Integration und Memory-System auf dem Testserver verifizieren
---

# Aktueller Stand
- Maven-Multi-Modul mit Parent-POM, zentraler Spigot-Version (26.2-R0.1-SNAPSHOT) und Java 21
- Vier Plugins: AdventureLog, WorkerNPCs (Citizens2/DeepSeek), ChestManager (neu aufgesetzt), plugin-template
- ChestManager: Copper-Golem-artiger Sorter, Bukkit-freier Domänenkern mit 112 Unit-Tests plus Container-Tests
- OpenSpec ist eingeführt (`openspec/`, `.agents/skills/openspec-*`); Change `rebuild-chest-sorter-core`
- Testserver per Docker (`docker/`): Paper 26.2, Citizens, RCON-Steuerung und Testskripte
- Build braucht JDK 21 user-space unter `~/.local/share/toolchain/`; `./build.sh` kapselt das

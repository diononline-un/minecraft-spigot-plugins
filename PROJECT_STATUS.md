---
status: in-development
progress: 55
version: "1.0.0-SNAPSHOT"
tags: [minecraft, spigot, maven, plugins]
scope: "Multi-Modul-Maven-Monorepo für Diono-Spigot-Plugins (Spigot 26.2, Java 21)"
last_updated: "2026-08-13"
next_steps:
  - ChestManager-Modul fertigstellen (Request-System, Sort-Tasks, Citizens-Trait testen)
  - Kompatibilitäts-Check laufen lassen und Baseline aktuell halten
  - WorkerNPCs DeepSeek-Integration und Memory-System auf dem Testserver verifizieren
  - Tests/Server-Tests für AdventureLog-Announcements ergänzen
---

# Aktueller Stand
- Maven-Multi-Modul mit Parent-POM, zentraler Spigot-Version (26.2-R0.1-SNAPSHOT) und Java 21
- Drei aktive Plugins: AdventureLog (Event-Announcements mit Anti-Spam), WorkerNPCs (KI-NPCs mit Citizens2/DeepSeek), ChestManager (neu, letzter Commit)
- Tooling vorhanden: compat-check.sh (Deprecation-Baseline) und update-version.sh (Versionsupdate mit Breaking-Change-Check)
- plugin-template als Kopiervorlage für neue Plugins
- Keine automatisierten Tests oder CI; Verifikation läuft manuell auf dem Server

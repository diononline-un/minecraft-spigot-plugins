# Changelog

## 2026-09-11 — ChestManager neu aufgesetzt: Copper-Golem-Sortierer

- Modul `plugin-chestmanager` vollständig neu gebaut: Bukkit-freier Domänenkern
  (`core/`) hinter Ports, Bukkit- und Citizens-Adapter dünn darüber. 112 Unit-Tests.
- Copper-Golem-Semantik statt Input-Truhe: Jede markierte Truhe ist Quelle und Ziel,
  der NPC korrigiert fehlplatzierte Bestände selbst.
- Item-Sicherheit über ein Journal auf Platte, das vor jeder Entnahme geschrieben wird;
  nach einem harten Absturz wurden im Container-Test alle 480 Items unverändert gefunden.
- Budgetierter Bereichsscan und ein Scheduler mit festem Arbeitskontingent je Sorter
  ersetzen den Main-Thread-blockierenden Quaderscan.
- Reichweitenprüfung, Navigations-Zeitlimit, Wiederholung und Sperrliste ersetzen die
  Zuweisung `isNavigating() == false`.
- Request-Chest-System (`RequestGui`, `FulfillRequestTask`, `[REQUEST]`, Permission
  `chestmanager.request`) ersatzlos entfernt.
- Schild-Vokabular bleibt gültig; `[INPUT]`, `[ORES]`, `[UNSORTABLE]`, `[IGNORE]` und
  die übrigen Kategorienamen funktionieren unverändert. Schilder mit JSON-Komponente
  werden ebenfalls gelesen.
- Sorter arbeitet stumm: keine Geräusche, abgesichert durch einen Contract-Test.
- Eine volle oder fehlende Zieltruhe hält den Lauf nicht mehr auf: Betroffene Stapel
  werden für den Lauf übersprungen, alles andere wird weiter sortiert. Zuvor brach
  ein einziger nicht unterzubringender Stapel die gesamte Sortierung ab.
- Neu: `docker/` mit Paper-Testserver, RCON-Client und vier Testskripten
  (Ende-zu-Ende, Absturz, Randfälle, eingemauerte Truhe); `build.sh` kapselt die
  user-space Toolchain.
- Offen: Citizens im Parent-POM steht auf 2.0.35-SNAPSHOT, das auf Spigot 26.2 nicht
  startet; nötig ist 2.0.43-SNAPSHOT.

## Why

`plugin-chestmanager` sortiert in der Praxis nichts. Das Modul hat einen Commit
und wurde nie erfolgreich gebaut oder auf einem Server verifiziert. Vier Defekte
treffen sich gegenseitig:

1. Es gibt keine `config.yml` und keine `config.yml.example` im Modul. Damit
   bleibt `CategoryConfig.categories` leer, `ChestArea.parseCategoryRole()` findet
   keine einzige Kategorie und jede Kategorie-Truhe gilt als unmarkiert.
2. `ChestArea.scan()` läuft synchron im Main-Thread über `(2·radius+1)² × Höhe`
   Blöcke (bei Radius 40 rund zwei Millionen `getBlockAt`-Aufrufe) und friert den
   Server bei jedem `create`, `home`, `radius` und `scan` ein.
3. `SortTask` und `FulfillRequestTask` prüfen vor dem Inventarzugriff nur
   `navigator.isNavigating()`. Citizens setzt dieses Flag bei fehlgeschlagenem Pfad
   auf `false`, der NPC steht dann beliebig weit weg und der Code greift trotzdem
   auf die Truhe zu.
4. Entnommene Items liegen in `ChestManagerTrait.carryInventory`, einer einfachen
   `ArrayList` ohne `@Persist`. Jeder Crash oder Restart vernichtet sie.

Ein Copper-Golem-artiger Sorter ist auf diesem Fundament nicht reparierbar: Scan,
Zustandsmaschine und Persistenz müssten gleichzeitig neu gebaut werden.

## What Changes

- **Neuer Domänenkern ohne Bukkit-Abhängigkeit.** Bereichserkennung, Routing und
  Aufgaben-Zustandsmaschine laufen hinter schmalen Ports (`BlockView`,
  `InventoryView`, `NavigatorPort`) und sind damit als Unit-Test prüfbar. Bukkit
  und Citizens bleiben dünne Adapter.
- **Copper-Golem-Semantik statt Input-Truhe.** Jede markierte Truhe ist gleichzeitig
  Quelle und Ziel. Der NPC durchläuft den erkannten Bereich, entnimmt Items, die
  laut Kategorie nicht in die jeweilige Truhe gehören, und legt sie in einer
  passenden Zieltruhe ab. Falsch einsortierte Bestände korrigieren sich dadurch
  von selbst.
- **Bereichserkennung per Flood-Fill** über zusammenhängende Truhen ab einem
  Ankerblock, mit konfigurierbaren Obergrenzen für Fläche und Höhendifferenz.
  Ersetzt den bisherigen Quader von Home bis Bedrock.
- **Budgetierter Scan.** Die Blockabfrage wird über Ticks verteilt und mit einer
  harten Obergrenze pro Tick ausgeführt, statt den Server zu blockieren.
- **Kein Item-Verlust.** Der Carry-Zustand wird persistiert und als Journal geführt.
  Ein Item verlässt die Quelltruhe erst, wenn ein erreichbares Ziel feststeht; bei
  Abbruch wandert es zurück.
- **Physische Reichweite als Bedingung.** Inventarzugriff nur, wenn der NPC
  tatsächlich am Truhenblock steht. Navigationsfehler haben Timeout, Retry und
  eine Sperrliste für unerreichbare Truhen.
- **BREAKING** – Das Request-Chest-System entfällt ersatzlos: `[REQUEST]`-Schilder,
  `RequestGui`, `RequestChestListener`, `FulfillRequestTask`, `WithdrawFromCategory`
  und die Permission `chestmanager.request`.
- **BREAKING** – Die Befehlsoberfläche wird neu geschnitten. Das Schild-Vokabular
  (`[ORES]`, `[BLOCK]`, `[TOOLS]`, `[MISC]`, …) bleibt erhalten, weil es den
  bestehenden Weltzustand auf dem Server beschreibt.
- **Konfiguration kommt ins Repo.** `config.yml.example` mit Kategorien,
  Reichweiten und Limits; `config.yml` bleibt per `.gitignore` ausgeschlossen.

## Capabilities

### New Capabilities

- `chest-sorter-area`: Erkennung und Pflege des Sortierbereichs, Truhen-Rollen über
  Schilder, Flood-Fill mit Limits, budgetierter Scan.
- `chest-sorter-routing`: Zuordnung Item zu Kategorie zu Zieltruhe inklusive
  Fallback, Stack-Auffüllung und Gleichverteilung über mehrere Truhen einer Kategorie.
- `chest-sorter-npc`: Citizens-Trait, Aufgaben-Zustandsmaschine, Navigation,
  Reichweitenprüfung, Fehlerbehandlung, Lifecycle über Restart.
- `chest-sorter-carry`: Item-Sicherheit beim Transport, Journal, Recovery nach
  Absturz, Rückgabe nicht abgelegter Items.
- `chest-sorter-commands`: Befehlsoberfläche, Permissions, Diagnose und
  Selbsttest für den Betreiber.

### Modified Capabilities

Keine. Das Repository hat bisher keine Specs (`openspec list --specs` meldet
„No specs found"), es gibt also nichts zu ändern.

## Non-goals

- Kein Auto-Crafting und keine Werkbank-Anbindung.
- Kein Request-System, keine Bestell-GUI, keine Item-Ausgabe an Spieler.
- Keine Shulker-Boxen, Fässer, Ender-Truhen oder Trichter als Sortierziele in
  dieser Version. Nur `CHEST` und `TRAPPED_CHEST`.
- Keine Multi-Welt- oder Multi-Server-Synchronisation.
- Keine Migration bestehender ChestManager-NPCs. Alte NPCs werden gelöscht und neu
  angelegt; die alte Konfiguration hatte keine Kategorien und damit nichts zu retten.
- Kein Ersatz für `plugin-workernpcs`. Beide Plugins bleiben getrennt.

## Impact

- **Ersetzt**: `plugin-chestmanager/src/main/java/me/diono/chestmanager/**`
  vollständig. Erhalten bleiben Modulname, `pom.xml`-Struktur, Citizens-Abhängigkeit
  und das Schild-Vokabular.
- **Entfällt**: `gui/RequestGui.java`, `listener/RequestChestListener.java`,
  `task/FulfillRequestTask.java`, `ChestArea.withdrawFromCategory()`,
  die Permission `chestmanager.request`.
- **Neu**: `config.yml.example`, Testquellen unter `src/test/java`, Testabhängigkeiten
  (JUnit 5) im Modul-POM.
- **Unverändert**: Root-`pom.xml` und die Spigot-Version darin, die anderen drei
  Module.
- **Betrieb**: `/cm`-Befehle ändern sich, bestehende Schilder bleiben gültig. Auf
  dem Testserver wird der Sorter real verifiziert, bevor er auf den Live-Server geht.
- **Doku**: `PROJECT_STATUS.md` und `CHANGELOG.md` werden aktualisiert.

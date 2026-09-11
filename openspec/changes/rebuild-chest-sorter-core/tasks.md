## 1. Vorbereitung

- [x] 1.1 Alten Quellcode unter `plugin-chestmanager/src/main/java/me/diono/chestmanager/` löschen und neue Paketstruktur `core/` und `bukkit/` anlegen; `find` zeigt danach nur die neuen, leeren Pakete
- [x] 1.2 JUnit 5 im Modul-POM als `test`-Abhängigkeit ergänzen und `mvn -pl plugin-chestmanager test` läuft ohne Auflösungsfehler durch
- [x] 1.3 Build-Skript `build.sh` im Repo anlegen, das `JAVA_HOME` und `PATH` auf `~/.local/share/toolchain/` setzt; `./build.sh` baut das Modul erfolgreich
- [x] 1.4 `plugin.yml` auf die neue Befehls- und Permission-Struktur umstellen; `unzip -p target/*.jar plugin.yml` enthält keine `${...}`-Platzhalter und keine `chestmanager.request`-Permission

## 2. Domänenkern: Werte und Ports

- [x] 2.1 Test für `BlockPos` (Gleichheit, Nachbarschaft, Distanz zum Quadrat) schreiben und rot laufen lassen
- [x] 2.2 `BlockPos` implementieren, bis der Test grün ist
- [x] 2.3 Test für `ItemKey` (Gleichheit über Typ und Variante, Kategorievergleich nur über Typ) schreiben und rot laufen lassen
- [x] 2.4 `ItemKey` und `ChestRole` implementieren, bis die Tests grün sind
- [x] 2.5 Ports `WorldPort`, `ChestPort`, `NavigationPort`, `JournalPort`, `Clock`, `LogPort` als Interfaces anlegen und mit `mvn compile` kompilieren
- [x] 2.6 Fakes für alle Ports unter `src/test/java` anlegen, mit denen ein Test ein vollständiges Inventarverhalten ohne Server nachbilden kann; ein Smoke-Test läuft grün

## 3. Bereichserkennung (`chest-sorter-area`)

- [x] 3.1 Test: Suchquader liefert nur Truhenblöcke, Doppeltruhen zählen als eine Einheit; rot laufen lassen
- [x] 3.2 Test: Graphkomponente ab Anker — verbundene Truhen drin, getrennte Insel draussen, `linkDistance` steuert die Verbindung; rot laufen lassen
- [x] 3.3 Bereichserkennung implementieren, bis 3.1 und 3.2 grün sind
- [x] 3.4 Test: Rollenerkennung aus Schildtext — Gross- und Kleinschreibung, fehlende Klammern, Schild an angrenzendem Block, unbekannte Kategorie, Ignorierrolle; rot laufen lassen
- [x] 3.5 Rollenauflösung über `WorldPort` implementieren, bis 3.4 grün ist
- [x] 3.6 Test: Obergrenzen — Höchstzahl Truhen und maximaler Höhenunterschied brechen die Ermittlung ab und melden die Grenze; rot laufen lassen
- [x] 3.7 Grenzprüfung implementieren, bis 3.6 grün ist
- [x] 3.8 Test: budgetierter Scan — über mehrere Aufrufe verteilt, nie mehr als `blocksPerTick` Zugriffe je Aufruf, Fortschritt und Abbruch funktionieren; rot laufen lassen
- [x] 3.9 Budgetierung und Fortschrittsmeldung implementieren, bis 3.8 grün ist
- [x] 3.10 Test: entfernte Truhe fällt beim nächsten Zugriff aus dem Bereich, ohne Fehler; rot laufen lassen und implementieren

## 4. Routing (`chest-sorter-routing`)

- [x] 4.1 Test: Kategoriezuordnung aus Konfiguration, Doppelnennung nimmt die erste Kategorie und meldet die Doppelnennung; rot laufen lassen und implementieren
- [x] 4.2 Test: Fehlplatzierung — falsche Kategorie in einer Truhe gilt als fehlplatziert, korrekte Kategorie nicht, Auffangtruhe nur bei fehlender Kategorie, Ignorierrolle nie; rot laufen lassen und implementieren
- [x] 4.3 Test: Zielwahl bevorzugt eine Truhe mit bestehendem Stapel desselben `ItemKey`, danach die Truhe mit dem meisten freien Platz, bei Gleichstand deterministisch; rot laufen lassen und implementieren
- [x] 4.4 Test: volle Kategorie — Item bleibt liegen, keine Ausweichablage in eine andere Kategorie, Hinweis auf die volle Kategorie; rot laufen lassen und implementieren
- [x] 4.5 Test: Items ohne Kategorie — mit Auffangtruhe dorthin, ohne Auffangtruhe liegen lassen und einmalig je Typ melden; rot laufen lassen und implementieren
- [x] 4.6 Test: Neuladen der Konfiguration wirkt ab dem nächsten Vorgang, laufende Fahrt bleibt gültig; rot laufen lassen und implementieren

## 5. Transport und Recovery (`chest-sorter-carry`)

- [x] 5.1 Test: Journal schreibt `PENDING` vor der Entnahme, `CARRIED` danach, löscht bei `DELIVERED`; rot laufen lassen und implementieren
- [x] 5.2 Test: Recovery nach Absturz — `PENDING` wird verworfen ohne Inventarzugriff, `CARRIED` führt Items zurück oder zu Ende; rot laufen lassen und implementieren
- [x] 5.3 Test: keine Duplikation — unklarer Ablagezustand führt zur Rückgabe in die Quelltruhe mit Unsicher-Meldung; rot laufen lassen und implementieren
- [x] 5.4 Test: Rückgabekette Quelltruhe → Auffangtruhe → Ablage an Position mit Benachrichtigung; rot laufen lassen und implementieren
- [x] 5.5 Test: Tragemenge begrenzt Entnahme und ist nie grösser als eine Doppeltruhe; rot laufen lassen und implementieren
- [x] 5.6 Test: Transportprotokoll hält Item-Typ, Menge, Quelltruhe, Zieltruhe und Zeitpunkt fest und ist filterbar; rot laufen lassen und implementieren

## 6. Zustandsautomat (`chest-sorter-npc`)

- [x] 6.1 Test: Übergänge `IDLE → SCANNING → SEEK_SOURCE → APPROACH_SOURCE → PICKUP → SEEK_TARGET → APPROACH_TARGET → DELIVER → IDLE` laufen in dieser Reihenfolge durch; rot laufen lassen
- [x] 6.2 Zustandsautomaten mit Übergangstabelle implementieren, bis 6.1 grün ist
- [x] 6.3 Test: Deadline je Zustand — abgelaufene Frist führt nach `RECOVER`, nicht in einen Folgezustand; rot laufen lassen und implementieren
- [x] 6.4 Test: Inventarzugriff nur bei Reichweite erfüllt — Navigation beendet und Sorter ausserhalb `reach` darf nicht zugreifen; rot laufen lassen und implementieren
- [x] 6.5 Test: Zeitlimit und Wiederholung — Anlauf bricht nach Ablauf ab, höchstens `maxAttempts` Versuche mit wachsendem Abstand; rot laufen lassen und implementieren
- [x] 6.6 Test: Sperrliste — nach erfolglosen Versuchen gesperrt, Freigabe nach Ablauf der Dauer, manuelles Entfernen wirkt sofort; rot laufen lassen und implementieren
- [x] 6.7 Test: `AccessPointResolver` wählt einen begehbaren Nachbarblock, verwirft blockierte und gefährliche Positionen, meldet Unerreichbarkeit bei leerer Kandidatenliste; rot laufen lassen und implementieren
- [x] 6.8 Test: Ruhezustand — leerer Bereich erzeugt keine Navigationsaufträge; rot laufen lassen und implementieren
- [x] 6.9 Test: Bereichskonflikt — zwei Sorter mit überlappenden Truhen, der zweite nimmt keine Sortierung auf und der Konflikt wird gemeldet; rot laufen lassen und implementieren
- [x] 6.10 Test: Anhalten mitten in einer Fahrt führt getragene Items zurück und beendet die Navigation; rot laufen lassen und implementieren

## 7. Bukkit-Adapter

- [x] 7.1 `BukkitWorldPort` implementieren (Blockart, Passierbarkeit, Gefahrenblöcke, Truhenerkennung, Schildtexte über `SignSide.FRONT`); `mvn compile` läuft ohne neue Deprecation-Warnung
- [x] 7.2 `BukkitChestPort` implementieren (Inventar lesen, entnehmen, ablegen, Doppeltruhen als ein Inventar); `mvn compile` läuft
- [x] 7.3 `BukkitNavigationPort` auf Basis des Citizens-Navigators implementieren; `mvn compile` läuft
- [x] 7.4 `YamlJournal` für `plugins/ChestManager/journal/<uuid>.yml` implementieren, mit Schreiben vor der Entnahme; ein Test prüft Schreiben, Lesen und Löschen gegen ein temporäres Verzeichnis
- [x] 7.5 Chunk-Tickets setzen und freigeben; ein Test prüft, dass die Zahl der Tickets die Zahl der Bereichschunks nicht überschreitet und beim Anhalten auf null geht

## 8. Citizens-Anbindung und Scheduler

- [x] 8.1 `ChestManagerTrait` neu implementieren: Persistenz über `@Persist` für Heimatpunkt, Bereichsanker, Radius und Konfiguration; keine Items im Trait-Zustand
- [x] 8.2 Trait-Lifecycle korrekt verdrahten (`onAttach`, `onSpawn`, `onDespawn`, `onRemove`, `load`, `save`), kein Aufruf einer eigenen `onServerStart`-Methode; `grep -rn "onServerStart" src/` findet nichts mehr
- [x] 8.3 Globalen `BukkitRunnable` implementieren, der alle Sorter im konfigurierten Takt mit einem Operationsbudget versorgt; ein Test mit Fake-Sortoren prüft die Budgeteinhaltung
- [x] 8.4 Sichtbares Verhalten umsetzen (Blickrichtung zur Truhe, Handbewegung bei Entnahme und Ablage); auf dem Testserver per Sichtprüfung bestätigt
- [x] 8.5 Warten auf die Welt beim Serverstart implementieren; ein Sorter in einer nicht geladenen Welt meldet keinen Fehler und startet nach dem Laden

## 9. Befehle (`chest-sorter-commands`)

- [x] 9.1 `create`, `delete`, `list` implementieren, inklusive Rückgabe getragener Items vor dem Entfernen und Ablehnung doppelter Namen
- [x] 9.2 `scan` und `status` implementieren, mit Zusammenfassung je Rolle, verworfenen Truhen samt Grund und Dauer
- [x] 9.3 `sortnow` implementieren, sodass der Lauf sofort beginnt und bei laufendem Lauf eine Meldung ohne Unterbrechung erscheint; auf dem Testserver bestätigt
- [x] 9.4 `selftest` implementieren: Anker vorhanden, jede Truhe anlaufbar, Kategorien vollständig, freier Platz je Kategorie; der Test verändert keinen Truheninhalt
- [x] 9.5 `blocked` und `unblock` für die Sperrliste implementieren
- [x] 9.6 `log` für das Transportprotokoll mit Filter nach Item-Typ implementieren
- [x] 9.7 `reload` implementieren, das fehlerhafte Einträge mit Dateiposition meldet und gültige Einträge übernimmt
- [x] 9.8 Permissions in `plugin.yml` auf Verwaltung, Betrieb und Einsicht trennen und in jedem Befehl prüfen; ein Test je Permission-Stufe bestätigt die Ablehnung
- [x] 9.9 Tab-Vervollständigung für Unterbefehle, Servernamen und Item-Typen implementieren, gefiltert nach der Eingabe

## 10. Konfiguration

- [x] 10.1 `config.yml.example` mit Kategorien (inklusive `[INPUT]`, `[UNSORTABLE]`, `[ORES]`, `[BLOCKS]`, `[WOOD]`, `[TOOLS]`, `[ARMOR]`, `[FOOD]`, `[REDSTONE]`, `[NATURAL]`, `[MOB_DROPS]`, `[MISC]`), Reichweiten, Limits und Journal-Einstellungen anlegen
- [x] 10.2 Konfigurationsladen mit Validierung implementieren: unbekannte Item-Typen, doppelt vergebene Items und fehlende Kategorien werden mit Dateiposition gemeldet; ein Test deckt jeden Fall ab
- [x] 10.3 Sicherstellen, dass `config.yml` nicht im Git landet und `config.yml.example` schon; `git status --short` zeigt keine `config.yml`

## 11. Verifikation auf dem Testserver

- [x] 11.1 Spigot-Container mit Citizens2 und RCON im Repo einrichten (`docker/`), startet und meldet `Done` im Log
- [x] 11.2 Testskript aufbauen, das per RCON eine Testanlage erstellt: Ankertruhe, je eine Truhe pro Kategorie, Auffangtruhe, eine unerreichbare Truhe, Schilder per `/data merge block`
- [x] 11.3 Ende-zu-Ende-Test: fehlplatzierte Items in mehreren Truhen werden innerhalb von 60 Sekunden in die richtigen Truhen gebracht; das Skript prüft die Inhalte per `/item replace … container.N`-Rücklesung
- [x] 11.4 Test: Sorter trägt Items, Container wird hart neu gestartet, nach dem Neustart liegt kein Item ausserhalb einer Truhe und keines doppelt
- [ ] 11.5 Test: unerreichbare Truhe landet in der Sperrliste, die übrigen Truhen werden weiter sortiert
- [ ] 11.6 Test: Tick-Zeit — `/cm status` weist die Dauer des letzten Scans aus und kein Tick überschreitet das konfigurierte Budget; Messwerte im Log dokumentiert
- [ ] 11.7 Test: Plugin deaktivieren während einer Fahrt, getragene Items liegen danach in einer Truhe des Bereichs

## 12. Abschluss

- [x] 12.1 `./compat-check.sh --baseline` neu setzen und `--diff` zeigt keine neuen Deprecation-Warnungen
- [x] 12.2 `mvn clean package -f pom.xml` baut alle vier Module erfolgreich
- [x] 12.3 `README.md` und `AGENTS.md` um die neue ChestManager-Bedienung ergänzen; die dort beschriebenen Befehle stimmen mit `plugin.yml` überein
- [x] 12.4 `PROJECT_STATUS.md` auf den erreichten Stand setzen (Frontmatter und Body, maximal zehn Body-Zeilen)
- [x] 12.5 `CHANGELOG.md`-Eintrag mit Datum und Kurztitel ergänzen
- [ ] 12.6 Change mit `openspec archive rebuild-chest-sorter-core` archivieren; `openspec/specs/` enthält danach die fünf Capabilities

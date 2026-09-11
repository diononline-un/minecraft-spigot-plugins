# chest-sorter-commands Specification

## Purpose
Die Bedienoberfläche für Betreiber: Sorter anlegen, Bereich einrichten, Zustand
prüfen und Fehler finden, ohne die Konfigurationsdatei von Hand zu bearbeiten.

## Requirements

### Requirement: Sorter anlegen und entfernen

Der Betreiber SHALL einen Sorter über einen Befehl anlegen und entfernen können.
Beim Anlegen MUSS der Sorter an der Position des Betreibers entstehen und diese
Position als Heimatpunkt übernehmen. Beim Entfernen SHALL der Sorter vorher
angehalten werden und getragene Items zurückgeben.

#### Scenario: Sorter anlegen

- **WHEN** der Betreiber den Anlegebefehl mit einem Namen ausführt
- **THEN** entsteht an seiner Position ein Sorter mit diesem Namen und die Position wird als Heimatpunkt gespeichert

#### Scenario: Sorter entfernen, während er Items trägt

- **WHEN** der Betreiber einen Sorter entfernt, der gerade Items trägt
- **THEN** gibt der Sorter die Items zuerst in eine Truhe des Bereichs zurück und wird danach entfernt

#### Scenario: Doppelter Name

- **WHEN** der Betreiber einen Sorter mit einem bereits vergebenen Namen anlegt
- **THEN** lehnt der Sorter den Befehl mit einem Hinweis auf den bestehenden Namen ab

### Requirement: Bereich einrichten und neu ermitteln

Der Betreiber SHALL den Bereich eines Sorters über einen Befehl neu ermitteln
lassen können. Der Befehl MUSS ohne Serverneustart wirken und das Ergebnis als
Zusammenfassung ausgeben: gefundene Truhen je Rolle, verworfene Truhen mit Grund,
Dauer der Ermittlung.

#### Scenario: Bereich neu ermitteln

- **WHEN** der Betreiber den Scan-Befehl für einen Sorter ausführt
- **THEN** erhält er eine Zusammenfassung mit Truhen je Rolle, Anzahl verworfener Truhen mit Grund und der Dauer der Ermittlung

#### Scenario: Fehlender Anker

- **WHEN** der Betreiber einen Scan ausführt und keine Ankertruhe markiert ist
- **THEN** meldet der Befehl, dass die Ankertruhe fehlt, und nennt das erwartete Schild

### Requirement: Zustand abfragen

Der Betreiber SHALL jederzeit abfragen können, was ein Sorter gerade tut und was
er über seinen Bereich weiß. Die Ausgabe MUSS mindestens enthalten: aktueller
Zustand, aktuell getragene Items, Anzahl Truhen je Rolle, gesperrte Truhen mit
Grund und den Heimatpunkt.

#### Scenario: Zustand abfragen

- **WHEN** der Betreiber den Statusbefehl für einen Sorter ausführt
- **THEN** nennt die Ausgabe Zustand, getragene Items, Truhen je Rolle, gesperrte Truhen und Heimatpunkt

#### Scenario: Mehrere Sorter ohne Namensangabe

- **WHEN** der Betreiber den Statusbefehl ohne Namen ausführt und mehrere Sorter existieren
- **THEN** listet der Befehl alle Sorter mit ihrem jeweiligen Zustand auf

### Requirement: Selbsttest der Einrichtung

Der Sorter SHALL einen Selbsttest anbieten, der die Einrichtung auf Fehler prüft,
ohne Items zu bewegen. Der Test MUSS mindestens prüfen: Existenz einer Ankertruhe,
Erreichbarkeit jeder Truhe des Bereichs, Vollständigkeit der Kategorie-Zuordnung
für die tatsächlich im Bereich liegenden Items und freien Platz je Kategorie. Das
Ergebnis SHALL als Liste von Befunden mit Schweregrad ausgegeben werden.

#### Scenario: Einrichtung fehlerfrei

- **WHEN** der Betreiber den Selbsttest auf einer korrekt eingerichteten Anlage ausführt
- **THEN** meldet der Test keinen Befund mit Schweregrad Fehler

#### Scenario: Unerreichbare Truhe erkannt

- **WHEN** eine Truhe des Bereichs nicht anlaufbar ist
- **THEN** meldet der Selbsttest diese Truhe mit Position und Schweregrad Fehler

#### Scenario: Items ohne Kategorie erkannt

- **WHEN** Items im Bereich liegen, deren Typ in keiner Kategorie vorkommt und keine Auffangtruhe markiert ist
- **THEN** meldet der Selbsttest diese Item-Typen mit Schweregrad Warnung

#### Scenario: Selbsttest bewegt nichts

- **WHEN** der Betreiber den Selbsttest ausführt
- **THEN** ändert sich der Inhalt keiner Truhe des Bereichs

### Requirement: Sortierung manuell auslösen

Der Betreiber SHALL einen Sortierlauf sofort auslösen können, ohne die
konfigurierte Wartezeit abzuwarten. Der ausgelöste Lauf MUSS unmittelbar beginnen
und im Status als manuell ausgelöst erkennbar sein.

#### Scenario: Sofort sortieren

- **WHEN** der Betreiber den Sofort-Befehl ausführt und der Sorter im Ruhezustand ist
- **THEN** beginnt der Sortierlauf innerhalb der nächsten Sekunde

#### Scenario: Sofort-Befehl während einer Fahrt

- **WHEN** der Betreiber den Sofort-Befehl ausführt, während der Sorter bereits sortiert
- **THEN** meldet der Befehl, dass bereits ein Lauf aktiv ist, und der laufende Lauf wird nicht unterbrochen

### Requirement: Sperrliste verwalten

Der Betreiber SHALL die Liste der als unerreichbar gesperrten Truhen einsehen und
Einträge daraus entfernen können.

#### Scenario: Sperrliste einsehen

- **WHEN** der Betreiber die Sperrliste abfragt
- **THEN** erhält er je Eintrag Truhenposition, Grund und verbleibende Sperrdauer

#### Scenario: Sperre entfernen

- **WHEN** der Betreiber eine Truhe aus der Sperrliste entfernt
- **THEN** erscheint die Truhe nicht mehr in der Liste und wird beim nächsten Lauf wieder angefahren

### Requirement: Transportprotokoll einsehen

Der Betreiber SHALL die letzten Transportvorgänge einsehen können, gefiltert nach
Sorter und optional nach Item-Typ.

#### Scenario: Letzte Vorgänge abrufen

- **WHEN** der Betreiber das Transportprotokoll abruft
- **THEN** erhält er die jüngsten Vorgänge mit Item-Typ, Menge, Quelltruhe, Zieltruhe und Zeitpunkt

### Requirement: Berechtigungen trennen

Die Befehle SHALL in Verwaltung, Betrieb und Einsicht getrennt berechtigt sein.
Ein Spieler ohne die Einsichtsberechtigung MUSS Status- und Protokollausgaben
verweigert bekommen. Ein Spieler ohne Verwaltungsberechtigung MUSS Sorter weder
anlegen noch entfernen noch deren Konfiguration ändern können.

#### Scenario: Spieler ohne Verwaltungsberechtigung

- **WHEN** ein Spieler ohne Verwaltungsberechtigung den Anlegebefehl ausführt
- **THEN** lehnt der Befehl ab und ändert nichts

#### Scenario: Spieler ohne Einsichtsberechtigung

- **WHEN** ein Spieler ohne Einsichtsberechtigung das Transportprotokoll abruft
- **THEN** lehnt der Befehl ab

### Requirement: Vervollständigung der Befehle

Jeder Befehl SHALL seine Argumente vervollständigen. Unterbefehl, Servername und
Item-Typ MÜSSEN als Vervollständigungsvorschläge erscheinen, gefiltert nach der
bereits eingegebenen Zeichenfolge.

#### Scenario: Unterbefehl vervollständigen

- **WHEN** der Betreiber den Befehl tippt und die Vervollständigung auslöst
- **THEN** erscheinen die passenden Unterbefehle

#### Scenario: Servername vervollständigen

- **WHEN** der Betreiber nach einem Unterbefehl die Vervollständigung auslöst
- **THEN** erscheinen die existierenden Servernamen

### Requirement: Konfiguration ohne Neustart neu laden

Der Betreiber SHALL die Konfiguration über einen Befehl neu laden können. Der
Befehl MUSS ungültige Einträge benennen, statt sie stillschweigend zu übergehen.

#### Scenario: Konfiguration mit Fehler neu laden

- **WHEN** der Betreiber die Konfiguration neu lädt und ein Eintrag nennt einen unbekannten Item-Typ
- **THEN** meldet der Befehl den fehlerhaften Eintrag mit Dateiposition und die übrigen Einträge werden übernommen

### Requirement: Bestehendes Schild-Vokabular bleibt gültig

Der Sorter SHALL die bisher verwendeten Rollen-Schilder weiter akzeptieren,
mindestens `[INPUT]`, `[UNSORTABLE]`, `[ORES]`, `[BLOCKS]`, `[WOOD]`, `[TOOLS]`,
`[ARMOR]`, `[FOOD]`, `[REDSTONE]`, `[NATURAL]`, `[MOB_DROPS]` und `[MISC]`.
Bestehende Schilder auf dem Server SHALL ohne Umetikettierung weiter funktionieren.

#### Scenario: Altes Schild weiterverwenden

- **WHEN** eine Truhe aus der früheren Einrichtung ein Schild mit dem Text `[BLOCK]` trägt
- **THEN** erkennt der Sorter die Rolle weiterhin und die Truhe muss nicht neu beschriftet werden

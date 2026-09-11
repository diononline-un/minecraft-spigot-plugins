# chest-sorter-area Specification

## Purpose
Bestimmt, welche Truhen zu einem Sorter gehören und welche Rolle jede Truhe im
Sortierbetrieb spielt. Ohne diese Zuordnung weiß der NPC weder, woher er Items
nehmen darf, noch wohin er sie bringen soll.

## Requirements

### Requirement: Bereichsermittlung ab Ankertruhe

Der Sorter SHALL seinen Bereich ausgehend von genau einer markierten Ankertruhe
ermitteln. Zum Bereich gehören alle Truhenblöcke, die über eine Kette
flächen- oder kantenbenachbarter Truhenblöcke mit der Ankertruhe verbunden sind.
Truhen, die keine solche Verbindung haben, gehören nicht zum Bereich.

#### Scenario: Verbundene Truhenwand

- **WHEN** die Ankertruhe mit einem Schild markiert ist und weitere Truhen lückenlos daneben, darüber oder darunter stehen
- **THEN** gehören Ankertruhe und alle verbundenen Truhen zum Bereich

#### Scenario: Getrennte Truheninsel

- **WHEN** zwei Blöcke neben dem Verbund eine einzelne Truhe ohne Verbindung zum Anker steht
- **THEN** bleibt diese Truhe ausserhalb des Bereichs und wird nie befüllt oder geleert

#### Scenario: Bereich ohne Anker

- **WHEN** kein Truhenblock ein Anker-Schild trägt
- **THEN** meldet der Sorter den Bereich als nicht konfiguriert und führt keine Sortierung aus

### Requirement: Obergrenzen der Bereichsermittlung

Die Ermittlung SHALL durch eine konfigurierbare Höchstzahl an Truhen und einen
konfigurierbaren maximalen Höhenunterschied begrenzt sein. Erreicht die Ermittlung
eine Grenze, SHALL sie anhalten, den bis dahin gefundenen Bereich verwenden und
den Betreiber auf die Grenzverletzung hinweisen.

#### Scenario: Grenze überschritten

- **WHEN** ein Truppenverbund mehr Truhen umfasst als die konfigurierte Höchstzahl
- **THEN** bricht die Ermittlung an der Höchstzahl ab, der Bereich enthält genau die Höchstzahl Truhen und der Betreiber erhält einen Hinweis mit der erreichten Grenze

### Requirement: Truhenrollen über Schilder

Der Sorter SHALL die Rolle einer Truhe aus dem Text der ersten Zeile eines
Schildes ableiten. Erkennbar sein MÜSSEN mindestens: eine Ankerrolle, eine
Kategorie-Rolle je konfigurierter Kategorie, eine Auffangrolle für Items ohne
passende Kategorie und eine Ignorierrolle, die die Truhe vom Sortierbetrieb
ausnimmt. Die Zuordnung SHALL unabhängig von Gross- und Kleinschreibung sowie von
eckigen Klammern im Schildtext sein.

#### Scenario: Kategorieschild erkannt

- **WHEN** ein Schild mit dem Text `[ORES]` an einer Truhe steht und `ores` in der Konfiguration als Kategorie existiert
- **THEN** gilt diese Truhe als Zieltruhe der Kategorie `ores`

#### Scenario: Schild an angrenzendem Block

- **WHEN** das Schild nicht an der Truhe selbst, sondern an einem unmittelbar angrenzenden Block angebracht ist
- **THEN** wird die Rolle genauso erkannt wie bei einem Schild an der Truhe

#### Scenario: Ignorierte Truhe

- **WHEN** eine Truhe innerhalb des Bereichs mit der Ignorierrolle markiert ist
- **THEN** entnimmt der Sorter daraus keine Items und legt keine Items hinein

#### Scenario: Unbekannte Kategorie

- **WHEN** ein Schild eine Kategorie nennt, die in der Konfiguration nicht existiert
- **THEN** gilt die Truhe als nicht markiert und der Betreiber erhält beim Scan einen Hinweis auf den unbekannten Namen

### Requirement: Doppeltruhen als eine Einheit

Zwei verbundene Truhenblöcke, die gemeinsam ein Inventar bilden, SHALL als genau
eine Sortiereinheit behandelt werden. Ein Schild an einer der beiden Hälften SHALL
für die gesamte Doppeltruhe gelten. Die Einheit SHALL bei der Ermittlung nur einmal
gezählt werden.

#### Scenario: Schild an einer Hälfte

- **WHEN** ein Kategorieschild an der linken Hälfte einer Doppeltruhe steht
- **THEN** gilt die gesamte Doppeltruhe als Zieltruhe dieser Kategorie und erscheint genau einmal in der Bereichsübersicht

### Requirement: Budgetierter Scan

Die Ermittlung SHALL den Server-Tick nicht blockieren. Die Blockabfrage MUSS über
mehrere Ticks verteilt werden und pro Tick eine konfigurierbare Obergrenze an
Blockzugriffen einhalten. Während eines laufenden Scans SHALL der Sorter keine
Sortierfahrt beginnen.

#### Scenario: Scan grosser Bereiche

- **WHEN** ein Betreiber einen Scan über einen Bereich mit mehreren tausend Blöcken startet
- **THEN** überschreitet kein einzelner Tick die konfigurierte Obergrenze an Blockzugriffen und der Server bleibt ohne Aussetzer bedienbar

#### Scenario: Scan mit Fortschritt

- **WHEN** ein Scan läuft und der Betreiber den Status abfragt
- **THEN** nennt der Sorter den Fortschritt in Prozent und die bis dahin gefundene Truhenzahl

#### Scenario: Scan abgebrochen

- **WHEN** der Betreiber einen laufenden Scan abbricht
- **THEN** endet der Scan innerhalb eines Ticks und der zuletzt vollständig ermittelte Bereich bleibt gültig

### Requirement: Chunks im Bereich geladen halten

Der Sorter SHALL die Chunks, die Truhen des Bereichs enthalten, für die Dauer
seines Betriebs geladen halten. Chunks ausserhalb des Bereichs SHALL er nicht
laden.

#### Scenario: Entfernte Bereichstruhe

- **WHEN** eine Truhe des Bereichs in einem Chunk steht, den kein Spieler geladen hat
- **THEN** bleibt dieser Chunk geladen, solange der Sorter aktiv ist, und die Truhe bleibt für die Sortierung erreichbar

#### Scenario: Sorter abgeschaltet

- **WHEN** der Betreiber den Sorter abschaltet oder das Plugin deaktiviert wird
- **THEN** gibt der Sorter alle von ihm gehaltenen Chunks frei

### Requirement: Bereichsänderungen erkennen

Der Sorter SHALL nach dem Hinzufügen oder Entfernen einer markierten Truhe den
Bereich neu ermitteln, ohne dass der Server neu gestartet werden muss.

#### Scenario: Neue Truhe im Verbund

- **WHEN** der Betreiber eine markierte Truhe an den bestehenden Verbund anbaut und einen Scan auslöst
- **THEN** nimmt der Sorter die neue Truhe mit ihrer Rolle in den Bereich auf

#### Scenario: Truhe entfernt

- **WHEN** eine Truhe des Bereichs abgebaut wird und der Sorter das nächste Mal auf ihren Block zugreift
- **THEN** entfernt er die Truhe aus dem Bereich, ohne einen Fehler zu protokollieren, und führt die Sortierung mit den verbleibenden Truhen fort

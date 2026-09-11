# chest-sorter-routing Specification

## Purpose
Entscheidet für jedes Item, in welche Truhe es gehört, und erkennt Items, die
falsch liegen. Das ist die Regel, die den Sorter von einem reinen Umschichter
unterscheidet: er korrigiert Bestände, statt nur Neues zu verteilen.

## Requirements

### Requirement: Kategorie-Zuordnung aus der Konfiguration

Der Sorter SHALL die Kategorie eines Items aus einer Konfigurationsdatei ableiten.
Eine Kategorie MUSS über eine Liste von Item-Typen definierbar sein. Ein Item-Typ
SHALL höchstens einer Kategorie angehören; bei mehrfacher Nennung gilt die zuerst
definierte Kategorie und der Betreiber erhält beim Laden einen Hinweis auf die
Doppelnennung.

#### Scenario: Item mit Kategorie

- **WHEN** ein Item vom Typ Eisen-Erz in der Konfiguration der Kategorie `ores` zugeordnet ist
- **THEN** ist die Kategorie dieses Items `ores`

#### Scenario: Doppelt genanntes Item

- **WHEN** ein Item-Typ in zwei Kategorien aufgeführt ist
- **THEN** gilt die zuerst definierte Kategorie und der Betreiber erhält beim Laden einen Hinweis mit beiden Kategorienamen

### Requirement: Auffangverhalten für Items ohne Kategorie

Der Sorter SHALL Items, deren Typ in keiner Kategorie vorkommt, gesondert
behandeln. Ist eine Truhe mit der Auffangrolle markiert, SHALL er diese Items
dorthin bringen. Ist keine Auffangtruhe markiert, SHALL er die Items in ihrer
Quelltruhe belassen und den Betreiber einmalig je Item-Typ auf die fehlende
Zuordnung hinweisen.

#### Scenario: Auffangtruhe vorhanden

- **WHEN** ein Item ohne Kategorie in einer Quelltruhe liegt und eine Truhe mit der Auffangrolle markiert ist
- **THEN** bringt der Sorter das Item in die Auffangtruhe

#### Scenario: Keine Auffangtruhe

- **WHEN** ein Item ohne Kategorie in einer Quelltruhe liegt und keine Truhe mit der Auffangrolle markiert ist
- **THEN** bleibt das Item in der Quelltruhe und der Betreiber erhält einen Hinweis auf den Typ ohne Kategorie

### Requirement: Fehlplatzierung erkennen

Der Sorter SHALL ein Item in einer Truhe als fehlplatziert betrachten, wenn die
Kategorie des Items nicht der Kategorie-Rolle dieser Truhe entspricht. Die
Ankertruhe führt keine Kategorie und wirkt als Eingangspuffer: Items mit Kategorie
gelten darin als fehlplatziert, Items ohne Kategorie bleiben liegen. Items in der
Auffangtruhe gelten nur dann als fehlplatziert, wenn für sie eine Kategorie
existiert. Items in Truhen mit der Ignorierrolle gelten nie als fehlplatziert.

#### Scenario: Falsch einsortiertes Item

- **WHEN** in einer Truhe der Kategorie `ores` ein Item der Kategorie `food` liegt
- **THEN** gilt dieses Item als fehlplatziert und wird entnommen

#### Scenario: Korrekt einsortiertes Item

- **WHEN** in einer Truhe der Kategorie `ores` ein Item der Kategorie `ores` liegt
- **THEN** bleibt das Item unangetastet

#### Scenario: Item in der Ankertruhe

- **WHEN** ein Item mit bekannter Kategorie in der Ankertruhe liegt
- **THEN** gilt es als fehlplatziert und wird zur passenden Kategorie gebracht

#### Scenario: Item ohne Kategorie in der Ankertruhe

- **WHEN** ein Item ohne bekannte Kategorie in der Ankertruhe liegt und keine Auffangtruhe markiert ist
- **THEN** bleibt es in der Ankertruhe liegen und der Betreiber erhält einen Hinweis auf den Typ ohne Kategorie

#### Scenario: Item in der Auffangtruhe

- **WHEN** ein Item ohne Kategorie in der Auffangtruhe liegt
- **THEN** gilt es als korrekt platziert und bleibt liegen

### Requirement: Bestehende Stacks bevorzugen

Der Sorter SHALL innerhalb der zulässigen Zieltruhen einer Kategorie zuerst eine
Truhe wählen, die bereits einen Stack desselben Item-Typs führt und noch Platz in
diesem Stack hat. Erst danach SHALL er Truhen mit freiem Platz wählen.

#### Scenario: Truhe mit gleichem Item vorhanden

- **WHEN** eine Kategorie über zwei Zieltruhen verfügt und eine davon bereits denselben Item-Typ führt
- **THEN** legt der Sorter das Item in diese Truhe

#### Scenario: Keine Truhe führt das Item

- **WHEN** keine Zieltruhe der Kategorie denselben Item-Typ führt, aber mindestens eine freien Platz hat
- **THEN** legt der Sorter das Item in die erreichbare Zieltruhe mit dem meisten freien Platz

### Requirement: Zielwahl deterministisch und nachvollziehbar

Bei mehreren gleichwertigen Zieltruhen SHALL der Sorter eine reproduzierbare
Reihenfolge einhalten, damit wiederholte Läufe dasselbe Ergebnis liefern. Die
getroffene Wahl MUSS über die Diagnose abfragbar sein.

#### Scenario: Gleiche Ausgangslage, gleiches Ergebnis

- **WHEN** zwei Zieltruhen derselben Kategorie identisch belegt und gleich weit entfernt sind
- **THEN** wählt der Sorter in beiden Durchläufen dieselbe Truhe

### Requirement: Kein Platz in der Kategorie

Der Sorter SHALL ein Item, für das keine Zieltruhe der passenden Kategorie Platz
hat, nicht in eine Truhe einer anderen Kategorie legen. Er SHALL es in der
Quelltruhe belassen und den Betreiber auf die volle Kategorie hinweisen.

Ein nicht unterzubringender Stapel SHALL die Sortierung der übrigen Stapel nicht
aufhalten. Der Sorter MUSS andere Items derselben Quelltruhe weiterhin an ihren
Platz bringen und darf für denselben Stapel nicht wiederholt neuen Anlauf nehmen.

#### Scenario: Kategorie voll

- **WHEN** alle Zieltruhen der Kategorie `ores` keinen freien Platz mehr haben
- **THEN** bleibt das Item in der Quelltruhe und der Betreiber erhält einen Hinweis, welche Kategorie voll ist

#### Scenario: Kategorie voll, andere leer

- **WHEN** die Kategorie `ores` voll ist und die Kategorie `blocks` freien Platz hat
- **THEN** legt der Sorter das Erz nicht in die Blocktruhe

#### Scenario: Volle Kategorie hält andere Stapel nicht auf

- **WHEN** in einer Quelltruhe Erz und Brot liegen, die Erzkategorie voll ist und die Esstruhe Platz hat
- **THEN** bringt der Sorter das Brot zur Esstruhe und lässt nur das Erz liegen

#### Scenario: Fehlende Zieltruhe hält andere Stapel nicht auf

- **WHEN** in einer Quelltruhe ein Werkzeug ohne markierte Zieltruhe und Brot mit Zieltruhe liegen
- **THEN** bringt der Sorter das Brot zur Esstruhe und lässt nur das Werkzeug liegen

#### Scenario: Kein Kreisen um nicht unterzubringende Stapel

- **WHEN** ein Stapel nicht unterzubringen ist und sonst nichts zu tun bleibt
- **THEN** kehrt der Sorter in den Ruhezustand zurück, statt denselben Stapel erneut zu versuchen

### Requirement: Konfiguration im Betrieb neu laden

Der Sorter SHALL Änderungen an Kategorien, Zieltruhen und Auffangrolle ohne
Serverneustart übernehmen. Ein Neuladen SHALL einen laufenden Sortiervorgang nicht
in einen ungültigen Zustand bringen.

#### Scenario: Kategorie geändert

- **WHEN** der Betreiber die Konfiguration ändert und ein Neuladen auslöst
- **THEN** verwendet der Sorter ab dem nächsten Sortiervorgang die neuen Zuordnungen

#### Scenario: Neuladen während einer Fahrt

- **WHEN** der Betreiber neu lädt, während der Sorter Items trägt
- **THEN** beendet der Sorter die laufende Fahrt mit den bis dahin gültigen Zielen und verwendet die neuen Zuordnungen erst danach

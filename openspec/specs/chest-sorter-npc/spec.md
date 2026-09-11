# chest-sorter-npc Specification

## Purpose
Der NPC, der die Sortierung ausführt: er läuft den Bereich ab, entnimmt
fehlplatzierte Items und bringt sie zu ihren Zieltruhen. Diese Capability
beschreibt sein Verhalten, seine Fehlerbehandlung und seinen Lebenszyklus.

## Requirements

### Requirement: Sortierlauf über den gesamten Bereich

Der Sorter SHALL den erkannten Bereich fortlaufend nach fehlplatzierten Items
absuchen. Er SHALL dabei alle Truhen des Bereichs berücksichtigen, denen eine
Rolle zugewiesen ist, und nicht nur eine einzelne feste Truhe.

#### Scenario: Durchlauf über mehrere Quelltruhen

- **WHEN** drei Truhen des Bereichs fehlplatzierte Items enthalten
- **THEN** bearbeitet der Sorter alle drei im selben Sortierlauf

#### Scenario: Leerer Bereich

- **WHEN** keine Truhe des Bereichs fehlplatzierte Items enthält
- **THEN** geht der Sorter in den Ruhezustand über und bewegt sich nicht fortlaufend

### Requirement: Inventarzugriff nur in Reichweite

Der Sorter SHALL eine Truhe erst dann öffnen oder verändern, wenn er sich
tatsächlich innerhalb einer konfigurierbaren Reichweite zu diesem Truhenblock
befindet. Eine laufende oder fehlgeschlagene Navigation SHALL niemals als Beleg
für Reichweite gelten.

#### Scenario: NPC steht an der Truhe

- **WHEN** der Sorter den Truhenblock innerhalb der konfigurierten Reichweite erreicht hat
- **THEN** darf er das Inventar dieser Truhe lesen und verändern

#### Scenario: Navigation meldet Ende, NPC ist weit weg

- **WHEN** die Navigation ohne Fehlermeldung endet, der Sorter aber weiter als die konfigurierte Reichweite vom Truhenblock entfernt steht
- **THEN** greift der Sorter nicht auf das Inventar zu, sondern behandelt den Anlauf als fehlgeschlagen

### Requirement: Navigation mit Zeitlimit und Wiederholung

Jeder Anlauf zu einer Truhe SHALL ein konfigurierbares Zeitlimit haben. Läuft das
Limit ab oder meldet die Navigation einen Fehler, SHALL der Sorter den Anlauf
abbrechen und höchstens eine konfigurierbare Anzahl weiterer Versuche mit
wachsendem Abstand unternehmen.

#### Scenario: Anlauf dauert zu lange

- **WHEN** der Sorter eine Truhe innerhalb des Zeitlimits nicht erreicht
- **THEN** bricht er den Anlauf ab und startet einen weiteren Versuch nach der konfigurierten Wartezeit

#### Scenario: Wiederholte Fehlversuche

- **WHEN** alle konfigurierten Versuche für dieselbe Truhe fehlschlagen
- **THEN** markiert der Sorter diese Truhe als unerreichbar und bearbeitet die übrigen Truhen des Bereichs weiter

### Requirement: Unerreichbare Truhen sperren und wieder freigeben

Der Sorter SHALL eine Truhe, die er als unerreichbar eingestuft hat, für eine
konfigurierbare Dauer von der Sortierung ausnehmen. Nach Ablauf der Dauer SHALL er
einen neuen Anlauf unternehmen. Der Betreiber MUSS die Sperrliste einsehen und
Einträge daraus entfernen können.

#### Scenario: Truhe vorübergehend unerreichbar

- **WHEN** eine Truhe hinter einer geschlossenen Tür liegt und der Sorter sie deshalb nicht erreicht
- **THEN** nimmt er sie für die konfigurierte Dauer von der Sortierung aus und versucht es danach erneut

#### Scenario: Betreiber hebt Sperre auf

- **WHEN** der Betreiber eine Truhe aus der Sperrliste entfernt
- **THEN** versucht der Sorter beim nächsten Sortierlauf wieder, diese Truhe zu erreichen

### Requirement: Ein Sorter je Truhe

Der Sorter SHALL eine Truhe, die bereits von einem anderen Sorter bearbeitet wird,
nicht gleichzeitig bearbeiten. Beim Start SHALL er erkennen, wenn zwei Sorter
denselben Bereich beanspruchen, und den Betreiber darauf hinweisen.

#### Scenario: Zwei Sorter, ein Bereich

- **WHEN** ein zweiter Sorter einen Bereich beansprucht, der bereits einem anderen zugeordnet ist
- **THEN** weist der Sorter den Betreiber auf den Konflikt hin und der zweite Sorter nimmt keine Sortierung auf, bis der Konflikt aufgelöst ist

### Requirement: Arbeit pro Tick begrenzt

Der Sorter SHALL pro Server-Tick höchstens eine konfigurierbare Anzahl von
Inventarzugriffen ausführen. Er SHALL seine Arbeit über Ticks verteilen, statt
einen Durchlauf in einem einzigen Tick zu erledigen.

#### Scenario: Viele Items in einer Truhe

- **WHEN** eine Quelltruhe mehrere Stapel fehlplatzierter Items enthält
- **THEN** verteilt der Sorter die Zugriffe über mehrere Ticks und überschreitet die Obergrenze pro Tick nicht

### Requirement: Zustand über Neustart hinweg

Der Sorter SHALL seinen Zustand, seinen Heimatpunkt und seinen beanspruchten
Bereich dauerhaft speichern. Nach einem Serverneustart SHALL er den Betrieb ohne
Zutun des Betreibers wieder aufnehmen.

#### Scenario: Server neu gestartet

- **WHEN** der Server neu startet und der Sorter zuvor aktiv war
- **THEN** nimmt der Sorter seine Sortiertätigkeit ohne erneutes Einrichten wieder auf

#### Scenario: Welt zum Startzeitpunkt nicht geladen

- **WHEN** die Welt des Sorters beim Serverstart noch nicht geladen ist
- **THEN** wartet der Sorter mit dem Zugriff, bis die Welt verfügbar ist, und meldet keinen Fehler

### Requirement: Sichtbares Verhalten

Der Sorter SHALL seine Handlungen sichtbar ausführen, damit ein Betreiber ohne
Diagnosebefehl erkennt, ob und was er tut. Beim Ablegen oder Entnehmen von Items
SHALL er eine sichtbare Handbewegung zeigen und sich zur bearbeiteten Truhe
ausrichten.

#### Scenario: Item wird abgelegt

- **WHEN** der Sorter ein Item in eine Zieltruhe legt
- **THEN** führt er eine sichtbare Handbewegung aus und blickt zur Truhe

### Requirement: Keine Geräusche

Der Sorter SHALL keine Geräusche erzeugen. Er MUSS stumm geschaltet sein, solange
er existiert, und darf weder eigene Klänge abspielen noch Geräusche seiner Entität
zulassen. Das gilt auch für Handbewegungen, die stumm auszuführen sind.

#### Scenario: Sorter arbeitet

- **WHEN** der Sorter läuft, Items entnimmt oder ablegt
- **THEN** ist kein Geräusch zu hören

#### Scenario: Sorter wird neu gespawnt

- **WHEN** der Sorter nach einem Chunkwechsel oder Serverneustart neu erscheint
- **THEN** ist er weiterhin stumm geschaltet

### Requirement: Kontrolliertes Anhalten

Der Betreiber SHALL den Sorter jederzeit anhalten können. Beim Anhalten SHALL der
Sorter laufende Navigation abbrechen, getragene Items zurückgeben und keine neuen
Sortierfahrten beginnen.

#### Scenario: Sorter anhalten

- **WHEN** der Betreiber den Sorter anhält, während dieser zu einer Truhe läuft
- **THEN** bricht der Sorter die Navigation ab, gibt getragene Items zurück und bleibt bis zur Freigabe im Ruhezustand

#### Scenario: Plugin deaktiviert

- **WHEN** das Plugin deaktiviert oder der Server heruntergefahren wird
- **THEN** halten alle Sorter an, geben getragene Items zurück, sichern ihren Zustand und geben gehaltene Chunks frei

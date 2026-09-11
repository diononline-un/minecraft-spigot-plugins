## Purpose

Stellt sicher, dass kein Item verloren geht oder sich vervielfacht, während es
zwischen zwei Truhen unterwegs ist. Der Sorter entnimmt Items physisch, also
braucht jeder Abbruch einen definierten Rückweg.

## ADDED Requirements

### Requirement: Kein Item-Verlust bei Absturz

Der Sorter SHALL den Transportzustand dauerhaft speichern, sobald er Items aus
einer Truhe entnimmt. Nach einem Serverabsturz oder Neustart SHALL er den
gespeicherten Zustand wiederherstellen und die betroffenen Items zurückführen.
Ein Item, das der Sorter entnommen hat, MUSS nach dem Wiederanlauf entweder in
einer Truhe des Bereichs liegen oder sich im wiederhergestellten Transportzustand
befinden.

#### Scenario: Absturz während des Transports

- **WHEN** der Server abstürzt, während der Sorter Items trägt
- **THEN** findet der Sorter nach dem Neustart die gespeicherte Transportinformation vor und legt die Items in die vorgesehene Zieltruhe oder zurück in die Quelltruhe

#### Scenario: Absturz zwischen Entnahme und Zielbestimmung

- **WHEN** der Server abstürzt, nachdem Items entnommen wurden, aber bevor ein Ziel feststand
- **THEN** führt der Sorter diese Items nach dem Neustart in ihre Quelltruhe zurück

### Requirement: Keine Duplikation bei der Wiederherstellung

Die Wiederherstellung SHALL ein Item höchstens einmal in eine Truhe legen. Kann der
Sorter nicht feststellen, ob ein Item bereits abgelegt wurde, SHALL er es in die
Quelltruhe zurückführen und den Vorgang als unsicher melden, statt es erneut in die
Zieltruhe zu legen.

#### Scenario: Unklarer Ablagezustand

- **WHEN** nach einem Absturz nicht feststellbar ist, ob ein Item die Zieltruhe bereits erreicht hat
- **THEN** legt der Sorter das Item in die Quelltruhe zurück und meldet den Vorgang als unsicher

### Requirement: Entnahme erst bei erreichbarem Ziel

Der Sorter SHALL ein Item erst dann aus einer Truhe entnehmen, wenn eine
erreichbare Zieltruhe mit ausreichend Platz feststeht. Steht keine Zieltruhe fest,
MUSS das Item in seiner Truhe bleiben.

#### Scenario: Ziel steht fest

- **WHEN** der Sorter eine Quelltruhe mit fehlplatzierten Items und eine erreichbare Zieltruhe mit Platz kennt
- **THEN** entnimmt er die Items und bringt sie zur Zieltruhe

#### Scenario: Kein Ziel erreichbar

- **WHEN** alle passenden Zieltruhen als unerreichbar gesperrt sind
- **THEN** entnimmt der Sorter nichts und die Items bleiben in der Quelltruhe

### Requirement: Rückgabe bei Abbruch

Bei jedem planmässigen oder unplanmässigen Ende einer Sortierfahrt SHALL der Sorter
getragene Items zurückgeben. Bevorzugt MUSS er sie in die Quelltruhe legen. Ist die
Quelltruhe nicht mehr verfügbar oder voll, SHALL er sie in die Auffangtruhe legen.
Ist auch das nicht möglich, SHALL er sie an seiner Position ablegen und den
Betreiber mit genauer Position und Item-Liste benachrichtigen.

#### Scenario: Sorter wird abgeschaltet

- **WHEN** der Betreiber den Sorter abschaltet, während dieser Items trägt
- **THEN** legt der Sorter die Items vor dem Abschalten in die Quelltruhe oder die Auffangtruhe zurück

#### Scenario: Quelltruhe abgebaut

- **WHEN** während einer Fahrt die Quelltruhe abgebaut wird
- **THEN** legt der Sorter die getragenen Items in die Auffangtruhe und meldet den Verlust der Quelltruhe

#### Scenario: Kein Rückweg möglich

- **WHEN** weder Quelltruhe noch Auffangtruhe Platz bieten
- **THEN** legt der Sorter die Items an seiner aktuellen Position ab und benachrichtigt den Betreiber mit Position und vollständiger Item-Liste

### Requirement: Begrenzte Tragemenge

Der Sorter SHALL höchstens eine konfigurierbare Anzahl an Items gleichzeitig
tragen. Die Tragemenge MUSS kleiner oder gleich der Kapazität einer Doppeltruhe
sein, damit eine Rückgabe immer möglich bleibt.

#### Scenario: Tragemenge erreicht

- **WHEN** der Sorter die konfigurierte Höchstmenge an Items trägt
- **THEN** entnimmt er aus der laufenden Quelltruhe nichts weiter und bringt zuerst die getragenen Items zu ihren Zielen

### Requirement: Nachvollziehbares Transportprotokoll

Der Sorter SHALL jeden Entnahme- und Ablagevorgang mit Item-Typ, Menge, Quelltruhe,
Zieltruhe und Zeitpunkt protokollieren. Das Protokoll MUSS für den Betreiber
einsehbar sein.

#### Scenario: Vorgang nachvollziehen

- **WHEN** der Betreiber wissen will, wohin ein bestimmter Item-Typ gebracht wurde
- **THEN** nennt ihm das Protokoll die letzten Vorgänge zu diesem Item-Typ mit Quelltruhe, Zieltruhe und Zeitpunkt

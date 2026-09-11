## Context

Ausgangslage und Defekte stehen in `proposal.md` unter „Why". Für die
Architektur relevant sind diese Randbedingungen:

- Bukkit erlaubt keine Weltzugriffe aus Async-Threads. Jede Entwurfsentscheidung
  muss auf dem Main-Thread funktionieren; Entlastung geht nur über Arbeitsteilung
  zwischen Ticks, nicht über Nebenläufigkeit.
- Citizens ruft `Trait.run()` jeden Tick auf. Der Trait-Lifecycle (`onAttach`,
  `onSpawn`, `onDespawn`, `onRemove`, `load`, `save`) ist der einzige verlässliche
  Einstiegspunkt; ein Hook für den Serverstart existiert nicht.
- Bukkit-Objekte wie `Inventory`, `Block` und `Location` sind nicht serialisierbar
  und in Tests nur schwer nachzubilden.
- Das Modul hat bisher keine Testquellen und keine Testabhängigkeit.
- Der Server läuft produktiv. Jede Änderung muss vor dem Ausrollen auf einem
  Testserver nachweisbar funktionieren.

## Goals / Non-Goals

**Goals:**

- Die Entscheidungslogik (Bereich, Rollen, Routing, Transportzustand) ohne
  laufenden Server ausführbar und damit als Unit-Test prüfbar machen.
- Fehlverhalten sichtbar machen statt schädlich: jeder Anlauf hat ein Zeitlimit,
  jede unerreichbare Truhe landet in einer einsehbaren Sperrliste.
- Item-Sicherheit als Eigenschaft des Entwurfs, nicht als nachträgliche Prüfung.
- Sortierbetrieb, der in kurzen, unterbrochenen Arbeitsschritten läuft und
  zwischenzeitlich in einen konsistenten Zustand zurückfindet.

**Non-Goals:**

- Kein generisches Truhen-Framework für andere Plugins dieses Monorepos.
- Kein Pathfinding-Eigenbau. Citizens navigiert; der Entwurf prüft nur das Ergebnis.
- Keine Itemsicherung ausserhalb des Sortierbereichs. Was der Sorter nicht
  angefasst hat, ist nicht seine Verantwortung.
- Keine Abwärtskompatibilität zu alten ChestManager-NPCs.

## Decisions

### D1: Bukkit-freier Kern hinter Ports, statt Bukkit-Typen im Kern

Der Kern liegt in `me.diono.chestmanager.core` und kennt ausschliesslich eigene
Werttypen: `BlockPos`, `ItemKey`, `ChestRole`, `ChestRef`, `SorterState`. Der
Zugriff auf die Welt läuft über schmale Ports, die der Bukkit-Adapter
implementiert.

Ports: `WorldPort` (Blockart, Blockeigenschaften, Truhen-Platzhalter),
`ChestPort` (Inventar lesen, entnehmen, ablegen), `NavigationPort` (anlaufen,
Fortschritt, Abbruch), `JournalPort` (Transportzustand sichern), `Clock`,
`LogPort`.

**Alternative: MockBukkit für Tests.** Verworfen. MockBukkit bildet Bukkit-Interna
nach, deckt Citizens aber nicht ab. Der Kern würde trotzdem an Bukkit-Typen hängen
und die Zustandsmaschine bliebe untestbar.

**Alternative: Alles in Bukkit-Klassen, nur Integrationstests.** Verworfen. Der
Rückmeldetakt wäre ein vollständiger Servertest pro Änderung, was den Zyklus
unbrauchbar lang macht.

### D2: Item-Identität als eigener Schlüssel

Der alte Code verglich `Material` und verlor damit Enchantments, Namen und
CustomModelData. Der Kern arbeitet mit `ItemKey`, bestehend aus `typeId` (dem
Material-Schlüssel, etwa `minecraft:iron_ore`) und `variantKey` (stabile Kennung
der Item-Metadaten, die der Adapter aus Enchantments, Anzeigename,
CustomModelData und persistenten Datacontainern bildet).

Kategorien ordnen über `typeId` zu. Das Auffüllen bestehender Stapel vergleicht
den vollständigen `ItemKey`. Ein Item gilt nur dann als identisch mit einem
vorhandenen Stapel, wenn beide Teile übereinstimmen.

**Alternative: Nur `Material` wie bisher.** Verworfen, weil verzauberte Werkzeuge
sonst in einem Stapel mit unverzauberten landen würden.

**Alternative: `ItemStack.isSimilar()` direkt im Kern.** Verworfen, weil der Kern
dann Bukkit-Typen kennt.

### D3: Transportzustand als Journal auf Platte, geschrieben vor der Entnahme

Ablauf je Entnahmevorgang: (1) Ziel und Menge bestimmen, (2) Journal-Eintrag
`PENDING` mit `ItemKey`, Menge, Quelltruhe, Zieltruhe, Zeitpunkt schreiben und auf
die Platte zwingen, (3) Items entnehmen, (4) Journal auf `CARRIED` setzen,
(5) Items ablegen, (6) Journal auf `DELIVERED` setzen und Eintrag löschen.

Beim Start liest der Sorter offene Einträge. `CARRIED` bedeutet: Items sind
unterwegs und werden zurückgeführt oder zugestellt. `PENDING` bedeutet: Der
Absturz lag zwischen Journal und Entnahme. Da (2) vor (3) liegt, ist die Truhe in
diesem Fall unangetastet, der Eintrag wird verworfen.

Das Journal liegt pro Sorter in `plugins/ChestManager/journal/<uuid>.yml`.

**Alternative: Transportzustand in `@Persist`-Feldern des Traits.** Verworfen.
Citizens speichert Traits periodisch, nicht atomar mit der Entnahme. Ein Absturz
dazwischen verliert Items.

**Alternative: Transportzustand in einem persistenten Datacontainer am NPC.**
Verworfen. NPC-Entities verschwinden beim Despawn, und die Grösse ist begrenzt.

**Alternative: Kein Zwischenlager, Item sofort zurücklegen.** Verworfen. Die
Zieltruhe ist erst nach dem Anlaufen erreichbar, ein Vorab-Ablegen wäre unmöglich.

### D4: Expliziter Zustandsautomat mit Deadline je Zustand

`SorterState` ist ein Aufzählungstyp mit genau definierten Übergängen:
`IDLE → SCANNING → SEEK_SOURCE → APPROACH_SOURCE → PICKUP → SEEK_TARGET →
APPROACH_TARGET → DELIVER → (IDLE | RECOVER)`. Jeder Zustand trägt eine Deadline,
die der Automat gegen die injizierte `Clock` prüft. Läuft sie ab, wechselt er in
`RECOVER`.

**Alternative: Verschachtelte Bedingungen wie im Bestand.** Verworfen. Genau daraus
entstand der Fehler, dass bei fehlgeschlagener Navigation trotzdem auf ein Inventar
zugegriffen wurde.

### D5: Reichweite als harte Vorbedingung, nicht Navigation als Beleg

Vor jedem Inventarzugriff prüft der Kern zwei Bedingungen: Der Sorter steht
innerhalb von `reach` Blöcken zum Truhenblock, und der Zugangspunkt dieser Truhe
ist als erreichbar bekannt. `NavigationPort.isNavigating()` wird nur als
Fortschrittssignal verwendet, nie als Erfolgsnachweis.

**Alternative: Auf `Navigator.isNavigating() == false` vertrauen wie bisher.**
Verworfen. Citizens setzt das Flag auch nach fehlgeschlagenem Pfad auf `false`.

**Alternative: Zusätzlich Sichtlinie per Raytrace prüfen.** Für diese Version
verworfen. Truhen in Regalwänden werden von Nachbarblöcken verdeckt, eine
Sichtlinienprüfung würde korrekte Anlagen fälschlich ablehnen.

### D6: Zugangspunkt je Truhe statt Truhenblock als Navigationsziel

Der alte Code lief auf den Block über der Truhe. Der `AccessPointResolver` prüft
die sechs Nachbarluken und den Block darüber, verwirft blockierte, gefährliche
(Lava, Feuer, Kaktus) und unpassierbare Positionen und wählt daraus den nächsten
Kandidaten zum Sorter. Findet er keinen, gilt die Truhe als unerreichbar und
wandert in die Sperrliste.

**Alternative: Immer den Block über der Truhe anlaufen.** Verworfen. Bei Truhen
an Wänden, unter Decken und in Regalwänden ist dieser Block belegt.

**Erkenntnis aus dem Container-Test:** Ein Schild hat keine Kollisionsbox und gilt
als passabel. Steht das Rollenschild auf der Truhe und ist die Luft darüber frei,
ist der Schildblock ein gültiger Standort; der Sorter steigt auf die Truhe und
erreicht sie. Eine Truhe ist erst dann unerreichbar, wenn über dem Schild
ebenfalls ein Block liegt. Der Zugangstest prüft deshalb zusätzlich den Block über
dem Kandidaten, und die Tests bilden beide Fälle ab.

### D7: Arbeitsteilung über Ticks mit festem Budget, ein Scheduler für alle Sorter

Ein einziger `BukkitRunnable` läuft im konfigurierten Takt und gibt jedem
registrierten Sorter ein Kontingent an Operationen pro Durchlauf. Der Scheduler
misst die verstrichene Zeit und bricht den Durchlauf ab, wenn das Budget
überschritten wird; der Rest wartet auf den nächsten Takt.

**Alternative: `Trait.run()` je NPC arbeiten lassen.** Verworfen. Bei mehreren
Sortoren wächst die Last unkontrolliert, und es gibt keine zentrale Drossel.

### D8: Bereich als Graphkomponente, nicht als Quader

Die Ermittlung läuft zweistufig: Zuerst sammelt ein budgetierter Blockdurchlauf
alle Truhenblöcke im konfigurierten Suchquader. Danach baut der Kern einen Graphen,
in dem zwei Truhen verbunden sind, wenn ihr Abstand höchstens `linkDistance`
beträgt. Der Bereich ist die Zusammenhangskomponente des Ankerblocks.

`linkDistance` erlaubt den Betrieb zweier Anlagen: `0` für lückenlos aneinander
gebaute Truhenwände, grössere Werte für Truhen, die mit Abstand im Raum stehen.

**Alternative: Flood-Fill über direkte Blocknachbarschaft.** Verworfen. Deckt nur
aneinander gebaute Truhen ab; verstreut stehende Truppen im Raum fielen heraus,
obwohl der Betreiber sie als eine Anlage versteht.

**Alternative: Konfigurierte Eckpunkte.** Verworfen. Der Betreiber müsste
Koordinaten von Hand pflegen, was die Anforderung „erkannter Bereich" verfehlt.

### D9: Chunks über Plugin-Chunk-Tickets halten

Beim erfolgreichen Scan setzt der Bukkit-Adapter für jeden Chunk mit einer
Truhe des Bereichs ein Plugin-Chunk-Ticket und entfernt es beim Anhalten, beim
Entfernen des Sorters und beim Deaktivieren des Plugins. Die Zahl der Tickets ist
durch die Höchstzahl an Truhen begrenzt.

**Alternative: `Chunk.load()` wie im Bestand.** Verworfen. Ohne Ticket entlädt der
Server den Chunk sofort wieder, wodurch Truhen sporadisch unerreichbar wurden.

### D10: Zeit und Zufall injizierbar

Der Kern liest Zeit ausschliesslich über den `Clock`-Port. Damit sind Zeitlimits,
Wiederholungsabstände und Sperrdauern in Tests ohne Warten prüfbar.

### D11: Verifikation gegen einen echten Spigot-Container per RCON

Der Integrationstest läuft gegen einen Spigot-Container mit Citizens. Steuerung
per RCON: Testanlage mit `/setblock`, `/data merge block` für Schildtexte und
`/item replace block … container.N` für Truheninhalte aufbauen, dann Sorter per
`/cm` steuern und die Truheninhalte über dieselben Befehle zurücklesen. Die
Erwartung wird aus dem Testskript geprüft, nicht per Sichtprüfung.

**Alternative: MockBukkit-Integrationstest.** Verworfen, siehe D1.

**Alternative: Testanlage per Produktionsbefehl im Plugin aufbauen.** Verworfen.
Testhilfen gehören nicht in den Auslieferungscode.

## Risks / Trade-offs

- **Navigationsverhalten von Citizens ist versionsabhängig** → Reichweitenprüfung,
  Zeitlimit und Sperrliste machen Abweichungen sichtbar, ohne Items zu gefährden.
- **Villager-NPCs scheitern an engen Durchgängen** → Zugangspunkt-Auflösung wählt
  begehbare Positionen; bleibt eine Truhe dauerhaft gesperrt, meldet der Selbsttest
  sie als Fehler.
- **Journal-Schreibvorgang kostet Main-Thread-Zeit** → ein Schreibvorgang je
  Entnahme, nicht je Item. Die Dauer wird gemessen und im Status ausgewiesen.
- **Zwei Journal-Einträge bei mehreren Zielen gleichzeitig** → der Sorter fährt
  genau ein Ziel je Fahrt an; die Tragemenge ist auf eine Doppeltruhe begrenzt.
- **Ein Sorter je Truhe ist nur über den Bereich prüfbar** → überlappende Bereiche
  werden beim Scan erkannt und gemeldet; die Laufzeitsperre verhindert parallelen
  Zugriff auf dieselbe Truhe.
- **Flood-Fill über einen zu gross gewählten Suchquader wird teuer** → Suchquader
  und Truhenhöchstzahl sind begrenzt, der Scan ist budgetiert und abbrechbar.

## Migration Plan

1. Alten Code des Moduls entfernen, neue Paketstruktur anlegen.
2. `config.yml.example` ins Modul legen; `config.yml` bleibt per `.gitignore`
   ausgeschlossen.
3. Unit-Tests für den Kern schreiben und grün bekommen.
4. Gegen den Spigot-Container bauen und dort verifizieren.
5. Ausrollen: alte Sorter-NPCs löschen, `plugins/ChestManager/config.yml` aus der
   Beispielkonfiguration erzeugen, Sorter neu anlegen, `/cm selftest` prüfen.
6. **Rollback**: Das alte JAR zurücklegen und die alten NPCs neu anlegen. Das
   Plugin schreibt ausserhalb seines Datenordners nichts, Truheninhalte werden
   nicht strukturell verändert; ein Rückbau berührt nur NPCs und die
   Plugin-Konfiguration.

## Open Questions

- Ob `TRAPPED_CHEST` eigene Zugangspunkte braucht oder wie `CHEST` behandelt werden
  kann, zeigt der Container-Test. Die Specs unterscheiden beide nicht.
- Ob die Tragemenge dauerhaft bei einer Doppeltruhe bleiben muss oder mit
  Shulker-Unterstützung steigen darf, ist ohne Shulker-Unterstützung irrelevant.

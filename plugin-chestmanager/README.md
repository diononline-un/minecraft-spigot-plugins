# ChestManager

Citizens-NPC, der Truheninhalte nach Kategorien sortiert. Das Prinzip ist das eines
Copper Golems: **jede markierte Truhe ist Quelle und Ziel.** Der NPC läuft den
erkannten Bereich ab, entnimmt Items, die dort nicht hingehören, und bringt sie in
die passende Truhe. Falsch einsortierte Bestände korrigieren sich dadurch selbst.
Der Sorter ist stumm.

## Voraussetzungen

| Was | Anforderung |
|---|---|
| Server | Spigot oder Paper, `api-version` 1.21 oder neuer |
| **Citizens** | **Pflicht.** Ohne Citizens deaktiviert sich das Plugin. |
| Java | 21 zum Bauen; Paper 26.2 verlangt Java 25 als Laufzeit |

**Das Plugin ist nicht standalone.** Es hat in `plugin.yml` ein `depend: [Citizens]`
und spricht die Citizens-API direkt an (`CitizensAPI`, `NPC`, `Trait`, `TraitInfo`,
`Persist`, `TraitName`). Der NPC ist ein Citizens-NPC und wird von Citizens
gespeichert und gespawnt. Weitere Plugins braucht es nicht — kein Vault, keine
Wirtschaft, keine Welteditoren.

## Installation

1. **Citizens installieren.** Das verteilbare Plugin-JAR nehmen, nicht das
   Maven-Artefakt `citizens-main`: dem fehlen die plattformspezifischen NMS-Module,
   es scheitert beim Start mit `SpigotUtil.getMinecraftPackage`. Die passende
   Version hängt an der Server-Version — Citizens 2.0.35 startet auf Paper 26.2
   nicht, 2.0.43-SNAPSHOT läuft dort (verifiziert am 2026-09-11).
   Distributions-JAR:
   `https://ci.citizensnpcs.co/job/Citizens2/lastSuccessfulBuild/artifact/dist/target/`
2. **ChestManager bauen und kopieren.**
   ```bash
   ./build.sh package -pl plugin-chestmanager
   cp plugin-chestmanager/target/chestmanager-plugin-*.jar <server>/plugins/
   ```
3. **Server starten.** Beim ersten Start legt das Plugin
   `plugins/ChestManager/config.yml` aus der mitgelieferten Vorlage an. Die
   Kategorien darin an die eigene Welt anpassen, dann `/cm reload`.

Die `2.0.35-SNAPSHOT` im Parent-POM ist nur die **Compile-API**, keine
Laufzeitanforderung. Das Plugin nutzt ausschließlich Citizens-API, die in 2.0.35 und
2.0.43 gleich ist; gebaut gegen 2.0.35, im Container gegen 2.0.43 betrieben.

## Einrichtung im Spiel

1. **Truhen markieren.** Ein Schild auf den Block über der Truhe oder daneben. Die
   erste Zeile entscheidet:

   | Schild | Rolle |
   |---|---|
   | `[INPUT]` | Ankertruhe. Genau eine, sie legt den Bereich fest und wirkt als Eingangspuffer. |
   | `[ORES]` `[FOOD]` … | Zieltruhe der gleichnamigen Kategorie aus `config.yml` |
   | `[UNSORTABLE]` | Auffangtruhe für Items, deren Typ in keiner Kategorie steht |
   | `[IGNORE]` | Truhe im Bereich, die der Sorter nicht anfasst |

   Der Bereich ist der Truhenverbund um den Anker. Truhen ohne Schild gehören zum
   Verbund, haben aber keine Rolle und werden nicht angefasst.

2. **Sorter anlegen und prüfen.**
   ```
   /cm create Sorter
   /cm scan Sorter
   /cm selftest Sorter
   ```

3. **Fertig.** Der Sorter arbeitet selbstständig weiter und nimmt nach einem
   Serverneustart den Betrieb ohne Zutun wieder auf.

## Befehle

| Befehl | Wirkung |
|---|---|
| `/cm create <Name> [x y z]` | Sorter anlegen; Koordinaten für die Konsole |
| `/cm delete <Name>` | Sorter entfernen, getragene Items gehen zurück |
| `/cm home [Name]` | Heimatpunkt auf die eigene Position setzen |
| `/cm scan [Name]` | Bereich neu ermitteln |
| `/cm status [Name]` | Zustand, Truhen je Rolle, Sperren, Scan-Dauer |
| `/cm selftest [Name]` | Einrichtung prüfen, ohne Items zu bewegen |
| `/cm sortnow [Name]` | sofort sortieren |
| `/cm pause` / `resume [Name]` | anhalten und fortsetzen |
| `/cm blocked [Name]` | gesperrte Truhen einsehen |
| `/cm unblock <Name> [x y z]` | Sperren aufheben |
| `/cm log [Name] [Item]` | Transportprotokoll |
| `/cm list` | alle Sorter |
| `/cm reload` | `config.yml` neu laden |
| `/cm setup` | Anleitung mit allen Schildnamen |

Permissions: `chestmanager.admin` (Verwaltung), `chestmanager.operate` (Betrieb),
`chestmanager.view` (Einsicht). Alle standardmäßig für Operatoren.

## Konfiguration

`plugins/ChestManager/config.yml`, Vorlage unter
`src/main/resources/config.yml.example`. Aufbau:

- `categories.<name>.display` – Anzeigename
- `categories.<name>.icon` – Materialname für Übersichten
- `categories.<name>.items` – Liste von Materialnamen
- `sorter.*` – Suchradius, Reichweite, Zeitlimits, Tragemenge, Takt

Unbekannte Materialnamen, doppelt vergebene Items und Kategorien ohne Zieltruhe
meldet `/cm reload` mit Dateiposition.

Sinnvolle erste Anpassungen:

- `sorter.search-radius` – wenn die Anlage größer ist als 48 Blöcke im Quadrat
- `sorter.link-distance` – `0` für lückenlos gebaute Truhenwände, `1` wenn zwischen
  den Truhen ein Block Luft liegt
- `sorter.carry-limit` – wie viele Items der NPC je Fahrt trägt, höchstens 54

## Wenn etwas nicht läuft

| Symptom | Ursache |
|---|---|
| Plugin lädt nicht, „Citizens fehlt" | Citizens nicht installiert oder nicht aktiviert |
| „Keine Ankertruhe gefunden" | Kein Schild mit `[INPUT]`, oder die Chunks sind nicht geladen |
| „Bereich wird ermittelt (0 %)" und steht | Heimat-Chunk nicht geladen; der Sorter wartet und versucht es erneut |
| Truhe in der Sperrliste | Kein begehbarer Zugang. Freie Luke neben der Truhe schaffen, dann `/cm unblock` |
| Items bleiben liegen | Kategorie voll oder ohne Zieltruhe. `/cm log` und `/cm selftest` zeigen welche |
| Nichts passiert nach `/cm create` | NPC nicht gespawnt oder Heimat-Chunk nicht geladen |

Kein Item geht verloren: Vor jeder Entnahme schreibt der Sorter einen
Journaleintrag nach `plugins/ChestManager/journal/<npc-uuid>.yml`. Nach einem
Absturz führt er offene Vorgänge beim nächsten Start zurück.

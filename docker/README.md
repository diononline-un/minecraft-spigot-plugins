> Testserver für ChestManager.
>
> ```bash
> docker compose -f docker/docker-compose.yml up -d
> docker compose -f docker/docker-compose.yml logs -f
> ```
>
> Steuerung läuft über RCON auf Port 25575 (Passwort in `server/server.properties`).
>
> ```bash
> ./docker/run-all-tests.sh              # alle vier nacheinander, mit Reset
> python3 docker/test-sorter.py          # Ende-zu-Ende: sortiert er richtig?
> python3 docker/test-crash-recovery.py  # harter Kill, kein Item-Verlust
> python3 docker/test-edge-cases.py      # Sperrliste, Scan-Zeit, geordnetes Anhalten
> python3 docker/diagnose-sealed.py      # eingemauerte Truhe erkennen
> ```
>
> `./docker/reset-server.sh [--plugins]` setzt den Server auf einen sauberen Stand
> zurück. Nötig, weil Paper Weltverzeichnisse aus älteren Läufen migriert und dabei
> abbrechen kann.
>
> Der Server liegt unter `docker/server/` und ist per `.gitignore` ausgeschlossen.

# Testserver

Paper 26.2 mit Citizens für die Verifikation des Sorters. Die Testwelt wird flach
generiert; die Testanlage baut das Skript zur Laufzeit auf.

## Stolpersteine

- **Java 25 nötig.** Paper 26.2 verweigert ältere Laufzeiten. Das Plugin selbst
  wird weiter mit Java 21 gebaut.
- **Citizens vom Jenkins nehmen.** Das Maven-Artefakt `citizens-main` ist nur die
  API plus Hauptklassen; ihm fehlen die NMS-Module und es scheitert beim Start mit
  `SpigotUtil.getMinecraftPackage`. Version 2.0.35 startet auf 26.2 gar nicht,
  funktionsfähig ist 2.0.43-SNAPSHOT. `reset-server.sh --plugins` lädt die richtige.
- **Ohne Spieler sind keine Chunks geladen.** `/setblock` wirkt dann nicht, und der
  Sorter findet nichts. Die Skripte setzen deshalb `forceload`; der Sorter hält
  seine Bereichschunks anschließend selbst über Plugin-Chunk-Tickets.
- **`execute if … run say` liefert über RCON keine Ausgabe.** Für Prüfungen
  `execute if block <pos> <material>` ohne `run` verwenden, das antwortet mit
  „Test passed"/„Test failed".
- **Paper kürzt lange `data get`-Antworten.** Ein volles Truheninventar kommt
  abgeschnitten an. Inventare slotweise über `Items[{Slot:Nb}]` auslesen, sonst
  zählt man stillschweigend zu wenig.
- **Ein Schild ist passabel.** Steht das Rollenschild auf der Truhe und ist die Luft
  darüber frei, klettert der NPC auf die Truhe und erreicht sie. Wirklich
  unerreichbar ist eine Truhe erst mit einem Block über dem Schild.

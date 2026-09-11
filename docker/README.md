> Testserver für ChestManager.
>
> ```bash
> docker compose -f docker/docker-compose.yml up -d
> docker compose -f docker/docker-compose.yml logs -f
> ```
>
> Steuerung läuft über RCON auf Port 25575 (Passwort in `server/server.properties`).
> Das Testskript `docker/test-sorter.py` baut eine Testanlage auf, lässt den Sorter
> laufen und prüft die Truheninhalte.
>
> ```bash
> python3 docker/test-sorter.py
> ```
>
> Der Server liegt unter `docker/server/` und ist per `.gitignore` ausgeschlossen.

# Testserver

Paper mit Citizens für die Verifikation des Sorters. Die Testwelt wird flach
generiert; die Testanlage baut das Skript zur Laufzeit auf.

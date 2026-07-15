# Minecraft Spigot Plugins

Multi-Modul Maven-Projekt für alle Diono-Minecraft-Spigot-Plugins.

## Spigot-Version

| Property      | Wert                     |
|---------------|--------------------------|
| Spigot API    | `1.21.4-R0.1-SNAPSHOT`   |
| Java          | 21                       |

Diese Version wird **zentral** in der `pom.xml` unter `<spigot.version>` gepflegt.  
Alle Plugin-Module erben sie über das Parent-POM.

## Projektstruktur

```
minecraft-spigot-plugins/
├── pom.xml                   ← Parent-POM (enthält Spigot-Version)
├── VERSION                   ← Dokumentierte Version
├── compat-check.sh           ← Kompatibilitäts-Checker
├── update-version.sh         ← Version-Update mit Auto-Check
├── plugin-template/          ← Vorlage für neue Plugins
├── plugin-X/                 ← Deine Plugins (als Module registrieren)
└── plugin-Y/
```

## Neues Plugin anlegen

```bash
cp -r plugin-template plugin-meinplugin

# pom.xml im neuen Ordner anpassen:
#   <artifactId>mein-plugin</artifactId>

# In der Root-pom.xml das Modul registrieren:
#   <module>plugin-meinplugin</module>

mvn clean package
```

## Spigot-Version updaten

```bash
./update-version.sh 1.22-R0.1-SNAPSHOT
```

Das Skript macht automatisch:
1. Versions-Property in `pom.xml` aktualisieren
2. `VERSION`-Datei aktualisieren
3. Alle Module mit `-Xlint:deprecation` kompilieren
4. Diff zur Baseline – neue Deprecations / Fehler = Breaking Changes
5. Wenn sauber: Baseline-Snapshot updaten

## Kompatibilitäts-Check manuell

```bash
./compat-check.sh              # Check: Neue Warnings/Errors seit Baseline?
./compat-check.sh --diff       # Diff: Was hat sich geändert?
./compat-check.sh --baseline   # Baseline neu setzen (nach Fixes)
```

Das System speichert einen Snapshot aller deprecation-Warnings. Bei jedem Check
wird der aktuelle Build mit dem Baseline-Snapshot verglichen. Neue Warnings
oder Kompilierungsfehler werden direkt als **potenzielle Breaking Changes**
ausgewiesen.

## Konventionen

- **api-version im `plugin.yml`** immer zur verwendeten Spigot-API-version passend setzen
- Packages: `me.diono.<plugin-name>`
- `@Deprecated`-Annotationen im eigenen Code vermeiden
- Bei Spigot-API-Änderungen: `compat-check.sh --diff` prüfen ob die eigene Codebase betroffen ist

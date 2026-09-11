# AGENTS.md – Coding Agent Instructions

> **Sprache:** Der User kommuniziert auf Deutsch. Code, Kommentare und
> API-Referenzen sind auf Englisch. Antworten des Agents auf Deutsch.

## Projektübersicht

Maven Multi-Modul für Spigot-Minecraft-Plugins (Server: Spigot 26.2).
Jedes Plugin ist ein eigenes Maven-Modul unterhalb dieses Verzeichnisses.

### Verzeichnisstruktur

```
minecraft-spigot-plugins/
├── pom.xml              ← Parent-POM: Spigot-Version, Dependency Mgmt
├── VERSION              ← Dokumentierte Spigot-Version (maschinenlesbar)
├── AGENTS.md            ← Diese Datei
├── compat-check.sh      ← Breaking-Change-Detektor
├── update-version.sh    ← Versionsupdate + Auto-Check
├── plugin-template/     ← Kopiervorlage für neue Plugins
└── plugin-*/            ← Konkrete Plugins (jedes mit eigener pom.xml)
```

### Wichtigste Datei

`pom.xml` – die **einzige** Stelle, an der die Spigot-Version definiert ist.
Property: `<spigot.version>`. Alle Module erben sie. NIEMALS duplizieren.

---

## Build & Check Commands

```bash
# Komplettes Projekt bauen
mvn clean package -f pom.xml

# Einzelnes Modul bauen
mvn clean package -pl plugin-<name>

# Nur kompilieren (schneller Check)
mvn clean compile -f pom.xml

# Kompatibilitäts-Check (Baseline vs. aktueller Build)
./compat-check.sh

# Baseline neu setzen (nach Fixes / Initial nach Clone)
./compat-check.sh --baseline

# Diff anzeigen (was hat sich seit Baseline geändert?)
./compat-check.sh --diff

# Spigot-Version updaten (macht alles automatisch)
./update-version.sh <NEUE_VERSION>
```

Nach jedem Clone: **erst `./compat-check.sh --baseline` ausführen**, sonst
hat der Check keine Vergleichsbasis.

---

## Neues Plugin anlegen

```bash
cp -r plugin-template plugin-<name>
```

Dann **drei Änderungen**:

1. **`plugin-<name>/pom.xml`** – `<artifactId>` anpassen
2. **Root-`pom.xml`** – `<module>plugin-<name></module>` in `<modules>` eintragen
3. **`plugin-<name>/src/main/resources/plugin.yml`** – Name, Main-Class, api-version setzen

Main-Class-Package: `me.diono.<pluginname>.<PluginName>`

---

## Coding Conventions

### Allgemein
- **Java 21**, Spigot API als `provided`-Dependency
- Package-Basis: `me.diono`
- Keine Wildcard-Imports (vermeiden, außer für Bukkit-Enums wie `org.bukkit.Material.*`)
- Logger über `getLogger()` (von `JavaPlugin`), nicht über statischen Logger oder `System.out`
- Commands in `plugin.yml` registrieren, nicht nur im Code
- Listener in `plugin.yml` NICHT registrieren – immer via `getServer().getPluginManager().registerEvents()`

### Spigot-spezifische Best Practices

**1. `onEnable` / `onDisable` – sauber halten**
```java
@Override
public void onEnable() {
    // 1. Config laden
    saveDefaultConfig();
    // 2. Listener registrieren
    getServer().getPluginManager().registerEvents(new MyListener(this), this);
    // 3. Commands registrieren (wenn dynamisch)
    // 4. Timer/Tasks starten
}

@Override
public void onDisable() {
    // Ressourcen freigeben, Tasks canceln
    getServer().getScheduler().cancelTasks(this);
}
```

**2. Asynchrone Operationen**
- NIEMALS die Bukkit-API vom Async-Thread aus aufrufen
- Datenbank/HTTP/IO → async (`runTaskAsynchronously`)
- Bukkit-API-Aufrufe → sync (`runTask` oder `new BukkitRunnable`)
- `getServer().getScheduler()` statt `new Thread()` verwenden

**3. Config-Handling**
- `saveDefaultConfig()` in `onEnable()` aufrufen
- `reloadConfig()` nutzen, nie Config neu aus Datei parsen
- Config-Werte cachen, nicht jedes Mal aus `getConfig()` lesen

**4. Events**
- `@EventHandler` immer mit `public void` und einem Event-Parameter
- `ignoreCancelled = true` bei physikalischen Events (BlockBreak, EntityDamage etc.) wenn du gecancelte nicht brauchst
- `EventPriority` bewusst setzen: `LOWEST` → `LOW` → `NORMAL` → `HIGH` → `HIGHEST` → `MONITOR`
- `MONITOR`-Priority: NIEMALS den Event-State ändern, nur lesen/loggen

**5. Commands**
- `onCommand` returned `true` bei Erfolg, `false` zeigt usage an
- TabCompleter separat implementieren (`implements TabCompleter`)
- Permissions in `plugin.yml` definieren, dann mit `sender.hasPermission()` prüfen

**6. ItemStacks & Inventories**
- `ItemStack` nie mit `new ItemStack(Material.AIR)` erstellen, immer `null` für leere Slots
- Inventories mit `Bukkit.createInventory()` statt `new Inventory()` erzeugen
- `InventoryHolder` implementieren für Inventory-Click-Handling

**7. Serialisierung / Persistenz**
- Entities/Items per UUID referenzieren, nicht per EntityId (ändert sich bei Restart)
- Locations als `world;x;y;z;yaw;pitch` serialisieren (World per Name, nicht UID)
- YAML für Configs, JSON nur wenn nötig

**8. Performance-kritische Stellen**
- `PlayerMoveEvent` hat extreme Frequenz → früh filtern (nur auf Block-Change prüfen)
- Loops über `getOnlinePlayers()` cachen via `List.copyOf()` oder `new ArrayList<>()`
- `Material`-Vergleiche nie mit `name()` als String, immer `== Material.ENUM_VALUE`

**9. API-Version in plugin.yml**
```yaml
api-version: "1.21"
```
Das schützt vor Breaking Changes in der Bukkit-API. NIEMALS weglassen.

---

## Fehler vermeiden: Häufige Fallen

| Falle | Richtig |
|-------|---------|
| `new ItemStack(Material.AIR)` | `null` verwenden |
| `player.getInventory().addItem(item)` ohne Check | `addItem` returned `HashMap<Integer, ItemStack>` mit Rest |
| `Bukkit.getWorld("world")` ohne Null-Check | World kann `null` sein (entladen) |
| `entity.getLocation().getBlock()` async | `Location` ist nicht thread-safe, sync ausführen |
| `plugin.yml` ohne `api-version` | Immer setzen – verhindert stille API-Änderungen |
| `getConfig().getString()` ohne Default | `getConfig().getString("key", "default")` |
| Plugin-Code im Konstruktor | Alles in `onEnable()` – vorher existiert kein Plugin-Kontext |

---

## Dependency Management

Braucht ein Plugin externe Libraries, diese als Dependency mit `compile`-Scope
einbinden und das `maven-shade-plugin` nutzen (ist im Parent-POM vorkonfiguriert,
muss nur im Modul-POM per `<plugin>` referenziert werden).

Shading-Regeln:
- **Immer** relocaten: `<relocations>` im shade-plugin konfigurieren
- Klassennamen-Präfix: `me.diono.<plugin>.lib`
- Niemals Spigot/Bukkit-Klassen mit-shaden (die sind `provided`)

---

## Versionsupdates

### Workflow für Spigot-Update:
```bash
./update-version.sh 1.XX-R0.1-SNAPSHOT
```
Das Skript:
1. Aktualisiert `<spigot.version>` in der Root-`pom.xml`
2. Aktualisiert die `VERSION`-Datei
3. Kompiliert alle Module mit `-Xlint:deprecation`
4. Vergleicht Warnings/Errors mit dem Baseline-Snapshot
5. **Bricht ab bei neuen Deprecation-Warnings oder Kompilierungsfehlern**

Neue Deprecations aus der Diff-Liste manuell beheben, dann:
```bash
./compat-check.sh --baseline
```

### Manueller Check ohne Update:
```bash
./compat-check.sh --diff
```
Zeigt exakt, welche Deprecations neu hinzugekommen oder verschwunden sind.

---

## Git-Workflow

- Feature-Branches von `main`
- Commit-Messages auf Englisch, beschreibend
- Keine `target/`-Verzeichnisse committen (`.gitignore` ist konfiguriert)
- Keine Secrets oder Token in Config-Dateien

### GitHub-Regeln (für ALLE Projekte)

- **ALLE Repos MÜSSEN `--private` sein** – nie `--public`
- Repo-Erstellung: `gh repo create diono/<name> --private --source=. --remote=origin --push`
- `config.yml` nie committen (`.gitignore` schützt)
- API-Keys, Tokens, Secrets → NIE im Git, NIE in Commit-Messages
- `.example`-Files für Templates nutzen (`config.yml.example`)
- Wenn ein Repo schon online existiert: Lokalen Stand mit Remote vergleichen, neueren behalten

---

## Prüfliste vor jedem Commit

- [ ] `mvn clean compile` läuft ohne Fehler
- [ ] `plugin.yml` hat korrekte `api-version`
- [ ] Keine `System.out.println` (→ `getLogger()`)
- [ ] Keine Async-Bukkit-API-Aufrufe
- [ ] Config hat `saveDefaultConfig()` in `onEnable()`
- [ ] Commands haben TabCompleter
- [ ] Keine Wildcard-Imports außer für org.bukkit.* Enums

---

## Plugin-Referenz

### AdventureLog (`plugin-adventurelog/`)
Announces player highlights in chat. Pure event listeners, no config.
- Kills, ore mining, harvests, breeding, biome changes, structure discoveries
- Anti-spam: bundles repeated events with 5s cooldown per sub-type

### WorkerNPCs (`plugin-workernpcs/`)
AI-driven citizen NPCs with DeepSeek integration. Requires **Citizens2**.
- `/worker create/delete <name>` – Manage NPCs
- `@Name <message>` in chat – Talk to NPC via DeepSeek AI
- `/worker task <name> follow|farm|breed|gohome|build|idle` – Assign tasks
- `/worker home [name]` – Set NPC home location
- `/worker inventory [name]` – Open NPC inventory (27 slots)
- `/worker teach/forget/memory` – NPC persistent memory system
- NPCs auto-remember farm locations, animal pens, ore spots, build sites
- Config: `plugins/WorkerNPCs/config.yml` (API key, max NPCs)

### ChestManager (`plugin-chestmanager/`)
Citizens-NPC, der Truheninhalte nach Kategorien sortiert (Copper-Golem-Prinzip: jede
markierte Truhe ist Quelle und Ziel). Requires **Citizens2**.
- Architektur: Bukkit-freier Kern unter `me.diono.chestmanager.core` hinter Ports
  (`WorldPort`, `ChestPort`, `CarryPort`, `NavigationPort`, `JournalPort`, `Clock`,
  `LogPort`, `FeedbackPort`); Adapter unter `me.diono.chestmanager.bukkit`.
  Der Kern ist ohne Server unit-testbar und darf keine Bukkit-Typen kennen.
- `/cm create <Name> [x y z]` – Sorter anlegen (Koordinaten für die Konsole)
- `/cm scan|status|selftest [Name]` – Bereich ermitteln, Zustand, Einrichtung prüfen
- `/cm sortnow|pause|resume|unblock [Name]` – Betrieb
- `/cm list|blocked|log [Name] [Item]` – Einsicht
- `/cm reload` – `config.yml` neu laden, meldet fehlerhafte Einträge mit Position
- `/cm setup` – Anleitung mit allen Schildnamen
- Schilder (erste Zeile, an oder über der Truhe): `[INPUT]` Anker, `[<KATEGORIE>]`
  Zieltruhe, `[UNSORTABLE]` Auffangtruhe, `[IGNORE]` nicht anfassen
- Item-Sicherheit: Vor jeder Entnahme schreibt der Sorter einen Journaleintrag nach
  `plugins/ChestManager/journal/<npc-uuid>.yml`. Getragene Items liegen nie nur im
  Speicher. Diese Reihenfolge nicht auflösen.
- Sorter arbeiten stumm (`setSilent(true)`, keine `playSound`-Aufrufe);
  `NoSoundContractTest` bricht den Build, wenn ein Klangaufruf hinzukommt.
- Testserver: `docker/` (Paper + Citizens + RCON), Tests siehe `docker/README.md`

### Citizens2 Integration Pattern
```xml
<repository>
    <id>citizens-repo</id>
    <url>https://maven.citizensnpcs.co/repo</url>
</repository>
<dependency>
    <groupId>net.citizensnpcs</groupId>
    <artifactId>citizens-main</artifactId>
    <version>2.0.35-SNAPSHOT</version>
    <scope>provided</scope>
    <exclusions><exclusion><groupId>*</groupId><artifactId>*</artifactId></exclusion></exclusions>
</dependency>
```
- `@TraitName("name")` + `extends Trait`
- `@Persist("key")` für persistent fields (Autosave)
- `run()` wird jeden Tick aufgerufen → Tasks nur alle N Ticks ausführen
- `onAttach()` / `onSpawn()` / `onRemove()` / `onDespawn()` Lifecycle. Es gibt **kein**
  `onServerStart()`; wer es aufruft, ruft eine eigene Methode, die Citizens nie aufruft.
- `getTraitNullable(Class)` statt `getTrait(Class)`, wenn der Trait fehlen darf

### Citizens-Version und Server-Kompatibilität
Das Artefakt `citizens-main` aus dem Maven-Repo ist **nicht** das verteilbare Plugin;
ihm fehlen die plattformspezifischen NMS-Module, es scheitert beim Start mit
`SpigotUtil.getMinecraftPackage`. Als Server-Plugin das Distributions-JAR vom
Jenkins nutzen:
`https://ci.citizensnpcs.co/job/Citizens2/lastSuccessfulBuild/artifact/dist/target/`
- Citizens 2.0.35 (Okt 2024) startet auf Spigot/Paper 26.2 nicht.
- Citizens 2.0.43-SNAPSHOT (Build 4249) läuft auf Paper 26.2. Verifiziert 2026-09-11.
- Paper 26.2 verlangt **Java 25** als Laufzeit; gebaut wird weiter mit Java 21.

---

## Gelernte Lektionen / Häufige Stolpersteine

### Resource-Filtering MUSS in jeder Modul-pom.xml aktiv sein

**Problem:** `plugin.yml` enthält `${project.name}` und `${project.version}`.
Ohne `<filtering>true</filtering>` bleiben diese Platzhalter unaufgelöst im JAR
und das Plugin lädt mit dem rohen Platzhalter-Text als Name/Version (z.B.
`${project.name}` statt `AdventureLog`).

**Fix:** In jeder Modul-pom.xml unter `<build>`:
```xml
<resources>
    <resource>
        <directory>src/main/resources</directory>
        <filtering>true</filtering>
    </resource>
</resources>
```

**Check:** Nach dem Build prüfen:
```bash
unzip -p target/pluginname-*.jar plugin.yml | head -3
```
Darf KEINE `${...}`-Platzhalter mehr enthalten.

### `mvn clean` löscht `target/` → JAR ist danach weg

Nach `mvn clean` muss immer `mvn package` folgen, sonst ist kein JAR da.

### Spigot 26.2: `Biome.name()` und `Biome.getKey()` sind deprecated

Ersatz: `biome.getKey().getKey()` (gibt den Key-String wie `"desert"`).
Mit `@SuppressWarnings("deprecation")` auf der Methode unterdrücken.
Spigot hat seit 26.x ein neues Versionierungsschema; der API-Bruch ist in
diesem Bereich noch nicht final stabilisiert.

### Spigot 26.2 → api-version = `"1.21"`

Das neue Versionierungsschema (26.x) ist extern; intern arbeitet die API
weiter mit `1.21` als höchster api-version.

### Citizens2 @Persist statt manuellem load/save

Citizens2 Traits mit `@Persist`-Annotation speichern Felder automatisch.
Die `load(DataKey)`/`save(DataKey)` Overrides sind NUR nötig, wenn
komplexe Serialisierung gebraucht wird. Für `Location`, `String`, `int`
etc. reicht `@Persist("key")` – die Location-serialisierung ist built-in.
Keine `getLocationRaw()` calls auf DataKey nötig.

---
description: Scaffold a new Spigot plugin module
agent: spigot-dev
---

Erstelle ein neues Spigot-Plugin-Modul mit dem Namen "$ARGUMENTS".

Ablauf:
1. `cp -r plugin-template plugin-$ARGUMENTS`
2. In `plugin-$ARGUMENTS/pom.xml`: `<artifactId>` auf `$ARGUMENTS` setzen
3. In der Root-`pom.xml`: `<module>plugin-$ARGUMENTS</module>` zu `<modules>` hinzufügen
4. In `plugin-$ARGUMENTS/src/main/resources/plugin.yml`:
   - `name` auf `$ARGUMENTS` setzen
   - `main` auf `me.diono.$ARGUMENTS.$ARGUMENTS` (PascalCase konvertieren) setzen
5. `plugin-$ARGUMENTS/src/main/java/me/diono/example/` verschieben nach `me/diono/$ARGUMENTS/`
6. `ExamplePlugin.java` in `$ARGUMENTS.java` umbenennen (PascalCase) und Klasse umbenennen
7. `mvn clean compile` zum Validieren

Falls der plugin-Name Bindestriche enthält, diese im Package-Namen entfernen und PascalCase für die Klasse nutzen (z.B. `my-plugin` → Package `me.diono.myplugin`, Klasse `MyPlugin`).

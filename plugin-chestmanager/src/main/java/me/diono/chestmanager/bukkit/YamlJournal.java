package me.diono.chestmanager.bukkit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import me.diono.chestmanager.core.BlockPos;
import me.diono.chestmanager.core.ItemKey;
import me.diono.chestmanager.core.JournalEntry;
import me.diono.chestmanager.core.port.JournalPort;

/**
 * Journal eines Sorters als YAML-Datei.
 *
 * <p>Der Schreibvorgang ist synchron: Die Item-Sicherheit hängt davon ab, dass der
 * Eintrag auf dem Datenträger liegt, bevor die Quelltruhe angefasst wird. Es
 * entsteht ein Schreibvorgang je Transport, nicht je Item.
 */
public final class YamlJournal implements JournalPort {

    private final File file;
    private final Logger logger;
    private final YamlConfiguration data = new YamlConfiguration();
    private long lastWriteMillis;

    public YamlJournal(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        reload();
    }

    /** Liest die Datei neu ein; offene Vorgänge aus einem früheren Lauf bleiben erhalten. */
    public void reload() {
        if (!file.isFile()) {
            return;
        }
        try {
            data.load(file);
        } catch (Exception failure) {
            logger.warning("Journal " + file.getName() + " ist unlesbar (" + failure.getMessage()
                    + "); es wird leer begonnen");
        }
    }

    public long lastWriteMillis() {
        return lastWriteMillis;
    }

    @Override
    public void write(JournalEntry entry) {
        String path = "entries." + entry.id();
        data.set(path + ".key", entry.key().typeId());
        data.set(path + ".variant", entry.key().variantKey());
        data.set(path + ".amount", entry.amount());
        data.set(path + ".source", encode(entry.source()));
        data.set(path + ".target", encode(entry.target()));
        data.set(path + ".phase", entry.phase().name());
        data.set(path + ".timestamp", entry.timestamp());
        data.set(path + ".targetBefore", entry.targetBefore());
        data.set(path + ".payload", entry.payload());
        flush();
    }

    @Override
    public void delete(String entryId) {
        data.set("entries." + entryId, null);
        flush();
    }

    @Override
    public List<JournalEntry> readAll() {
        List<JournalEntry> entries = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection("entries");
        if (section == null) {
            return entries;
        }
        for (String id : section.getKeys(false)) {
            String path = "entries." + id + ".";
            try {
                entries.add(new JournalEntry(
                        id,
                        new ItemKey(data.getString(path + "key"), data.getString(path + "variant")),
                        data.getInt(path + "amount"),
                        decode(data.getString(path + "source")),
                        decode(data.getString(path + "target")),
                        JournalEntry.JournalPhase.valueOf(data.getString(path + "phase",
                                JournalEntry.JournalPhase.PENDING.name())),
                        data.getLong(path + "timestamp"),
                        data.getString(path + "payload"),
                        data.getInt(path + "targetBefore")));
            } catch (RuntimeException broken) {
                logger.warning("Journaleintrag " + id + " in " + file.getName()
                        + " ist unvollständig und wird übersprungen: " + broken.getMessage());
            }
        }
        return entries;
    }

    private void flush() {
        long started = System.nanoTime();
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                logger.warning("Journalverzeichnis " + parent + " liess sich nicht anlegen");
            }
            data.save(file);
        } catch (IOException failure) {
            throw new IllegalStateException("Journal " + file.getName()
                    + " konnte nicht geschrieben werden: " + failure.getMessage(), failure);
        } finally {
            lastWriteMillis = (System.nanoTime() - started) / 1_000_000L;
        }
    }

    private static String encode(BlockPos pos) {
        return pos == null ? "" : pos.x() + ";" + pos.y() + ";" + pos.z();
    }

    private static BlockPos decode(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String[] parts = value.split(";");
        if (parts.length != 3) {
            return null;
        }
        return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]));
    }
}

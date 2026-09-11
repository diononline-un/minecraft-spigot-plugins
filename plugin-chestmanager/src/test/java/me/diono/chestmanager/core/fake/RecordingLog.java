package me.diono.chestmanager.core.fake;

import java.util.ArrayList;
import java.util.List;

import me.diono.chestmanager.core.port.LogPort;

/** Log-Attrappe, die Meldungen für Prüfungen sammelt. */
public final class RecordingLog implements LogPort {

    private final List<String> info = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> debug = new ArrayList<>();

    public List<String> infos() {
        return List.copyOf(info);
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    public List<String> debugs() {
        return List.copyOf(debug);
    }

    /** Ob irgendeine Meldung aller Stufen den Text enthält. */
    public boolean containsText(String needle) {
        return info.stream().anyMatch(m -> m.contains(needle))
                || warnings.stream().anyMatch(m -> m.contains(needle))
                || debug.stream().anyMatch(m -> m.contains(needle));
    }

    @Override
    public void info(String message) {
        info.add(message);
    }

    @Override
    public void warn(String message) {
        warnings.add(message);
    }

    @Override
    public void debug(String message) {
        debug.add(message);
    }
}

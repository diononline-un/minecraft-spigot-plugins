package me.diono.chestmanager.core.port;

/** Ausgabe des Kerns. Der Adapter leitet sie an den Plugin-Logger weiter. */
public interface LogPort {

    void info(String message);

    void warn(String message);

    void debug(String message);
}

package me.diono.chestmanager.core.port;

/**
 * Zeitquelle des Kerns. Injizierbar, damit Zeitlimits, Wiederholungsabstände und
 * Sperrdauern in Tests ohne Warten prüfbar sind.
 */
public interface Clock {

    long nowMillis();

    /** Uhr für Tests, die die Zeit von Hand vorspulen. */
    final class Fixed implements Clock {
        private long now;

        public Fixed(long start) {
            this.now = start;
        }

        @Override
        public long nowMillis() {
            return now;
        }

        public void advance(long millis) {
            now += millis;
        }

        public void set(long value) {
            now = value;
        }
    }
}

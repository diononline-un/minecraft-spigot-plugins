package me.diono.chestmanager.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlockPosTest {

    @Test
    void gleicheKoordinatenSindGleich() {
        assertEquals(new BlockPos(3, 64, -7), new BlockPos(3, 64, -7));
    }

    @Test
    void unterschiedlicheKoordinatenSindUngleich() {
        assertNotEquals(new BlockPos(3, 64, -7), new BlockPos(3, 64, -8));
    }

    @Test
    void offsetVerschiebtJeAchse() {
        assertEquals(new BlockPos(4, 63, -9), new BlockPos(3, 64, -7).offset(1, -1, -2));
    }

    @Test
    void abstandZumQuadratRechnetUeberDreiAchsen() {
        // 3-4-12 ist ein pythagoreisches Tripel mit Hypotenuse 13.
        BlockPos a = new BlockPos(0, 0, 0);
        BlockPos b = new BlockPos(3, 4, 12);

        assertEquals(169L, a.distanceSquared(b));
        assertEquals(13.0, a.distance(b), 1e-9);
    }

    @Test
    void abstandZumQuadratLaeuftNichtUeber() {
        BlockPos a = new BlockPos(Integer.MIN_VALUE, 0, 0);
        BlockPos b = new BlockPos(Integer.MAX_VALUE, 0, 0);

        assertTrue(a.distanceSquared(b) > 0, "Überlauf darf nicht zu einem negativen Abstand führen");
    }

    @Test
    void abstandIstSymmetrisch() {
        BlockPos a = new BlockPos(-10, 70, 22);
        BlockPos b = new BlockPos(5, 62, -3);

        assertEquals(a.distanceSquared(b), b.distanceSquared(a));
    }

    @Test
    void chebyshevAbstandNimmtDieGroessteAchsendifferenz() {
        assertEquals(12, new BlockPos(0, 0, 0).chebyshevDistance(new BlockPos(3, 4, 12)));
        assertEquals(0, new BlockPos(5, 5, 5).chebyshevDistance(new BlockPos(5, 5, 5)));
    }

    @Test
    void abstandZumBlockmittelpunktVerschiebtUmHalbeBloecke() {
        // Zwei direkt benachbarte Blöcke haben zwischen ihren Mittelpunkten den
        // Abstand 1, nicht 0.
        assertEquals(1.0, new BlockPos(0, 0, 0).distanceToCenter(new BlockPos(1, 0, 0)), 1e-9);
    }

    @Test
    void toStringTrenntMitSchraegstrichen() {
        assertEquals("3/64/-7", new BlockPos(3, 64, -7).toString());
    }
}

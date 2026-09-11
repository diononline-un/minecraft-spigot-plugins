package me.diono.chestmanager.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import me.diono.chestmanager.core.fake.FakeWorld;

class AccessPointResolverTest {

    private static final BlockPos CHEST = new BlockPos(10, 64, 10);

    private AccessPointResolver resolverFor(FakeWorld world) {
        return new AccessPointResolver(world);
    }

    @Test
    void findetEineBegehbarePositionNebenDerTruhe() {
        FakeWorld world = new FakeWorld().solid(CHEST);

        java.util.Optional<BlockPos> point = resolverFor(world).resolve(CHEST, new BlockPos(10, 65, 10));

        assertTrue(point.isPresent());
        assertEquals(1, point.get().chebyshevDistance(CHEST), "der Zugang liegt direkt neben der Truhe");
    }

    @Test
    void bevorzugtDieNaechstePosition() {
        FakeWorld world = new FakeWorld().solid(CHEST);

        // Der Sorter steht westlich der Truhe.
        java.util.Optional<BlockPos> point = resolverFor(world).resolve(CHEST, new BlockPos(5, 64, 10));

        assertEquals(new BlockPos(9, 64, 10), point.orElseThrow());
    }

    @Test
    void verwirftBlockiertePositionen() {
        FakeWorld world = new FakeWorld()
                .solid(CHEST)
                // Alle Nachbarluken ausser der oberen sind zugebaut.
                .solid(new BlockPos(11, 64, 10), new BlockPos(9, 64, 10),
                        new BlockPos(10, 64, 11), new BlockPos(10, 64, 10 - 1),
                        new BlockPos(10, 63, 10));

        java.util.Optional<BlockPos> point = resolverFor(world).resolve(CHEST, new BlockPos(10, 65, 10));

        assertEquals(new BlockPos(10, 65, 10), point.orElseThrow());
    }

    @Test
    void verwirftGefaehrlichePositionen() {
        FakeWorld world = new FakeWorld()
                .solid(CHEST)
                // Neben der Truhe liegt Lava.
                .dangerous(new BlockPos(9, 64, 10));

        java.util.Optional<BlockPos> point = resolverFor(world).resolve(CHEST, new BlockPos(9, 64, 10));

        assertFalse(point.orElseThrow().equals(new BlockPos(9, 64, 10)),
                "der Sorter darf nicht in die Lava laufen");
    }

    @Test
    void verwirftPositionenOhnePlatzNachOben() {
        FakeWorld world = new FakeWorld()
                .solid(CHEST)
                // Ueber allen Nachbarluken liegt ein Block.
                .solid(new BlockPos(11, 64, 10), new BlockPos(11, 65, 10),
                        new BlockPos(9, 64, 10), new BlockPos(9, 65, 10),
                        new BlockPos(10, 64, 11), new BlockPos(10, 65, 11),
                        new BlockPos(10, 64, 9), new BlockPos(10, 65, 9),
                        new BlockPos(10, 65, 10), new BlockPos(10, 66, 10),
                        new BlockPos(10, 63, 10));

        java.util.Optional<BlockPos> point = resolverFor(world).resolve(CHEST, new BlockPos(10, 65, 10));

        assertTrue(point.isEmpty(), "ohne begehbaren Zugang meldet der Resolver Unerreichbarkeit");
    }

    @Test
    void verwirftPositionenInNichtGeladenenChunks() {
        FakeWorld world = new FakeWorld()
                .solid(CHEST)
                .unloaded(new BlockPos(11, 64, 10), new BlockPos(9, 64, 10),
                        new BlockPos(10, 64, 11), new BlockPos(10, 64, 9),
                        new BlockPos(10, 65, 10), new BlockPos(10, 63, 10));

        assertTrue(resolverFor(world).resolve(CHEST, new BlockPos(10, 65, 10)).isEmpty());
    }

    @Test
    void gleicheAusgangslageLiefertDenselbenZugang() {
        FakeWorld world = new FakeWorld().solid(CHEST);

        java.util.Optional<BlockPos> first = resolverFor(world).resolve(CHEST, new BlockPos(5, 64, 10));
        java.util.Optional<BlockPos> second = resolverFor(world).resolve(CHEST, new BlockPos(5, 64, 10));

        assertEquals(first.orElseThrow(), second.orElseThrow());
    }

    @Test
    void schildblockUeberDerTruheIstEinGueltigerZugang() {
        // Ein Schild hat keine Kollisionsbox und gilt als passabel. Der Sorter kann
        // von dort aus an die Truhe, deshalb ist eine Truhe mit Schild obendrauf
        // erreichbar. Das ist im Container verifiziert.
        FakeWorld world = new FakeWorld().solid(CHEST);

        assertTrue(resolverFor(world).resolve(CHEST, new BlockPos(10, 66, 10)).isPresent());
    }

    @Test
    void deckeUeberDemSchildMachtDieTruheUnerreichbar() {
        // Erst wenn ueber dem Schild ebenfalls ein Block liegt, bleibt kein
        // Standort neben der Truhe uebrig.
        FakeWorld world = new FakeWorld()
                .solid(CHEST)
                .solid(new BlockPos(11, 64, 10), new BlockPos(9, 64, 10),
                        new BlockPos(10, 64, 11), new BlockPos(10, 64, 9),
                        new BlockPos(10, 63, 10),
                        // Schildblock bleibt passabel, der Block darueber sperrt ihn.
                        new BlockPos(10, 66, 10));

        assertTrue(resolverFor(world).resolve(CHEST, new BlockPos(10, 65, 10)).isEmpty(),
                "ohne freien Standort meldet der Resolver Unerreichbarkeit");
    }

    @Test
    void erkenntUeberlappendeBereiche() {
        ChestArea a = new ChestArea(CHEST, List.of(
                ChestRef.of(CHEST, RoleKind.ANCHOR),
                ChestRef.category(new BlockPos(11, 64, 10), "ores")), List.of());
        ChestArea b = new ChestArea(new BlockPos(11, 64, 10), List.of(
                ChestRef.of(new BlockPos(11, 64, 10), RoleKind.ANCHOR),
                ChestRef.category(new BlockPos(12, 64, 10), "ores")), List.of());
        ChestArea c = new ChestArea(new BlockPos(20, 64, 20), List.of(
                ChestRef.of(new BlockPos(20, 64, 20), RoleKind.ANCHOR)), List.of());

        assertTrue(a.overlaps(b));
        assertFalse(a.overlaps(c));
        assertFalse(a.overlaps(null));
        assertEquals(List.of(new BlockPos(11, 64, 10)), a.sharedWith(b));
        assertTrue(a.sharedWith(c).isEmpty());
    }
}

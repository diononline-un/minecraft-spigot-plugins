package me.diono.chestmanager.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.diono.chestmanager.core.ChestSnapshot.StackView;

class RoutingEngineTest {

    private static final ItemKey IRON = ItemKey.of("minecraft:iron_ore");
    private static final ItemKey GOLD = ItemKey.of("minecraft:gold_ore");
    private static final ItemKey BREAD = ItemKey.of("minecraft:bread");
    private static final ItemKey STICK = ItemKey.of("minecraft:stick");

    private static final BlockPos ANCHOR_POS = new BlockPos(0, 64, 0);
    private static final BlockPos ORES_POS = new BlockPos(1, 64, 0);
    private static final BlockPos ORES_POS_2 = new BlockPos(2, 64, 0);
    private static final BlockPos FOOD_POS = new BlockPos(3, 64, 0);
    private static final BlockPos FALLBACK_POS = new BlockPos(4, 64, 0);

    private CategoryTable categories;
    private RoutingEngine engine;
    private ChestArea area;

    /** Steuerbarer Kontext: Inhalte und Erreichbarkeit setzt der jeweilige Test. */
    private static final class Context implements RoutingEngine.TargetContext {
        private final java.util.Map<BlockPos, ChestSnapshot> snapshots = new java.util.HashMap<>();
        private final Set<BlockPos> unreachable = new java.util.HashSet<>();

        Context put(BlockPos pos, ChestSnapshot snapshot) {
            snapshots.put(pos, snapshot);
            return this;
        }

        Context unreachable(BlockPos pos) {
            unreachable.add(pos);
            return this;
        }

        @Override
        public Optional<ChestSnapshot> snapshot(BlockPos pos) {
            return Optional.ofNullable(snapshots.get(pos));
        }

        @Override
        public boolean isReachable(BlockPos pos) {
            return !unreachable.contains(pos);
        }
    }

    private static ChestSnapshot snapshot(int size, ItemKey... contents) {
        List<StackView> slots = new ArrayList<>();
        for (ItemKey key : contents) {
            slots.add(key == null ? null : new StackView(key, 64));
        }
        while (slots.size() < size) {
            slots.add(null);
        }
        return new ChestSnapshot(slots, 64);
    }

    private static ChestSnapshot withStack(int size, ItemKey key, int amount) {
        List<StackView> slots = new ArrayList<>();
        slots.add(new StackView(key, amount));
        while (slots.size() < size) {
            slots.add(null);
        }
        return new ChestSnapshot(slots, 64);
    }

    @BeforeEach
    void setUp() {
        categories = CategoryTable.load(List.of(
                new CategoryTable.Definition("ores", "Erze", "minecraft:iron_ore",
                        List.of("minecraft:iron_ore", "minecraft:gold_ore")),
                new CategoryTable.Definition("food", "Essen", "minecraft:bread",
                        List.of("minecraft:bread")))).table();
        engine = new RoutingEngine(categories);

        area = new ChestArea(ANCHOR_POS, List.of(
                ChestRef.of(ANCHOR_POS, RoleKind.ANCHOR),
                ChestRef.category(ORES_POS, "ores"),
                ChestRef.category(ORES_POS_2, "ores"),
                ChestRef.category(FOOD_POS, "food"),
                ChestRef.of(FALLBACK_POS, RoleKind.FALLBACK)), List.of());
    }

    private ChestRef ref(BlockPos pos) {
        return area.at(pos).orElseThrow();
    }

    // --- Fehlplatzierung -------------------------------------------------

    @Test
    void falscheKategorieInKategorietruheIstFehlplatziert() {
        assertTrue(engine.isMisplaced(ref(ORES_POS), BREAD, area));
    }

    @Test
    void richtigeKategorieInKategorietruheBleibtLiegen() {
        assertFalse(engine.isMisplaced(ref(ORES_POS), IRON, area));
        assertFalse(engine.isMisplaced(ref(ORES_POS), GOLD, area));
    }

    @Test
    void inDerAnkertruheIstAllesMitKategorieFehlplatziert() {
        assertTrue(engine.isMisplaced(ref(ANCHOR_POS), IRON, area));
        assertTrue(engine.isMisplaced(ref(ANCHOR_POS), BREAD, area));
    }

    @Test
    void inDerAnkertruheGehtEinItemOhneKategorieInDieAuffangtruhe() {
        assertTrue(engine.isMisplaced(ref(ANCHOR_POS), STICK, area),
                "mit markierter Auffangtruhe gehört der Stab dorthin");
    }

    @Test
    void ohneAuffangtruheBleibtEinItemOhneKategorieInDerAnkertruheLiegen() {
        ChestArea withoutFallback = new ChestArea(ANCHOR_POS, List.of(
                ChestRef.of(ANCHOR_POS, RoleKind.ANCHOR),
                ChestRef.category(ORES_POS, "ores")), List.of());

        assertFalse(engine.isMisplaced(withoutFallback.at(ANCHOR_POS).orElseThrow(), STICK, withoutFallback));
    }

    @Test
    void inDerAuffangtruheIstNurMitKategorieFehlplatziert() {
        assertTrue(engine.isMisplaced(ref(FALLBACK_POS), IRON, area));
        assertFalse(engine.isMisplaced(ref(FALLBACK_POS), STICK, area));
    }

    @Test
    void inDerIgnoriertenTruheIstNichtsFehlplatziert() {
        ChestArea withIgnored = new ChestArea(ANCHOR_POS, List.of(
                ChestRef.of(ANCHOR_POS, RoleKind.ANCHOR),
                ChestRef.of(new BlockPos(9, 64, 0), RoleKind.IGNORED)), List.of());

        assertFalse(engine.isMisplaced(withIgnored.at(new BlockPos(9, 64, 0)).orElseThrow(), IRON, withIgnored));
    }

    @Test
    void itemOhneKategorieInKategorietruheIstNurMitAuffangtruheFehlplatziert() {
        ChestArea withoutFallback = new ChestArea(ANCHOR_POS, List.of(
                ChestRef.of(ANCHOR_POS, RoleKind.ANCHOR),
                ChestRef.category(ORES_POS, "ores")), List.of());

        assertFalse(engine.isMisplaced(withoutFallback.at(ORES_POS).orElseThrow(), STICK, withoutFallback));
        assertTrue(engine.isMisplaced(ref(ORES_POS), STICK, area), "mit Auffangtruhe gehört der Stab dorthin");
    }

    @Test
    void misplacedInLiefertNurDieFehlplatziertenStapel() {
        ChestSnapshot content = snapshot(27, IRON, BREAD, null, GOLD);


        List<RoutingEngine.MisplacedStack> found = engine.misplacedIn(ref(ORES_POS), content, area);

        assertEquals(1, found.size());
        assertEquals(BREAD, found.get(0).key());
        assertEquals(64, found.get(0).amount());
        assertEquals("food", found.get(0).categoryKey());
    }

    @Test
    void misplacedInIgnoriertIgnorierteTruhen() {
        ChestArea withIgnored = new ChestArea(ANCHOR_POS, List.of(
                ChestRef.of(new BlockPos(9, 64, 0), RoleKind.IGNORED)), List.of());

        List<RoutingEngine.MisplacedStack> found = engine.misplacedIn(
                withIgnored.at(new BlockPos(9, 64, 0)).orElseThrow(), snapshot(27, IRON), withIgnored);

        assertTrue(found.isEmpty());
    }

    // --- Zielwahl --------------------------------------------------------

    @Test
    void zielIstEineTruheDerPassendenKategorie() {
        Context context = new Context()
                .put(ORES_POS, snapshot(27))
                .put(ORES_POS_2, snapshot(27));

        RoutingEngine.TargetChoice choice = engine.chooseTarget(IRON, 64, area, context);

        RoutingEngine.TargetChoice.Found found = assertInstanceOf(
                RoutingEngine.TargetChoice.Found.class, choice);
        assertTrue(found.chest().targets("ores"));
    }

    @Test
    void truheMitBestehendemStapelWirdBevorzugt() {
        Context context = new Context()
                // Freie Plätze wären hier grösser, aber der Stapel entscheidet.
                .put(ORES_POS, withStack(27, IRON, 1))
                .put(ORES_POS_2, snapshot(27));

        RoutingEngine.TargetChoice.Found found = assertInstanceOf(
                RoutingEngine.TargetChoice.Found.class, engine.chooseTarget(IRON, 64, area, context));

        assertEquals(ORES_POS, found.chest().pos());
    }

    @Test
    void ohnePassendenStapelGewinntDerMeisteFreiePlatz() {
        Context context = new Context()
                .put(ORES_POS, snapshot(5))
                .put(ORES_POS_2, snapshot(27));

        RoutingEngine.TargetChoice.Found found = assertInstanceOf(
                RoutingEngine.TargetChoice.Found.class, engine.chooseTarget(IRON, 64, area, context));

        assertEquals(ORES_POS_2, found.chest().pos());
    }

    @Test
    void beiGleichstandEntscheidetDiePositionUndBleibtGleich() {
        Context context = new Context()
                .put(ORES_POS, snapshot(27))
                .put(ORES_POS_2, snapshot(27));

        RoutingEngine.TargetChoice first = engine.chooseTarget(IRON, 64, area, context);
        RoutingEngine.TargetChoice second = engine.chooseTarget(IRON, 64, area, context);

        assertEquals(assertInstanceOf(RoutingEngine.TargetChoice.Found.class, first).chest().pos(),
                assertInstanceOf(RoutingEngine.TargetChoice.Found.class, second).chest().pos());
        assertEquals(ORES_POS, assertInstanceOf(RoutingEngine.TargetChoice.Found.class, first).chest().pos());
    }

    @Test
    void abgabemengeIstDurchDenFreienPlatzBegrenzt() {
        // Eine einköpfige Truhe mit fast vollem Stapel: nur vier Plätze frei.
        Context context = new Context().put(ORES_POS, withStack(1, IRON, 60));

        RoutingEngine.TargetChoice.Found found = assertInstanceOf(RoutingEngine.TargetChoice.Found.class,
                engine.chooseTarget(IRON, 64, area, context));

        assertEquals(4, found.amount(), "nur der Restplatz im vorhandenen Stapel");
    }

    @Test
    void volleKategorieWirdAbgelehntUndNichtAusgewichen() {
        Context context = new Context()
                .put(ORES_POS, snapshot(1, IRON))
                .put(ORES_POS_2, snapshot(1, IRON))
                .put(FOOD_POS, snapshot(27));

        RoutingEngine.TargetChoice.Rejected rejected = assertInstanceOf(
                RoutingEngine.TargetChoice.Rejected.class, engine.chooseTarget(GOLD, 64, area, context));

        assertEquals(RoutingEngine.Rejection.CATEGORY_FULL, rejected.reason());
        assertEquals("ores", rejected.categoryKey());
    }

    @Test
    void fehlendeZieltruheWirdGemeldet() {
        ChestArea withoutFood = new ChestArea(ANCHOR_POS, List.of(
                ChestRef.of(ANCHOR_POS, RoleKind.ANCHOR),
                ChestRef.category(ORES_POS, "ores")), List.of());
        Context context = new Context().put(ORES_POS, snapshot(27));

        RoutingEngine.TargetChoice.Rejected rejected = assertInstanceOf(
                RoutingEngine.TargetChoice.Rejected.class, engine.chooseTarget(BREAD, 64, withoutFood, context));

        assertEquals(RoutingEngine.Rejection.NO_TARGET_CHEST, rejected.reason());
        assertEquals("food", rejected.categoryKey());
    }

    @Test
    void gesperrteZieltruhenWerdenGemeldet() {
        Context context = new Context()
                .put(ORES_POS, snapshot(27))
                .put(ORES_POS_2, snapshot(27))
                .unreachable(ORES_POS)
                .unreachable(ORES_POS_2);

        RoutingEngine.TargetChoice.Rejected rejected = assertInstanceOf(
                RoutingEngine.TargetChoice.Rejected.class, engine.chooseTarget(IRON, 64, area, context));

        assertEquals(RoutingEngine.Rejection.ALL_UNREACHABLE, rejected.reason());
    }

    @Test
    void itemOhneKategorieGehtInDieAuffangtruhe() {
        Context context = new Context().put(FALLBACK_POS, snapshot(27));

        RoutingEngine.TargetChoice.Found found = assertInstanceOf(RoutingEngine.TargetChoice.Found.class,
                engine.chooseTarget(STICK, 64, area, context));

        assertEquals(FALLBACK_POS, found.chest().pos());
    }

    @Test
    void itemOhneKategorieOhneAuffangtruheWirdEinmaligGemeldet() {
        ChestArea withoutFallback = new ChestArea(ANCHOR_POS, List.of(
                ChestRef.of(ANCHOR_POS, RoleKind.ANCHOR),
                ChestRef.category(ORES_POS, "ores")), List.of());
        Context context = new Context().put(ORES_POS, snapshot(27));

        assertFalse(engine.hasReportedUnknownType(STICK));
        RoutingEngine.TargetChoice.Rejected rejected = assertInstanceOf(RoutingEngine.TargetChoice.Rejected.class,
                engine.chooseTarget(STICK, 1, withoutFallback, context));
        assertEquals(RoutingEngine.Rejection.NO_CATEGORY_AND_NO_FALLBACK, rejected.reason());
        assertTrue(engine.hasReportedUnknownType(STICK), "der Typ gilt danach als gemeldet");
    }

    @Test
    void nachDemNeuladenDuerfenFehlendeZuordnungenErneutGemeldetWerden() {
        Context context = new Context().put(FALLBACK_POS, snapshot(27));

        engine.chooseTarget(STICK, 1, area, context);
        assertFalse(engine.hasReportedUnknownType(STICK), "mit Auffangtruhe gibt es nichts zu melden");

        ChestArea withoutFallback = new ChestArea(ANCHOR_POS, List.of(
                ChestRef.of(ANCHOR_POS, RoleKind.ANCHOR)), List.of());
        engine.chooseTarget(STICK, 1, withoutFallback, context);
        assertTrue(engine.hasReportedUnknownType(STICK));

        engine.clearReportedTypes();
        assertFalse(engine.hasReportedUnknownType(STICK));
    }

    @Test
    void neueKategorieNachKonfigurationswechselAendertDasZiel() {
        // Vorher: Stab hat keine Kategorie, es gibt keine Auffangtruhe.
        ChestArea withoutFallback = new ChestArea(ANCHOR_POS, List.of(
                ChestRef.of(ANCHOR_POS, RoleKind.ANCHOR),
                ChestRef.category(ORES_POS, "ores")), List.of());
        Context context = new Context()
                .put(ORES_POS, snapshot(27))
                .put(FALLBACK_POS, snapshot(27));

        assertInstanceOf(RoutingEngine.TargetChoice.Rejected.class,
                engine.chooseTarget(STICK, 1, withoutFallback, context));

        // Nach dem Neuladen ist "minecraft:stick" der Kategorie "ores" zugeordnet.
        CategoryTable reloaded = CategoryTable.load(List.of(
                new CategoryTable.Definition("ores", "Erze", "minecraft:iron_ore",
                        List.of("minecraft:iron_ore", "minecraft:gold_ore", "minecraft:stick")))).table();
        RoutingEngine newEngine = new RoutingEngine(reloaded);

        RoutingEngine.TargetChoice.Found found = assertInstanceOf(RoutingEngine.TargetChoice.Found.class,
                newEngine.chooseTarget(STICK, 1, withoutFallback, context));
        assertEquals(ORES_POS, found.chest().pos());
    }
}

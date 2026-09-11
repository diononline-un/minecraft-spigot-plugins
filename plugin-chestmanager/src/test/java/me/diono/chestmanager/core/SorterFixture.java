package me.diono.chestmanager.core;

import java.util.List;

import me.diono.chestmanager.core.fake.FakeChests;
import me.diono.chestmanager.core.fake.FakeFeedback;
import me.diono.chestmanager.core.fake.FakeNavigation;
import me.diono.chestmanager.core.fake.FakeWorld;
import me.diono.chestmanager.core.fake.InMemoryJournal;
import me.diono.chestmanager.core.fake.RecordingLog;
import me.diono.chestmanager.core.port.Clock;

/**
 * Baut einen vollständigen Sorter aus Attrappen auf. Navigation wird auf Wunsch
 * automatisch als angekommen behandelt, damit Ende-zu-Ende-Tests kurz bleiben.
 */
final class SorterFixture {

    static final BlockPos ANCHOR_POS = new BlockPos(0, 64, 0);
    static final BlockPos ORES_POS = new BlockPos(1, 64, 0);
    static final BlockPos ORES_POS_2 = new BlockPos(2, 64, 0);
    static final BlockPos FOOD_POS = new BlockPos(3, 64, 0);
    static final BlockPos FALLBACK_POS = new BlockPos(4, 64, 0);

    static final ItemKey IRON = ItemKey.of("minecraft:iron_ore");
    static final ItemKey GOLD = ItemKey.of("minecraft:gold_ore");
    static final ItemKey BREAD = ItemKey.of("minecraft:bread");
    static final ItemKey STICK = ItemKey.of("minecraft:stick");
    /** Hat eine Kategorie, aber in der Testanlage keine Zieltruhe. */
    static final ItemKey PICKAXE = ItemKey.of("minecraft:diamond_pickaxe");

    final FakeWorld world = new FakeWorld();
    final FakeChests chests = new FakeChests();
    final FakeNavigation navigation = new FakeNavigation();
    final InMemoryJournal journalPort = new InMemoryJournal();
    final Clock.Fixed clock = new Clock.Fixed(1_000L);
    final RecordingLog log = new RecordingLog();
    final FakeFeedback feedback = new FakeFeedback();

    CategoryTable categories = CategoryTable.load(List.of(
            new CategoryTable.Definition("ores", "Erze", "minecraft:iron_ore",
                    List.of("minecraft:iron_ore", "minecraft:gold_ore")),
            new CategoryTable.Definition("food", "Essen", "minecraft:bread",
                    List.of("minecraft:bread")),
            // Absichtlich ohne Zieltruhe in der Testanlage.
            new CategoryTable.Definition("tools", "Werkzeug", "minecraft:diamond_pickaxe",
                    List.of("minecraft:diamond_pickaxe")))).table();

    SorterSettings settings = testSettings();
    Sorter sorter;

    SorterFixture() {
        navigation.at(new BlockPos(0, 65, 0));
    }

    /**
     * Kleiner Suchraum, damit Scans in Tests in wenigen Takten durchlaufen. Die
     * übrigen Werte entsprechen den Voreinstellungen.
     */
    static SorterSettings testSettings() {
        SorterSettings defaults = SorterSettings.defaults();
        return new SorterSettings(8, 128, 2, 0, 4096,
                defaults.reach(), defaults.navTimeoutMillis(), defaults.maxNavAttempts(),
                defaults.retryBackoffMillis(), defaults.blockDurationMillis(), defaults.carryLimit(),
                defaults.sortIntervalMillis(), defaults.rescanIntervalMillis(), defaults.opsPerTick(),
                defaults.maxChunkTickets(), false);
    }

    SorterFixture defineStandardChests() {
        BlockPos[] all = {ANCHOR_POS, ORES_POS, ORES_POS_2, FOOD_POS, FALLBACK_POS};
        for (BlockPos pos : all) {
            // Truhen sind solide Blöcke; der Zugang liegt daneben oder darüber.
            world.solid(pos);
        }
        world.chest(ANCHOR_POS).sign(ANCHOR_POS.offset(0, 1, 0), "[INPUT]");
        world.chest(ORES_POS).sign(ORES_POS.offset(0, 1, 0), "[ORES]");
        world.chest(ORES_POS_2).sign(ORES_POS_2.offset(0, 1, 0), "[ORES]");
        world.chest(FOOD_POS).sign(FOOD_POS.offset(0, 1, 0), "[FOOD]");
        world.chest(FALLBACK_POS).sign(FALLBACK_POS.offset(0, 1, 0), "[UNSORTABLE]");
        for (BlockPos pos : all) {
            chests.define(pos, 27);
        }
        return this;
    }

    SorterFixture withSettings(java.util.function.UnaryOperator<SorterSettings> change) {
        this.settings = change.apply(settings);
        return this;
    }

    Sorter build() {
        sorter = new Sorter("Test", ANCHOR_POS, settings, categories,
                new Sorter.Ports(world, chests, chests, navigation, journalPort, clock, log, feedback));
        return sorter;
    }

    /** Ein Arbeitstakt, bei dem jede begonnene Navigation sofort als angekommen gilt. */
    void tickArriving(int ops) {
        for (int i = 0; i < ops; i++) {
            sorter.tick(1);
            if (navigation.isNavigating()) {
                navigation.arrive();
            }
            clock.advance(50);
        }
    }

    /** Arbeitstakte, bei denen der Sorter nie ankommt; die Navigation endet ergebnislos. */
    void tickWithoutArrival(int ops) {
        for (int i = 0; i < ops; i++) {
            sorter.tick(1);
            if (navigation.isNavigating()) {
                navigation.finishNavigation();
            }
            clock.advance(50);
        }
    }

    void runUntilIdle(int maxRounds) {
        for (int i = 0; i < maxRounds; i++) {
            if (sorter.state() == SorterState.IDLE || sorter.state() == SorterState.STOPPED) {
                return;
            }
            tickArriving(1);
        }
    }
}

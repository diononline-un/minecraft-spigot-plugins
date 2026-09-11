package me.diono.chestmanager.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import me.diono.chestmanager.core.RoutingEngine.MisplacedStack;
import me.diono.chestmanager.core.RoutingEngine.TargetChoice;
import me.diono.chestmanager.core.port.CarryPort;
import me.diono.chestmanager.core.port.ChestPort;
import me.diono.chestmanager.core.port.Clock;
import me.diono.chestmanager.core.port.FeedbackPort;
import me.diono.chestmanager.core.port.JournalPort;
import me.diono.chestmanager.core.port.LogPort;
import me.diono.chestmanager.core.port.NavigationPort;
import me.diono.chestmanager.core.port.WorldPort;

/**
 * Ein Sorter: der Zustandsautomat, der fehlplatzierte Items findet, sie unter
 * Journalkontrolle entnimmt und in die passende Truhe bringt.
 *
 * <p>Der Automat ist Bukkit-frei; alles, was er von aussen braucht, kommt über
 * {@link Ports}. Er arbeitet in kurzen Schritten: {@link #tick(int)} führt
 * höchstens das übergebene Kontingent an Zustandsaktionen aus und kehrt dann
 * zurück, damit der Server-Tick nicht blockiert.
 *
 * <p>Der Zugriff auf ein Inventar setzt immer zweierlei voraus: Der Sorter steht
 * innerhalb der Reichweite zur Truhe, und die Truhe ist nicht als unerreichbar
 * gesperrt. Eine beendete Navigation gilt nicht als Ankunft.
 */
public final class Sorter {

    /** Alle Aussenkontakte des Sorters. */
    public record Ports(WorldPort world, ChestPort chests, CarryPort carry,
                        NavigationPort navigation, JournalPort journal, Clock clock, LogPort log,
                        FeedbackPort feedback) {}

    /** Momentaufnahme für Statusbefehle. */
    public record Status(SorterState state, String detail, int carriedItems,
                         BlockPos home, BlockPos anchor, int markedChests, int unmarkedChests,
                         Map<RoleKind, Integer> chestCounts, Map<String, Integer> categoryCounts,
                         List<BlockedChests.Entry> blocked, long lastScanDurationMillis,
                         long lastScanAt, long openJournalEntries) {}

    /** Ziel der laufenden Fahrt, inklusive der Truhe, an der gehandelt wird. */
    private record Errand(ChestRef source, ChestRef target, MisplacedStack stack, int amount) {}

    private final String name;
    private final Ports ports;
    private final SorterSettings settings;
    private final AccessPointResolver accessPoints;
    private final BlockedChests blocked = new BlockedChests();
    private final TransportLog transportLog = new TransportLog(200);
    private final CarryJournal journal;

    private CategoryTable categories;
    private RoutingEngine routing;

    private BlockPos home;
    private SorterState state = SorterState.IDLE;
    private ChestArea area;
    private AreaScanner.Session scanSession;
    private List<String> scanWarnings = List.of();

    private Errand errand;
    private JournalEntry currentEntry;
    private BlockPos currentAccessPoint;
    private int sourceCursor;

    private long lastSortAt;
    private long lastScanAt;
    private long lastScanDurationMillis;
    private long stateDeadline;
    private long retryAt;
    /** Stapel, die in diesem Lauf nicht unterzubringen sind: Quelle plus Item-Schlüssel. */
    private final Set<String> skippedThisRun = new java.util.LinkedHashSet<>();
    private int navAttempts;
    private boolean sortRequested;
    private boolean scanRequested;
    private boolean stopped;
    private String detail = "bereit";

    public Sorter(String name, BlockPos home, SorterSettings settings,
                  CategoryTable categories, Ports ports) {
        this.name = name;
        this.home = home;
        this.settings = settings;
        this.categories = categories;
        this.ports = ports;
        this.routing = new RoutingEngine(categories);
        this.accessPoints = new AccessPointResolver(ports.world());
        this.journal = new CarryJournal(ports.journal(), ports.clock());
        // Ohne Ermittlung gibt es keinen Anker. Ein vorbelegter Anker würde in der
        // Diagnose einen Bereich vortäuschen, den es nicht gibt.
        this.area = ChestArea.empty(null);
    }

    // --- Steuerung durch den Betreiber -----------------------------------

    /**
     * Verlangt eine neue Bereichsermittlung. Der Sorter führt sie aus, sobald er
     * keinen offenen Transportvorgang mehr abzuarbeiten hat.
     */
    public void requestScan() {
        scanSession = null;
        scanRequested = true;
        lastScanAt = 0;
    }

    /** Löst einen Sortierlauf ohne Wartezeit aus. */
    public void sortNow() {
        sortRequested = true;
    }

    /** Hält den Sorter an und gibt getragene Items zurück. */
    public void stop() {
        stopped = true;
        ports.navigation().cancel();
        if (!ports.carry().isCarryEmpty()) {
            state = SorterState.RECOVER;
            detail = "gibt getragene Items zurück";
        } else {
            state = SorterState.STOPPED;
            detail = "angehalten";
        }
    }

    /** Nimmt den Betrieb wieder auf. */
    public void resume() {
        stopped = false;
        if (state == SorterState.STOPPED) {
            state = SorterState.IDLE;
            detail = "bereit";
        }
    }

    /** Bricht eine laufende Navigation ab, ohne den Sorter anzuhalten. */
    public void cancelNavigation() {
        ports.navigation().cancel();
    }

    /** Übernimmt eine geänderte Kategorienkonfiguration. */
    public void applyCategories(CategoryTable reloaded) {
        this.categories = reloaded;
        this.routing = new RoutingEngine(reloaded);
    }

    /** Setzt den Bereich direkt, für Tests und für die Wiederherstellung. */
    public void setArea(ChestArea newArea) {
        this.area = newArea;
    }

    public void setHome(BlockPos newHome) {
        this.home = newHome;
    }

    // --- Arbeitstakt -----------------------------------------------------

    /**
     * Führt höchstens {@code ops} Zustandsaktionen aus. Kehrt früher zurück, wenn
     * der Sorter auf etwas wartet: auf die Navigation oder auf den nächsten Lauf.
     */
    public void tick(int ops) {
        long start = System.nanoTime();
        try {
            for (int i = 0; i < ops; i++) {
                if (!advance()) {
                    return;
                }
            }
        } finally {
            long elapsed = System.nanoTime() - start;
            if (elapsed > 50_000_000L) {
                ports.log().warn("Sorter " + name + " hat " + (elapsed / 1_000_000L)
                        + " ms für einen Arbeitsdurchlauf gebraucht");
            }
        }
    }

    /**
     * Ein einzelner Zustandsschritt.
     *
     * @return {@code false}, wenn der Sorter auf ein Ereignis wartet und der
     *         Aufrufer mit dem Kontingent aufhören kann
     */
    private boolean advance() {
        long now = ports.clock().nowMillis();
        if (now < retryAt) {
            return false;
        }
        if (!ports.navigation().isAvailable() && requiresNavigation(state)) {
            return false;
        }

        switch (state) {
            case IDLE -> stepIdle(now);
            case SCANNING -> stepScanning();
            case SEEK_SOURCE -> stepSeekSource(now);
            case SEEK_TARGET -> stepSeekTarget(now);
            case APPROACH_SOURCE -> stepApproachSource(now);
            case PICKUP -> stepPickup(now);
            case APPROACH_TARGET -> stepApproachTarget(now);
            case DELIVER -> stepDeliver(now);
            case RECOVER -> stepRecover(now);
            case STOPPED -> {
                return false;
            }
        }
        return true;
    }

    private boolean requiresNavigation(SorterState candidate) {
        return candidate == SorterState.APPROACH_SOURCE || candidate == SorterState.APPROACH_TARGET;
    }

    private void stepIdle(long now) {
        if (stopped) {
            state = SorterState.STOPPED;
            detail = "angehalten";
            return;
        }
        // Ein angeforderter Scan hat Vorrang: Ohne Bereich kennt der Sorter weder
        // Quell- noch Auffangtruhe und kann getragene Items nicht zurückgeben.
        if (scanRequested) {
            state = SorterState.SCANNING;
            detail = "Bereich wird ermittelt";
            return;
        }
        // Offene Transportvorgänge haben Vorrang vor dem Sortieren.
        if (!journal.isEmpty()) {
            state = SorterState.RECOVER;
            detail = "offene Transportvorgänge werden abgearbeitet";
            return;
        }
        boolean needsScan = lastScanAt == 0
                || (now - lastScanAt) >= settings.rescanIntervalMillis();
        if (needsScan) {
            state = SorterState.SCANNING;
            detail = "Bereich wird ermittelt";
            return;
        }
        boolean due = sortRequested || (now - lastSortAt) >= settings.sortIntervalMillis();
        if (due) {
            state = SorterState.SEEK_SOURCE;
            detail = "sucht fehlplatzierte Items";
        }
    }

    private void stepScanning() {
        if (scanSession == null) {
            // Ohne geladenen Heimat-Chunk findet der Scan nichts. Statt einen leeren
            // Bereich zu übernehmen, wartet der Sorter und versucht es später erneut.
            if (!ports.world().isLoaded(home)) {
                detail = "Heimat-Chunk ist nicht geladen; Bereich wird später ermittelt";
                state = SorterState.IDLE;
                retryAt = ports.clock().nowMillis() + 5_000L;
                return;
            }
            long started = ports.clock().nowMillis();
            lastScanAt = started;
            scanSession = new AreaScanner(ports.world(), categories, ports.log())
                    .start(home, settings);
        }
        boolean done = scanSession.step();
        if (!done) {
            detail = "Bereich wird ermittelt (" + scanSession.progressPercent() + " %)";
            return;
        }
        area = scanSession.area();
        scanWarnings = scanSession.warnings();
        lastScanDurationMillis = ports.clock().nowMillis() - lastScanAt;
        scanSession = null;
        scanRequested = false;

        for (String warning : scanWarnings) {
            ports.log().warn("Sorter " + name + ": " + warning);
        }
        if (!area.hasAnchor()) {
            detail = "kein Anker; erwartet wird ein Schild mit [INPUT]";
            ports.log().warn("Sorter " + name + ": " + detail);
            lastSortAt = ports.clock().nowMillis();
            state = SorterState.IDLE;
            return;
        }
        resolveAccessPoints();
        // Zurück in den Ruhezustand: Dort entscheidet sich, ob zuerst offene
        // Transportvorgänge abzuarbeiten sind oder sortiert wird.
        state = SorterState.IDLE;
        detail = "Bereich steht";
    }

    /** Löst für jede Truhe den Zugangspunkt auf und sperrt Truhen ohne Zugang. */
    private void resolveAccessPoints() {
        long now = ports.clock().nowMillis();
        List<ChestRef> updated = new ArrayList<>();
        BlockPos from = ports.navigation().position().orElse(home);

        for (ChestRef ref : area.marked()) {
            if (ref.kind() == RoleKind.IGNORED) {
                updated.add(ref);
                continue;
            }
            Optional<BlockPos> point = accessPoints.resolve(ref.pos(), from);
            if (point.isEmpty()) {
                blocked.block(ref.pos(), "kein begehbarer Zugang", now, settings.blockDurationMillis(), 1);
                updated.add(ref);
            } else {
                updated.add(ref.withAccessPoint(point.get()));
            }
        }
        area = new ChestArea(area.anchor(), updated, area.unmarked());
    }

    private void stepSeekSource(long now) {
        sortRequested = false;
        if (area.sources().isEmpty()) {
            finishRun(now, "keine Quelltruhe im Bereich");
            return;
        }

        List<ChestRef> sources = area.sources();
        for (int i = 0; i < sources.size(); i++) {
            ChestRef candidate = sources.get((sourceCursor + i) % sources.size());
            if (blocked.isBlocked(candidate.pos(), now)) {
                continue;
            }
            Optional<ChestSnapshot> snapshot = ports.chests().read(candidate.pos());
            if (snapshot.isEmpty()) {
                continue;
            }
            List<MisplacedStack> misplaced = routing.misplacedIn(candidate, snapshot.get(), area);
            MisplacedStack next = null;
            for (MisplacedStack stack : misplaced) {
                // Ein Stapel, der in diesem Lauf schon abgelehnt wurde, wird nicht
                // erneut versucht; sonst dreht der Sorter im Kreis.
                if (!skippedThisRun.contains(skipKey(candidate, stack))) {
                    next = stack;
                    break;
                }
            }
            if (next == null) {
                continue;
            }
            sourceCursor = (sourceCursor + i + 1) % sources.size();
            errand = new Errand(candidate, null, next, next.amount());
            state = SorterState.SEEK_TARGET;
            detail = "sucht Ziel für " + next.key();
            return;
        }
        finishRun(now, skippedThisRun.isEmpty() ? "nichts zu sortieren" : "Rest nicht unterzubringen");
    }

    private void stepSeekTarget(long now) {
        MisplacedStack stack = errand.stack();
        TargetChoice choice = routing.chooseTarget(stack.key(), stack.amount(), area, targetContext());

        if (choice instanceof TargetChoice.Rejected rejected) {
            handleRejection(rejected, stack, now);
            return;
        }
        TargetChoice.Found found = (TargetChoice.Found) choice;
        errand = new Errand(errand.source(), found.chest(), stack, found.amount());
        state = SorterState.APPROACH_SOURCE;
        detail = "läuft zur Quelltruhe " + errand.source().pos();
        navAttempts = 0;
        startApproach(errand.source(), now);
    }

    /**
     * Ein Stapel, für den sich kein Ziel findet, wird für diesen Lauf übersprungen.
     * Der Lauf endet deshalb nicht: Andere Items in derselben Truhe sollen trotzdem
     * an ihren Platz, etwa wenn nur eine einzelne Kategorie voll ist.
     */
    private void handleRejection(TargetChoice.Rejected rejected, MisplacedStack stack, long now) {
        skippedThisRun.add(skipKey(errand.source(), stack));
        String reason = switch (rejected.reason()) {
            case NO_CATEGORY_AND_NO_FALLBACK -> {
                if (!routing.hasReportedUnknownType(stack.key())) {
                    ports.log().warn("Sorter " + name + ": für " + stack.key().typeId()
                            + " fehlt eine Kategorie und es ist keine Auffangtruhe markiert;"
                            + " das Item bleibt liegen");
                }
                yield "Item ohne Kategorie und ohne Auffangtruhe";
            }
            case NO_TARGET_CHEST -> {
                ports.log().warn("Sorter " + name + ": keine Zieltruhe für Kategorie '"
                        + rejected.categoryKey() + "'; betroffene Items bleiben liegen");
                yield "Zieltruhe fehlt";
            }
            case CATEGORY_FULL -> {
                ports.log().warn("Sorter " + name + ": Kategorie '" + rejected.categoryKey()
                        + "' ist voll; betroffene Items bleiben liegen");
                yield "Kategorie voll";
            }
            case ALL_UNREACHABLE -> {
                ports.log().warn("Sorter " + name + ": alle Zieltruhen der Kategorie '"
                        + rejected.categoryKey() + "' sind gesperrt");
                yield "Zieltruhen gesperrt";
            }
        };
        errand = null;
        detail = reason;
        state = SorterState.SEEK_SOURCE;
    }

    /** Schlüssel eines übersprungenen Stapels innerhalb eines Laufs. */
    private static String skipKey(ChestRef source, MisplacedStack stack) {
        return source.pos() + "|" + stack.key();
    }

    private void stepApproachSource(long now) {
        if (arrivedAt(errand.source().pos())) {
            state = SorterState.PICKUP;
            detail = "entnimmt " + errand.stack().key();
            return;
        }
        if (navigationExpired(now)) {
            handleNavigationFailure(errand.source(), now, () -> {
                state = SorterState.SEEK_SOURCE;
                errand = null;
            });
        }
    }

    private void stepPickup(long now) {
        BlockPos source = errand.source().pos();
        if (!arrivedAt(source)) {
            // Die Navigation ist beendet, der Sorter steht aber zu weit weg.
            handleNavigationFailure(errand.source(), now, () -> {
                state = SorterState.SEEK_SOURCE;
                errand = null;
            });
            return;
        }

        ItemKey key = errand.stack().key();
        int planned = Math.min(errand.amount(), settings.carryLimit());

        Optional<String> preview = ports.carry().preview(source, key, planned);
        if (preview.isEmpty()) {
            errand = null;
            state = SorterState.SEEK_SOURCE;
            return;
        }

        int targetBefore = countOf(errand.target().pos(), key);
        JournalEntry entry;
        try {
            entry = journal.reserve(key, planned, source, errand.target().pos(), targetBefore);
        } catch (RuntimeException failure) {
            // Ohne Journaleintrag darf nichts entnommen werden.
            ports.log().warn("Sorter " + name + ": Journal nicht beschreibbar (" + failure.getMessage()
                    + "); es wird nichts entnommen");
            finishRun(now, "Journal nicht beschreibbar");
            return;
        }

        int taken = ports.carry().extract(source, key, planned);
        if (taken <= 0) {
            journal.discard(entry);
            errand = null;
            state = SorterState.SEEK_SOURCE;
            return;
        }

        currentEntry = journal.markCarried(entry, ports.carry().carriedPayload(), taken);
        ports.feedback().swing();
        state = SorterState.APPROACH_TARGET;
        detail = "bringt " + taken + "x " + key + " nach " + errand.target().pos();
        navAttempts = 0;
        startApproach(errand.target(), now);
    }

    private void stepApproachTarget(long now) {
        if (arrivedAt(errand.target().pos())) {
            state = SorterState.DELIVER;
            detail = "legt ab";
            return;
        }
        if (navigationExpired(now)) {
            handleNavigationFailure(errand.target(), now, () -> {
                state = SorterState.RECOVER;
                detail = "Ziel nicht erreichbar, Items gehen zurück";
            });
        }
    }

    private void stepDeliver(long now) {
        BlockPos target = errand.target().pos();
        if (!arrivedAt(target)) {
            handleNavigationFailure(errand.target(), now, () -> {
                state = SorterState.RECOVER;
                detail = "Ziel nicht erreichbar, Items gehen zurück";
            });
            return;
        }

        int leftover = ports.carry().deposit(target);
        if (leftover > 0) {
            // Es passte nicht alles hinein; der Rest bleibt im Träger und wird
            // über die Wiederherstellung zurückgeführt.
            ports.log().warn("Sorter " + name + ": " + leftover + " Items passten nicht in "
                    + target + "; der Rest geht zurück");
            state = SorterState.RECOVER;
            detail = "Ablage unvollständig";
            return;
        }

        int delivered = currentEntry != null ? currentEntry.amount() : 0;
        if (currentEntry != null) {
            journal.complete(currentEntry);
        }
        ports.feedback().swing();
        transportLog.record(errand.stack().key(), delivered, errand.source().pos(), target, now,
                TransportLog.Outcome.DELIVERED);
        ports.log().debug("Sorter " + name + ": " + delivered + "x " + errand.stack().key()
                + " von " + errand.source().pos() + " nach " + target);

        currentEntry = null;
        errand = null;
        state = SorterState.SEEK_SOURCE;
        detail = "sucht fehlplatzierte Items";
    }

    /**
     * Arbeitet offene Journalvorgänge ab. {@code PENDING} bedeutet, dass die
     * Quelltruhe nie angefasst wurde; der Eintrag wird verworfen. {@code CARRIED}
     * wird zu Ende geführt, wenn die Zieltruhe den Zugang nachweislich erhalten hat,
     * sonst werden die Items zurückgeführt.
     */
    private void stepRecover(long now) {
        List<JournalEntry> open = journal.openEntries();
        if (open.isEmpty()) {
            if (stopped) {
                state = SorterState.STOPPED;
                detail = "angehalten";
            } else {
                state = SorterState.IDLE;
                detail = "bereit";
            }
            return;
        }

        JournalEntry entry = open.get(0);

        if (entry.phase() == JournalEntry.JournalPhase.PENDING) {
            journal.discard(entry);
            return;
        }

        // Zuerst prüfen, ob die Ablage schon stattgefunden hat. In diesem Fall darf
        // der Träger nicht beladen werden, sonst entstünde ein Duplikat.
        if (entry.target() != null) {
            int nowInTarget = countOf(entry.target(), entry.key());
            if (nowInTarget >= entry.targetBefore() + entry.amount()) {
                journal.complete(entry);
                transportLog.record(entry.key(), entry.amount(), entry.source(), entry.target(), now,
                        TransportLog.Outcome.DELIVERED);
                ports.log().info("Sorter " + name + ": Vorgang " + entry.id()
                        + " war bereits abgelegt und wird abgeschlossen");
                return;
            }
        }

        if (entry.hasPayload() && !ports.carry().isCarryEmpty()
                && !ports.carry().carriedPayload().equals(entry.payload())) {
            // Im Träger liegt etwas anderes als im Journal vermerkt.
            ports.log().warn("Sorter " + name + ": Trägerinhalt passt nicht zum Journal;"
                    + " der Träger wird aus dem Journal wiederhergestellt");
        }
        if (entry.hasPayload()) {
            ports.carry().loadCarried(entry.payload());
        }

        // Zurückführen: Quelltruhe, dann Auffangtruhe, dann Boden.
        if (returnTo(entry.source(), entry, TransportLog.Outcome.RETURNED, now)) {
            return;
        }
        Optional<ChestRef> fallback = area.fallback();
        if (fallback.isPresent() && returnTo(fallback.get().pos(), entry,
                TransportLog.Outcome.FALLBACK, now)) {
            return;
        }
        if (!area.hasAnchor()) {
            // Der Bereich ist noch nicht ermittelt. Ohne ihn ist die Auffangtruhe
            // unbekannt; erst ermitteln, dann erneut zurückführen.
            requestScan();
            state = SorterState.IDLE;
            detail = "Bereich wird ermittelt, um getragene Items zurückzugeben";
            return;
        }
        int dropped = ports.carry().dropCarried();
        journal.complete(entry);
        transportLog.record(entry.key(), dropped, entry.source(), null, now,
                TransportLog.Outcome.DROPPED);
        ports.log().warn("Sorter " + name + ": " + dropped + "x " + entry.key()
                + " konnten nicht zurückgelegt werden und liegen jetzt an "
                + ports.navigation().position().orElse(home) + "; bitte einsammeln");
    }

    /** Legt den Trägerinhalt in eine Truhe. Gibt {@code false} zurück, wenn das nicht vollständig ging. */
    private boolean returnTo(BlockPos pos, JournalEntry entry, TransportLog.Outcome outcome, long now) {
        if (pos == null)
            return false;
        if (!ports.chests().read(pos).isPresent()) {
            return false;
        }
        int leftover = ports.carry().deposit(pos);
        if (leftover > 0) {
            // Ein Teil passte nicht; der Rest bleibt im Träger für den nächsten Versuch.
            transportLog.record(entry.key(), entry.amount() - leftover, pos, null, now,
                    TransportLog.Outcome.UNSAFE);
            ports.log().warn("Sorter " + name + ": " + leftover + " Items passten nicht zurück nach "
                    + pos + " und bleiben im Träger");
            return false;
        }
        journal.complete(entry);
        transportLog.record(entry.key(), entry.amount(), pos, null, now, outcome);
        return true;
    }

    // --- Hilfsmittel -----------------------------------------------------

    private void startApproach(ChestRef chest, long now) {
        Optional<BlockPos> point = chest.hasAccessPoint()
                ? Optional.of(chest.accessPoint())
                : accessPoints.resolve(chest.pos(), ports.navigation().position().orElse(home));
        if (point.isEmpty()) {
            blocked.block(chest.pos(), "kein begehbarer Zugang", now, settings.blockDurationMillis(), 1);
            handleNavigationFailure(chest, now, () -> {
                state = SorterState.SEEK_SOURCE;
                errand = null;
            });
            return;
        }
        currentAccessPoint = point.get();
        navAttempts++;
        ports.navigation().navigateTo(currentAccessPoint);
        stateDeadline = now + settings.navTimeoutMillis();
    }

    /**
     * Ob der Sorter tatsächlich an der Truhe steht. Die Reichweite zum Truhenblock
     * ist die Bedingung; eine beendete Navigation allein genügt nicht.
     */
    private boolean arrivedAt(BlockPos chest) {
        return ports.navigation().position()
                .map(pos -> pos.distanceToCenter(chest) <= settings.reach())
                .orElse(false);
    }

    private boolean navigationExpired(long now) {
        return now >= stateDeadline;
    }

    /**
     * Wiederholt einen fehlgeschlagenen Anlauf bis zur Höchstzahl und sperrt die
     * Truhe danach für die konfigurierte Dauer.
     */
    private void handleNavigationFailure(ChestRef chest, long now, Runnable giveUp) {
        ports.navigation().cancel();
        if (navAttempts >= settings.maxNavAttempts()) {
            blocked.block(chest.pos(), "nicht angelaufen", now, settings.blockDurationMillis(), navAttempts);
            ports.log().warn("Sorter " + name + ": " + chest.pos() + " ist nach " + navAttempts
                    + " Versuchen nicht erreichbar und wird für "
                    + (settings.blockDurationMillis() / 1000) + " s gesperrt");
            giveUp.run();
            return;
        }
        retryAt = now + settings.retryBackoffMillis();
        navAttempts++;
        ports.navigation().navigateTo(currentAccessPoint);
        stateDeadline = retryAt + settings.navTimeoutMillis();
        detail = "neuer Anlauf (" + navAttempts + ")";
    }

    private void finishRun(long now, String reason) {
        lastSortAt = now;
        errand = null;
        currentEntry = null;
        skippedThisRun.clear();
        if (stopped) {
            state = SorterState.STOPPED;
            detail = "angehalten";
        } else {
            state = SorterState.IDLE;
            detail = reason;
        }
    }

    private int countOf(BlockPos pos, ItemKey key) {
        return ports.chests().read(pos).map(snapshot -> {
            int total = 0;
            for (int slot = 0; slot < snapshot.size(); slot++) {
                ChestSnapshot.StackView stack = snapshot.slot(slot);
                if (stack != null && stack.key().equals(key)) {
                    total += stack.amount();
                }
            }
            return total;
        }).orElse(0);
    }

    private RoutingEngine.TargetContext targetContext() {
        return new RoutingEngine.TargetContext() {
            @Override
            public Optional<ChestSnapshot> snapshot(BlockPos pos) {
                return ports.chests().read(pos);
            }

            @Override
            public boolean isReachable(BlockPos pos) {
                return !blocked.isBlocked(pos, ports.clock().nowMillis());
            }
        };
    }

    // --- Auskunft --------------------------------------------------------

    public SorterState state() {
        return state;
    }

    public String name() {
        return name;
    }

    public ChestArea area() {
        return area;
    }

    public BlockedChests blockedChests() {
        return blocked;
    }

    public TransportLog transportLog() {
        return transportLog;
    }

    public CarryJournal journal() {
        return journal;
    }

    public List<String> lastScanWarnings() {
        return scanWarnings;
    }

    public Status status() {
        return new Status(state, detail,
                ports.carry().isCarryEmpty() ? 0 : -1,
                home, area.anchor(), area.marked().size(), area.unmarked().size(),
                area.countByRole(), area.countByCategory(),
                blocked.active(ports.clock().nowMillis()), lastScanDurationMillis,
                lastScanAt, journal.size());
    }

    public String describe() {
        StringBuilder builder = new StringBuilder();
        builder.append("Zustand: ").append(state).append(" (").append(detail).append(")\n");
        builder.append("Heimat: ").append(home).append('\n');
        builder.append("Anker: ").append(area.anchor() == null ? "keiner" : area.anchor()).append('\n');
        builder.append("Markierte Truhen: ").append(area.marked().size())
                .append(", ohne Rolle: ").append(area.unmarked().size()).append('\n');
        if (!area.countByCategory().isEmpty()) {
            builder.append("Kategorien: ").append(area.countByCategory()).append('\n');
        }
        builder.append("Gesperrt: ").append(blocked.active(ports.clock().nowMillis()).size()).append('\n');
        builder.append("Offene Vorgänge: ").append(journal.size()).append('\n');
        builder.append("Letzter Scan: ").append(lastScanDurationMillis).append(" ms");
        return builder.toString();
    }

    public static Status emptyStatus() {
        return new Status(SorterState.IDLE, "nicht gestartet", 0, null, null, 0, 0,
                Map.of(), Map.of(), List.of(), 0, 0, 0);
    }

    /** Ein Befund des Selbsttests. */
    public record Finding(Severity severity, String message) {
        public enum Severity {
            ERROR,
            WARNING,
            INFO
        }
    }

    /**
     * Prüft die Einrichtung, ohne ein Inventar zu verändern: Anker vorhanden, jede
     * Truhe anlaufbar, jede Item-Kategorie zugeordnet, jede Kategorie mit Platz.
     */
    public List<Finding> selfTest() {
        List<Finding> findings = new ArrayList<>();
        long now = ports.clock().nowMillis();

        if (!area.hasAnchor()) {
            findings.add(new Finding(Finding.Severity.ERROR,
                    "Keine Ankertruhe gefunden. Markiere eine Truhe mit einem Schild [INPUT]."));
            return findings;
        }
        findings.add(new Finding(Finding.Severity.INFO,
                "Ankertruhe bei " + area.anchor() + ", " + area.marked().size()
                        + " markierte Truhen, " + area.unmarked().size() + " ohne Rolle."));

        for (ChestRef ref : area.marked()) {
            if (ref.kind() == RoleKind.IGNORED) {
                continue;
            }
            if (!ref.hasAccessPoint()) {
                findings.add(new Finding(Finding.Severity.ERROR,
                        "Truhe " + ref.pos() + " (" + ref.kind() + ") hat keinen begehbaren Zugang."));
            }
            if (blocked.isBlocked(ref.pos(), now)) {
                findings.add(new Finding(Finding.Severity.WARNING,
                        "Truhe " + ref.pos() + " ist als unerreichbar gesperrt."));
            }
        }

        boolean hasFallback = area.fallback().isPresent();
        Set<String> missingCategories = new java.util.LinkedHashSet<>();
        Map<String, Integer> freePerCategory = new java.util.LinkedHashMap<>();

        for (ChestRef source : area.sources()) {
            Optional<ChestSnapshot> snapshot = ports.chests().read(source.pos());
            if (snapshot.isEmpty()) {
                findings.add(new Finding(Finding.Severity.WARNING,
                        "Quelltruhe " + source.pos() + " liess sich nicht lesen."));
                continue;
            }
            for (MisplacedStack stack : routing.misplacedIn(source, snapshot.get(), area)) {
                if (!stack.hasCategory() && !hasFallback) {
                    missingCategories.add(stack.key().typeId());
                }
                if (stack.hasCategory()) {
                    freePerCategory.merge(stack.categoryKey(), 0, Integer::sum);
                }
            }
        }

        for (String typeId : missingCategories) {
            findings.add(new Finding(Finding.Severity.WARNING,
                    "Für " + typeId + " fehlt eine Kategorie und es ist keine Auffangtruhe markiert."));
        }

        for (String categoryKey : categories.categoryKeys()) {
            List<ChestRef> targets = area.targets(categoryKey);
            if (targets.isEmpty()) {
                findings.add(new Finding(Finding.Severity.WARNING,
                        "Kategorie '" + categoryKey + "' hat keine Zieltruhe."));
                continue;
            }
            int free = 0;
            for (ChestRef target : targets) {
                free += ports.chests().read(target.pos())
                        .map(ChestSnapshot::freeSlots)
                        .orElse(0);
            }
            findings.add(new Finding(free > 0 ? Finding.Severity.INFO : Finding.Severity.WARNING,
                    "Kategorie '" + categoryKey + "': " + targets.size() + " Truhe(n), "
                            + free + " freie Plätze."));
        }

        if (findings.stream().noneMatch(f -> f.severity() == Finding.Severity.ERROR)) {
            findings.add(new Finding(Finding.Severity.INFO, "Keine Fehler gefunden."));
        }
        return findings;
    }
}

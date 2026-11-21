package com.tfaticktracker.tickloss;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Real raid, kept as a test: every mechanic this project argued about, replayed from a checked-in slice.
 * <p>
 * This replaces the recorded raids, which grew to 3.7 GB across 72 files -- too big to check in, too slow to
 * sweep, and gone as soon as anyone tidies their disk. Each fixture is a cut from one of those raids made by
 * {@link LogFixtures}, running through the same {@link LogReplay} the live client path mirrors, so a
 * regression in the window, the timeline or the adapters still fails here.
 * <p>
 * Every scenario named below was a bug first, and the numbers are what the full logs produced on the day
 * they were captured. To add one: put its range in {@link #FIXTURES}, regenerate with
 * {@code TICKLOSS_FIXTURES=write}, and read the printed numbers back into the expectations.
 */
public class FixtureReplayTest {

    private static final Path DIR = Paths.get("src/test/resources/tickloss-fixtures");
    private static final Path SOURCE_LOGS = Paths.get("debug-logs");

    /** name, source log, first tick, last tick, and why it is worth keeping. */
    private static final String[][] FIXTURES = {
            {"archive-guardian", "tfa-events-20261116-215240.log", "308", "585",
             "The first room: window opens on the TFA_GUARDIAN transform, no start grace."},
            {"bork-and-smorks", "tfa-events-20261116-215240.log", "576", "845",
             "Two health bars. The Smorks drops target all fight and either one can die first."},
            {"bork-dud-shot-and-eat", "tfa-events-20261116-215240.log", "4672", "4965",
             "A crossbow fired with no ammo, then an anglerfish onto the cooldown it started."},
            {"moia", "tfa-events-20261116-215240.log", "2430", "2700",
             "The one boss who spawns as her bar comes up, so her first tick cannot be attacked."},
            {"xarth-corpse-freeze", "tfa-events-20261116-215240.log", "2680", "2960",
             "Freezing a corpse resets the cooldown, so attacks land early and must not be charged."},
            {"ocellus-all-phases", "tfa-events-20261116-215240.log", "2960", "4010",
             "P1 handing over, P2 opening a tick late, P3 quadrants, the void explosion run, and the "
             + "settle when a quadrant dies while you are ready."},
            {"no-ammo-repro", "tfa-events-20261116-215240.log", "6700", "6970",
             "Seven dud shots in a row, deliberately reproduced at the Archive Guardian."},
            {"player-death", "tfa-events-20261114-115313.log", "330", "470",
             "Dying mid fight. ActorDeath lands on the killing hitsplat tick, so whatever wait was "
             + "outstanding at that moment is settled and anything after it is not."},
            {"no-ammo-and-fight-end", "tfa-events-20261116-215240.log", "7150", "7290",
             "Dud shots with a large settlement at the end, where the fight stopped mid cooldown."},
    };

    /**
     * What each fixture produced when it was captured.
     * <p>
     * name, segments, lost, counted, attacks, early, eat, dud, dudShots.
     */
    private static final int[][] EXPECTED = {
            // archive-guardian
            {1, 15, 217, 48, 0, 0, 0, 0},
            // bork-and-smorks
            {1, 24, 222, 40, 0, 0, 0, 0},
            // bork-dud-shot-and-eat
            {1, 45, 247, 43, 0, 3, 5, 1},
            // moia
            {1, 19, 193, 38, 0, 0, 0, 0},
            // xarth-corpse-freeze
            {1, 5, 195, 42, 8, 0, 0, 0},
            // ocellus-all-phases
            {3, 98, 888, 190, 6, 0, 0, 0},
            // no-ammo-repro
            {1, 87, 167, 16, 0, 12, 34, 7},
            // player-death
            {1, 35, 97, 13, 0, 12, 0, 0},
            // no-ammo-and-fight-end
            {1, 48, 58, 2, 0, 0, 10, 2},
    };

    @Test
    public void everyFixtureStillProducesTheSameNumbers() throws IOException {
        if ("write".equals(System.getenv("TICKLOSS_FIXTURES"))) {
            regenerate();
            return;
        }

        assumeTrue("fixtures not generated yet", Files.isDirectory(DIR));

        final LogReplay replay = new LogReplay();
        final List<String> problems = new ArrayList<>();

        for (int i = 0; i < FIXTURES.length; i++) {
            final String name = FIXTURES[i][0];
            final Path fixture = DIR.resolve(name + ".log");
            assertTrue("missing fixture " + fixture, Files.exists(fixture));

            final LogReplay.Result r = replay.replay(fixture);
            final int[] got = {
                    (int) r.segments.stream().filter(s -> s.counted > 0).count(),
                    r.totalLost(),
                    r.totalCounted(),
                    r.totalAttacks(),
                    r.segments.stream().mapToInt(s -> s.earlyAttacks).sum(),
                    r.segments.stream().mapToInt(s -> s.lostToEating).sum(),
                    r.segments.stream().mapToInt(s -> s.lostToDudShots).sum(),
                    r.segments.stream().mapToInt(s -> s.dudShots).sum(),
            };

            if (!Arrays.equals(got, EXPECTED[i])) {
                problems.add(String.format("%n  %s%n    expected %s%n    got      %s%n    (%s)",
                        name, describe(EXPECTED[i]), describe(got), FIXTURES[i][4]));
            }
        }

        assertTrue(String.join("", problems), problems.isEmpty());
    }

    /** The mechanics each fixture exists for, asserted by name rather than hidden in a total. */
    @Test
    public void theDudShotFixturesSeeTheDudShots() throws IOException {
        assumeTrue("fixtures not generated yet", Files.isDirectory(DIR));

        assertEquals("the marked Bork gap is one dud plus an anglerfish",
                1, dudShots("bork-dud-shot-and-eat"));
        assertEquals("the deliberate repro fired seven duds", 7, dudShots("no-ammo-repro"));
        assertEquals("no dud shots at Moia", 0, dudShots("moia"));
    }

    @Test
    public void theXarthFixtureSeesTheCorpseFreezeResets() throws IOException {
        assumeTrue("fixtures not generated yet", Files.isDirectory(DIR));

        final LogReplay.Result r = new LogReplay().replay(DIR.resolve("xarth-corpse-freeze.log"));
        final int early = r.segments.stream().mapToInt(s -> s.earlyAttacks).sum();
        assertTrue("freezing a corpse should read as early attacks, not loss, but saw " + early,
                early >= 5);
    }

    /**
     * Dying stops the count there, and invents nothing.
     * <p>
     * This death is the mid-cooldown case, which is the one worth guarding: the last attack was two ticks
     * earlier with a 5t weapon, so nothing was outstanding and the settlement is zero. A death must not
     * manufacture loss out of ticks that were never available -- it settles what was already owed or
     * nothing at all. The 12 ticks charged to food are the panic-eating on the way down, which is real.
     */
    @Test
    public void aDeathMidCooldownStopsTheCountWithoutInventingLoss() throws IOException {
        assumeTrue("fixtures not generated yet", Files.isDirectory(DIR));

        final LogReplay.Result r = new LogReplay().replay(DIR.resolve("player-death.log"));

        assertEquals("nothing was outstanding, so nothing is settled",
                0, r.segments.stream().mapToInt(s -> s.closedOut).sum());
        assertEquals("the eats on the way down are still charged",
                12, r.segments.stream().mapToInt(s -> s.lostToEating).sum());
        assertTrue("counting must stop at the death, well short of the slice",
                r.totalCounted() < 110);
    }

    /** The settle: a quadrant dying while the cooldown has already run out is charged there and then. */
    @Test
    public void theOcellusFixtureSeesTheSettlements() throws IOException {
        assumeTrue("fixtures not generated yet", Files.isDirectory(DIR));

        final LogReplay.Result r = new LogReplay().replay(DIR.resolve("ocellus-all-phases.log"));
        final int settled = r.segments.stream().mapToInt(s -> s.closedOut).sum();
        assertTrue("expected at least one settlement across the three phases, saw " + settled,
                settled > 0);
        assertEquals("P1, P2 and P3 should each be their own segment",
                3, r.segments.stream().filter(s -> s.counted > 0).count());
    }

    /**
     * Things that must hold of every segment of every fixture, whatever the numbers are.
     * <p>
     * These came off the golden-file test that swept the recorded raids. They are the cheap sanity net that
     * catches a sign error or a double count long before anyone reads a total: an exact expectation above
     * tells you a number moved, these tell you it moved somewhere impossible.
     */
    @Test
    public void everyFixtureObeysTheArithmetic() throws IOException {
        assumeTrue("fixtures not generated yet", Files.isDirectory(DIR));

        final LogReplay replay = new LogReplay();
        final List<String> problems = new ArrayList<>();

        for (String[] fixture : FIXTURES) {
            final LogReplay.Result r = replay.replay(DIR.resolve(fixture[0] + ".log"));
            for (LogReplay.Segment s : r.segments) {
                final String where = fixture[0] + "/" + s.label();
                check(problems, where, s.lost >= 0, "negative lost ticks");
                check(problems, where, s.counted >= 0, "negative counted ticks");
                check(problems, where, s.attacks >= 0, "negative attacks");
                check(problems, where, s.earlyAttacks <= s.attacks, "more early attacks than attacks");
                check(problems, where, s.lost <= s.counted, "lost more ticks than were counted");
                check(problems, where, s.longestGap <= s.lost, "longest gap exceeds total lost");
                check(problems, where, s.lostToEating <= s.lost, "eating blamed for more than was lost");
                check(problems, where, s.lostToDudShots <= s.lost,
                        "dud shots blamed for more than was lost");
                check(problems, where, s.closedOut <= s.lost, "settled more than was lost");
                check(problems, where, s.endTick >= s.startTick, "segment ends before it starts");
            }
        }

        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    private static void check(List<String> into, String where, boolean ok, String what) {
        if (!ok) into.add("  " + where + ": " + what);
    }

    /** No fixture may blame food and dud shots together for more ticks than it lost. */
    @Test
    public void attributionNeverExceedsTheLoss() throws IOException {
        assumeTrue("fixtures not generated yet", Files.isDirectory(DIR));

        final LogReplay replay = new LogReplay();
        for (String[] fixture : FIXTURES) {
            final LogReplay.Result r = replay.replay(DIR.resolve(fixture[0] + ".log"));
            for (LogReplay.Segment s : r.segments) {
                assertTrue(fixture[0] + "/" + s.label() + " blames more than it lost",
                        s.lostToEating + s.lostToDudShots <= s.lost);
            }
        }
    }

    /**
     * One replay in detail: its segments, its markers, and optionally a tick-by-tick trace.
     * <p>
     * This is the tool for "why did that say 8", which is otherwise answered by guessing. It takes a fixture
     * name, or a path to any log while one is still around; the marker hotkey writes {@code MARK} into a log,
     * so a moment that looked wrong in game can be found here rather than described. Markers are pressed a
     * few ticks AFTER the thing they are about.
     * <pre>
     *   PROBE=bork-dud-shot-and-eat ./gradlew test --tests '*replayOneInDetail*' -i
     *   PROBE=bork-dud-shot-and-eat TRACE_TICKS=4740-4750 ./gradlew test --tests '*replayOneInDetail*' -i
     * </pre>
     */
    @Test
    public void replayOneInDetail() throws IOException {
        final String name = System.getenv("PROBE");
        assumeTrue("set PROBE to a fixture name or a path to a log", name != null);

        Path path = DIR.resolve(name.endsWith(".log") ? name : name + ".log");
        if (!Files.exists(path)) path = Paths.get(name);
        if (!Files.exists(path)) path = SOURCE_LOGS.resolve(name);
        assertTrue("no such fixture or log: " + name, Files.exists(path));

        final LogReplay.Result r = new LogReplay().replay(path);
        System.out.printf("%n  %s  (local player %s)%n%n", path, r.localPlayer);
        System.out.printf("  %-18s %6s %6s %6s %5s %4s %7s %7s %6s %6s %s%n", "segment", "ticks", "lost",
                "eat", "dud", "duds", "settled", "attacks", "early", "worst", "ticks");
        for (LogReplay.Segment s : r.segments) {
            if (s.counted == 0) continue;
            System.out.printf("  %-18s %6d %6d %6d %5d %4d %7d %7d %6d %6d   t%d-%d%n",
                    s.label(), s.counted, s.lost, s.lostToEating, s.lostToDudShots, s.dudShots,
                    s.closedOut, s.attacks, s.earlyAttacks, s.longestGap, s.startTick, s.endTick);
        }

        if (!r.markers.isEmpty()) {
            System.out.println();
            System.out.println("  markers (pressed a few ticks after what they are about):");
            for (int[] m : r.markers) {
                final LogReplay.Segment in = r.segments.stream()
                        .filter(s -> s.startTick <= m[0] && m[0] <= s.endTick)
                        .findFirst().orElse(null);
                System.out.printf("    #%-3d tick %-7d %s%n", m[1], m[0],
                        in == null ? "(between segments)" : in.label());
            }
        }
    }

    private static int dudShots(String name) throws IOException {
        return new LogReplay().replay(DIR.resolve(name + ".log"))
                .segments.stream().mapToInt(s -> s.dudShots).sum();
    }

    private static String describe(int[] n) {
        return String.format("segments=%d lost=%d counted=%d attacks=%d early=%d eat=%d dud=%d duds=%d",
                n[0], n[1], n[2], n[3], n[4], n[5], n[6], n[7]);
    }

    /** Cut every fixture again from the original logs, and print what to paste into {@link #EXPECTED}. */
    private void regenerate() throws IOException {
        assumeTrue("debug-logs/ is needed to regenerate fixtures", Files.isDirectory(SOURCE_LOGS));

        final LogReplay replay = new LogReplay();
        final StringBuilder expectations = new StringBuilder();
        long bytes = 0;

        for (String[] fixture : FIXTURES) {
            final Path source = SOURCE_LOGS.resolve(fixture[1]);
            if (!Files.exists(source)) {
                System.out.println("  SKIPPED (no source log) " + fixture[0] + "  <- " + fixture[1]);
                continue;
            }

            final Path out = DIR.resolve(fixture[0] + ".log");
            System.out.println("  " + LogFixtures.slice(source, Integer.parseInt(fixture[2]),
                    Integer.parseInt(fixture[3]), out));
            bytes += Files.size(out);

            final LogReplay.Result r = replay.replay(out);
            expectations.append(String.format("            // %s%n            {%d, %d, %d, %d, %d, %d, %d, %d},%n",
                    fixture[0],
                    r.segments.stream().filter(s -> s.counted > 0).count(),
                    r.totalLost(), r.totalCounted(), r.totalAttacks(),
                    r.segments.stream().mapToInt(s -> s.earlyAttacks).sum(),
                    r.segments.stream().mapToInt(s -> s.lostToEating).sum(),
                    r.segments.stream().mapToInt(s -> s.lostToDudShots).sum(),
                    r.segments.stream().mapToInt(s -> s.dudShots).sum()));
        }

        System.out.printf("%n  %d fixtures, %d KB total%n%n", FIXTURES.length, bytes / 1024);
        System.out.println("  paste into EXPECTED:");
        System.out.print(expectations);

        Files.write(DIR.resolve("README.md"), readme().getBytes(StandardCharsets.UTF_8));
    }

    private static String readme() {
        final StringBuilder sb = new StringBuilder();
        sb.append("# Tick loss fixtures\n\n")
                .append("Slices of real raids, cut by `LogFixtures` and replayed by `FixtureReplayTest`.\n")
                .append("They exist so the regression net does not depend on `debug-logs/`, which reached\n")
                .append("3.7 GB and cannot be checked in.\n\n")
                .append("Each file opens with a generated checkpoint -- the room, the health-bar varbits and\n")
                .append("the NPCs that were already alive -- because a raw tick range would be missing all\n")
                .append("of it. After that the lines are verbatim, minus the varbits and other players'\n")
                .append("events that cannot change the outcome.\n\n")
                .append("Regenerate (needs the original logs):\n\n")
                .append("    TICKLOSS_FIXTURES=write ./gradlew test --tests '*FixtureReplayTest*' -i\n\n")
                .append("| fixture | why |\n|---|---|\n");
        for (String[] f : FIXTURES) {
            sb.append("| `").append(f[0]).append("` | ").append(f[4]).append(" |\n");
        }
        return sb.toString();
    }
}

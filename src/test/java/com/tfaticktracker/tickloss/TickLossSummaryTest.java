package com.tfaticktracker.tickloss;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.tfaticktracker.RaidRoom;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The published overlay, read as text.
 * <p>
 * This is the one piece of the project a player actually looks at, so it is worth being able to see it without
 * raiding. {@link #preview()} prints every state it can be in; run it with
 * {@code ./gradlew test --tests '*TickLossSummaryTest.preview*' -i}.
 * <p>
 * The rest assert the things that would be embarrassing to ship: "1 ticks", a room called ARCHIVE_GUARDIAN, or
 * blaming food for a gap it did not cause.
 */
public class TickLossSummaryTest {

    // --- what it says -----------------------------------------------------------------------------------

    @Test
    public void namesRoomsTheWayTheGameDoes() {
        assertEquals("Archive Guardian", TickLossLabels.room(RaidRoom.ARCHIVE_GUARDIAN));
        assertEquals("Bork", TickLossLabels.room(RaidRoom.BORK));
        assertEquals("Ocellus", TickLossLabels.room(RaidRoom.OCELLUS));
        assertEquals("Loot Room", TickLossLabels.room(RaidRoom.LOOT));
    }

    /** A room added later must not reach the overlay as an enum name. */
    @Test
    public void everyRoomHasAName() {
        for (RaidRoom room : RaidRoom.values()) {
            final String name = TickLossLabels.room(room);
            assertFalse(room + " has no display name", name.contains("_"));
            assertFalse(room + " has no display name", name.equals(name.toUpperCase()));
        }
    }

    @Test
    public void spellsOutPhases() {
        assertEquals("Ocellus, phase 3", TickLossLabels.fight(RaidRoom.OCELLUS, "P3"));
        assertEquals("Ocellus (P3)", TickLossLabels.fightShort(RaidRoom.OCELLUS, "P3"));
        assertEquals("Bork", TickLossLabels.fight(RaidRoom.BORK, null));
        assertEquals("Bork", TickLossLabels.fightShort(RaidRoom.BORK, null));
    }

    @Test
    public void countsTicksInEnglish() {
        assertEquals("1 tick", TickLossLabels.ticks(1));
        assertEquals("2 ticks", TickLossLabels.ticks(2));
        assertEquals("0 ticks", TickLossLabels.ticks(0));
    }

    // --- what it shows ----------------------------------------------------------------------------------

    @Test
    public void showsNothingBeforeTheFirstRoom() {
        assertTrue(TickLossSummary.rows(view(null, Collections.emptyList(), null)).isEmpty());
    }

    /**
     * The active room is what the last attack cost and what the room has cost. Nothing else.
     * <p>
     * Efficiency moved out to the summary: it is a judgement on a finished room, and watching a percentage
     * crawl while you fight invites playing to the number instead of the boss.
     */
    @Test
    public void theActiveRoomIsJustTheLastAttackAndTheLoss() {
        final List<TickLossSummary.Row> rows = TickLossSummary.rows(view(
                segment(RaidRoom.BORK, null, 247, 45, 3, 5), Collections.emptyList(), null));

        assertEquals("45", rightOf(rows, "Lost this room"));
        assertEquals("3", rightOf(rows, "Lost to eating"));
        assertEquals(null, rightOf(rows, "Efficiency"));
        assertEquals("the raw tick count is not shown", null, rightOf(rows, "Total ticks"));
    }

    /**
     * The food row holds its place when food cost nothing, blank, as the gap before the summary.
     * <p>
     * "Lost to eating 0" says nothing, but a row that vanishes un-fixes the height and shunts every row
     * below it up a line. The panel must be the same height either way, which is the whole point of the
     * fixed slots underneath.
     */
    @Test
    public void theFoodRowKeepsItsPlaceWhenFoodCostNothing() {
        final List<TickLossSummary.Row> withFood = TickLossSummary.rows(view(
                segment(RaidRoom.BORK, null, 247, 45, 3, 5), Collections.emptyList(), null));
        final List<TickLossSummary.Row> without = TickLossSummary.rows(view(
                segment(RaidRoom.BORK, null, 247, 45, 0, 5), Collections.emptyList(), null));

        assertEquals("3", rightOf(withFood, "Lost to eating"));
        assertEquals(null, rightOf(without, "Lost to eating"));
        assertEquals("the panel must not change height", withFood.size(), without.size());
    }

    /** And the height does not move as rooms are finished either. */
    @Test
    public void thePanelIsTheSameHeightAllRaid() {
        final int atTheStart = TickLossSummary.rows(view(
                segment(RaidRoom.ARCHIVE_GUARDIAN, null, 40, 1, 0, 0),
                Collections.emptyList(), null)).size();

        final int laterOn = TickLossSummary.rows(view(
                segment(RaidRoom.OCELLUS, "P3", 300, 40, 9, 0),
                Arrays.asList(segment(RaidRoom.ARCHIVE_GUARDIAN, null, 217, 15, 0, 0),
                        segment(RaidRoom.BORK, null, 222, 24, 0, 0),
                        segment(RaidRoom.MOIA, null, 194, 20, 0, 0),
                        segment(RaidRoom.XARTH, null, 195, 5, 0, 0),
                        segment(RaidRoom.OCELLUS, "P1", 200, 8, 0, 0),
                        segment(RaidRoom.OCELLUS, "P2", 102, 4, 0, 0)), null)).size();

        assertEquals(atTheStart, laterOn);
    }

    /**
     * The summary holds still while you are in the room, because the block above is the live one.
     * <p>
     * The room being fought is absent from the list AND from the raid total, so nothing below the active
     * block moves until the room ends.
     */
    @Test
    public void theRoomSummaryExcludesTheRoomYouAreIn() {
        final SegmentStats bork = segment(RaidRoom.BORK, null, 100, 9, 0, 0);
        final List<TickLossSummary.Row> rows = TickLossSummary.rows(view(bork,
                Arrays.asList(segment(RaidRoom.ARCHIVE_GUARDIAN, null, 217, 15, 0, 0), bork), null));

        assertEquals(null, rightOf(rows, "Bork"));
        assertEquals("   15  93.1%", rightOf(rows, "Archive Guardian"));
        assertEquals("the total must not include the room in progress either",
                "   15  93.1%", rightOf(rows, "Whole raid"));
    }

    /**
     * Every room has a row from the first tick of the raid, so the panel never changes height.
     * <p>
     * A list that grows a row per boss moves the party table and the overlay's own edge with it, which
     * makes both a moving target. The rotating three are not known in advance, so they wait as placeholders.
     */
    @Test
    public void everyRoomHasARowFromTheStart() {
        final SegmentStats guardian = segment(RaidRoom.ARCHIVE_GUARDIAN, null, 100, 4, 0, 0);
        final List<TickLossSummary.Row> rows = TickLossSummary.rows(view(guardian,
                Collections.singletonList(guardian), null));

        assertEquals("   --       ", rightOf(rows, "Archive Guardian"));
        assertEquals("   --       ", rightOf(rows, "Boss 2"));
        assertEquals("   --       ", rightOf(rows, "Boss 3"));
        assertEquals("   --       ", rightOf(rows, "Boss 4"));
        assertEquals("   --       ", rightOf(rows, "Ocellus (P1)"));
        assertEquals("   --       ", rightOf(rows, "Ocellus (P3)"));
        assertEquals("   --       ", rightOf(rows, "Whole raid"));
    }

    /** A placeholder takes the boss's real name once that room is done, in the order they were fought. */
    @Test
    public void aFinishedBossTakesItsPlaceholdersRow() {
        final SegmentStats xarth = segment(RaidRoom.XARTH, null, 195, 5, 0, 0);
        final List<TickLossSummary.Row> rows = TickLossSummary.rows(view(
                segment(RaidRoom.MOIA, null, 50, 2, 0, 0),
                Arrays.asList(segment(RaidRoom.ARCHIVE_GUARDIAN, null, 217, 15, 0, 0), xarth), null));

        assertEquals("    5  97.4%", rightOf(rows, "Xarth"));
        assertEquals("the next two are still unknown", "   --       ", rightOf(rows, "Boss 3"));
        assertEquals("   --       ", rightOf(rows, "Boss 4"));
        assertEquals("the room being fought is not in the summary", null, rightOf(rows, "Moia"));
    }

    /** Unchecking the summary leaves only the room you are in. */
    @Test
    public void theRoomSummaryCanBeTurnedOff() {
        final SegmentStats bork = segment(RaidRoom.BORK, null, 100, 9, 0, 0);
        final List<TickLossSummary.Row> rows = TickLossSummary.rows(new TickLossSummary.View(
                bork, true, Arrays.asList(segment(RaidRoom.ARCHIVE_GUARDIAN, null, 217, 15, 0, 0), bork),
                null, false, false, true, false, Collections.emptyList()));

        assertEquals("9", rightOf(rows, "Lost this room"));
        assertEquals(null, rightOf(rows, "Archive Guardian"));
        assertEquals(null, rightOf(rows, "Whole raid"));
    }

    /**
     * Between rooms the last one stays up, and joins the summary at the same time.
     * <p>
     * You are running to the next boss and the numbers are still readable, which is the only chance to look
     * at them without something trying to kill you.
     */
    @Test
    public void theFinishedRoomStaysUpWhileYouRunToTheNext() {
        final SegmentStats guardian = segment(RaidRoom.ARCHIVE_GUARDIAN, null, 217, 15, 0, 0);
        final List<TickLossSummary.Row> rows = TickLossSummary.rows(new TickLossSummary.View(
                guardian, false, Collections.singletonList(guardian), null, false, false, true, true,
                Collections.emptyList()));

        assertEquals("still the headline", "15", rightOf(rows, "Lost this room"));
        assertEquals("and now also in the summary", "   15  93.1%", rightOf(rows, "Archive Guardian"));
        assertEquals("   15  93.1%", rightOf(rows, "Whole raid"));
    }

    /** A room line is loss out of what that room had, plus the share of it used. */
    @Test
    public void eachRoomShowsLossOutOfItsTicks() {
        final List<TickLossSummary.Row> rows = TickLossSummary.rows(view(null,
                Arrays.asList(segment(RaidRoom.ARCHIVE_GUARDIAN, null, 217, 15, 0, 0),
                        segment(RaidRoom.OCELLUS, "P3", 586, 88, 12, 0)),
                null));

        assertEquals("   15  93.1%", rightOf(rows, "Archive Guardian"));
        assertEquals("   88  85.0%", rightOf(rows, "Ocellus (P3)"));
        assertEquals("  103  87.2%", rightOf(rows, "Whole raid"));
    }

    /** A clean attack says so, rather than going quiet and looking broken. */
    @Test
    public void saysSoWhenAnAttackWasOnTime() {
        final List<TickLossSummary.Row> rows = TickLossSummary.rows(view(
                segment(RaidRoom.BORK, null, 120, 0, 0, 0),
                Collections.emptyList(),
                moment(TickLossTracker.Cause.ATTACK, 0, 0, 0)));

        assertEquals("on time", rightOf(rows, "Last attack"));
        assertEquals(TickLossSummary.Tone.GOOD, toneOf(rows, "Last attack"));
    }

    /** The headline is the gap itself; the reason line was dropped as more detail than anyone reads. */
    @Test
    public void reportsTheGapWithoutExplainingIt() {
        final List<TickLossSummary.Row> rows = TickLossSummary.rows(view(
                segment(RaidRoom.BORK, null, 247, 45, 3, 5),
                Collections.emptyList(),
                moment(TickLossTracker.Cause.ATTACK, 8, 3, 5)));

        assertEquals("+8 ticks", rightOf(rows, "Last attack"));
        assertEquals(null, rightOf(rows, "    due to"));
    }

    /**
     * Only the last-attack row is coloured. Everything else is white, however it went.
     * <p>
     * That row is one event, replaced every few ticks, and it is what you glance at mid-fight -- which is
     * what a colour is for. The room table is seven standing rows, and colouring those by how they went
     * painted nearly all of them red at once, which reads as decoration rather than as a warning.
     */
    @Test
    public void onlyTheLastAttackIsColoured() {
        final List<TickLossSummary.Row> lost = TickLossSummary.rows(view(
                segment(RaidRoom.XARTH, null, 200, 9, 0, 0),
                List.of(segment(RaidRoom.BORK, null, 247, 45, 0, 0)),
                moment(TickLossTracker.Cause.ATTACK, 1, 0, 0)));

        assertEquals(TickLossSummary.Tone.BAD, toneOf(lost, "Last attack"));
        assertEquals(TickLossSummary.Tone.PLAIN, toneOf(lost, "Lost this room"));
        assertEquals(TickLossSummary.Tone.PLAIN, toneOf(lost, "Bork"));
        assertEquals(TickLossSummary.Tone.PLAIN, toneOf(lost, "Whole raid"));

        assertEquals(TickLossSummary.Tone.GOOD, toneOf(TickLossSummary.rows(view(
                segment(RaidRoom.XARTH, null, 200, 0, 0, 0),
                Collections.emptyList(),
                moment(TickLossTracker.Cause.ATTACK, 0, 0, 0))), "Last attack"));
    }

    /** The last result stays up until the next attack replaces it. */
    @Test
    public void theLastResultDoesNotClearOnItsOwn() {
        final List<TickLossSummary.Row> rows = TickLossSummary.rows(view(
                segment(RaidRoom.BORK, null, 120, 4, 0, 0),
                Collections.emptyList(),
                moment(TickLossTracker.Cause.ATTACK, 4, 0, 0)));

        assertEquals("+4 ticks", rightOf(rows, "Last attack"));
    }

    @Test
    public void wordsTheEndOfAFightAndADeathDifferently() {
        assertEquals("+2 ticks", rightOf(TickLossSummary.rows(view(
                segment(RaidRoom.OCELLUS, "P3", 200, 2, 0, 0), Collections.emptyList(),
                moment(TickLossTracker.Cause.FIGHT_ENDED, 2, 0, 0))), "When it ended"));

        assertEquals("+6 ticks", rightOf(TickLossSummary.rows(view(
                segment(RaidRoom.ARCHIVE_GUARDIAN, null, 200, 6, 0, 0), Collections.emptyList(),
                moment(TickLossTracker.Cause.DIED, 6, 0, 0))), "When you died"));
    }

    /** No enum names, no animation ids, no varbits: none of the debug vocabulary reaches a player. */
    @Test
    public void saysNothingThatOnlyADeveloperWouldUnderstand() {
        final List<TickLossSummary.Row> rows = TickLossSummary.rows(view(
                segment(RaidRoom.OCELLUS, "P3", 586, 88, 12, 0),
                Arrays.asList(segment(RaidRoom.ARCHIVE_GUARDIAN, null, 217, 15, 0, 0),
                        segment(RaidRoom.BORK, null, 222, 24, 0, 0)),
                moment(TickLossTracker.Cause.ATTACK, 3, 2, 0)));

        for (TickLossSummary.Row row : rows) {
            final String text = row.getLeft() + " " + row.getRight();
            for (String banned : new String[]{"anim", "varbit", "COUNTING", "BOSS_", "_", "spotanim",
                    "overshoot", "nextAttackTick", "npc"}) {
                assertFalse(banned + " leaked into \"" + text + "\"", text.contains(banned));
            }
        }
    }

    // --- see it -----------------------------------------------------------------------------------------

    /** Every state the overlay can be in, printed. Not an assertion: this is for looking at. */
    /**
     * One room, every reason: what Bork looks like as the fight goes on and the last attack keeps changing.
     * <p>
     * These are the lines a player will actually read, so they are worth seeing side by side rather than one
     * at a time. The room counters climb through the run; only the top line changes with each attack.
     */
    @Test
    public void previewOneRoomEveryReason() {
        final int[][] moments = {
                // lost, from eating, from a dud shot
                {0, 0, 0},
                {1, 0, 0},
                {2, 2, 0},
                {3, 3, 0},
                {5, 3, 0},
                {5, 0, 5},
                {8, 3, 5},
                {18, 3, 10},
        };
        final String[] what = {
                "clean attack",
                "one tick late, nothing to blame",
                "a karambwan, which explains all of it",
                "an anglerfish, which explains all of it",
                "an anglerfish plus two ticks of your own",
                "fired with no ammo, nothing else",
                "no ammo and then an anglerfish on its cooldown",
                "two duds and an anglerfish",
        };

        int lostSoFar = 0;
        int eatSoFar = 0;
        for (int i = 0; i < moments.length; i++) {
            lostSoFar += moments[i][0];
            eatSoFar += moments[i][1];
            show("Bork, " + what[i],
                    view(segment(RaidRoom.BORK, null, 60 + i * 24, lostSoFar, eatSoFar, 0),
                            Collections.singletonList(
                                    segment(RaidRoom.ARCHIVE_GUARDIAN, null, 217, 15, 0, 0)),
                            moment(TickLossTracker.Cause.ATTACK,
                                    moments[i][0], moments[i][1], moments[i][2])));
        }

        show("Bork, it died while you were ready",
                view(segment(RaidRoom.BORK, null, 252, 44, 12, 0),
                        Collections.singletonList(segment(RaidRoom.ARCHIVE_GUARDIAN, null, 217, 15, 0, 0)),
                        moment(TickLossTracker.Cause.FIGHT_ENDED, 2, 0, 0)));

        show("Bork, with a party",
                view(segment(RaidRoom.BORK, null, 204, 24, 11, 0),
                        Collections.singletonList(segment(RaidRoom.ARCHIVE_GUARDIAN, null, 217, 15, 0, 0)),
                        moment(TickLossTracker.Cause.ATTACK, 8, 3, 5),
                        Arrays.asList(
                                progress("Greg M", 15, 217, 24, 222),
                                progress("Dps11", 21, 219, 31, 222),
                                progress("Norazo", 9, 217))));

        show("Bork, you died",
                view(segment(RaidRoom.BORK, null, 198, 51, 15, 0),
                        Collections.singletonList(segment(RaidRoom.ARCHIVE_GUARDIAN, null, 217, 15, 0, 0)),
                        moment(TickLossTracker.Cause.DIED, 4, 3, 0)));
    }

    /** The party section: three numbers per raider, under a heading that names them. */
    @Test
    public void showsEachRaidersLastRoomRaidTotalAndEfficiency() {
        final List<TickLossSummary.Row> rows = TickLossSummary.rows(view(
                segment(RaidRoom.BORK, null, 222, 24, 0, 0),
                Collections.singletonList(segment(RaidRoom.ARCHIVE_GUARDIAN, null, 217, 15, 0, 0)),
                moment(TickLossTracker.Cause.ATTACK, 2, 0, 0),
                Arrays.asList(
                        progress("Greg M", 15, 217, 24, 222),
                        progress("Dps11", 21, 219, 31, 222))));

        assertEquals("last  raid    eff", rightOf(rows, "Party"));
        assertEquals("  24    39  91.1%", rightOf(rows, "Greg M"));
        assertEquals("  31    52  88.2%", rightOf(rows, "Dps11"));
    }

    /**
     * Before the room opens, every number is a dash rather than a confident zero.
     * <p>
     * Walking in, nothing has been measured. "0 lost at 100%" would be a claim about a fight that has not
     * started, and it would read as a perfect room right up until the first tick ruins it.
     */
    @Test
    public void showsDashesUntilTheRoomActuallyStarts() {
        final List<TickLossSummary.Row> rows = TickLossSummary.rows(view(
                segment(RaidRoom.BORK, null, 0, 0, 0, 0), Collections.emptyList(), null));

        assertEquals("--", rightOf(rows, "Last attack"));
        assertEquals("--", rightOf(rows, "Lost this room"));
    }

    /** Once it starts the room reads zero, but the last attack waits for an actual attack. */
    @Test
    public void theLastAttackStaysDashedUntilYouAttack() {
        final List<TickLossSummary.Row> rows = TickLossSummary.rows(view(
                segment(RaidRoom.BORK, null, 12, 0, 0, 0), Collections.emptyList(), null));

        assertEquals("--", rightOf(rows, "Last attack"));
        assertEquals("0", rightOf(rows, "Lost this room"));
    }

    /** And it is not inherited from the room before: a new room starts dashed. */
    @Test
    public void theLastAttackDoesNotCarryOverBetweenRooms() {
        final TickLossTracker.Moment atTheGuardian = new TickLossTracker.Moment(
                0, TickLossTracker.Cause.ATTACK, "Archive Guardian", 4, 0, 0);
        final List<TickLossSummary.Row> rows = TickLossSummary.rows(new TickLossSummary.View(
                segment(RaidRoom.BORK, null, 12, 0, 0, 0), true, Collections.emptyList(),
                atTheGuardian, false, false, true, true, Collections.emptyList()));

        assertEquals("--", rightOf(rows, "Last attack"));
    }

    /** The three rows are always there, so the block is the same height whatever has happened. */
    @Test
    public void theActiveBlockIsTheSameHeightBeforeAndAfterAnAttack() {
        final int before = TickLossSummary.rows(view(
                segment(RaidRoom.BORK, null, 0, 0, 0, 0), Collections.emptyList(), null)).size();
        final int after = TickLossSummary.rows(view(
                segment(RaidRoom.BORK, null, 40, 3, 0, 0), Collections.emptyList(),
                moment(TickLossTracker.Cause.ATTACK, 3, 0, 0))).size();

        assertEquals(before, after);
    }

    /**
     * Practice mode re-runs a room, and the row shows the attempt you just did.
     * <p>
     * Retrying never leaves the room, so the segment that normally closes on a room change does not. Each
     * attempt is its own segment now, and both the row and the total take the latest -- a total that added
     * up attempts you have already replaced would disagree with the list directly above it.
     */
    @Test
    public void aRetriedRoomShowsTheLatestAttempt() {
        final List<TickLossSummary.Row> rows = TickLossSummary.rows(view(null,
                Arrays.asList(segment(RaidRoom.ARCHIVE_GUARDIAN, null, 100, 40, 0, 0),
                        segment(RaidRoom.ARCHIVE_GUARDIAN, null, 120, 9, 0, 0)), null));

        assertEquals("    9  92.5%", rightOf(rows, "Archive Guardian"));
        assertEquals("the total must match the row", "    9  92.5%", rightOf(rows, "Whole raid"));
    }

    /** No party, no section: a solo raid must not grow an empty heading. */
    @Test
    public void showsNoPartySectionWhenAlone() {
        final List<TickLossSummary.Row> rows = TickLossSummary.rows(view(
                segment(RaidRoom.BORK, null, 222, 24, 0, 0), Collections.emptyList(), null));

        assertEquals(null, rightOf(rows, "Party"));
    }

    @Test
    public void preview() {
        show("Mid fight, last attack was clean",
                view(segment(RaidRoom.ARCHIVE_GUARDIAN, null, 180, 4, 0, 0),
                        Collections.emptyList(),
                        moment(TickLossTracker.Cause.ATTACK, 0, 0, 0)));

        show("A one tick slip, two rooms done",
                view(segment(RaidRoom.XARTH, null, 195, 5, 0, 0),
                        Arrays.asList(segment(RaidRoom.ARCHIVE_GUARDIAN, null, 217, 15, 0, 0),
                                segment(RaidRoom.BORK, null, 222, 24, 0, 0)),
                        moment(TickLossTracker.Cause.ATTACK, 1, 0, 0)));

        show("The eat on top of a dud shot",
                view(segment(RaidRoom.BORK, null, 247, 45, 3, 5),
                        Collections.singletonList(segment(RaidRoom.ARCHIVE_GUARDIAN, null, 225, 69, 0, 0)),
                        moment(TickLossTracker.Cause.ATTACK, 8, 3, 5)));

        show("A quadrant died while you were ready",
                view(segment(RaidRoom.OCELLUS, "P3", 586, 88, 12, 0),
                        Arrays.asList(segment(RaidRoom.OCELLUS, "P1", 200, 8, 0, 0),
                                segment(RaidRoom.OCELLUS, "P2", 102, 4, 0, 0)),
                        moment(TickLossTracker.Cause.FIGHT_ENDED, 2, 0, 0)));

        show("You died",
                view(segment(RaidRoom.ARCHIVE_GUARDIAN, null, 187, 23, 8, 0),
                        Collections.emptyList(),
                        moment(TickLossTracker.Cause.DIED, 6, 3, 0)));

        show("Raid complete", new TickLossSummary.View(null, false,
                Arrays.asList(segment(RaidRoom.ARCHIVE_GUARDIAN, null, 217, 15, 0, 0),
                        segment(RaidRoom.BORK, null, 222, 24, 0, 0),
                        segment(RaidRoom.MOIA, null, 194, 20, 0, 0),
                        segment(RaidRoom.XARTH, null, 195, 5, 0, 0),
                        segment(RaidRoom.OCELLUS, "P1", 200, 8, 0, 0),
                        segment(RaidRoom.OCELLUS, "P2", 102, 4, 0, 0),
                        segment(RaidRoom.OCELLUS, "P3", 586, 88, 12, 0)),
                null, true, true, true, true, Collections.emptyList()));
    }

    private static void show(String title, TickLossSummary.View view) {
        System.out.println();
        System.out.println("  === " + title + " ===");
        for (TickLossSummary.Row row : TickLossSummary.rows(view)) {
            if (row.getTone() == TickLossSummary.Tone.BLANK) {
                System.out.println("  |                                      |");
            } else if (row.getRight() == null) {
                System.out.printf("  | %-36s |%n", row.getLeft());
            } else {
                System.out.printf("  | %-18s %17s |  %s%n", row.getLeft(), row.getRight(), row.getTone());
            }
        }
    }

    // --- fixtures ---------------------------------------------------------------------------------------

    private static TickLossSummary.View view(SegmentStats current, List<SegmentStats> rooms,
                                             TickLossTracker.Moment moment) {
        return view(current, rooms, moment, Collections.emptyList());
    }

    private static TickLossSummary.View view(SegmentStats current, List<SegmentStats> rooms,
                                             TickLossTracker.Moment moment,
                                             List<TickLossParty.Progress> party) {
        // A moment belongs to the room it happened in, and the scenarios here write them without one.
        // Attach each to the room under test, which is what the tracker does in a real raid.
        if (moment != null && current != null) {
            moment = new TickLossTracker.Moment(moment.getTick(), moment.getCause(),
                    TickLossLabels.fight(current.getRoom(), current.getPhaseName()),
                    moment.getLost(), moment.getFromEating(), moment.getFromDudShot());
        }
        return new TickLossSummary.View(current, current != null, rooms, moment, false, false, true, true,
                party);
    }

    private static SegmentStats segment(RaidRoom room, String phase, int counted, int lost, int eat, int dud) {
        final SegmentStats s = new SegmentStats(room, 0, phase, 0);
        for (int i = 0; i < counted; i++) {
            s.tickInRoom();
            s.tickCounting();
        }
        if (lost > 0) s.addClosedOut(lost, eat, dud);
        return s;
    }

    /** A party member who finished rooms of {lost, counted} in that order. */
    private static TickLossParty.Progress progress(String name, int... lostThenCounted) {
        final TickLossParty.Progress p = new TickLossParty.Progress(name);
        for (int i = 0; i + 1 < lostThenCounted.length; i += 2) {
            p.add(new TickLossParty.Room(RaidRoom.BORK, null, lostThenCounted[i], lostThenCounted[i + 1]),
                    1);
        }
        return p;
    }

    private static TickLossTracker.Moment moment(TickLossTracker.Cause cause, int lost, int eat, int dud) {
        return new TickLossTracker.Moment(0, cause, "fixture", lost, eat, dud);
    }

    /**
     * The value beside a label, skipping rows that have none.
     * <p>
     * A label can legitimately appear twice: the room being fought is the heading of the active block AND a
     * row in the summary once it finishes. The heading carries no value, so the one worth asserting on is
     * the first row that actually has one.
     */
    // --- when a list has a gap in it --------------------------------------------------------------------

    /**
     * An animation neither list knows about is named on the overlay, under the numbers it affected.
     * <p>
     * The denylist counts an unlisted animation as an attack, so a gap in it makes a room read better than
     * it was -- the kind of error nobody reports. Printing the ids is what turns a flattering number into a
     * reportable one. The row only ever appears when something is actually wrong.
     */
    @Test
    public void namesAnimationsItDidNotUnderstand() {
        final SegmentStats room = segment(RaidRoom.BORK, null, 100, 20, 0, 0);
        room.noteUnrecognisedAnimation(15103);
        room.noteUnrecognisedAnimation(14253);

        final List<TickLossSummary.Row> rows = TickLossSummary.rows(view(room, Collections.singletonList(room), null, null));

        assertEquals("15103, 14253", rightOf(rows, "Counted, never hit"));
    }

    /** And says nothing at all when every animation was understood, which is almost always. */
    @Test
    public void staysQuietWhenEverythingWasUnderstood() {
        final SegmentStats room = segment(RaidRoom.BORK, null, 100, 20, 0, 0);

        final List<TickLossSummary.Row> rows = TickLossSummary.rows(view(room, Collections.singletonList(room), null, null));

        assertNull(rightOf(rows, "Counted, never hit"));
        assertNull(toneOf(rows, "Unrecognised animations"));
    }

    private static String rightOf(List<TickLossSummary.Row> rows, String left) {
        for (TickLossSummary.Row row : rows) {
            if (row.getLeft().equals(left) && row.getRight() != null) return row.getRight();
        }
        return null;
    }

    private static TickLossSummary.Tone toneOf(List<TickLossSummary.Row> rows, String left) {
        for (TickLossSummary.Row row : rows) {
            if (row.getLeft().equals(left)) return row.getTone();
        }
        return null;
    }
}

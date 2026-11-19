package com.tfaticktracker.tickloss;

import com.tfaticktracker.RaidRoom;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The words a player reads.
 * <p>
 * Everything here is what someone sees in an overlay rather than what a log is checked against, and the two
 * have separate vocabularies on purpose. A test is the only place this is readable without being in a raid.
 */
public class TickLossLabelsTest {

    @Test
    public void namesRoomsTheWayTheGameDoes() {
        assertEquals("Archive Guardian", TickLossLabels.room(RaidRoom.ARCHIVE_GUARDIAN));
        assertEquals("Bork", TickLossLabels.room(RaidRoom.BORK));
        assertEquals("Ocellus", TickLossLabels.room(RaidRoom.OCELLUS));
    }

    /**
     * A room this build has never heard of still reads as words.
     * <p>
     * New bosses arrive and the enum is what the raid tells us, not what we decided. Falling back to the
     * enum name title-cased beats printing DAGONHAI_SOMETHING at somebody.
     */
    @Test
    public void fallsBackToWordsForARoomWithNoNameOfItsOwn() {
        assertEquals("Unknown", TickLossLabels.room(null));
    }

    @Test
    public void readsPhasesOutInFull() {
        assertEquals("Ocellus, phase 3", TickLossLabels.fight(RaidRoom.OCELLUS, "P3"));
        assertEquals("Bork", TickLossLabels.fight(RaidRoom.BORK, null));
        assertEquals("Bork", TickLossLabels.fight(RaidRoom.BORK, ""));
    }

    /** A table row has no space for "Ocellus, phase 3", so it keeps the terse form. */
    @Test
    public void keepsTheShortFormForTableRows() {
        assertEquals("Ocellus (P3)", TickLossLabels.fightShort(RaidRoom.OCELLUS, "P3"));
        assertEquals("Xarth", TickLossLabels.fightShort(RaidRoom.XARTH, null));
    }

    /** Anything not shaped like P-then-digits is somebody else's label and is left alone. */
    @Test
    public void leavesAPhaseLabelItDoesNotUnderstandAlone() {
        assertEquals("Enrage", TickLossLabels.phase("Enrage"));
        assertEquals("", TickLossLabels.phase(null));
        assertEquals("phase 12", TickLossLabels.phase("P12"));
    }

    /** "1 ticks" in a published plugin reads as a bug, whatever the number next to it says. */
    @Test
    public void countsTicksInEnglish() {
        assertEquals("1 tick", TickLossLabels.ticks(1));
        assertEquals("4 ticks", TickLossLabels.ticks(4));
        assertEquals("0 ticks", TickLossLabels.ticks(0));
    }

    @Test
    public void showsEfficiencyToOneDecimal() {
        assertEquals("94.2%", TickLossLabels.percent(0.9415d));
        assertEquals("100.0%", TickLossLabels.percent(1.0d));
    }
}

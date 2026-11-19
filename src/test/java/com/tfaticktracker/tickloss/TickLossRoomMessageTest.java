package com.tfaticktracker.tickloss;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A party message is another client's word for it, and that client can say anything.
 * <p>
 * Everything arriving over the party is untrusted input. It is only safe to render because it never touches
 * our own figures and because it has to survive {@link TickLossRoomMessage#isPlausible()} first. These are
 * the ways a sender could be wrong -- buggy, a different build, or hostile.
 */
public class TickLossRoomMessageTest {

    @Test
    public void acceptsAnOrdinaryRoom() {
        assertTrue(message("BORK", "P3", 222, 24, 3, 5, 40, 0).isPlausible());
        assertTrue("a flawless room is still plausible",
                message("XARTH", null, 195, 0, 0, 0, 42, 8).isPlausible());
    }

    /** You cannot lose more ticks than were measured, which is the headline number people compare. */
    @Test
    public void rejectsLosingMoreThanWasCounted() {
        assertFalse(message("BORK", null, 100, 101, 0, 0, 10, 0).isPlausible());
    }

    @Test
    public void rejectsNegatives() {
        assertFalse(message("BORK", null, -1, 0, 0, 0, 0, 0).isPlausible());
        assertFalse(message("BORK", null, 100, -1, 0, 0, 0, 0).isPlausible());
        assertFalse(message("BORK", null, 100, 10, -1, 0, 0, 0).isPlausible());
        assertFalse(message("BORK", null, 100, 10, 0, -1, 0, 0).isPlausible());
        assertFalse(message("BORK", null, 100, 10, 0, 0, -1, 0).isPlausible());
    }

    /** The same invariant the fixtures assert of us: attribution cannot exceed the loss. */
    @Test
    public void rejectsBlamingMoreThanWasLost() {
        assertFalse(message("BORK", null, 200, 5, 6, 0, 10, 0).isPlausible());
        assertFalse(message("BORK", null, 200, 5, 0, 6, 10, 0).isPlausible());
        assertFalse("food and ammo together cannot exceed it either",
                message("BORK", null, 200, 8, 5, 5, 10, 0).isPlausible());
    }

    @Test
    public void rejectsMoreEarlyAttacksThanAttacks() {
        assertFalse(message("BORK", null, 200, 5, 0, 0, 10, 11).isPlausible());
    }

    /** A room cannot run for hours. Without a ceiling, one message can swamp a running total. */
    @Test
    public void rejectsAbsurdMagnitudes() {
        assertFalse(message("BORK", null, 100_001, 0, 0, 0, 0, 0).isPlausible());
        assertFalse(message("BORK", null, 100, 0, 0, 0, 100_001, 0).isPlausible());
    }

    /** The room name is a string, so it is also an injection surface for a long or missing value. */
    @Test
    public void rejectsAMissingOrOversizedRoom() {
        assertFalse(message(null, null, 100, 0, 0, 0, 0, 0).isPlausible());
        assertFalse(message("", null, 100, 0, 0, 0, 0, 0).isPlausible());
        assertFalse(message(repeat('A', 33), null, 100, 0, 0, 0, 0, 0).isPlausible());
        assertFalse(message("BORK", repeat('P', 9), 100, 0, 0, 0, 0, 0).isPlausible());
    }

    /** A room this build has not heard of is still plausible: newer sender, same arithmetic. */
    @Test
    public void acceptsARoomNameItDoesNotKnow() {
        assertTrue(message("SOME_NEW_BOSS", "P9", 200, 10, 0, 0, 40, 0).isPlausible());
    }

    private static String repeat(char c, int n) {
        final StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }

    private static TickLossRoomMessage message(String room, String phase, int counted, int lost,
                                               int eat, int dud, int attacks, int early) {
        final TickLossRoomMessage m = new TickLossRoomMessage();
        m.setV(TickLossRoomMessage.VERSION);
        m.setRoom(room);
        m.setPhase(phase);
        m.setCounted(counted);
        m.setLost(lost);
        m.setLostToEating(eat);
        m.setLostToDudShots(dud);
        m.setAttacks(attacks);
        m.setEarlyAttacks(early);
        return m;
    }
}

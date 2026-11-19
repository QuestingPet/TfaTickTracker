package com.tfaticktracker.tickloss;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.tfaticktracker.RaidRoom;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Who is allowed into your table, and in what order.
 * <p>
 * This is the only code in the plugin that decides whether another person's data is shown at all, so it is
 * worth pinning on its own rather than leaving it to the fixtures, which are all solo replays and never
 * exercise it. Both rules here are pure functions precisely so that no mock has to stand in for the client.
 */
public class TickLossPartyTest {

    private static Set<String> raiders(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }

    /**
     * In your party AND in your raid. Both, never either.
     * <p>
     * A party is a RuneLite construct with nothing to do with who is in the instance. Being in the party is
     * already implied -- a non-member's message never arrives -- so the raid side is what this adds, and
     * without it somebody's unrelated raid would appear in your table looking comparable.
     */
    @Test
    public void onlyAcceptsSomeoneWhoIsInTheRaidWithYou() {
        final Set<String> raid = raiders("Questing Pet", "STLs FINEST");

        assertTrue(TickLossParty.accepts(true, raid, "STLs FINEST"));
        assertFalse("a party member who is not raiding with us",
                TickLossParty.accepts(true, raid, "Someone Else"));
        assertFalse("outside a raid there is no list to match against",
                TickLossParty.accepts(false, raid, "STLs FINEST"));
    }

    /**
     * The same person, spelled two ways.
     * <p>
     * The raid's name varcs and the party's display name are different sources, and the client decorates
     * names with an icon prefix and non-breaking spaces. {@code Text.sanitize} handles both but does not
     * trim, so without normalising on BOTH sides a leading space is enough to drop a raider from the table
     * with no sign that anything happened.
     */
    @Test
    public void matchesNamesThroughTheClientsOwnDecoration() {
        assertTrue(TickLossParty.accepts(true, raiders("STLs FINEST"), " STLs FINEST"));
        assertTrue("decorated on the raid side instead",
                TickLossParty.accepts(true, raiders(" STLs FINEST"), "STLs FINEST"));
        assertTrue("an icon in front of the name",
                TickLossParty.accepts(true, raiders("STLs FINEST"), "<img=2>STLs FINEST"));
        assertFalse("normalising must not make two different people equal",
                TickLossParty.accepts(true, raiders("STLs FINEST"), "STLs FINES"));
    }

    @Test
    public void acceptsNobodyWhenThereIsNothingToMatch() {
        assertFalse(TickLossParty.accepts(true, Collections.emptySet(), "Questing Pet"));
        assertFalse(TickLossParty.accepts(true, raiders("Questing Pet"), null));
        assertFalse("a name that is nothing but decoration matches nobody",
                TickLossParty.accepts(true, raiders("Questing Pet"), " "));
        assertFalse(TickLossParty.accepts(true, null, "Questing Pet"));
    }

    /**
     * The table reads the same on everyone's screen.
     * <p>
     * Sorting by arrival would order it by whose message happened to land first, which differs per client --
     * so two people comparing screens mid-raid would be looking at two different tables.
     */
    @Test
    public void ordersByTheRaidPartyRatherThanByWhoSentFirst() {
        final TickLossParty.Progress first = new TickLossParty.Progress("Alpha");
        final TickLossParty.Progress second = new TickLossParty.Progress("Beta");
        final TickLossParty.Progress third = new TickLossParty.Progress("Gamma");

        final List<TickLossParty.Progress> ordered = TickLossParty.inRaidOrder(
                Arrays.asList(third, first, second), Arrays.asList("Alpha", "Beta", "Gamma"));

        assertEquals(Arrays.asList("Alpha", "Beta", "Gamma"),
                Arrays.asList(ordered.get(0).getName(), ordered.get(1).getName(), ordered.get(2).getName()));
    }

    /** Someone the raid has stopped listing keeps their numbers and goes last, rather than disappearing. */
    @Test
    public void keepsSomeoneTheRaidNoLongerLists() {
        final TickLossParty.Progress gone = new TickLossParty.Progress("Logged Out");
        final TickLossParty.Progress here = new TickLossParty.Progress("Alpha");

        final List<TickLossParty.Progress> ordered =
                TickLossParty.inRaidOrder(Arrays.asList(gone, here), Collections.singletonList("Alpha"));

        assertEquals("Alpha", ordered.get(0).getName());
        assertEquals("Logged Out", ordered.get(1).getName());
    }

    /** A raid total is the sum of the rooms they actually sent, not of the rooms they should have. */
    @Test
    public void addsUpOnlyTheRoomsThatArrived() {
        final TickLossParty.Progress member = new TickLossParty.Progress("Alpha");
        member.add(new TickLossParty.Room(RaidRoom.ARCHIVE_GUARDIAN, null, 15, 217), 1);
        member.add(new TickLossParty.Room(RaidRoom.BORK, null, 24, 222), 1);

        assertEquals(39, member.getRaidLost());
        assertEquals(439, member.getRaidCounted());
        assertEquals("the room everyone is talking about is the one that just finished",
                24, member.getLastRoomLost());
        assertEquals(0.911d, member.getEfficiency(), 0.001d);
    }

    /**
     * A sender cannot make us hold an unbounded number of rooms.
     * <p>
     * A raid is seven. Nothing stops a wrong or hostile client sending more, and the cost of believing it is
     * memory that only grows.
     */
    @Test
    public void stopsAcceptingRoomsLongBeforeMemoryIsAProblem() {
        final TickLossParty.Progress member = new TickLossParty.Progress("Alpha");
        for (int i = 0; i < 500; i++) {
            member.add(new TickLossParty.Room(RaidRoom.BORK, null, 1, 1), 1);
        }

        assertTrue("capped well above a real raid and well below anything costly",
                member.getRooms().size() <= 32);
    }

    /** Nobody has an efficiency until they have been measured; it is not 0%. */
    @Test
    public void readsAsPerfectUntilThereIsAnythingToJudge() {
        assertEquals(1.0d, new TickLossParty.Progress("Alpha").getEfficiency(), 0.0001d);
    }
}

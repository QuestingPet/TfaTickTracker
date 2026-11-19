package com.tfaticktracker.tickloss;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.tfaticktracker.RaidRoom;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * The raid's shape, shared by the overlay and the side panel so the two cannot disagree about it.
 * <p>
 * Always the same number of rooms in the same order, from the first tick of a raid to the last. That is
 * what keeps the overlay a fixed height and what lets the panel put every raider's rooms on the same lines.
 */
public class RaidRoomsTest {

    @Test
    public void alwaysLaysOutTheWholeRaid() {
        assertEquals(RaidRooms.SLOTS, RaidRooms.layOut(Collections.emptyList()).size());
        assertEquals(RaidRooms.SLOTS, RaidRooms.layOut(wholeRaid()).size());
    }

    @Test
    public void putsTheGuardianFirstAndOcellusLast() {
        final List<RaidRooms.Slot> slots = RaidRooms.layOut(Collections.emptyList());

        assertEquals("Archive Guardian", slots.get(0).label);
        assertEquals("Boss 2", slots.get(1).label);
        assertEquals("Boss 3", slots.get(2).label);
        assertEquals("Boss 4", slots.get(3).label);
        assertEquals("Ocellus (P1)", slots.get(4).label);
        assertEquals("Ocellus (P2)", slots.get(5).label);
        assertEquals("Ocellus (P3)", slots.get(6).label);
    }

    /** The rotating three are unknown in advance, so they take their names in the order they are fought. */
    @Test
    public void middleBossesTakeTheirNamesInTheOrderFought() {
        final List<RaidRooms.Slot> slots = RaidRooms.layOut(Arrays.asList(
                segment(RaidRoom.ARCHIVE_GUARDIAN, null),
                segment(RaidRoom.XARTH, null),
                segment(RaidRoom.MOIA, null)));

        assertEquals("Xarth", slots.get(1).label);
        assertEquals("Moia", slots.get(2).label);
        assertEquals("still unknown", "Boss 4", slots.get(3).label);
    }

    @Test
    public void findsEachOcellusPhaseSeparately() {
        final List<RaidRooms.Slot> slots = RaidRooms.layOut(Arrays.asList(
                segment(RaidRoom.OCELLUS, "P1"), segment(RaidRoom.OCELLUS, "P3")));

        assertNotNull(slots.get(4).done);
        assertNull("P2 was not reached", slots.get(5).done);
        assertNotNull(slots.get(6).done);
    }

    /** A room nobody has reached has a label but no numbers, which is what draws the placeholder. */
    @Test
    public void anUnreachedRoomHasNoNumbers() {
        for (RaidRooms.Slot slot : RaidRooms.layOut(Collections.emptyList())) {
            assertNull(slot.label, slot.done);
        }
    }

    /** A raid that got no further than the first room still lays the whole raid out around it. */
    @Test
    public void oneRoomIsStillARaid() {
        final List<RaidRooms.Slot> slots =
                RaidRooms.layOut(Collections.singletonList(segment(RaidRoom.ARCHIVE_GUARDIAN, null)));

        assertEquals(RaidRooms.SLOTS, slots.size());
        assertNotNull("the only room measured must be shown", slots.get(0).done);
    }

    private static List<SegmentStats> wholeRaid() {
        return Arrays.asList(
                segment(RaidRoom.ARCHIVE_GUARDIAN, null), segment(RaidRoom.BORK, null),
                segment(RaidRoom.MOIA, null), segment(RaidRoom.XARTH, null),
                segment(RaidRoom.OCELLUS, "P1"), segment(RaidRoom.OCELLUS, "P2"),
                segment(RaidRoom.OCELLUS, "P3"));
    }

    private static SegmentStats segment(RaidRoom room, String phase) {
        final SegmentStats s = new SegmentStats(room, 0, phase, 0);
        s.tickInRoom();
        s.tickCounting();
        return s;
    }
}

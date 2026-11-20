package com.tfaticktracker.tickloss;

import java.util.ArrayList;
import java.util.List;

import com.tfaticktracker.RaidRoom;

/**
 * The shape of a raid: which rooms you meet, in which order, whether or not you have reached them yet.
 * <p>
 * Shared by the overlay and the side panel so the two can never disagree about what order the rooms go in,
 * or about how many there are. Both show every room from the start -- the overlay so it never changes
 * height, the panel so a half-finished raid still reads as a raid rather than a list that happens to stop.
 * <p>
 * The Archive Guardian is first and Ocellus is last, in three phases. Between them are three of the
 * rotating bosses, which are not known in advance, so they wait as "Boss 2".."Boss 4" and take their real
 * name once they are done. The recorded raids bear that shape out: the Guardian appeared 220 times and
 * Ocellus 146, with the six possible middle bosses summing to 489, or about three a raid.
 */
final class RaidRooms {

    static final int MIDDLE_BOSSES = 3;
    static final int OCELLUS_PHASES = 3;
    /** Rooms in a raid: the Guardian, three rotating bosses, and Ocellus three times. */
    static final int SLOTS = 1 + MIDDLE_BOSSES + OCELLUS_PHASES;

    /** One room of the raid, with whatever has been measured for it so far. */
    static final class Slot {
        /** What to call it: the room's name once known, else a placeholder like "Boss 2". */
        final String label;
        /** Null until that room has been finished. */
        final SegmentStats done;

        Slot(String label, SegmentStats done) {
            this.label = label;
            this.done = done;
        }
    }

    private RaidRooms() {
    }

    /**
     * Lay finished rooms out in raid order, one entry per slot, always {@link #SLOTS} long.
     *
     * @param finished rooms that are over, in the order they were fought
     */
    static List<Slot> layOut(List<SegmentStats> finished) {
        final List<Slot> slots = new ArrayList<>(SLOTS);

        slots.add(new Slot(TickLossLabels.room(RaidRoom.ARCHIVE_GUARDIAN),
                latest(finished, RaidRoom.ARCHIVE_GUARDIAN, null)));

        // One entry per distinct boss, holding its latest attempt, in the order they were first met.
        final List<RaidRoom> metInOrder = new ArrayList<>();
        for (SegmentStats room : finished) {
            if (room.getRoom() != RaidRoom.ARCHIVE_GUARDIAN && room.getRoom() != RaidRoom.OCELLUS
                    && !metInOrder.contains(room.getRoom())) {
                metInOrder.add(room.getRoom());
            }
        }
        final List<SegmentStats> middle = new ArrayList<>();
        for (RaidRoom boss : metInOrder) {
            middle.add(latest(finished, boss, null));
        }
        for (int i = 0; i < MIDDLE_BOSSES; i++) {
            final SegmentStats done = i < middle.size() ? middle.get(i) : null;
            slots.add(new Slot(done == null ? "Boss " + (i + 2)
                    : TickLossLabels.fightShort(done.getRoom(), done.getPhaseName()), done));
        }

        for (int phase = 1; phase <= OCELLUS_PHASES; phase++) {
            final String name = "P" + phase;
            slots.add(new Slot(TickLossLabels.fightShort(RaidRoom.OCELLUS, name),
                    latest(finished, RaidRoom.OCELLUS, name)));
        }

        return slots;
    }

    /**
     * The LATEST attempt at a room, not the first.
     * <p>
     * Practice mode re-runs one room until you are happy with it, and the run you care about is the one you
     * just did. In an ordinary raid there is only ever one, so this changes nothing there.
     */
    /** Ticks lost across the attempts actually shown, so a total can never disagree with its own rows. */
    static int lost(List<Slot> slots) {
        int total = 0;
        for (Slot slot : slots) {
            if (slot.done != null) total += slot.done.getLost();
        }
        return total;
    }

    static int counted(List<Slot> slots) {
        int total = 0;
        for (Slot slot : slots) {
            if (slot.done != null) total += slot.done.getTicksCounting();
        }
        return total;
    }

    private static SegmentStats latest(List<SegmentStats> finished, RaidRoom room, String phase) {
        SegmentStats found = null;
        for (SegmentStats candidate : finished) {
            if (candidate.getRoom() == room && java.util.Objects.equals(candidate.getPhaseName(), phase)) {
                found = candidate;
            }
        }
        return found;
    }
}

package com.tfaticktracker.tickloss;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.tfaticktracker.RaidRoom;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What the player-facing overlay says, worked out without touching a client or a Graphics2D.
 * <p>
 * Separate from {@link TickLossSummaryOverlay} so the published wording and layout can be read in a test
 * instead of only in a raid. The overlay turns each {@link Row} into a line and each {@link Tone} into a
 * colour, and does nothing else, so what a test prints is what a player sees.
 */
final class TickLossSummary {

    /** Shown for a room that has not happened yet. */
    private static final String EMPTY = "--";

    /** What a row means, which the overlay maps to a colour. */
    enum Tone {
        TITLE,
        /** A section heading, e.g. the name of the fight you are in. */
        HEADING,
        /**
         * Nothing lost. Only the last-attack row is ever this.
         * <p>
         * Colour works here and nowhere else because this row is ONE event, replaced every few ticks: it is
         * the thing you glance at mid-fight, and glancing is exactly what a colour is for. The same treatment
         * on the room table coloured seven standing rows at once, which is a wall, not a signal.
         */
        GOOD,
        /** Anything lost. Only the last-attack row is ever this. */
        BAD,
        /** Supporting detail that should not draw the eye, and rooms nobody has reached yet. */
        QUIET,
        /** Every other number, whatever it says. */
        PLAIN,
        /** A spacer. */
        BLANK,
    }

    @Getter
    @RequiredArgsConstructor
    static final class Row {
        private final String left;
        /** Null for headings, titles and spacers. */
        private final String right;
        private final Tone tone;
    }

    /** Everything the summary needs, so it can be built from a test as easily as from the tracker. */
    @RequiredArgsConstructor
    static final class View {
        /**
         * The room to show at the top, which is NOT always the one being fought.
         * <p>
         * It stays put after a room ends so the numbers are still readable while you run to the next one,
         * and is replaced only when another room opens. {@link #inProgress} is what separates the two.
         */
        private final SegmentStats activeRoom;
        /** Whether {@link #activeRoom} is still being fought, or is the one that just finished. */
        private final boolean inProgress;
        private final List<SegmentStats> rooms;
        private final TickLossTracker.Moment moment;
        private final boolean raidFinished;
        private final boolean raidCompleted;
        private final boolean showActiveRoom;
        private final boolean showRooms;
        /** Other raiders, self-reported over the party. Empty when the feature is off or nobody qualifies. */
        private final List<TickLossParty.Progress> party;
    }

    private TickLossSummary() {
    }

    /** The whole panel, top to bottom. Empty when there is nothing worth showing. */
    static List<Row> rows(View view) {
        final List<Row> rows = new ArrayList<>();
        if (view.activeRoom == null && view.rooms.isEmpty()) return rows;

        rows.add(new Row("TFA Tick Tracker", null, Tone.TITLE));
        addCurrentFight(rows, view);
        addRooms(rows, view);
        addParty(rows, view);
        addUnrecognised(rows, view);
        return rows;
    }

    /**
     * Animations this build has never heard of, and what to do about them.
     * <p>
     * The safety net under {@link AttackAnimations}. Deciding attacks from an allowlist means an animation
     * nobody has listed registers no attack, which inflates the loss for that room -- so the ids that were
     * not understood are printed, and the number above them stops being a mystery.
     * <p>
     * Last in the overlay, on purpose. Everything above it is fixed height so nothing shifts under your eyes
     * mid-fight; this appears only when something is actually wrong, and at the bottom it has nothing below
     * it to shunt.
     */
    private static void addUnrecognised(List<Row> rows, View view) {
        final Set<Integer> ids = new LinkedHashSet<>();
        if (view.activeRoom != null) ids.addAll(view.activeRoom.getUnrecognisedAnimations());
        for (SegmentStats room : view.rooms) ids.addAll(room.getUnrecognisedAnimations());
        if (ids.isEmpty()) return;

        blank(rows);
        rows.add(new Row("Unrecognised animations", null, Tone.HEADING));
        final StringBuilder list = new StringBuilder();
        for (Integer id : ids) {
            if (list.length() > 0) list.append(", ");
            list.append(id);
        }
        rows.add(new Row("Not counted as attacks", list.toString(), Tone.QUIET));
    }

    /**
     * The other raiders, three numbers each: the room that just finished, the raid so far, and efficiency.
     * <p>
     * A heading row names the columns, because three bare numbers in a line are unreadable otherwise. Only
     * players who are in the party AND in the raid reach this, and only room totals are ever sent, so there
     * is nothing per-attack to show even if someone wanted it.
     */
    private static void addParty(List<Row> rows, View view) {
        if (view.party == null || view.party.isEmpty()) return;

        blank(rows);
        rows.add(new Row("Party", columns("last", "raid", "eff"), Tone.HEADING));

        for (TickLossParty.Progress member : view.party) {
            rows.add(new Row(member.getName(),
                    columns(String.valueOf(member.getLastRoomLost()),
                            String.valueOf(member.getRaidLost()),
                            TickLossLabels.percent(member.getEfficiency())),
                    Tone.PLAIN));
        }
    }

    /** Ticks lost and efficiency, right aligned in fixed columns so they line up under the heading. */
    private static String roomColumns(String lost, String efficiency) {
        return String.format("%5s %6s", lost, efficiency);
    }

    /** Three right-aligned columns of fixed width, so the rows line up under the heading. */
    private static String columns(String last, String raid, String efficiency) {
        return String.format("%4s %5s %6s", last, raid, efficiency);
    }

    private static void addCurrentFight(List<Row> rows, View view) {
        final SegmentStats room = view.activeRoom;
        if (room == null || !view.showActiveRoom) return;

        final String label = TickLossLabels.fight(room.getRoom(), room.getPhaseName());
        heading(rows, label);

        // All three rows are always here, however little is known yet. A row that appears when you first
        // attack is a row that moves everything below it the moment you do, which is the opposite of a
        // fixed height -- and it moves it mid-fight, at the worst possible time.
        addLastMoment(rows, view, label);

        // Dashes until the room actually opens. Walking in, nothing has been measured, and "0 lost at
        // 100%" would be a claim about a fight that has not started.
        final boolean started = room.getTicksCounting() > 0;
        rows.add(new Row("Lost this room", started ? String.valueOf(room.getLost()) : EMPTY,
                started ? Tone.PLAIN : Tone.QUIET));

        // Food gets a row whether or not it cost anything. A line reading "Lost to eating 0" says nothing,
        // but a row that disappears un-fixes the height and shunts everything below it up a line.
        if (room.getLostToEating() > 0) {
            rows.add(new Row("Lost to eating", String.valueOf(room.getLostToEating()), Tone.PLAIN));
        } else {
            rows.add(new Row(" ", null, Tone.BLANK));
        }
    }

    /**
     * What the last attack, or the end of the fight, cost -- or dashes until there has been one.
     * <p>
     * Only these moments are reported, because they are the only ones where the number is known: a gap is
     * measurable once something closes it and not before. The debug overlay shows a cooldown ticking into
     * the negatives instead, which is honest but unusable -- it counts at you while you are still deciding.
     * <p>
     * A moment belongs to the room it happened in, so entering a new room puts this back to dashes rather
     * than leaving the last room's number sitting there looking current. An attack that cost nothing still
     * says so: going quiet after a good attack reads as the overlay having missed it, and a reset that lets
     * you attack early is a good attack.
     */
    private static void addLastMoment(List<Row> rows, View view, String room) {
        final TickLossTracker.Moment moment = view.moment;
        if (moment == null || !java.util.Objects.equals(moment.getFight(), room)) {
            rows.add(new Row("Last attack", EMPTY, Tone.QUIET));
            return;
        }

        final String what;
        switch (moment.getCause()) {
            case DIED:
                what = "When you died";
                break;
            case FIGHT_ENDED:
                what = "When it ended";
                break;
            case ATTACK:
            default:
                what = "Last attack";
                break;
        }

        final int lost = moment.getLost();
        if (lost <= 0) {
            rows.add(new Row(what, "on time", Tone.GOOD));
            return;
        }
        rows.add(new Row(what, "+" + TickLossLabels.ticks(lost), Tone.BAD));
    }

    /**
     * Every room of the raid, in the order you meet them, whether or not they have happened yet.
     * <p>
     * The slots are FIXED so the panel is the same height from the first room to the last. A list that
     * grows a row per boss moves everything under it -- the party table, and the overlay's own edge -- which
     * makes it a moving target to read and a moving target to place on screen.
     * <p>
     * A TFA raid is the Archive Guardian, then three of the rotating bosses, then Ocellus in three phases.
     * The rotating three are not known in advance, so they sit as "Boss 2".."Boss 4" and take their real
     * name once they are done. Across the recorded raids that shape held: the Guardian appeared 220 times
     * and Ocellus 146, with the six possible middle bosses summing to 489, or about three per raid.
     * <p>
     * The room being fought is deliberately absent, and so is its contribution to the total. A summary that
     * ticks upward while you fight is two live readouts competing for the same glance, and the block above
     * is already the live one.
     */
    private static void addRooms(List<Row> rows, View view) {
        if (!view.showRooms) return;

        final List<SegmentStats> finished = new ArrayList<>();
        for (SegmentStats room : view.rooms) {
            if (!(view.inProgress && room == view.activeRoom)) finished.add(room);
        }

        // The active block above ends with the eating row, which is blank unless food cost something and
        // so doubles as the gap. Without that block there is nothing above at all, so add one.
        if (!view.showActiveRoom || view.activeRoom == null) blank(rows);
        // Column labels on the heading, which otherwise has an empty right half. Two bare numbers beside a
        // room name do not say which is which, and the answer costs no height at all here.
        rows.add(new Row(view.raidFinished
                ? (view.raidCompleted ? "Raid complete" : "Raid abandoned")
                : "Room summary", roomColumns("lost", "eff"), Tone.HEADING));

        final List<RaidRooms.Slot> slots = RaidRooms.layOut(finished);
        for (RaidRooms.Slot slot : slots) {
            if (slot.done == null) {
                rows.add(new Row(slot.label, roomColumns(EMPTY, ""), Tone.QUIET));
            } else {
                rows.add(new Row(slot.label,
                        roomColumns(String.valueOf(slot.done.getLost()),
                                TickLossLabels.percent(slot.done.getEfficiency())),
                        Tone.PLAIN));
            }
        }

        // Summed from the rows above, not from every segment ever opened. Practice mode re-runs a room
        // until you are happy with it, and adding up the attempts you have already replaced would make the
        // total disagree with its own list.
        final int lost = RaidRooms.lost(slots);
        final int counted = RaidRooms.counted(slots);
        final double efficiency = RaidRooms.efficiency(lost, counted);

        blank(rows);
        rows.add(new Row("Whole raid",
                counted <= 0 ? roomColumns(EMPTY, "")
                        : roomColumns(String.valueOf(lost), TickLossLabels.percent(efficiency)),
                counted <= 0 ? Tone.QUIET : Tone.PLAIN));
    }



    private static void heading(List<Row> rows, String text) {
        blank(rows);
        rows.add(new Row(text, null, Tone.HEADING));
    }

    private static void blank(List<Row> rows) {
        if (rows.isEmpty()) return;
        rows.add(new Row(" ", null, Tone.BLANK));
    }
}

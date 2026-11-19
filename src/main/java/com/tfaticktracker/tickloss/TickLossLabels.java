package com.tfaticktracker.tickloss;

import java.util.EnumMap;
import java.util.Map;

import com.tfaticktracker.RaidRoom;

/**
 * The words the player-facing overlay uses, kept away from the thing that does the counting.
 * <p>
 * The debug overlay prints enum names and raw ids on purpose -- {@code OCELLUS P3}, {@code anim 8056} -- because
 * that is what a log is checked against. None of it belongs in front of someone who just wants to know how their
 * raid went, so the two have separate vocabularies and this is the published one.
 */
final class TickLossLabels {

    private static final Map<RaidRoom, String> ROOM_NAMES = new EnumMap<>(RaidRoom.class);

    static {
        ROOM_NAMES.put(RaidRoom.LOBBY, "Lobby");
        ROOM_NAMES.put(RaidRoom.NEXUS, "Nexus");
        ROOM_NAMES.put(RaidRoom.ARCHIVE_GUARDIAN, "Archive Guardian");
        ROOM_NAMES.put(RaidRoom.EZEKIEL, "Ezekiel");
        ROOM_NAMES.put(RaidRoom.BORK, "Bork");
        ROOM_NAMES.put(RaidRoom.MOIA, "Moia");
        ROOM_NAMES.put(RaidRoom.DREADNOTH, "Dreadnoth");
        ROOM_NAMES.put(RaidRoom.DAGONHAI, "Dagonhai");
        ROOM_NAMES.put(RaidRoom.XARTH, "Xarth");
        ROOM_NAMES.put(RaidRoom.OCELLUS, "Ocellus");
        ROOM_NAMES.put(RaidRoom.LOOT, "Loot Room");
    }

    private TickLossLabels() {
    }

    /** The room's name as the game says it, e.g. "Archive Guardian" rather than ARCHIVE_GUARDIAN. */
    static String room(RaidRoom room) {
        if (room == null) return "Unknown";

        final String name = ROOM_NAMES.get(room);
        return name != null ? name : titleCase(room.name());
    }

    /**
     * A room and phase together, for the heading over the fight you are in: "Ocellus, phase 3".
     * <p>
     * Phases are internally "P1".."P4", which is fine in a log and terse in front of a player.
     */
    static String fight(RaidRoom room, String phaseName) {
        final String name = room(room);
        if (phaseName == null || phaseName.isEmpty()) return name;

        return name + ", " + phase(phaseName);
    }

    /** The same pair but short enough for a table row: "Ocellus (P3)". */
    static String fightShort(RaidRoom room, String phaseName) {
        final String name = room(room);
        return phaseName == null || phaseName.isEmpty() ? name : name + " (" + phaseName + ")";
    }

    /** "P3" as "phase 3", and anything that is not in that shape left alone. */
    static String phase(String phaseName) {
        if (phaseName == null || phaseName.isEmpty()) return "";

        if (phaseName.length() >= 2 && (phaseName.charAt(0) == 'P' || phaseName.charAt(0) == 'p')) {
            final String rest = phaseName.substring(1);
            if (rest.chars().allMatch(Character::isDigit)) return "phase " + rest;
        }
        return phaseName;
    }

    /** "1 tick" / "4 ticks", because "1 ticks" in a published plugin looks like a bug. */
    static String ticks(int n) {
        return n == 1 ? "1 tick" : n + " ticks";
    }

    /** A percentage with one decimal, e.g. "94.2%". */
    static String percent(double fraction) {
        return String.format("%.1f%%", fraction * 100d);
    }

    private static String titleCase(String enumName) {
        final StringBuilder out = new StringBuilder(enumName.length());
        for (String word : enumName.split("_")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(word.charAt(0)).append(word.substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        return out.toString();
    }
}

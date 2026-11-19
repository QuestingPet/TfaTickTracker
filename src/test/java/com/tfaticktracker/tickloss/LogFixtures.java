package com.tfaticktracker.tickloss;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.runelite.api.gameval.VarbitID;

/**
 * Cuts a small, self-contained slice out of a debug log, so a scenario can be kept as a test instead of as
 * gigabytes of raid.
 * <p>
 * {@code debug-logs/} reached 3.7 GB and 72 files, which made the only end-to-end regression net both slow
 * (a minute per run) and impossible to check in. These fixtures replace it: each is one fight, or one
 * mechanic, trimmed to the lines that change the answer, and small enough to live in the repo.
 * <p>
 * A slice cannot just be a tick range. By the time a fight starts, the log has already established the room,
 * the health-bar varbits and which NPCs are alive, and none of that is restated. So a fixture opens with a
 * CHECKPOINT: the state as it stood on the first tick of the slice, written as ordinary events, followed by
 * the real lines. {@link LogReplay} cannot tell the difference, which is the point -- the fixtures exercise
 * the same adapter path the live client drives, not a reimplementation of it.
 * <p>
 * Regenerate with {@code TICKLOSS_FIXTURES=write ./gradlew test --tests '*FixtureReplayTest*' -i}, which
 * needs the original logs present; everybody else just runs the fixtures.
 */
final class LogFixtures {

    /**
     * The varbits {@link LogReplay} actually reads. Every other VAR line is dropped, which is most of the
     * log: prayer drain, run energy and the rest tick over constantly and change nothing here.
     */
    private static final int[] WATCHED_VARBITS = {
            VarbitID.TFA_CLIENT_SPECTATE_MODE,
            VarbitID.HPBAR_HUD_BOSS,
            VarbitID.HPBAR_HUD_BASEHP,
            VarbitID.HPBAR_HUD_HP,
            VarbitID.MINIMAP_STATE,
            VarbitID.HPBAR_HUD_EXTRA_BASE,
            VarbitID.HPBAR_HUD_EXTRA_CURRENT,
    };

    private LogFixtures() {
    }

    /**
     * Write ticks {@code from}..{@code to} of {@code log} to {@code out} as a replayable fixture.
     *
     * @return a one-line note on what came out, for the regeneration log
     */
    static String slice(Path log, int from, int to, Path out) throws IOException {
        final Checkpoint checkpoint = scan(log, from);
        if (checkpoint.localPlayerHitsplat == null) {
            // Nothing had hit us yet by the start of the slice, which is normal for an early fight. The
            // replay identifies the local player from the first such line ANYWHERE in the log, so look
            // past the slice for one and re-stamp it into the checkpoint.
            findLocalPlayerAnywhere(log, checkpoint);
        }
        if (checkpoint.localPlayerHitsplat == null) {
            throw new IOException("no local player hitsplat anywhere in " + log);
        }

        Files.createDirectories(out.getParent());
        int kept = 0;

        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            for (String line : checkpoint.asEvents(from)) {
                w.write(line);
                w.write('\n');
                kept++;
            }

            try (BufferedReader in = Files.newBufferedReader(log, StandardCharsets.UTF_8)) {
                String line;
                while ((line = in.readLine()) != null) {
                    final int tab = line.indexOf('\t');
                    if (tab <= 0) continue;

                    final int tick;
                    try {
                        tick = Integer.parseInt(line.substring(0, tab));
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    if (tick < from) continue;
                    if (tick > to) break;
                    if (!keep(line, checkpoint.localPlayer)) continue;

                    w.write(line);
                    w.write('\n');
                    kept++;
                }
            }
        }

        return String.format("%-34s t%d-%d  %d lines  %d KB", out.getFileName(), from, to, kept,
                Files.size(out) / 1024);
    }

    /**
     * The first line that identifies us, from anywhere in the log.
     * <p>
     * Who the local player is comes from {@link LogReplay#findLocalPlayer}, so the fixture and the replay
     * can never disagree about it. The line itself is found again here because the checkpoint has to carry
     * a real one, re-stamped onto its first tick.
     */
    private static void findLocalPlayerAnywhere(Path log, Checkpoint into) throws IOException {
        final String me = LogReplay.findLocalPlayer(log);
        if (me == null) return;

        try (BufferedReader in = Files.newBufferedReader(log, StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.contains("\tHITSPLAT\tplayer name=" + me + " ") && line.contains("mine=true")) {
                    into.localPlayer = me;
                    into.localPlayerHitsplat = line;
                    return;
                }
            }
        }
    }

    /**
     * Whether this line can change the outcome. Everything else is noise that only costs disk.
     * <p>
     * An ALLOWLIST of the kinds {@link LogReplay} actually consumes, not a denylist of noise. A denylist
     * kept the 10123 GAME_OBJECT_SPAWNED, 8426 VARC_INT and 3045 GROUND_OBJECT_SPAWNED lines in a
     * 321-tick slice, which was 28 MB across eight fixtures -- more than the problem it was solving. The
     * same lesson as the attack-animation veto list: name what counts, not what does not.
     * <p>
     * A kind the replay starts reading must be added here, or the fixtures will quietly lack it. The switch
     * in {@code LogReplay.replay} is the list to check against.
     */
    private static boolean keep(String line, String me) {
        final int second = line.indexOf('\t', line.indexOf('\t') + 1);
        if (second < 0) return false;

        final String kind = line.substring(line.indexOf('\t') + 1, second);
        switch (kind) {
            case "TICK":
            case "RAID_ROOM_CHANGED":
            case "NPC_SPAWNED":
            case "NPC_CHANGED":
            case "NPC_DESPAWNED":
            case "ACTOR_DEATH":
            case "GRAPHICS_OBJECT":
            case "CHAT":
            case "MENU":
            case "INVENTORY":
            case "MOVE":
            case "MARK":
                return true;
            case "VAR":
                return watched(line);
            case "ANIMATION":
                // NPC animations drive the phase and the Ocellus siphon; of the players, only ours matters.
                return line.contains("\tANIMATION\tnpc") || line.contains("name=" + me + " ");
            case "HITSPLAT":
                // Only ours: the rest are other raiders', which the replay ignores by design.
                return line.contains("mine=true");
            case "INTERACTING":
                // The replay only reads our own retargeting, by matching this exact source prefix.
                return line.contains("source=[player name=" + me + "]");
            default:
                return false;
        }
    }

    private static boolean watched(String line) {
        final int at = line.indexOf("varbit=");
        if (at < 0) return false;

        int end = at + 7;
        while (end < line.length() && (line.charAt(end) == '-' || Character.isDigit(line.charAt(end)))) end++;

        final int id;
        try {
            id = Integer.parseInt(line.substring(at + 7, end));
        } catch (NumberFormatException e) {
            return false;
        }
        for (int watched : WATCHED_VARBITS) {
            if (watched == id) return true;
        }
        return false;
    }

    /** The state a slice has to be told about, because the log established it earlier and never repeats it. */
    private static final class Checkpoint {
        private String localPlayer;
        private String localPlayerHitsplat;
        private String room;
        private final Map<Integer, String> varbits = new LinkedHashMap<>();
        private final Map<String, String> aliveNpcs = new LinkedHashMap<>();

        /** The checkpoint as lines {@link LogReplay} will read exactly as it reads real ones. */
        private List<String> asEvents(int tick) {
            final List<String> out = new ArrayList<>();
            out.add("# fixture checkpoint, generated by LogFixtures -- see its javadoc");
            out.add(restamp(localPlayerHitsplat, tick));
            if (room != null) {
                out.add(tick + "\tRAID_ROOM_CHANGED\tprev=null new=" + room);
            }
            for (String spawn : aliveNpcs.values()) {
                out.add(restamp(spawn, tick));
            }
            // Varbits last: a room change resets the window, so the bar state must arrive after it.
            out.addAll(varbits.values().stream().map(l -> restamp(l, tick))
                    .collect(java.util.stream.Collectors.toList()));
            return out;
        }

        private static String restamp(String line, int tick) {
            return tick + line.substring(line.indexOf('\t'));
        }
    }

    /** Everything before {@code upToTick}, reduced to what the slice needs to know. */
    private static Checkpoint scan(Path log, int upToTick) throws IOException {
        final Checkpoint c = new Checkpoint();
        final Set<String> despawned = new LinkedHashSet<>();

        try (BufferedReader in = Files.newBufferedReader(log, StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                final int tab = line.indexOf('\t');
                if (tab <= 0) continue;

                final int tick;
                try {
                    tick = Integer.parseInt(line.substring(0, tab));
                } catch (NumberFormatException e) {
                    continue;
                }
                if (tick >= upToTick) break;

                final int second = line.indexOf('\t', tab + 1);
                if (second < 0) continue;
                final String kind = line.substring(tab + 1, second);
                final String detail = line.substring(second + 1);

                switch (kind) {
                    case "HITSPLAT":
                        if (c.localPlayer == null && detail.startsWith("player")
                                && detail.contains("mine=true")) {
                            final int nameAt = detail.indexOf("name=");
                            final int nameEnd = detail.indexOf(" type=", nameAt);
                            if (nameAt >= 0 && nameEnd > nameAt) {
                                c.localPlayer = detail.substring(nameAt + 5, nameEnd);
                                c.localPlayerHitsplat = line;
                            }
                        }
                        break;
                    case "RAID_ROOM_CHANGED": {
                        final int at = detail.indexOf("new=");
                        if (at >= 0) c.room = detail.substring(at + 4).trim();
                        break;
                    }
                    case "VAR":
                        if (watched(line)) {
                            final int at = line.indexOf("varbit=");
                            int end = at + 7;
                            while (end < line.length()
                                    && (line.charAt(end) == '-' || Character.isDigit(line.charAt(end)))) end++;
                            c.varbits.put(Integer.parseInt(line.substring(at + 7, end)), line);
                        }
                        break;
                    case "NPC_SPAWNED":
                        c.aliveNpcs.put(indexOf(detail), line);
                        break;
                    case "NPC_CHANGED":
                        // A transform replaces the spawn, so the checkpoint carries its CURRENT form.
                        c.aliveNpcs.put(indexOf(detail), line.replace("\tNPC_CHANGED\t", "\tNPC_SPAWNED\t"));
                        break;
                    case "NPC_DESPAWNED":
                        c.aliveNpcs.remove(indexOf(detail));
                        despawned.add(indexOf(detail));
                        break;
                    default:
                        break;
                }
            }
        }

        return c;
    }

    private static String indexOf(String detail) {
        final int at = detail.indexOf("index=");
        if (at < 0) return detail;

        int end = at + 6;
        while (end < detail.length() && Character.isDigit(detail.charAt(end))) end++;
        return detail.substring(at + 6, end);
    }
}

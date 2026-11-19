package com.tfaticktracker.tickloss;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.tfaticktracker.RaidRoom;
import com.tfaticktracker.RaidState;
import com.tfaticktracker.TfaTickTrackerConfig;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.util.Text;

/**
 * Everyone else's room summaries, self-reported over the RuneLite party.
 * <p>
 * Kept away from {@link TickLossTracker} so the thing that does the counting knows nothing about parties,
 * and so the one place that handles another client's data is one small file.
 * <p>
 * <b>A member counts only if they are in your party AND a raider in your raid.</b> Both, never either. The
 * party is a RuneLite construct that has nothing to do with who is in the instance, so a party-only filter
 * would put someone else's unrelated raid in your table as though it were comparable; a raid-only filter is
 * not possible, because a raider who is not in the party cannot send anything. The raid side comes from
 * {@link RaidState#getRaiderNames()}, which reads the game's own raider name varcs.
 * <p>
 * Nothing here is persisted, and nothing arriving here is trusted. See {@link TickLossRoomMessage}.
 */
@Slf4j
@Singleton
public class TickLossParty {

    /** Party is capped well below this; a bound on memory if a sender spoofs member ids. */
    private static final int MAX_MEMBERS = 16;
    /** A raid is seven rooms; this bounds what a wrong or hostile sender can make us keep. */
    private static final int MAX_ROOMS = 32;

    private final Client client;
    private final TfaTickTrackerConfig config;
    private final PartyService partyService;
    private final WSClient wsClient;
    private final RaidState raidState;

    /**
     * Bumped whenever anything the panel draws changes.
     * <p>
     * The panel watches this instead of rebuilding every tick: Swing work on a game tick is wasted effort
     * 99 ticks out of 100, and a panel that rebuilds under the cursor eats clicks.
     */
    @Getter
    private int revision;

    /** Member id to their running totals, in arrival order so the table does not reshuffle. */
    private final Map<Long, Progress> byMember = new LinkedHashMap<>();

    @Inject
    public TickLossParty(Client client, TfaTickTrackerConfig config, PartyService partyService,
                         WSClient wsClient, RaidState raidState) {
        this.client = client;
        this.config = config;
        this.partyService = partyService;
        this.wsClient = wsClient;
        this.raidState = raidState;
    }

    /** One room another raider finished, as THEY reported it. */
    @Getter
    @RequiredArgsConstructor
    public static final class Room {
        /** The room, or null if this build has never heard of it. */
        private final RaidRoom room;
        private final String phase;
        private final int lost;
        private final int counted;

        public String getLabel() {
            return TickLossLabels.fightShort(room, phase);
        }

        public double getEfficiency() {
            if (counted <= 0) return 1.0d;
            return Math.max(0.0d, 1.0d - ((double) lost / counted));
        }
    }

    /**
     * One member's raid so far, as THEY reported it.
     * <p>
     * Rooms are kept individually rather than only summed, because the side panel lays them out room by
     * room. Deliberately free of RuneLite types, so it is testable.
     */
    @Getter
    public static final class Progress {
        private final String name;
        /** Finished rooms, in the order they were finished, which is the order they were fought. */
        private final List<Room> rooms = new ArrayList<>();
        /** The sender's protocol version, so a field they could not have sent is shown as unknown. */
        private int version;

        Progress(String name) {
            this.name = name;
        }

        void add(Room room, int version) {
            // A raid is seven rooms. Anything beyond this is a sender that is wrong or hostile, and the
            // cost of believing it is unbounded memory.
            if (rooms.size() >= MAX_ROOMS) return;

            rooms.add(room);
            this.version = version;
        }

        public int getRaidLost() {
            int total = 0;
            for (Room room : rooms) total += room.getLost();
            return total;
        }

        public int getRaidCounted() {
            int total = 0;
            for (Room room : rooms) total += room.getCounted();
            return total;
        }

        /** The room that finished most recently, which is the one everyone is talking about. */
        public Room getLastRoom() {
            return rooms.isEmpty() ? null : rooms.get(rooms.size() - 1);
        }

        public int getLastRoomLost() {
            final Room last = getLastRoom();
            return last == null ? 0 : last.getLost();
        }

        public double getEfficiency() {
            final int counted = getRaidCounted();
            if (counted <= 0) return 1.0d;
            return Math.max(0.0d, 1.0d - ((double) getRaidLost() / counted));
        }
    }

    public void startUp() {
        wsClient.registerMessage(TickLossRoomMessage.class);
    }

    public void shutDown() {
        wsClient.unregisterMessage(TickLossRoomMessage.class);
        byMember.clear();
    }

    /** A new raid wipes everyone's numbers, the same as our own. */
    public void reset() {
        byMember.clear();
        revision++;
    }

    /**
     * A room of ours finished, so tell the party.
     * <p>
     * Sent on close rather than live. A segment's numbers are not final until it ends, and a running
     * broadcast of the room you are standing in is the per-attack information this deliberately does not
     * share.
     */
    void onSegmentClosed(SegmentStats segment) {
        // Our own room finishing changes the panel whether or not we share it.
        revision++;

        if (!config.shareWithParty()) return;
        if (segment == null || segment.getTicksCounting() <= 0 || segment.getRoom() == null) return;
        if (!partyService.isInParty() || partyService.getLocalMember() == null) return;
        // Only from inside a raid. Outside one there is no raider list for the far end to match us against,
        // so the message could only ever be discarded.
        if (!raidState.isInRaid()) return;

        final TickLossRoomMessage message = new TickLossRoomMessage();
        message.setV(TickLossRoomMessage.VERSION);
        message.setRoom(segment.getRoom().name());
        message.setPhase(segment.getPhaseName());
        message.setCounted(segment.getTicksCounting());
        message.setLost(segment.getLost());
        message.setLostToEating(segment.getLostToEating());
        message.setLostToDudShots(segment.getLostToDudShots());
        message.setAttacks(segment.getAttacks());
        message.setEarlyAttacks(segment.getEarlyAttacks());

        partyService.send(message);
    }

    @Subscribe
    public void onTickLossRoomMessage(TickLossRoomMessage message) {
        if (!message.isPlausible()) {
            log.debug("Tick loss: discarding an implausible party message from {}", message.getMemberId());
            return;
        }

        final PartyMember sender = partyService.getMemberById(message.getMemberId());
        if (sender == null) return;

        // Our own message comes back to us; our numbers come from the tracker, not from the wire.
        final PartyMember me = partyService.getLocalMember();
        if (me != null && me.getMemberId() == message.getMemberId()) return;

        final String name = sender.getDisplayName();
        if (name == null || !isRaiderInOurRaid(name)) return;

        if (!byMember.containsKey(message.getMemberId()) && byMember.size() >= MAX_MEMBERS) return;

        byMember.computeIfAbsent(message.getMemberId(), k -> new Progress(normalise(name)))
                .add(new Room(parseRoom(message.getRoom()), message.getPhase(),
                        message.getLost(), message.getCounted()), message.getV());
        revision++;
    }

    /**
     * Whether this party member is also in the raid with us.
     * <p>
     * A raider who joins the party late needs no catch-up: they simply start appearing from the next room
     * that closes, which is what the sender does anyway.
     */
    private boolean isRaiderInOurRaid(String displayName) {
        return accepts(raidState.isInRaid(), raidState.getRaiderNames(), displayName);
    }

    /**
     * The gate itself, with the client taken out of it so it can be read in a test.
     * <p>
     * This is the one rule in the plugin that decides whether another person's data is shown at all, and it
     * is not a rule a mock should be standing in for. Everything it needs is a boolean, a set of names and a
     * name, so none of it has to be.
     */
    static boolean accepts(boolean inRaid, Set<String> raiders, String displayName) {
        if (!inRaid || raiders == null || displayName == null) return false;

        final String wanted = normalise(displayName);
        if (wanted.isEmpty()) return false;

        for (String raider : raiders) {
            if (raider != null && normalise(raider).equals(wanted)) return true;
        }
        return false;
    }

    /**
     * One spelling of a name, so two sources of it can be compared.
     * <p>
     * The two sides come from different places -- the raid's own name varcs and the party's display name --
     * and {@link Text#sanitize} alone is not enough to make them agree. It strips an {@code <img>} prefix and
     * turns a non-breaking space into an ordinary one, but it does not trim, so a name decorated on one side
     * and not the other differs by a leading space and the raider silently never appears. Trimming can only
     * ever make a match more likely, and no real name begins or ends with a space.
     */
    static String normalise(String name) {
        return Text.sanitize(name).trim();
    }

    /**
     * Order a set of senders by the raid party, which is the order every raider's client sees.
     * <p>
     * Not arrival order: that differs per client, so two people comparing screens would be reading different
     * tables. Anyone the raid no longer lists goes last with their numbers intact, rather than vanishing
     * mid-raid because they logged out.
     */
    static List<Progress> inRaidOrder(Collection<Progress> members, Collection<String> raidOrder) {
        final List<Progress> sorted = new ArrayList<>(members);
        final List<String> order = new ArrayList<>(raidOrder);

        order.replaceAll(TickLossParty::normalise);

        sorted.sort((a, b) -> {
            final int ia = order.indexOf(normalise(a.getName()));
            final int ib = order.indexOf(normalise(b.getName()));
            return Integer.compare(ia < 0 ? Integer.MAX_VALUE : ia, ib < 0 ? Integer.MAX_VALUE : ib);
        });
        return sorted;
    }

    private static RaidRoom parseRoom(String name) {
        try {
            return RaidRoom.valueOf(name);
        } catch (IllegalArgumentException e) {
            // A room this build has never heard of, from a newer sender. The numbers are still theirs and
            // still comparable, so the row stays and only the label is unknown.
            return null;
        }
    }

    /**
     * Everyone to show, in the raid party's own order.
     * <p>
     * Ordered by the raid rather than by who happened to send first, so the list reads the same for
     * everybody and does not reshuffle as messages arrive. Anyone the raid no longer lists goes last,
     * keeping their numbers rather than vanishing.
     */
    public List<Progress> getOthers() {
        return inRaidOrder(byMember.values(), raidState.getRaiderNames());
    }

    /** The same list, or empty when the overlay's party section is switched off or we are alone. */
    public List<Progress> getParty() {
        if (!config.showParty() || !partyService.isInParty()) return java.util.Collections.emptyList();

        return getOthers();
    }
}

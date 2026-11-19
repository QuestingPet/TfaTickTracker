package com.tfaticktracker.tickloss;

import com.tfaticktracker.RaidRoom;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The window's own rules, driven directly rather than through a raid.
 * <p>
 * {@link LogReplayTest} covers these across sixty recorded raids, but a log can only show what actually
 * happened in one. These are the orderings and edge cases that have gone wrong, written down so they stay
 * fixed.
 */
public class FightWindowStateTest {

    private static final int AG_INDEX = 100;

    private static FightWindowState.Tick tick(int n, RaidRoom room) {
        return new FightWindowState.Tick()
                .tick(n)
                .room(room)
                .enabled(true)
                .inRaid(true)
                .localPlayerPresent(true)
                .bossBar(1)
                .bossBaseHp(1030)
                .bossHp(1030);
    }

    /** Walk in, the boss goes live, and the window opens a tick later as the Archive Guardian requires. */
    private static FightWindowState liveArchiveGuardian() {
        final FightWindowState window = new FightWindowState();
        window.roomChanged(RaidRoom.ARCHIVE_GUARDIAN, Collections.emptyList());
        window.npcSeen(AG_INDEX, NpcID.TFA_GUARDIAN);
        window.update(tick(0, RaidRoom.ARCHIVE_GUARDIAN));   // OPENING, the start delay
        return window;
    }

    @Test
    public void countsOnceTheGuardianIsLive() {
        final FightWindowState window = liveArchiveGuardian();
        assertEquals(FightWindowState.State.COUNTING, window.update(tick(1, RaidRoom.ARCHIVE_GUARDIAN)));
    }

    /**
     * Entering a raid resets this object AFTER the room change has already been announced, because
     * {@code RaidState} posts RaidRoomChanged and only then InRaidChanged. Remembering the room from the event
     * meant the reset wiped it, and since the Archive Guardian is the first room and the room does not change
     * again until you leave, the whole fight read as NOT_A_FIGHT_ROOM.
     */
    @Test
    public void aResetMidRoomDoesNotLoseTheRoom() {
        final FightWindowState window = liveArchiveGuardian();

        window.reset();
        window.npcSeen(AG_INDEX, NpcID.TFA_GUARDIAN);

        // The room is still the Archive Guardian, because it is read fresh rather than remembered.
        window.update(tick(1, RaidRoom.ARCHIVE_GUARDIAN));
        assertEquals(FightWindowState.State.COUNTING, window.update(tick(2, RaidRoom.ARCHIVE_GUARDIAN)));
    }

    /** Nothing is measured outside a fight room, however live the rest of the state looks. */
    @Test
    public void lobbyIsNotAFightRoom() {
        final FightWindowState window = liveArchiveGuardian();
        assertEquals(FightWindowState.State.NOT_A_FIGHT_ROOM, window.update(tick(1, RaidRoom.LOBBY)));
    }

    @Test
    public void nullRoomIsNotAFightRoom() {
        final FightWindowState window = liveArchiveGuardian();
        assertEquals(FightWindowState.State.NOT_A_FIGHT_ROOM, window.update(tick(1, null)));
    }

    /** The health bar is what says a fight is live; NPC presence alone opened 15 ticks early at Bork. */
    @Test
    public void doesNotCountBeforeTheHealthBarComesUp() {
        final FightWindowState window = new FightWindowState();
        window.roomChanged(RaidRoom.BORK, Collections.emptyList());
        window.npcSeen(200, NpcID.TFA_BORK);

        assertEquals(FightWindowState.State.NOT_STARTED,
                window.update(tick(0, RaidRoom.BORK).bossBar(0)));
        assertEquals(FightWindowState.State.COUNTING, window.update(tick(1, RaidRoom.BORK)));
    }

    /** Teleporting between rooms blanks the minimap, and those ticks are not yours to lose. */
    @Test
    public void theRoomTransitionIsNotCounted() {
        final FightWindowState window = liveArchiveGuardian();
        assertEquals(FightWindowState.State.ROOM_TRANSITION,
                window.update(tick(1, RaidRoom.ARCHIVE_GUARDIAN)
                        .minimapState(FightWindowState.MINIMAP_HIDDEN)));
    }

    /** Spectating reports inRaid, so this guard is load bearing. */
    @Test
    public void spectatingIsNotCounted() {
        final FightWindowState window = liveArchiveGuardian();
        assertEquals(FightWindowState.State.SPECTATING,
                window.update(tick(1, RaidRoom.ARCHIVE_GUARDIAN)
                        .spectateMode(FightWindowState.OWN_SPECTATE_MODE)));
    }

    @Test
    public void dyingStopsTheCount() {
        final FightWindowState window = liveArchiveGuardian();
        window.localPlayerDied();
        assertEquals(FightWindowState.State.PLAYER_DEAD, window.update(tick(1, RaidRoom.ARCHIVE_GUARDIAN)));
    }

    @Test
    public void theBossDyingEndsTheFight() {
        final FightWindowState window = liveArchiveGuardian();
        window.npcDied(AG_INDEX, NpcID.TFA_GUARDIAN);
        assertEquals(FightWindowState.State.BOSS_DEAD, window.update(tick(1, RaidRoom.ARCHIVE_GUARDIAN)));
    }

    /**
     * At Bork the fight is over only when BOTH are down, in either order. The Smorks never dies in a way the
     * client can see -- its end is the extra health bar emptying.
     */
    @Test
    public void borkIsNotOverWhileTheSmorksLives() {
        final FightWindowState window = new FightWindowState();
        window.roomChanged(RaidRoom.BORK, Collections.emptyList());
        window.npcSeen(200, NpcID.TFA_BORK);
        window.npcSeen(201, NpcID.TFA_SMORK);
        window.update(tick(0, RaidRoom.BORK).extraBarBase(1).extraBarCurrent(900));

        window.npcDied(200, NpcID.TFA_BORK);
        assertEquals("Bork is down but the Smorks is not",
                FightWindowState.State.COUNTING,
                window.update(tick(1, RaidRoom.BORK).extraBarBase(1).extraBarCurrent(400)));

        assertEquals("the extra bar emptying is the Smorks dying",
                FightWindowState.State.BOSS_DEAD,
                window.update(tick(2, RaidRoom.BORK).extraBarBase(1).extraBarCurrent(0)));
    }

    /** And the other way round, because either can go first. */
    @Test
    public void borkIsNotOverWhileBorkLives() {
        final FightWindowState window = new FightWindowState();
        window.roomChanged(RaidRoom.BORK, Collections.emptyList());
        window.npcSeen(200, NpcID.TFA_BORK);
        window.npcSeen(201, NpcID.TFA_SMORK);
        window.update(tick(0, RaidRoom.BORK).extraBarBase(1).extraBarCurrent(900));

        assertEquals("the Smorks is down but Bork is not",
                FightWindowState.State.COUNTING,
                window.update(tick(1, RaidRoom.BORK).extraBarBase(1).extraBarCurrent(0)));

        window.npcDied(200, NpcID.TFA_BORK);
        assertEquals(FightWindowState.State.BOSS_DEAD, window.update(tick(2, RaidRoom.BORK)));
    }

    /** An empty extra bar means nothing until it has been seen full, or a bar that is simply not up reads as a death. */
    @Test
    public void anExtraBarThatWasNeverUpIsNotADeath() {
        final FightWindowState window = new FightWindowState();
        window.roomChanged(RaidRoom.BORK, Collections.emptyList());
        window.npcSeen(200, NpcID.TFA_BORK);
        window.npcSeen(201, NpcID.TFA_SMORK);

        window.update(tick(0, RaidRoom.BORK).extraBarBase(0).extraBarCurrent(0));
        assertEquals(FightWindowState.State.COUNTING,
                window.update(tick(1, RaidRoom.BORK).extraBarBase(0).extraBarCurrent(0)));
    }

    /** The Archive Guardian is not reachable on the tick it transforms, so that tick is not counted. */
    @Test
    public void theGuardianNeedsATickBeforeItCanBeHit() {
        final FightWindowState window = new FightWindowState();
        window.roomChanged(RaidRoom.ARCHIVE_GUARDIAN, Collections.emptyList());
        window.npcSeen(AG_INDEX, NpcID.TFA_GUARDIAN);

        assertEquals(FightWindowState.State.OPENING, window.update(tick(0, RaidRoom.ARCHIVE_GUARDIAN)));
        assertEquals(FightWindowState.State.COUNTING, window.update(tick(1, RaidRoom.ARCHIVE_GUARDIAN)));
    }

    // --- which closes settle an outstanding wait, and which merely stop measuring -----------------------

    /**
     * The fight taking the chance to attack away from you, which makes an expired cooldown real loss.
     * <p>
     * Death belongs here: {@code ActorDeath} on the local player fires on the tick the killing hitsplat
     * lands, not when the respawn finishes, so the ticks settled are ones you were alive and ready for.
     */
    @Test
    public void theFightEndingIsSettled() {
        assertTrue(FightWindowState.endsTheFight(FightWindowState.State.BOSS_DEAD));
        assertTrue(FightWindowState.endsTheFight(FightWindowState.State.PHASE_ENDING));
        assertTrue(FightWindowState.endsTheFight(FightWindowState.State.BOSS_UNATTACKABLE));
        assertTrue(FightWindowState.endsTheFight(FightWindowState.State.PLAYER_DEAD));
    }

    /** The measurement stopping. Billing these would charge for walking between rooms. */
    @Test
    public void theMeasurementStoppingIsNotSettled() {
        assertFalse(FightWindowState.endsTheFight(FightWindowState.State.COUNTING));
        assertFalse(FightWindowState.endsTheFight(FightWindowState.State.NOT_IN_RAID));
        assertFalse(FightWindowState.endsTheFight(FightWindowState.State.SPECTATING));
        assertFalse(FightWindowState.endsTheFight(FightWindowState.State.NOT_A_FIGHT_ROOM));
        assertFalse(FightWindowState.endsTheFight(FightWindowState.State.ROOM_TRANSITION));
        assertFalse(FightWindowState.endsTheFight(FightWindowState.State.NO_TARGET_YET));
        assertFalse(FightWindowState.endsTheFight(FightWindowState.State.NOT_STARTED));
        assertFalse(FightWindowState.endsTheFight(FightWindowState.State.OPENING));
        assertFalse(FightWindowState.endsTheFight(FightWindowState.State.DISABLED));
    }

    /** Every state is classified one way or the other, so a new one cannot slip through unconsidered. */
    @Test
    public void everyStateIsClassified() {
        for (FightWindowState.State state : FightWindowState.State.values()) {
            // Fails to compile-time-check anything, but fails loudly if a state is added and this test is
            // not updated -- the two lists above must between them name all of them.
            FightWindowState.endsTheFight(state);
        }
        assertEquals("a new State needs adding to one of the two tests above", 13,
                FightWindowState.State.values().length);
    }
}

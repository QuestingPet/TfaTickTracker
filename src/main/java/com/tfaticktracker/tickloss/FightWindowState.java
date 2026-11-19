package com.tfaticktracker.tickloss;

import com.tfaticktracker.RaidRoom;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.HashSet;
import java.util.Set;

/**
 * Whether this tick counts, as a pure state machine. No client, no events, no RuneLite types.
 * <p>
 * The window is driven by {@link RaidNpcs}: it is open while something in this room's required set is alive.
 * That is deliberately the primary signal rather than the boss health bar, because every boss presents its real
 * target by transforming into it, so NPC transforms land exactly on the moment the fight becomes live and the
 * health bar does not. The bar is kept as a visible cross-check only.
 * <p>
 * It closes for: the designated boss dying (Xarth's bar lingers 14 ticks past that), a phase having no target
 * yet (Ocellus P2's sword hitbox spawns 7 ticks after the transform), the room transition fade, the local
 * player being dead, and spectating.
 * <p>
 * {@link FightWindow} is the adapter that reads the client and feeds this; nothing else should.
 */
public final class FightWindowState {

    // Spectate modes: 1 is watching your own raid after dying, 2 is someone else's raid entirely
    public static final int OWN_SPECTATE_MODE = 1;
    public static final int EXTERNAL_SPECTATE_MODE = 2;
    /** The raid blanks the minimap while teleporting between rooms. */
    public static final int MINIMAP_HIDDEN = 2;
    /** Ticks after a boss comes to rest before it can be hit. See {@link #checkArrived}. */
    private static final int ARRIVAL_REOPEN_DELAY = 1;

    /** Why we are not counting this tick. {@link #COUNTING} means we are. */
    public enum State {
        COUNTING,
        NOT_IN_RAID,
        SPECTATING,
        NOT_A_FIGHT_ROOM,
        ROOM_TRANSITION,
        NO_TARGET_YET,
        NOT_STARTED,
        /** Everything is in place but the boss is not reachable for another tick or two. */
        OPENING,
        BOSS_UNATTACKABLE,
        PHASE_ENDING,
        BOSS_DEAD,
        PLAYER_DEAD,
        DISABLED,
    }

    /**
     * Whether this state means the FIGHT ended, as opposed to the measurement stopping.
     * <p>
     * The difference decides whether an expired cooldown is billed when the window shuts
     * ({@link AttackTimeline#settle}). A boss dying, a phase handing over, the boss stepping out of reach and
     * your own death are all the fight taking the chance to attack away from you, and whatever wait was
     * outstanding was really yours. Leaving the raid, spectating, changing room, waiting for a phase's target
     * to appear or switching the plugin off are not: nothing was lost, we merely stopped watching, and
     * billing those would charge for walking between rooms.
     * <p>
     * Death is in the first group because {@code ActorDeath} on the local player fires on the tick the killing
     * hitsplat lands, not when the respawn finishes -- verified at tick 428 of
     * debug-logs/tfa-events-20261114-115313.log, where it arrives with the hitsplat that did it. So the ticks
     * being settled are ones you really were alive, ready, and not attacking for.
     */
    public static boolean endsTheFight(State state) {
        switch (state) {
            case BOSS_DEAD:
            case PHASE_ENDING:
            case BOSS_UNATTACKABLE:
            case PLAYER_DEAD:
                return true;
            default:
                return false;
        }
    }

    /**
     * Whether this state means the ROOM is over, as opposed to the fight merely pausing.
     * <p>
     * Narrower than {@link #endsTheFight}, and used for a different decision. That one asks whether an
     * expired cooldown should be billed, and a boss leaping out of reach qualifies -- the chance to attack
     * was taken away. This one asks whether the room's numbers are FINAL, which is when the summary is
     * published and the party is told, and a leap is emphatically not that: the boss lands a few ticks
     * later and the fight carries on. Closing on it would cut every Ocellus phase into quadrant-sized
     * pieces.
     * <p>
     * Both of these hold until a new fight starts: {@link State#BOSS_DEAD} until something else worth
     * hitting turns up in the room, {@link State#PLAYER_DEAD} until you are alive in a fight again. That
     * is what makes them safe to close on -- nothing re-opens behind them by accident, and a practice-mode
     * retry re-opens on purpose.
     * <p>
     * {@link State#PHASE_ENDING} is NOT here, though it reads like it belongs. The empty health bar is an
     * early warning, not the end: in tickloss-fixtures/ocellus-all-phases.log P1's bar empties at tick
     * 3187 and three more ticks are still counted before the transform at 3204. Closing on it cut P1 into
     * two rows, a 197-tick one and a 3-tick one. The transform advances the phase label, which closes the
     * phase anyway and is the moment the phase actually ends.
     */
    public static boolean endsTheRoom(State state) {
        switch (state) {
            case BOSS_DEAD:
            case PLAYER_DEAD:
                return true;
            default:
                return false;
        }
    }

    /** What the client looked like on one tick. */
    @Setter
    @Accessors(fluent = true, chain = true)
    public static final class Tick {
        private int tick;
        /**
         * The room we are in, read fresh every tick rather than remembered from a room-change event.
         * <p>
         * Latching it was a bug. {@code RaidState} posts RaidRoomChanged and only then InRaidChanged, and the
         * latter resets this whole object -- so entering the raid set the room and then immediately cleared
         * it, and since the Archive Guardian is the first room and the room does not change again until you
         * leave, the entire fight read as NOT_A_FIGHT_ROOM. Polling cannot go stale.
         */
        private RaidRoom room;
        private boolean enabled;
        private boolean inRaid;
        private boolean localPlayerPresent = true;
        private int spectateMode;
        private int bossBar;
        private int bossBaseHp;
        private int bossHp;
        private int minimapState;
        private int extraBarBase;
        private int extraBarCurrent;
        /** Where the NPC named by {@link #getUnattackableNpcIndex()} stands now, or null if unknown. */
        private Integer unattackableX;
        private Integer unattackableY;
    }

    private final Set<Integer> requiredAlive = new HashSet<>();
    private final Set<Integer> endsFightAlive = new HashSet<>();

    @Getter
    private State state = State.NOT_IN_RAID;
    @Getter
    private RaidRoom room;
    @Getter
    private boolean bossBarUp;
    @Getter
    private int bossBaseHp;
    @Getter
    private int bossHp;
    @Getter
    private int phase;
    @Getter
    private String phaseLabel;
    @Getter
    private boolean bossDeathSeen;
    @Getter
    private int requiredAliveCount;
    @Getter
    private boolean fightStarted;
    @Getter
    private boolean bossUnattackable;
    /** Tick the window's conditions first held, or -1. */
    @Getter
    private int openedAtTick = -1;
    /** Scene index of the NPC that went out of reach, or -1. The adapter feeds us its position each tick. */
    @Getter
    private int unattackableNpcIndex = -1;

    private Integer unattackableFromX;
    private Integer unattackableFromY;
    private Integer unattackableLastX;
    private Integer unattackableLastY;
    private int reopenAtTick = -1;
    private int reopenDelay;
    private int phaseNpcId = -1;
    private boolean localPlayerDead;
    private int extraBarNpcIndex = -1;
    private boolean extraBarArmed;
    private int openingDelay;

    public void reset() {
        requiredAlive.clear();
        endsFightAlive.clear();
        state = State.NOT_IN_RAID;
        // The room is NOT cleared. It is an input, refreshed every tick, and clearing it leaves a gap in
        // which npcSeen would test arrivals against no room at all and throw them away -- which is how the
        // Archive Guardian ended up with no target after the reset that entering a raid triggers.
        bossBarUp = false;
        bossBaseHp = 0;
        bossHp = 0;
        phase = 0;
        phaseLabel = null;
        bossDeathSeen = false;
        requiredAliveCount = 0;
        phaseNpcId = -1;
        openedAtTick = -1;
        localPlayerDead = false;
        fightStarted = false;
        clearUnattackable();
        reopenDelay = 0;
        extraBarNpcIndex = -1;
        extraBarArmed = false;
        openingDelay = 0;
    }

    public boolean isCounting() {
        return state == State.COUNTING;
    }

    // --- what the world did ---

    /**
     * Per-fight state is scoped to the room. It must NOT be cleared merely because the target sets are empty for
     * a moment: Ocellus hands off from P1 to the P2 sword hitbox to P3 with several ticks of nothing attackable
     * in between, and clearing fightStarted there would shut the window for the rest of the fight, since the
     * health bar only goes up once per room.
     * <p>
     * {@code present} is every NPC in the scene. A room's NPCs can spawn on the SAME tick as the room change,
     * and the spawn is dispatched first, so they get tested against the PREVIOUS room and thrown away -- which
     * left the window shut for a whole fight with no targets while the boss stood there. Seen at Bork and Xarth
     * in debug-logs/tfa-events-20261112-211532.log (ticks 961 and 1431). Rescanning here fixes the race.
     */
    public void roomChanged(RaidRoom newRoom, Iterable<int[]> present) {
        room = newRoom;
        fightStarted = false;
        bossDeathSeen = false;
        clearUnattackable();
        phase = 0;
        phaseLabel = null;
        phaseNpcId = -1;

        requiredAlive.clear();
        endsFightAlive.clear();
        extraBarNpcIndex = -1;
        extraBarArmed = false;

        if (present == null) return;
        for (int[] npc : present) {
            npcSeen(npc[0], npc[1]);
        }
    }

    /** An NPC spawned, or transformed into another form. Bosses present their real target by transforming. */
    public void npcSeen(int index, int id) {
        if (RaidNpcs.getRequired(room).contains(id)) {
            if (requiredAlive.add(index)) {
                if (requiredAlive.size() == 1) {
                    // This NPC is what opens the window, so its own delay is the one that applies
                    openingDelay = RaidNpcs.startDelayTicks(id);

                    if (endsFightAlive.isEmpty()) {
                        // First target of a new fight in this room
                        bossDeathSeen = false;
                        // A new fight means we got here alive, so clear a death from an earlier room
                        localPlayerDead = false;
                    }
                }
            }
            advancePhase(id);
        } else {
            // Transformed into something that is not a target (Ocellus P2 before its sword hitbox spawns)
            requiredAlive.remove(index);
        }

        if (RaidNpcs.getEndsFight(room).contains(id)) {
            endsFightAlive.add(index);
        } else {
            endsFightAlive.remove(index);
        }

        if (RaidNpcs.extraBarNpc(room) == id) {
            extraBarNpcIndex = index;
        }
    }

    public void npcGone(int index) {
        requiredAlive.remove(index);
        endsFightAlive.remove(index);
    }

    /**
     * A required NPC going out of reach, and coming back. The leap animation starts it; its next real animation,
     * once it has landed, ends it.
     */
    public void npcAnimation(int index, int animation) {
        if (!requiredAlive.contains(index)) return;

        if (RaidNpcs.leavesUnattackable(room, animation)) {
            bossUnattackable = true;
            unattackableNpcIndex = index;
            unattackableFromX = null;
            unattackableFromY = null;
            unattackableLastX = null;
            unattackableLastY = null;
            reopenAtTick = -1;
            reopenDelay = RaidNpcs.reopenDelayTicks(room, animation);
        } else if (animation != -1 && bossUnattackable && !RaidNpcs.staysUnattackable(room, animation)) {
            clearUnattackable();
        }
    }

    /**
     * A boss picking a player to fight is the most universal "it is live" signal there is -- it needs no
     * per-boss animation. Measured per boss across debug-logs/, the first hit lands 1 to 8 ticks AFTER it
     * starts targeting someone, for every boss. So if it has picked a target, it is reachable.
     */
    public void npcTargetsPlayer(int tick, int index) {
        if (bossUnattackable && requiredAlive.contains(index)) {
            // Reopening on the targeting tick itself is right coming out of the phase 3 transition (first
            // attack lands on it in 44 of 52), but a quadrant leap takes one more tick (+1 in 96 cases).
            reopenAtTick = tick + reopenDelay;
        }
    }

    public void npcDied(int index, int id) {
        if (RaidNpcs.getEndsFight(room).contains(id)) {
            bossDeathSeen = true;
            endsFightAlive.remove(index);
        }
        // Treat a death as gone, because the despawn lags it by the whole death animation
        requiredAlive.remove(index);
    }

    public void localPlayerDied() {
        localPlayerDead = true;
    }

    // --- the tick ---

    public State update(Tick in) {
        room = in.room;
        readHudVarbits(in);
        checkArrived(in);

        final State computed = computeState(in);

        if (computed != State.COUNTING) {
            openedAtTick = -1;
            state = computed;
        } else {
            // The tick the conditions first hold IS counted. An earlier version skipped it, on the basis that a
            // boss cannot be hit on the tick it goes live -- but the logs disagree: across every boss, the first
            // hit lands 2 to 4 ticks after the health bar, exactly what an attack launched ON the bar tick plus
            // projectile travel looks like. Skipping it threw away real attacks and left their cooldown
            // uncredited.
            if (openedAtTick == -1) {
                openedAtTick = in.tick;
            }
            // ...but some targets are not reachable on the tick they turn up. See RaidNpcs.START_DELAY.
            state = in.tick - openedAtTick < openingDelay ? State.OPENING : State.COUNTING;
        }

        requiredAliveCount = requiredAlive.size();
        return state;
    }

    private State computeState(Tick in) {
        if (!in.enabled) return State.DISABLED;
        if (!in.inRaid) return State.NOT_IN_RAID;

        if (in.spectateMode == EXTERNAL_SPECTATE_MODE || in.spectateMode == OWN_SPECTATE_MODE) {
            // The raid reports inRaid while spectating, so this guard is load bearing. Mode 1 is what you get a
            // few ticks after dying, before the raid moves you out.
            return State.SPECTATING;
        }

        if (!RaidNpcs.isFightRoom(room)) return State.NOT_A_FIGHT_ROOM;
        if (in.minimapState == MINIMAP_HIDDEN) return State.ROOM_TRANSITION;
        if (!in.localPlayerPresent) return State.NOT_IN_RAID;
        if (localPlayerDead) return State.PLAYER_DEAD;

        // A designated boss died and nothing else that would end the fight is left standing.
        //
        // Order independent on purpose: bossDeathSeen only says that at least one fight-ending NPC has gone
        // down, and the window does not shut until endsFightAlive is empty, so whichever dies first the other
        // keeps it open. Bork is the room this matters in -- Bork dies first in 157 of the 159 fights in
        // debug-logs/ that show both deaths and the Smorks dies first in 1, and in 6 more the Smorks goes down
        // while Bork never does at all. Either way there is still tick loss to be had on the survivor.
        if (bossDeathSeen && endsFightAlive.isEmpty()) return State.BOSS_DEAD;

        // Nothing we are supposed to be hitting exists: pre-fight, between phases, or after the kill
        if (requiredAlive.isEmpty()) return State.NO_TARGET_YET;

        // The boss is mid-leap and there is nothing to hit until it lands
        if (bossUnattackable) return State.BOSS_UNATTACKABLE;

        // An empty health bar means the phase is over and there is nothing left to hit until the next form
        // turns up. At Ocellus P1 the bar empties around 10 ticks before the transform and every raider stops
        // there. Only where a phase can actually end: at Bork the bar reads empty while the Smorks are still
        // being killed, and the blocked-attack report showed attacks right through it, up to +22.
        if (RaidNpcs.hasPhases(room) && bossBarUp && bossBaseHp > 0 && bossHp == 0) {
            return State.PHASE_ENDING;
        }

        // The health bar is what says the fight is actually live. Required for the window to OPEN but not to
        // keep it open: Bork and the Smorks spawn already in their final form with no transform, so NPC
        // presence alone opened 15 ticks early, while Dreadnoth's bar clears ~9 ticks before the last jelly dies.
        if (!fightStarted) return State.NOT_STARTED;

        return State.COUNTING;
    }

    /**
     * Read the HUD's health bars and the minimap state, every tick.
     * <p>
     * Polled rather than latched from a change event, because a latch only ever learns about a bar that CHANGES
     * while we are listening. Switch the plugin on midway through a fight and the boss bar is already up, so no
     * event ever arrives and {@link #fightStarted} stays false for the whole room: in
     * debug-logs/tfa-events-20261102-163705.log, whose first line is already inside the Archive Guardian, that
     * accounted for 159 of the 169 attacks charged to NOT_STARTED across every log.
     */
    private void readHudVarbits(Tick in) {
        bossBarUp = in.bossBar == 1;
        if (bossBarUp) {
            // Sticky on purpose: required to OPEN the window, not to keep it open. Dreadnoth's bar clears about
            // 9 ticks before the last jelly dies.
            fightStarted = true;
        }
        bossBaseHp = in.bossBaseHp;
        bossHp = in.bossHp;

        if (in.extraBarBase == 0) {
            extraBarArmed = false;
        } else if (in.extraBarCurrent > 0) {
            extraBarArmed = true;
        } else if (extraBarArmed) {
            extraBarArmed = false;
            extraBarEmptied();
        }
    }

    /**
     * The room's second health bar just ran out, so whatever it was tracking is dead. This is the only usable
     * death signal for the Smorks, which never fires a death at all -- see {@link RaidNpcs#extraBarNpc}.
     */
    private void extraBarEmptied() {
        if (RaidNpcs.extraBarNpc(room) == -1 || extraBarNpcIndex == -1) return;

        if (RaidNpcs.getEndsFight(room).contains(RaidNpcs.extraBarNpc(room))) {
            bossDeathSeen = true;
        }
        endsFightAlive.remove(extraBarNpcIndex);
        requiredAlive.remove(extraBarNpcIndex);
        extraBarNpcIndex = -1;
    }

    /**
     * The boss is reachable again the moment it turns up somewhere else. Checked every tick rather than from an
     * event, because its arrival is a move, not an animation.
     */
    private void checkArrived(Tick in) {
        if (!bossUnattackable) return;

        if (reopenAtTick != -1 && in.tick >= reopenAtTick) {
            clearUnattackable();
            return;
        }

        if (unattackableNpcIndex == -1) return;

        // The NPC that left is no longer one of this room's targets, so it can never "arrive" and end the
        // window. Ocellus P1 does exactly this: its transformation animation opens the window, then it turns
        // into the P2 body in place -- same index, no move, and no longer required -- which left the window
        // stuck shut for the whole of P2 with nothing counted.
        if (!requiredAlive.contains(unattackableNpcIndex)) {
            clearUnattackable();
            return;
        }

        if (in.unattackableX == null || in.unattackableY == null) return;

        if (unattackableFromX == null) {
            unattackableFromX = in.unattackableX;
            unattackableFromY = in.unattackableY;
            unattackableLastX = in.unattackableX;
            unattackableLastY = in.unattackableY;
            return;
        }

        // Arrived means moved AND come to rest. Testing "moved" alone fired on the first step of a walk: after
        // the Ocellus P2 to P3 transform he walks over four ticks, and the window reopened on the first step
        // while he was still on his way.
        final boolean moved = !in.unattackableX.equals(unattackableFromX)
                || !in.unattackableY.equals(unattackableFromY);
        final boolean atRest = in.unattackableX.equals(unattackableLastX)
                && in.unattackableY.equals(unattackableLastY);
        unattackableLastX = in.unattackableX;
        unattackableLastY = in.unattackableY;

        if (moved && atRest && reopenAtTick == -1) {
            // Arriving is not the same as being hittable: it is reachable the tick AFTER it comes to rest,
            // always, whatever took it away. A quadrant lands at +17, is at rest at +18 and the first attack
            // fires at +19 -- 53, 52 and 72 of the first three quadrants in debug-logs/, against 6 of 360 on
            // the arrival tick itself. The P3 entry walk is the same shape one tick apart: in
            // tfa-events-20261113-144608.log he moves at 1439, is at rest at 1440 and the first attack lands at
            // 1441. This is why the delay lives here and not in reopenDelay, which belongs to the re-target
            // path: on that entry he picks a target at 1441 and is hittable on that very tick.
            reopenAtTick = in.tick + ARRIVAL_REOPEN_DELAY;
        }
    }

    private void clearUnattackable() {
        bossUnattackable = false;
        unattackableNpcIndex = -1;
        unattackableFromX = null;
        unattackableFromY = null;
        unattackableLastX = null;
        unattackableLastY = null;
        reopenAtTick = -1;
    }

    private void advancePhase(int npcId) {
        final String label = RaidNpcs.getPhaseLabel(npcId);
        if (label == null) return;
        if (npcId == phaseNpcId) return;

        if (phaseNpcId != -1) {
            phase++;
        }
        phaseNpcId = npcId;
        phaseLabel = label;
    }
}

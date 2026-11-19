package com.tfaticktracker.tickloss;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.tfaticktracker.RaidRoom;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarbitID;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Which NPCs mean "there is something you are supposed to be hitting right now".
 * <p>
 * This table, not the boss health bar, is what opens and closes the fight window. Every boss in the raid presents its
 * real target by transforming into it, which is why the window is driven by NpcChanged:
 * <ul>
 *     <li>Archive Guardian spawns as TFA_GUARDIAN_START on room entry and only becomes TFA_GUARDIAN 55 ticks later,
 *     on the same tick the health bar appears -- so the walk-in is excluded for free</li>
 *     <li>Ocellus P1 transforms into P2, but P2 itself is not what you hit: the sword hitbox spawns 7 ticks later,
 *     and those 7 ticks are unavoidable, so P2 is deliberately not in this set</li>
 *     <li>Xarth's corpses are attackable but optional, so they are not in this set either</li>
 *     <li>Dreadnoth never dies, it splits, so its minions keep the window open past the health bar</li>
 * </ul>
 * All of that is game knowledge the client cannot derive: corpses and jellies look identical to it.
 */
final class RaidNpcs {

    /** NPCs whose presence means the fight is live. */
    private static final Map<RaidRoom, Set<Integer>> REQUIRED = ImmutableMap.<RaidRoom, Set<Integer>>builder()
            // TFA_GUARDIAN_START is the inert pre-fight form and is excluded
            .put(RaidRoom.ARCHIVE_GUARDIAN, ImmutableSet.of(
                    NpcID.TFA_GUARDIAN,
                    NpcID.TFA_GUARDIAN_UNSHIELDED))
            .put(RaidRoom.EZEKIEL, ImmutableSet.of(
                    NpcID.TFA_EZEKIEL,
                    NpcID.TFA_EZEKIEL_SWORD))
            .put(RaidRoom.BORK, ImmutableSet.of(
                    NpcID.TFA_BORK,
                    NpcID.TFA_SMORK))
            .put(RaidRoom.MOIA, ImmutableSet.of(
                    NpcID.TFA_MOIA,
                    NpcID.TFA_MOIA_MINION))
            // The boss never dies, it splits -- the minions keep this window open past the health bar
            .put(RaidRoom.DREADNOTH, ImmutableSet.of(
                    NpcID.TFA_JELLY_BOSS,
                    NpcID.TFA_JELLY_MINION))
            .put(RaidRoom.DAGONHAI, ImmutableSet.of(
                    NpcID.TFA_DAGONHAI_LEADER,
                    NpcID.TFA_DAGONHAI_LEADER_UNSHIELDED,
                    NpcID.TFA_DAGONHAI_MINION_1,
                    NpcID.TFA_DAGONHAI_MINION_1_UNSHIELDED,
                    NpcID.TFA_DAGONHAI_MINION_2,
                    NpcID.TFA_DAGONHAI_MINION_2_UNSHIELDED,
                    NpcID.TFA_DAGONHAI_DEMON))
            // TFA_XARTH_CORPSE is intentionally absent: corpses are optional, killing them is not required
            .put(RaidRoom.XARTH, ImmutableSet.of(
                    NpcID.TFA_XARTH))
            // TFA_OCELLUS_P2 is intentionally absent: in P2 the damage goes to the sword hitbox, which spawns a few
            // ticks after the transform. Those ticks are not loss.
            .put(RaidRoom.OCELLUS, ImmutableSet.of(
                    NpcID.TFA_OCELLUS_P1,
                    NpcID.TFA_OCELLUS_P2_SWORD_HITBOX,
                    NpcID.TFA_OCELLUS_P3))
            .build();

    /**
     * NPCs whose death ends the fight, so the window closes on the death rather than waiting out the death animation
     * and the health bar (14 ticks at Xarth).
     * <p>
     * Dreadnoth has no entry on purpose: it despawns into jellies and never fires an ActorDeath at all.
     */
    private static final Map<RaidRoom, Set<Integer>> ENDS_FIGHT = ImmutableMap.<RaidRoom, Set<Integer>>builder()
            .put(RaidRoom.ARCHIVE_GUARDIAN, ImmutableSet.of(
                    NpcID.TFA_GUARDIAN,
                    NpcID.TFA_GUARDIAN_UNSHIELDED))
            .put(RaidRoom.EZEKIEL, ImmutableSet.of(NpcID.TFA_EZEKIEL))
            .put(RaidRoom.BORK, ImmutableSet.of(
                    NpcID.TFA_BORK,
                    NpcID.TFA_SMORK))
            .put(RaidRoom.MOIA, ImmutableSet.of(NpcID.TFA_MOIA))
            .put(RaidRoom.DAGONHAI, ImmutableSet.of(
                    NpcID.TFA_DAGONHAI_LEADER,
                    NpcID.TFA_DAGONHAI_LEADER_UNSHIELDED))
            .put(RaidRoom.XARTH, ImmutableSet.of(NpcID.TFA_XARTH))
            .put(RaidRoom.OCELLUS, ImmutableSet.of(NpcID.TFA_OCELLUS_P3))
            .build();

    /**
     * The NPC whose life the raid's SECOND health bar tracks, per room.
     * <p>
     * The HUD can show an extra bar beside the boss bar ({@link VarbitID#HPBAR_HUD_EXTRA_CURRENT} over
     * {@link VarbitID#HPBAR_HUD_EXTRA_BASE}), and at Bork that extra bar is the Smorks. This matters because the
     * Smorks is the one required NPC in the raid that never dies in a way the client can see: across 166 Bork
     * fights in debug-logs/ it fired ActorDeath exactly zero times, played no death animation, never transformed,
     * and went on targeting players and dropping them right up to the room change. It simply despawns, 9 or 10
     * ticks after its last hitsplat, and those ticks were being charged as loss.
     * <p>
     * The extra bar is the marker. It reads 0 on the Smorks' last hitsplat tick in 149 of 157 fights, one tick
     * BEFORE it in 7 -- those being hits already in flight, so the bar is the more accurate of the two -- and it
     * never once reads 0 early, at any point in any fight.
     * <p>
     * Scoped per room on purpose. Ocellus uses the same extra bar for something that is not an NPC at all: its
     * base sits at 96 in all 146 Ocellus rooms logged, so reading it as a life bar there would end the fight on
     * a mechanic.
     */
    private static final Map<RaidRoom, Integer> EXTRA_BAR_NPC = ImmutableMap.<RaidRoom, Integer>builder()
            .put(RaidRoom.BORK, NpcID.TFA_SMORK)
            .build();

    /** @return the NPC id this room's extra health bar tracks, or -1 if it does not track one */
    static int extraBarNpc(RaidRoom room) {
        if (room == null) return -1;
        return EXTRA_BAR_NPC.getOrDefault(room, -1);
    }

    /**
     * Labels for NPC forms that are a real phase change, so a room can be split into segments. Driven off the NPC id
     * because that is what actually changes -- the health bar's base HP only moves on some transitions (Ocellus
     * P1 to P2 moves it, P2 to P3 does not), and it also cycles on things that are not phases at all, like the
     * Archive Guardian's shield.
     * <p>
     * Rooms with no entry here are measured as one segment.
     */
    private static final Map<Integer, String> PHASE_LABELS = ImmutableMap.<Integer, String>builder()
            .put(NpcID.TFA_OCELLUS_P1, "P1")
            .put(NpcID.TFA_OCELLUS_P2_SWORD_HITBOX, "P2")
            .put(NpcID.TFA_OCELLUS_P3, "P3")
            .build();

    /**
     * Rooms where the fight starts under you rather than being walked to, so the "Grace Ticks On Enter"
     * setting does not apply however it is set. There is nothing to forgive: the Archive Guardian activates
     * where you already stand, and every Ocellus phase hands you its next target in place. Everywhere else
     * is one of the six side rooms, which you run into.
     */
    private static final Set<RaidRoom> NO_ENTRY_GRACE = ImmutableSet.of(
            RaidRoom.ARCHIVE_GUARDIAN,
            RaidRoom.OCELLUS);

    /**
     * Ticks after a target turns up before it can actually be hit, keyed on the NPC that opens the window.
     * <p>
     * Keyed per NPC rather than per room because it is a property of the target, not the fight: within Ocellus,
     * P2's sword hitbox needs a tick and P3 does not.
     * <ul>
     *     <li>The Archive Guardian activates in place and gets no opening grace, so it was being charged a tick
     *     it could not avoid. Over 204 fights in debug-logs/ the first attack comes at +1 from the
     *     TFA_GUARDIAN_START to TFA_GUARDIAN transform in 150 and later in the rest -- not once at +0. The
     *     first hit anyone lands is at +3 in 179 of 204, an attack launched at +1 plus two ticks of travel.</li>
     *     <li>P2's sword hitbox is the same: across 135 spawns NOBODY in the team attacks on the spawn tick,
     *     110 of them go at +1, and the first hitsplat on the sword is at +3 in 87.</li>
     * </ul>
     * This is not leeway, it is the target not being there yet, which is why it is separate from the start
     * grace. Most targets need none: on the tick their health bar comes up the first attack is already at +0
     * in 98 of 127 Ocellus rooms, 79 of 134 Xarth and 27 of 33 Dreadnoth.
     */
    private static final Map<Integer, Integer> START_DELAY = ImmutableMap.<Integer, Integer>builder()
            .put(NpcID.TFA_GUARDIAN, 1)
            .put(NpcID.TFA_GUARDIAN_UNSHIELDED, 1)
            .put(NpcID.TFA_OCELLUS_P2_SWORD_HITBOX, 1)
            .build();

    /** @return ticks to wait after this NPC opens the window before counting */
    static int startDelayTicks(int npcId) {
        return START_DELAY.getOrDefault(npcId, 0);
    }

    /**
     * Animations after which the boss cannot be reached. The window ends when it turns up somewhere else, or
     * failing that on its next animation outside {@link #STAYS_UNATTACKABLE}.
     * <p>
     * Melee is the honest test here, not hitsplats. Across 138 Ocellus quadrant moves in debug-logs/, melee
     * attacks stop dead the tick QUADRANT_DESTROY_IN fires (75 land at -1, none at all from +0 to +18) and
     * resume at +19, while ranged attacks carry on until about +10 -- shots fired off as he leaves, which land
     * but say nothing about whether he is still reachable. The boss turns up in the next quadrant at +21 in
     * every one of those 138 moves.
     */
    private static final Map<RaidRoom, Set<Integer>> LEAVES_UNATTACKABLE =
            ImmutableMap.<RaidRoom, Set<Integer>>builder()
                    // The Archive Guardian's re-shield takeoff used to be listed here, on the basis that the
                    // team's hits stop for 6-7 ticks afterwards. Removed: that was hitsplat timing, which
                    // counts projectiles already in flight, and the blocked-attack report showed attacks
                    // being made right through the window at +0 to +4 in every raid.
                    .put(RaidRoom.OCELLUS, ImmutableSet.of(
                            AnimationID.NPC_OCELLUS_QUADRANT_DESTROY_IN,
                            // Phase 2 ends here. The animation is on Ocellus's own body, not on the Blade, and
                            // it lands within a tick of the Blade's last hitsplat (-1, 0 or +1 across every
                            // instance in debug-logs/) -- tighter than waiting for the Blade to despawn at +1
                            // to +3.
                            AnimationID.NPC_OCELLUS_P2_TO_P3_PART1,
                            // P1 ends here, 7 ticks before the transform in all 48 transitions in debug-logs/.
                            // The team actually stops attacking around -11, when the bar bottoms out, but that
                            // is not a clean marker: the bar often lands on 1 rather than 0.
                            AnimationID.NPC_OCELLUS_PHASE1_TRANSFORMATION,
                            // Phase 3 opens with a transition nobody can hit through, about 9 ticks
                            AnimationID.NPC_OCELLUS_P2_TO_P3_PART2))
                    .build();

    /**
     * Animations that must not be read as the boss being back, for the stretch before a hit lands.
     */
    private static final Map<RaidRoom, Set<Integer>> STAYS_UNATTACKABLE =
            ImmutableMap.<RaidRoom, Set<Integer>>builder()
                    .put(RaidRoom.OCELLUS, ImmutableSet.of(
                            AnimationID.NPC_OCELLUS_QUADRANT_DESTROY_IN,
                            AnimationID.NPC_OCELLUS_QUADRANT_DESTROY_LOOP,
                            AnimationID.NPC_OCELLUS_PHASE3_UNLEASH_OUT_90F,
                            AnimationID.NPC_OCELLUS_P2_TO_P3_PART1,
                            AnimationID.NPC_OCELLUS_P2_TO_P3_PART2))
                    .build();

    /**
     * Ticks after the boss picks a target again before anyone can actually hit it. Zero almost everywhere --
     * the first attack lands on the targeting tick itself -- but a quadrant leap takes one more: measured over
     * 156 leaps in debug-logs/, the first attack is at +1 from the re-target in 96 of them.
     */
    static int reopenDelayTicks(RaidRoom room, int animationId) {
        return room == RaidRoom.OCELLUS && animationId == AnimationID.NPC_OCELLUS_QUADRANT_DESTROY_IN ? 1 : 0;
    }

    static boolean staysUnattackable(RaidRoom room, int animationId) {
        if (room == null) return false;
        return STAYS_UNATTACKABLE.getOrDefault(room, Collections.emptySet()).contains(animationId);
    }

    static boolean leavesUnattackable(RaidRoom room, int animationId) {
        if (room == null) return false;
        return LEAVES_UNATTACKABLE.getOrDefault(room, Collections.emptySet()).contains(animationId);
    }

    static boolean hasEntryGrace(RaidRoom room) {
        return room != null && !NO_ENTRY_GRACE.contains(room);
    }

    static Set<Integer> getRequired(RaidRoom room) {
        if (room == null) return Collections.emptySet();
        return REQUIRED.getOrDefault(room, Collections.emptySet());
    }

    static Set<Integer> getEndsFight(RaidRoom room) {
        if (room == null) return Collections.emptySet();
        return ENDS_FIGHT.getOrDefault(room, Collections.emptySet());
    }

    /**
     * Ocellus P4's void explosion, which resets your attack cooldown if you step off the tile it lands on.
     * <p>
     * It goes off six times, two ticks apart, and each one takes three ticks off the cooldown -- so a five
     * tick weapon swings every two. Across debug-logs/ the graphic fires 3257 times, EVERY gap is exactly 2
     * ticks, 517 of 571 runs are exactly six long, and every run starts on a tick the siphon predicts.
     * <p>
     * Watched directly rather than predicted from the siphon animation and its hit delay, the way
     * {@code OcellusP3Detector} has to. That detector is working out where every raider in the party will be
     * standing; this only cares about one person, and for that person the explosion is simply visible. You
     * cannot see anyone else's, so any one of these is yours and missing it is your tick loss.
     */
    static final int VOID_EXPLOSION_SPOTANIM = SpotanimID.VFX_VOID_CHAIN_EXPLOSION;
    static final int VOID_EXPLOSION_COOLDOWN_REDUCTION = 3;

    /** @return a phase label for this NPC form, or null if it is not a phase boundary */
    /** Whether this room has phases at all, i.e. whether an empty health bar can mean "phase over". */
    static boolean hasPhases(RaidRoom room) {
        if (room == null) return false;
        for (Integer npcId : getRequired(room)) {
            if (PHASE_LABELS.containsKey(npcId)) return true;
        }
        return false;
    }

    static String getPhaseLabel(int npcId) {
        return PHASE_LABELS.get(npcId);
    }

    /** @return true if this room has a fight to measure (the Nexus, lobby and loot room do not) */
    static boolean isFightRoom(RaidRoom room) {
        return !getRequired(room).isEmpty();
    }
}

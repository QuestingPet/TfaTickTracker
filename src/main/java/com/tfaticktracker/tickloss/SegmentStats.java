package com.tfaticktracker.tickloss;

import com.tfaticktracker.RaidRoom;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Counters for one room, or one phase within a room.
 * <p>
 * Lost ticks are the sum of how far each attack overshot its cooldown, never the window minus ticks used. The
 * difference matters because the raid resets your attack cooldown in places (freezing a Xarth corpse with the purging
 * staff), which lets you attack sooner than the weapon's speed. Summing overshoots clamps that to zero, where a
 * window-minus-used figure would report over 100% efficiency.
 */
@RequiredArgsConstructor
public class SegmentStats {

    @Getter
    private final RaidRoom room;
    @Getter
    private final int phase;
    /** Phase name from the boss's NPC form, e.g. "P2". Null in rooms that are measured as one segment. */
    @Getter
    private final String phaseName;

    /** Ticks spent in the room, whether or not we were counting. */
    @Getter
    private int ticksInRoom;
    /** Ticks the fight window was open -- the denominator. */
    @Getter
    private int ticksCounting;
    @Getter
    private int attacks;
    /** Sum of positive overshoots past each attack's cooldown. */
    @Getter
    private int lostTicks;
    /** The part of {@link #lostTicks} attributable to a confirmed eat. Reported alongside it, not deducted. */
    @Getter
    private int lostToEating;
    /** The part of {@link #lostTicks} spent on a cooldown a dud shot started. */
    @Getter
    private int lostToDudShots;
    /** Bows or crossbows fired with no usable ammo. See {@link DudShot}. */
    @Getter
    private int dudShots;
    @Getter
    private int eatsFree;
    @Getter
    private int eatsCostly;
    /** Longest single overshoot, so an outlier can be spotted rather than averaged away. */
    @Getter
    private int longestGap;
    /** Attacks that landed before the cooldown we expected -- a cooldown reset. Never counted as loss. */
    @Getter
    private int earlyAttacks;

    /**
     * Animations seen in this fight that are in neither {@link AttackAnimations} nor
     * {@link NonAttackAnimations}.
     * <p>
     * The whole reason an allowlist is safe to use. A gap in the lists cannot change a number quietly: the
     * ids that were not understood are shown next to the figure they affected, so "my loss looks wrong"
     * comes with the answer attached. Capped because a sender of nonsense -- or a bug -- must not be able
     * to grow this without bound.
     */
    private final Set<Integer> unrecognised = new LinkedHashSet<>();

    public Set<Integer> getUnrecognisedAnimations() {
        return Collections.unmodifiableSet(unrecognised);
    }

    void noteUnrecognisedAnimation(int animationId) {
        if (unrecognised.size() < 8) unrecognised.add(animationId);
    }

    void tickInRoom() {
        ticksInRoom++;
    }

    void tickCounting() {
        ticksCounting++;
    }

    void addDudShot() {
        dudShots++;
    }

    void addAttack(int overshoot, int eatDelay, int dudCost) {
        lostToDudShots += Math.min(overshoot, dudCost);
        attacks++;

        if (overshoot <= 0) {
            // Attacked at or before the cooldown. An eat that cost nothing is a free eat.
            if (eatDelay > 0) eatsFree++;
            return;
        }

        lostTicks += overshoot;
        longestGap = Math.max(longestGap, overshoot);

        if (eatDelay > 0) {
            int fromEating = Math.min(overshoot, eatDelay);
            lostToEating += fromEating;
            if (fromEating >= overshoot) {
                eatsFree++; // The eat explains the whole gap
            } else {
                eatsCostly++;
            }
        }
    }

    /**
     * Ticks settled because the fight ended while we were already off cooldown.
     * <p>
     * Kept apart from {@link #addAttack} because there is no attack: no {@link #attacks} to increment, and no
     * eat to call free or costly. It is loss all the same, so it joins {@link #lostTicks} and can set
     * {@link #longestGap} -- the five ticks left on the table when a quadrant dies are the same five ticks
     * whether an attack eventually closed the gap or the fight did.
     */
    void addClosedOut(int ticks, int fromEating, int fromDudShot) {
        if (ticks <= 0) return;

        lostTicks += ticks;
        longestGap = Math.max(longestGap, ticks);
        lostToEating += Math.min(ticks, fromEating);
        lostToDudShots += Math.min(ticks, fromDudShot);
    }

    void addEarlyAttack() {
        attacks++;
        earlyAttacks++;
    }

    /**
     * Lost ticks, all of them. Eating is reported alongside in {@link #getLostToEating()} rather than taken
     * off: a tick spent on food is still a tick you did not attack in, so it belongs in the total.
     */
    public int getLost() {
        return lostTicks;
    }

    /**
     * Fraction of the fight window not lost.
     * <p>
     * Eating is INCLUDED, for the reason {@link #getLost()} gives: a tick spent on food is a tick you did
     * not attack in. {@link #getLostToEating()} is the breakdown of how much of it was food, not a discount.
     */
    public double getEfficiency() {
        return efficiency(lostTicks, ticksCounting);
    }

    /**
     * The one definition of efficiency, shared by every place that shows one.
     * <p>
     * Nothing measured yet reads as 100%, not 0%: a room nobody has fought in has lost nothing. The floor
     * at zero matters because the raid resets the attack cooldown in places, so a segment can bank more
     * attacks than its length allows and a naive figure would climb past 100%.
     */
    static double efficiency(int lost, int counted) {
        if (counted <= 0) return 1.0d;
        return Math.max(0.0d, 1.0d - ((double) lost / counted));
    }

    public String getLabel() {
        String name = room == null ? "?" : room.name();
        if (phaseName != null) return name + " " + phaseName;
        return phase > 0 ? name + " p" + (phase + 1) : name;
    }
}

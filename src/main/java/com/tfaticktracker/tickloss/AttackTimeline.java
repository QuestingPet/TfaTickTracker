package com.tfaticktracker.tickloss;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * The attack cooldown, as a pure state machine. No client, no events, no RuneLite types.
 * <p>
 * Everything that decides whether a tick was lost lives here, so it can be driven tick by tick from a test
 * instead of from a raid. {@link TickLossTracker} is the adapter: it reads the client each tick, fills in a
 * {@link Input}, and applies the {@link Result} to the current segment.
 * <p>
 * The rules, and why each one exists -- every one of these was a bug first:
 * <ul>
 *     <li><b>An attack must have STARTED.</b> An attack animation lingers after the shot that produced it, so
 *     "an attack animation is showing" is true both for a fresh attack and for one two ticks old. Reading it
 *     as an attack invents them.</li>
 *     <li><b>Attacking early is never loss, and always re-seeds the cooldown.</b> The raid resets cooldowns --
 *     freezing a corpse, and plenty of ordinary cases with any weapon. Dropping the re-seed leaves a stale
 *     cooldown that charges the NEXT attack for the gap.</li>
 *     <li><b>Eating costs only what it adds.</b> Not the food's whole delay, and not nothing: the ticks by
 *     which it pushes you past the cooldown you were already on.</li>
 *     <li><b>A shut window accrues no debt, but an attack made in one still seeds the cooldown.</b></li>
 *     <li><b>Some things add to the cooldown without being an attack.</b> Eating, and firing a bow with
 *     no usable ammo. Both cost ticks, neither is an attack, and both are charged to themselves rather
 *     than to idling.</li>
 *     <li><b>A fight that ENDS settles whatever wait is outstanding.</b> Overshoot is charged by the next
 *     attack, and when the boss dies there is no next attack -- so without this, sitting ready when a
 *     quadrant dies costs nothing. Food is settled only for the ticks the fight actually lasted.</li>
 * </ul>
 */
public final class AttackTimeline {

    /** The fastest a raid reset can let you attack: two ticks apart, which is what the explosions set. */
    private static final int MIN_RESET_GAP = 2;

    /** What the client looked like on one tick. */
    @Setter
    @Accessors(fluent = true, chain = true)
    public static final class Input {
        private int tick;
        /** Whether the fight window is open, i.e. whether this tick is measured at all. */
        private boolean counting;
        /** An attackable NPC is targeted and we are not idle or mid-block. */
        private boolean attacking;
        /** A NEW attack began this tick, as opposed to one still animating. */
        private boolean attackStarted;
        /** Cooldown the current weapon/style would put us on. */
        private int weaponSpeed = 1;
        /** Food delay incurred this tick, summed when several are eaten together. 0 for none. */
        private int eatDelay;
        /** Leeway to forgive on the first attack, applied only on the tick the window opens. */
        private int graceOnOpen;
        /** Ticks the raid knocked off the cooldown this tick, e.g. an Ocellus void explosion. */
        private int cooldownReduction;
        /**
         * Whether a shut window means the FIGHT ended rather than the measurement stopping.
         * <p>
         * Only meaningful on the tick {@link #counting} goes false. See {@link #settle}.
         */
        private boolean fightEnded;
        /**
         * A bow or crossbow fired with no usable ammo: no shot, but the weapon's cooldown runs anyway.
         * <p>
         * See {@link DudShot}. Costs {@link #weaponSpeed} ticks for nothing.
         */
        private boolean dudShot;
    }

    /** What that tick meant. */
    @Getter
    public static final class Result {
        private boolean counted;
        private boolean attack;
        /** An attack that landed before the cooldown was up. Counted, never charged. */
        private boolean earlyAttack;
        /** Ticks this attack overshot its cooldown by. */
        private int overshoot;
        /** The part of {@link #overshoot} that eating is responsible for. */
        private int overshootFromEating;
        /** Ticks settled because the fight ended while the cooldown had already run out. */
        private int closedOut;
        /** The part of {@link #closedOut} that eating is responsible for. */
        private int closedOutFromEating;
        /** The part of {@link #overshoot} a dud shot is responsible for. */
        private int overshootFromDudShot;
        /** The part of {@link #closedOut} a dud shot is responsible for. */
        private int closedOutFromDudShot;
        /** A bow or crossbow fired with no usable ammo on this tick. Never an {@link #attack}. */
        private boolean dudShot;
    }

    /** Tick we may attack again on. */
    @Getter
    private int nextAttackTick;
    /** Tick we last registered an attack on, so at most one is taken per tick. */
    @Getter
    private int lastAttackTick = -1;
    @Getter
    private int graceTicksRemaining;
    /** Eating's share of the wait, banked until an attack claims it. */
    private int pendingEatCost;
    /** A dud shot's share of the wait, banked the same way. */
    private int pendingDudCost;
    /**
     * The attack cooldown as the GAME keeps it: what food and dud shots add to, and which runs on past zero.
     * <p>
     * Separate from {@link #nextAttackTick}, which stays the weapon's own clock. That separation is what
     * makes the totals come out right while the attribution still works: the weapon clock measures the whole
     * gap, and this one knows how much of it something other than idling is responsible for.
     */
    private int realReady;
    private boolean wasCounting;

    public void reset() {
        nextAttackTick = 0;
        lastAttackTick = -1;
        graceTicksRemaining = 0;
        pendingEatCost = 0;
        pendingDudCost = 0;
        realReady = 0;
        wasCounting = false;
    }

    /** Drop a running cooldown, for when the measurement restarts rather than the fight. */
    public void seed(int tick) {
        nextAttackTick = Math.max(nextAttackTick, tick);
    }

    public Result onTick(Input in) {
        final Result out = new Result();
        final boolean wasCountingBefore = wasCounting;

        if (in.counting && !wasCounting) {
            // The window just opened. Leeway is granted as ticks to forgive on the first attack, never as a
            // cooldown: seeding ahead of now makes an immediate attack look early and throws it away.
            //
            // A cooldown already running is kept. If you attacked just before the window opened -- during a
            // phase transition, or before the boss was detectable -- you are waiting, not idling, and
            // resetting to now would charge you that wait.
            nextAttackTick = Math.max(nextAttackTick, in.tick);
            graceTicksRemaining = in.graceOnOpen;
        }
        wasCounting = in.counting;

        if (in.cooldownReduction > 0) {
            reduceCooldown(in.tick, in.cooldownReduction);
        }

        if (in.dudShot) {
            out.dudShot = true;
            pendingDudCost += seedCooldown(in.tick, in.weaponSpeed);
        }

        if (in.eatDelay > 0) {
            pendingEatCost += addToCooldown(in.tick, in.eatDelay);
        }

        if (!in.counting) {
            // The fight just ended with a cooldown already expired: those ticks were attackable and were not
            // used, and this is the only chance to say so. Settled BEFORE the cooldown is parked forward,
            // which would otherwise wipe exactly the gap being billed.
            if (wasCountingBefore && in.fightEnded) {
                settle(in.tick, out);
            }

            // No debt accrues while the window is shut -- walking in, waiting for a phase's target, boss dead,
            // player dead, mid-transition. Parked at the current tick rather than ahead of it, so an idle
            // client reads as ready rather than on cooldown.
            nextAttackTick = Math.max(nextAttackTick, in.tick);

            // A food delay that has run its course leaves nothing to attribute. Dropped here rather than while
            // counting, where an attack arriving after the delay expires is still partly the food's fault.
            if (in.tick >= realReady) {
                pendingEatCost = 0;
                pendingDudCost = 0;
            }

            // An attack made while the window is shut still happened, and proves the exclusion's edge is a
            // tick or two off, so it seeds the cooldown even though it is not counted. Without this the next
            // attack is charged the whole gap.
            if (in.attacking && in.attackStarted) {
                nextAttackTick = Math.max(nextAttackTick, in.tick + in.weaponSpeed);
                lastAttackTick = in.tick;
            }
            return out;
        }

        out.counted = true;

        if (!in.attacking || !in.attackStarted || in.tick == lastAttackTick) {
            return out;
        }

        if (in.tick < nextAttackTick) {
            // Something reset the cooldown. Never charged as loss, but it MUST re-seed, or the next attack
            // wears the gap. Common with any weapon, not just the raid's own resets.
            out.attack = true;
            out.earlyAttack = true;
            commit(in.tick, in.weaponSpeed);
            return out;
        }

        int overshoot = in.tick - nextAttackTick;

        if (graceTicksRemaining > 0) {
            final int forgiven = Math.min(overshoot, graceTicksRemaining);
            overshoot -= forgiven;
            graceTicksRemaining = 0;
        }

        out.attack = true;
        out.overshoot = overshoot;
        // The two buckets are disjoint stretches of the same gap, and together they can never exceed it:
        // the weapon clock lags the real cooldown by exactly the sum of what was added to it.
        out.overshootFromDudShot = Math.min(overshoot, pendingDudCost);
        out.overshootFromEating = Math.min(overshoot - out.overshootFromDudShot, pendingEatCost);
        pendingEatCost = 0;
        pendingDudCost = 0;

        commit(in.tick, in.weaponSpeed);
        return out;
    }

    /**
     * Add something to the attack cooldown that is not an attack, and return what it cost.
     * <p>
     * Used for a mouthful of food -- its own delay, the number you would recognise: 3 for an anglerfish, 2
     * for a karambwan, 5 for the pair -- and for a dud shot, which costs the weapon's whole speed.
     * <p>
     * Eating does not start its own timer: it ADDS its delay to the attack cooldown wherever that already
     * stands, and the cooldown runs on past zero. So what a mouthful costs depends on how long you had been
     * ready for:
     * <pre>
     *   cooldown at -1, eat a shark (3)      -> -1 + 3 = 2, so two ticks lost
     *   cooldown at -2, eat a shark (3)      -> -2 + 3 = 1, so one
     *   cooldown at -2, eat a karambwan (2)  -> -2 + 2 = 0, so none at all
     *   cooldown at -2, combo both (5)       -> -2 + 5 = 3, so three
     *   cooldown at -5, combo both (5)       -> -5 + 5 = 0, instant
     *   cooldown at +1, eat a karambwan (2)  -> +1 + 2 = 3, two of which are the food's
     * </pre>
     * Every one of those is in debug-logs/tfa-events-20261114-115313.log, and the same arithmetic explains the
     * combos spread over two ticks that nothing simpler could: ready 386, anglerfish at 387, karambwan at 388,
     * bow at 391; and ready 396, anglerfish at 398, karambwan at 399, bow at 401.
     * <p>
     * {@link #realReady} is that counter, shared with dud shots, which add to it the same way.
     * {@link #nextAttackTick} is deliberately NOT moved by eating:
     * it stays the weapon's own cooldown, so the wait a mouthful causes still shows up as overshoot rather
     * than being quietly absorbed. The two meet at {@code min(overshoot, pendingEatCost)}, which is what keeps
     * eating from ever being blamed for more ticks than were actually lost.
     */
    /**
     * A dud shot, which SETS the cooldown from this tick rather than adding to the counter.
     * <p>
     * This is the one way it differs from eating, and the difference matters. Food adds its delay to the
     * counter wherever that stands, below zero included -- which is why an anglerfish costs two ticks when
     * you have already been ready for one. A dud shot is an ATTACK that produced nothing, so the game treats
     * it like any other attack and starts the weapon's full cooldown from now. Adding instead would charge
     * only part of it whenever you had been idle beforehand.
     * <p>
     * The max() is a guard, not a case: the game will not let you fire while on cooldown, so if one arrives
     * anyway our cooldown is wrong, and the counter must not be dragged backwards.
     */
    private int seedCooldown(int tick, int speed) {
        final int couldHaveAttackedAt = Math.max(realReady, tick);
        realReady = Math.max(realReady, tick + speed);
        return Math.max(0, realReady - couldHaveAttackedAt);
    }

    private int addToCooldown(int tick, int delayTicks) {
        final int couldHaveAttackedAt = Math.max(realReady, tick);
        realReady += delayTicks;
        return Math.max(0, Math.max(realReady, tick) - couldHaveAttackedAt);
    }

    /**
     * The raid knocking ticks off the cooldown, as Ocellus P4's void explosion does six times in a row.
     * <p>
     * Only a cooldown still RUNNING is reduced. One that has already expired is left exactly where it is,
     * which is what makes a missed explosion show up: if you were idle when it went off, the idle time is
     * still yours and still counts. Clamping it forward to the current tick instead would quietly wipe a
     * tick of that away each time, so skipping one attack through a run would read as one lost tick when it
     * is two.
     */
    private void reduceCooldown(int tick, int ticks) {
        // Never closer than MIN_RESET_GAP to the attack before it. The reduction is a discount on the
        // cooldown, not a licence to attack at any speed: a 2 tick weapon is already at the cadence the
        // explosions set, so taking three off it would claim you could have fired on a tick you could not,
        // and bill you for the difference on every single explosion.
        // The reset lands you ready on the tick AFTER the explosion, never on the explosion itself: across
        // debug-logs/ every attack inside a void run sits at +1 or +2 from the blast and not one at +0, so
        // treating the blast tick as attackable bills a tick that was never on offer.
        //
        // It only shows when the last attack was more than MIN_RESET_GAP ago, since otherwise the
        // lastAttackTick term already lands on tick + 1. A forced idle before the dodge is exactly that: at
        // tick 3951 of debug-logs/tfa-events-20261116-215240.log the previous attack was at 3949, the
        // cooldown was cut to 3951, and the attack at 3954 was billed 3 when only 3952 and 3953 were usable.
        final int floor = Math.max(tick + 1, lastAttackTick + MIN_RESET_GAP);

        if (nextAttackTick > tick) {
            nextAttackTick = Math.max(floor, nextAttackTick - ticks);
        }
        if (realReady > tick) {
            realReady = Math.max(floor, realReady - ticks);
        }
    }

    /**
     * The fight ending while the cooldown had already run out, which is loss with no attack to hang it on.
     * <p>
     * Overshoot is normally charged by the NEXT attack: the gap is measured when it is closed. A boss dying,
     * a phase handing over, or your own death means no next attack ever comes, so without this the wait is
     * simply forgotten -- sitting three ticks off cooldown when a quadrant dies reads as zero. The arithmetic
     * is the same one an attack uses, with the close tick standing in for the attack tick.
     * <p>
     * Death counts. {@code ActorDeath} on the local player fires on the tick the killing hitsplat lands, so
     * everything being settled here is a tick that really was yours and really was spent not attacking.
     * <p>
     * Food, though, is only charged for the ticks the fight actually lasted. A mouthful banks its whole cost
     * the moment it is swallowed -- an anglerfish at -1 banks two ticks -- but if the boss dies the tick
     * after, only one of those two ever had a tick to cost you. The rest was going to be spent in a fight
     * that no longer existed, so {@link #realReady} reaching past the close is subtracted back off.
     * Whatever is left over stays banked, in case the next phase opens while the food delay is still running.
     */
    private void settle(int tick, Result out) {
        int total = Math.max(0, tick - nextAttackTick);

        if (graceTicksRemaining > 0) {
            total -= Math.min(total, graceTicksRemaining);
            graceTicksRemaining = 0;
        }

        // The part of the food's delay that extends past the end of the fight, and so never cost anything.
        final int neverArrived = Math.max(0, realReady - tick);

        // Whatever reaches past the close is trimmed off the thing that was added LAST, since that is the
        // part of the counter still running when the fight stopped.
        final int eatNeverArrived = Math.min(neverArrived, pendingEatCost);
        final int dudNeverArrived = Math.min(neverArrived - eatNeverArrived, pendingDudCost);

        out.closedOut = total;
        out.closedOutFromDudShot = Math.min(total, pendingDudCost - dudNeverArrived);
        out.closedOutFromEating =
                Math.min(total - out.closedOutFromDudShot, pendingEatCost - eatNeverArrived);
        pendingEatCost = eatNeverArrived;
        pendingDudCost = dudNeverArrived;
    }

    private void commit(int tick, int weaponSpeed) {
        lastAttackTick = tick;
        nextAttackTick = tick + weaponSpeed;
        // Attacking sets the counter outright, so any food still standing on it is spent.
        realReady = nextAttackTick;
    }
}

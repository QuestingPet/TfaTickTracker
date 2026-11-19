package com.tfaticktracker.tickloss;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * A raid, written down tick by tick, so a case only has to be diagnosed once.
 * <p>
 * Every rule in {@link AttackTimeline} came out of a real run, and several of them were "fixed" more than once
 * because nothing held the earlier answer in place. The point of this is that when a run shows an attack on
 * tick X and another on tick Y scoring N lost ticks where it should have been M, that goes straight in as a
 * scenario and stays true.
 * <p>
 * Ticks run from 0 and advance implicitly: anything not named is an idle tick with the window open.
 * <pre>
 *     Sim s = new Sim().speed(5);
 *     s.attack(0);
 *     s.attack(7);        // two ticks late
 *     s.assertLost(2);
 * </pre>
 */
final class Sim {

    private final AttackTimeline timeline = new AttackTimeline();
    private final List<String> log = new ArrayList<>();

    private int now;
    private int speed = 5;
    private boolean counting = true;
    private int graceOnOpen;
    private boolean voidThisTick;
    private boolean fightEnded;
    private boolean dudThisTick;

    private int attacks;
    private int earlyAttacks;
    private int lost;
    private int lostToEating;
    private int lostToDudShots;
    private int counted;
    private int longestGap;

    Sim speed(int ticks) {
        this.speed = ticks;
        return this;
    }

    /** Leeway granted on the tick the window opens, as the config's start grace does. */
    Sim grace(int ticks) {
        this.graceOnOpen = ticks;
        return this;
    }

    /**
     * Stop MEASURING from here -- left the raid, changed room, spectating, plugin switched off.
     * <p>
     * Not the fight ending: nothing outstanding is settled, because nothing was lost. Use
     * {@link #fightEnds(int)} for a boss dying, a phase handing over, or your own death.
     */
    Sim windowShut() {
        this.counting = false;
        this.fightEnded = false;
        return this;
    }

    /**
     * The FIGHT ends on this tick: the boss dies, the phase hands over, or you do.
     * <p>
     * Whatever wait was outstanding is settled here, because no attack is ever coming to close it.
     */
    Sim fightEnds(int tick) {
        return endFight(tick, 0, "fight ends");
    }

    /**
     * Eating on the very tick the fight ends, which is where food costs the least it can.
     * <p>
     * The mouthful banks its whole delay and then has no fight left to spend it in.
     */
    Sim eatAsFightEnds(int tick, int... delayTicks) {
        int total = 0;
        for (int d : delayTicks) total += d;
        return endFight(tick, total, "eat(" + total + "t) + ends");
    }

    private Sim endFight(int tick, int eatDelay, String what) {
        advanceTo(tick);
        counting = false;
        fightEnded = true;
        apply(tick, false, false, eatDelay, what);
        now = tick + 1;
        return this;
    }

    Sim windowOpen() {
        this.counting = true;
        return this;
    }

    /** An attack that genuinely starts on this tick. */
    Sim attack(int tick) {
        return feed(tick, true, true, 0, "attack");
    }

    /** An attack with a different weapon speed, e.g. swapping mid fight. */
    Sim attack(int tick, int withSpeed) {
        final int was = speed;
        speed = withSpeed;
        feed(tick, true, true, 0, "attack@" + withSpeed + "t");
        speed = was;
        return this;
    }

    /**
     * The previous attack's animation is still playing. This is NOT a new attack, and reading it as one is what
     * invented attacks that never happened.
     */
    Sim stillAnimating(int tick) {
        return feed(tick, true, false, 0, "animating");
    }

    /**
     * Eating, with the food's own delay in ticks: karambwan 2, most food 3, slow food 4. Pass several for a
     * combo eat on one tick -- their delays add up, which is why a karambwan plus a normal food is 5 ticks.
     */
    Sim eat(int tick, int... delayTicks) {
        int total = 0;
        StringBuilder what = new StringBuilder("eat(");
        for (int d : delayTicks) {
            total += d;
            what.append(what.length() > 4 ? "+" : "").append(d);
        }
        return feed(tick, false, false, total, what.append("t)").toString());
    }

    /** Eating and attacking on the same tick, which is what you do when the food lands you on zero. */
    Sim eatAndAttack(int tick, int... delayTicks) {
        int total = 0;
        for (int d : delayTicks) total += d;
        return feed(tick, true, true, total, "eat(" + total + "t)+attack");
    }

    /**
     * A bow or crossbow fired with no usable ammo: no shot, but the weapon's cooldown runs anyway.
     * <p>
     * Not an attack. Costs the weapon's whole speed for nothing. See {@link DudShot}.
     */
    Sim dudShot(int tick) {
        advanceTo(tick);
        dudThisTick = true;
        apply(tick, false, false, 0, "DUD SHOT (no ammo)");
        dudThisTick = false;
        now = tick + 1;
        return this;
    }

    /** An Ocellus void explosion, which knocks 3 ticks off a running cooldown. */
    Sim voidExplosion(int tick) {
        // Armed only AFTER advancing. feed() fills the gap with idle ticks, and a flag set beforehand
        // lands on every one of them -- which charged a dud shot five times over before it was caught.
        advanceTo(tick);
        voidThisTick = true;
        apply(tick, false, false, 0, "void explosion");
        voidThisTick = false;
        now = tick + 1;
        return this;
    }

    /** A void explosion you used, attacking on the tick after it. */
    Sim voidExplosionThenAttack(int boom) {
        voidExplosion(boom);
        return attack(boom + 1);
    }

    /** Idle up to and including this tick, doing nothing. */
    Sim idleUntil(int tick) {
        advanceTo(tick + 1);
        return this;
    }

    private Sim feed(int tick, boolean attacking, boolean started, int eatDelay, String what) {
        advanceTo(tick);
        apply(tick, attacking, started, eatDelay, what);
        now = tick + 1;
        return this;
    }

    private void advanceTo(int tick) {
        if (tick < now) {
            throw new IllegalArgumentException("tick " + tick + " is before " + now + "; scenarios run forwards");
        }
        while (now < tick) {
            apply(now, false, false, 0, "idle");
            now++;
        }
    }

    private void apply(int tick, boolean attacking, boolean started, int eatDelay, String what) {
        final AttackTimeline.Input in = new AttackTimeline.Input()
                .tick(tick)
                .counting(counting)
                .attacking(attacking)
                .attackStarted(started)
                .weaponSpeed(speed)
                .eatDelay(eatDelay)
                .graceOnOpen(graceOnOpen)
                .cooldownReduction(voidThisTick ? 3 : 0)
                .fightEnded(fightEnded)
                .dudShot(dudThisTick);

        final AttackTimeline.Result r = timeline.onTick(in);

        if (r.isCounted()) counted++;
        if (r.isAttack()) attacks++;
        if (r.isEarlyAttack()) earlyAttacks++;
        lost += r.getOvershoot() + r.getClosedOut();
        lostToEating += r.getOvershootFromEating() + r.getClosedOutFromEating();
        lostToDudShots += r.getOvershootFromDudShot() + r.getClosedOutFromDudShot();
        longestGap = Math.max(longestGap, Math.max(r.getOvershoot(), r.getClosedOut()));

        final int readyAt = timeline.getNextAttackTick();
        final String state;
        if (r.isAttack()) {
            state = r.isEarlyAttack() ? "ATTACK (early, reset)"
                    : r.getOvershoot() > 0
                      ? "ATTACK  <-- " + r.getOvershoot() + " LOST"
                        + (r.getOvershootFromEating() > 0 ? " (" + r.getOvershootFromEating() + " eat)" : "")
                        + (r.getOvershootFromDudShot() > 0 ? " (" + r.getOvershootFromDudShot() + " dud)" : "")
                      : "ATTACK";
        } else if (r.getClosedOut() > 0) {
            state = "SETTLED  <-- " + r.getClosedOut() + " LOST"
                    + (r.getClosedOutFromEating() > 0 ? " (" + r.getClosedOutFromEating() + " eat)" : "");
        } else if (!counting) {
            state = "";
        } else if (tick >= readyAt) {
            // Able to attack and did not: this is the tick that becomes loss.
            state = "could attack  <-- WASTED";
        } else {
            state = "on cooldown until " + readyAt;
        }

        log.add(String.format("    %4d  %-18s %s", tick, what, state));
    }

    /** Print the whole run, for walking through a scenario rather than asserting on it. */
    Sim explain(String title) {
        System.out.println();
        System.out.println("  " + title);
        log.forEach(System.out::println);
        System.out.printf("    => %d lost (%d to eating, %d to dud shots), %d attacks, %d early, %d counted%n",
                lost, lostToEating, lostToDudShots, attacks, earlyAttacks, counted);
        return this;
    }

    // --- assertions, which print the whole scenario when they fail ---

    Sim assertLost(int expected) {
        return check("total lost ticks", expected, lost);
    }

    Sim assertLostToEating(int expected) {
        return check("lost to eating", expected, lostToEating);
    }

    Sim assertLostToDudShots(int expected) {
        return check("lost to dud shots", expected, lostToDudShots);
    }

    Sim assertAttacks(int expected) {
        return check("attacks", expected, attacks);
    }

    Sim assertEarlyAttacks(int expected) {
        return check("early attacks", expected, earlyAttacks);
    }

    Sim assertCounted(int expected) {
        return check("counted ticks", expected, counted);
    }

    Sim assertLongestGap(int expected) {
        return check("longest gap", expected, longestGap);
    }

    private Sim check(String what, int expected, int actual) {
        assertEquals(what + "\n" + String.join("\n", log) + "\n", expected, actual);
        return this;
    }
}

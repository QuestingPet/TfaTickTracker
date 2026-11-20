package com.tfaticktracker.tickloss;

import org.junit.Test;

/**
 * Ocellus P4's void explosion, walked through tick by tick.
 * <p>
 * Not assertions -- {@link AttackTimelineTest} holds those. This prints what the real
 * {@link AttackTimeline} does in each situation, so the rule can be read rather than inferred. Run it with
 * {@code ./gradlew test --tests '*VoidExplosionWalkthrough*' -i}.
 * <p>
 * Ticks are relative. A real run from a recorded raid has the explosions at 1597,
 * 1599, 1601, 1603, 1605 and 1607, with twisted bow shots on every tick in between.
 */
public class VoidExplosionWalkthroughTest {

    private static final int TBOW = 5;

    @Test
    public void walkthrough() {
        // 1. Perfect: step off every explosion and swing on the tick after it.
        final Sim perfect = new Sim().speed(TBOW).attack(0);
        for (int boom = 1; boom <= 11; boom += 2) {
            perfect.voidExplosionThenAttack(boom);
        }
        perfect.explain("1. Every explosion taken -- a 5t weapon swinging every 2 ticks, nothing lost");

        // 2. Miss one slot. The explosions do not stop, so the next chance is two ticks later -- which is
        //    what the logs show: 96.7% of gaps inside a run are 2 ticks, and a miss goes to 4, not 3.
        new Sim().speed(TBOW)
                .attack(0)
                .voidExplosionThenAttack(1)   // attack at 2
                .voidExplosion(3)             // the slot at 4, missed
                .voidExplosionThenAttack(5)   // back on rhythm at 6
                .voidExplosionThenAttack(7)
                .explain("2. One slot missed, next one taken -- two ticks, because the slots are 2 apart");

        // 2b. The rare off-rhythm case: attacking between the slots. 1% of gaps in the logs.
        new Sim().speed(TBOW)
                .attack(0)
                .voidExplosionThenAttack(1)
                .voidExplosion(3)
                .attack(5)                    // one tick late rather than waiting for the slot at 6
                .explain("2b. Attacking off-rhythm instead of waiting -- one tick (rare, 1% of gaps)");

        // 3. Nothing taken at all: stood still through the whole run.
        final Sim stood = new Sim().speed(TBOW).attack(0);
        for (int boom = 1; boom <= 11; boom += 2) {
            stood.voidExplosion(boom);
        }
        stood.attack(13).explain("3. Stood through the whole run -- every tick of it is yours");

        // 4. A fast weapon, where the reset cannot help: a blowpipe is already on the 2 tick cadence.
        final Sim fast = new Sim().speed(2).attack(0);
        for (int boom = 1; boom <= 7; boom += 2) {
            fast.voidExplosion(boom).attack(boom + 1);
        }
        fast.explain("4. A 2t weapon -- already at the cadence, so the reset changes nothing");

        // 5. Eating mid run, which pushes the cooldown back out again.
        new Sim().speed(TBOW)
                .attack(0)
                .voidExplosionThenAttack(1)
                .voidExplosion(3)
                .eat(4, 3)                        // an anglerfish, a tick after the explosion
                .attack(7)
                .explain("5. Eating during a run -- the food is charged, the explosion is not");
    }
}

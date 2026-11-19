package com.tfaticktracker.tickloss;

import org.junit.Test;

/**
 * What a fight ENDING does to an outstanding wait, walked through tick by tick.
 * <p>
 * Not assertions -- {@link AttackTimelineTest} holds those. This prints what the real {@link AttackTimeline}
 * does, so the rule can be read rather than inferred. Run it with
 * {@code ./gradlew test --tests '*FightEndWalkthrough*' -i}.
 * <p>
 * All of these use a 5 tick weapon, and "fight ends" is a boss dying, a phase handing over, the boss going
 * out of reach, or your own death -- the four states {@link FightWindowState#endsTheFight} accepts.
 */
public class FightEndWalkthroughTest {

    private static final int TBOW = 5;

    @Test
    public void walkthrough() {
        new Sim().speed(TBOW)
                .attack(0)
                .fightEnds(8)
                .explain("1. Three ticks off cooldown when it ends -- all three are yours");

        new Sim().speed(TBOW)
                .attack(0)
                .attack(8)
                .explain("1b. The same gap closed by an attack instead, which must be the same number");

        new Sim().speed(TBOW)
                .attack(0)
                .fightEnds(3)
                .explain("2. It ends mid cooldown -- nothing owed, those ticks were never yours");

        new Sim().speed(TBOW)
                .attack(0)
                .idleUntil(7)
                .windowShut()
                .idleUntil(12)
                .explain("3. The MEASUREMENT stops instead (left the raid, changed room) -- never billed");

        new Sim().speed(TBOW)
                .attack(0)
                .eat(6, 3)
                .fightEnds(7)
                .explain("4. One tick late, eat an angler, it ends the next tick -- the food gets 1 of its 2");

        new Sim().speed(TBOW)
                .attack(0)
                .eat(6, 3)
                .fightEnds(9)
                .explain("4b. The same eat with room to run -- now the food is charged both ticks");

        new Sim().speed(TBOW)
                .attack(0)
                .idleUntil(5)
                .eatAsFightEnds(6, 3)
                .explain("5. Eating on the very tick it ends -- the food costs nothing at all");

        new Sim().speed(TBOW)
                .attack(0)
                .idleUntil(9)
                .eat(10, 3)
                .fightEnds(11)
                .explain("6. Five ticks in hand already, so the eat is free and the idling is not");

        new Sim().speed(TBOW)
                .attack(0)
                .fightEnds(8)
                .idleUntil(20)
                .windowOpen()
                .attack(21)
                .explain("7. Next phase opens -- the settled gap is not charged a second time");
    }
}

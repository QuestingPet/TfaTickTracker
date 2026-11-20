package com.tfaticktracker.tickloss;

import org.junit.Test;

/**
 * Every case here is a real one, from a real run, that was reported wrong at least once.
 * <p>
 * When a run shows something the plugin got wrong, it goes in here first, as the ticks it happened on and the
 * figure it should have produced. That is what stops the same thing being rediscovered a week later.
 */
public class AttackTimelineTest {

    // --- attacking normally ---

    @Test
    public void perfectAttacksCostNothing() {
        new Sim().speed(5)
                .attack(0).attack(5).attack(10).attack(15)
                .assertAttacks(4)
                .assertLost(0);
    }

    @Test
    public void lateAttackIsCharged() {
        new Sim().speed(5)
                .attack(0)
                .attack(7)
                .assertLost(2)
                .assertLongestGap(2);
    }

    /**
     * The blowpipe fires every 2 ticks and its animation does NOT re-fire AnimationChanged between shots, so
     * the plugin sees one animation event and eight attacks. Archive Guardian, ticks 2029-2042 of
     * a recorded raid: hitsplats land on every even tick throughout.
     */
    @Test
    public void blowpipeRepeatsAreNotLoss() {
        new Sim().speed(2)
                .attack(0).attack(2).attack(4).attack(6).attack(8).attack(10)
                .assertAttacks(6)
                .assertLost(0);
    }

    /**
     * The attack animation lingers after the shot. Reading "an attack animation is showing" as an attack
     * invented one at tick 342 of a recorded raid, where the hitsplats show shots at
     * 338, 340 and 345 and nothing at 342 -- which swallowed two of the three ticks actually lost to the eat.
     */
    @Test
    public void lingeringAnimationIsNotAnAttack() {
        new Sim().speed(2)
                .attack(0)
                .attack(2)
                .stillAnimating(4)
                .stillAnimating(5)
                .attack(7)
                .assertAttacks(3)   // 0, 2 and 7 -- the two animating ticks are not attacks
                .assertLost(3);
    }

    // --- resets: attacking sooner than the weapon allows ---

    /**
     * The raid resets cooldowns, and so do plenty of ordinary situations, with any weapon. An early attack is
     * never loss and must ALWAYS re-seed, or the next attack wears the gap. End of Ocellus in
     * a recorded raid reads 5 lost ticks when it re-seeds and 12 when it does not.
     */
    @Test
    public void earlyAttackIsNeverLossAndResetsTheCooldown() {
        new Sim().speed(5)
                .attack(0)
                .attack(2)          // 3 ticks early
                .attack(7)          // 5 after the reset: on time
                .assertAttacks(3)
                .assertEarlyAttacks(1)
                .assertLost(0);
    }

    @Test
    public void repeatedResetsKeepReseeding() {
        new Sim().speed(5)
                .attack(0).attack(2).attack(4).attack(6).attack(8)
                .attack(13)
                .assertAttacks(6)
                .assertEarlyAttacks(4)
                .assertLost(0);
    }

    /**
     * Ocellus P3, where the void explosion resets the cooldown every two ticks. A 5 tick scythe swinging on
     * a 2 tick cadence is all early attacks and no loss at all: ticks 1596 to 1608 of
     * a recorded raid are seven twisted bow shots two ticks apart.
     */
    @Test
    public void ocellusP3ResetsCostNothingWhenYouKeepUp() {
        new Sim().speed(5)
                .attack(0).attack(2).attack(4).attack(6).attack(8).attack(10).attack(12)
                .assertAttacks(7)
                .assertEarlyAttacks(6)
                .assertLost(0);
    }

    /** A reset does not forgive a gap that comes after it. */
    @Test
    public void lossAfterAResetIsStillCharged() {
        new Sim().speed(5)
                .attack(0)
                .attack(2)
                .attack(9)          // cooldown was 7
                .assertEarlyAttacks(1)
                .assertLost(2);
    }

    /**
     * Freezing a corpse at Xarth comes in pairs, the second inside the first's cooldown.
     * a recorded raid: bow 954, casts 960 and 962, bow 967 -- perfect play.
     */
    @Test
    public void xarthFreezePairCostsNothing() {
        new Sim().speed(5)
                .attack(0)          // 954, bow
                .attack(6)          // 960, first cast, one tick late
                .attack(8)          // 962, second cast, inside the first's cooldown
                .attack(13)         // 967, bow, exactly 5 after the second cast
                .assertAttacks(4)
                .assertEarlyAttacks(1)
                .assertLost(1);     // only the single tick before the first cast
    }

    @Test
    public void weaponSwapUsesTheNewSpeed() {
        new Sim().speed(5)
                .attack(0)
                .attack(5, 2)       // swap to something faster
                .attack(7)
                .assertLost(0);
    }

    // --- Ocellus P4's void explosion ---
    //
    // It goes off six times, two ticks apart, and each one takes three ticks off the cooldown, so a five
    // tick weapon swings every two. Ticks 1596 to 1608 of a recorded raid are
    // seven twisted bow shots through one run, explosions at 1597, 1599, 1601, 1603, 1605 and 1607.

    /** Taking every explosion costs nothing, whatever the weapon's own speed. */
    @Test
    public void takingEveryVoidExplosionCostsNothing() {
        final Sim s = new Sim().speed(5).attack(0);   // 1596
        for (int boom = 1; boom <= 11; boom += 2) {   // 1597, 1599, ... 1607
            s.voidExplosionThenAttack(boom);
        }
        s.assertAttacks(7).assertLost(0);
    }

    /** One explosion pulls a five tick cooldown down to two. */
    @Test
    public void oneVoidExplosionTakesThreeTicksOff() {
        new Sim().speed(5)
                .attack(0)          // ready at 5
                .voidExplosion(1)   // ready at 2
                .attack(2)
                .assertLost(0)
                .assertEarlyAttacks(0);
    }

    /**
     * The rhythm is attack, move, attack, move, and the slots are two ticks apart. Miss one and the next
     * chance is two ticks later, not one: inside a run 96.7% of the gaps in the recorded raids are exactly 2 ticks
     * and a miss goes to 4, never 3.
     */
    @Test
    public void missingASlotCostsTwoBecauseTheNextOneIsTwoTicksAway() {
        new Sim().speed(5)
                .attack(0)
                .voidExplosionThenAttack(1)   // attack at 2
                .voidExplosion(3)             // slot at 4, missed
                .voidExplosionThenAttack(5)   // next slot, at 6
                .assertLost(2);
    }

    /**
     * Attacking between the slots rather than waiting for the next one saves a tick, and is charged as one
     * rather than two. Real but rare -- 1% of the gaps inside a run.
     */
    @Test
    public void attackingOffRhythmCostsOnlyOne() {
        new Sim().speed(5)
                .attack(0)
                .voidExplosionThenAttack(1)   // attack at 2
                .voidExplosion(3)             // move
                .attack(5)                    // should have been 4
                .assertLost(1);
    }

    /**
     * Miss it again and it is two. An explosion only reduces a cooldown that is still RUNNING -- one that
     * has already expired is left alone, so the idle time stays yours rather than being wiped each time.
     */
    @Test
    public void missingOneVoidExplosionCostsTwoTicks() {
        new Sim().speed(5)
                .attack(0)
                .voidExplosionThenAttack(1)   // attack at 2, ready at 7
                .voidExplosion(3)             // ready at 4
                .voidExplosion(5)             // already expired, left alone
                .attack(6)
                .assertLost(2);
    }

    /** Sitting through a whole run and attacking at the end is charged the lot. */
    @Test
    public void sittingOutAVoidRunIsCharged() {
        final Sim s = new Sim().speed(5).attack(0);
        for (int boom = 1; boom <= 11; boom += 2) {
            s.voidExplosion(boom);
        }
        s.attack(13).assertLost(11);
    }

    /**
     * A weapon already at the two tick cadence gains nothing from an explosion and must not be billed for
     * it. Taking three off a two tick cooldown would claim you could have fired on a tick you could not,
     * and charge the difference on every explosion of the run -- four lost ticks for perfect blowpipe play.
     */
    @Test
    public void aFastWeaponIsNotBilledForResetsItCannotUse() {
        new Sim().speed(2)
                .attack(0)
                .voidExplosionThenAttack(1)
                .voidExplosionThenAttack(3)
                .voidExplosionThenAttack(5)
                .assertLost(0);
    }

    /**
     * An explosion can never reach further back than two ticks from the attack before it, so loss cannot be
     * handed back for a tick you could not have attacked on.
     */
    @Test
    public void aVoidExplosionNeverReachesBackwards() {
        new Sim().speed(2)
                .attack(0)          // ready at 2
                .voidExplosion(1)   // floored at 2, not -1
                .attack(3)
                .assertLost(1);     // 4 if it had reached backwards
    }

    // --- eating ---

    /**
     * Eating off cooldown costs one tick less than the food's delay, because the tick you eat on is one you
     * were going to spend on the attack anyway. Measured at the Archive Guardian in
     * a recorded raid, where the weapon came off cooldown first:
     * ready 361, anglerfish at 362, bow at 364.
     */
    @Test
    public void eatingOffCooldownCostsOneLessThanItsDelay() {
        new Sim().speed(5)
                .attack(0)          // ready at 5
                .eat(6, 3)          // a tick after that
                .attack(8)
                .assertLost(3)      // one tick idle before the eat, two to the food
                .assertLostToEating(2);
    }

    /** Same shape with a karambwan: ready 312, eaten at 313, bow at 314. */
    @Test
    public void karambwanOffCooldownCostsOneTick() {
        new Sim().speed(5)
                .attack(0)
                .eat(5, 2)
                .attack(6)
                .assertLost(1)
                .assertLostToEating(1);
    }

    /**
     * Eating while still ON cooldown costs the food's whole delay, because its wait does not run during the
     * weapon's -- it starts when that ends. Same log: ready 252, karambwan at 251, bow at 254.
     */
    @Test
    public void eatingOnCooldownCostsTheWholeDelay() {
        new Sim().speed(5)
                .attack(0)          // ready at 5
                .eat(4, 2)          // one tick before that
                .attack(7)          // ready + 2
                .assertLost(2)
                .assertLostToEating(2);
    }

    /** And with hard food: ready 259, anglerfish at 258, bow at 262. */
    @Test
    public void hardFoodOnCooldownCostsThreeFromWhenTheWeaponWasReady() {
        new Sim().speed(5)
                .attack(0)
                .eat(4, 3)
                .attack(8)          // ready + 3
                .assertLost(3)
                .assertLostToEating(3);
    }

    /** Eating with plenty of cooldown left is free -- the food delay runs out underneath it. */
    @Test
    public void eatingEarlyInTheCooldownIsFree() {
        new Sim().speed(5)
                .attack(0)
                .eat(1, 3)
                .attack(5)
                .assertLost(0)
                .assertLostToEating(0);
    }

    /** Combo eating sums: a karambwan plus hard food is 5 ticks, not 3, and not 7. */
    @Test
    public void comboEatingSumsTheDelays() {
        new Sim().speed(5)
                .attack(0)
                .eat(4, 2, 3)       // on cooldown, so the whole 5
                .attack(10)
                .assertLost(5)
                .assertLostToEating(5);
    }

    /** The subtraction is per eat TICK, not per mouthful, so a combo off cooldown loses one tick in total. */
    @Test
    public void aComboOffCooldownLosesOneTickNotTwo() {
        new Sim().speed(5)
                .attack(0)
                .eat(5, 2, 3)
                .attack(9)
                .assertLost(4)
                .assertLostToEating(4);
    }

    /** Eating and attacking on the same tick, which costs nothing because the attack still went out. */
    @Test
    public void eatingOnAnAttackTickCostsNothing() {
        new Sim().speed(5)
                .attack(0)
                .eatAndAttack(5, 3)
                .attack(10)
                .assertAttacks(3)
                .assertLost(0);
    }

    /** Loss beyond what the food explains is the player's. */
    @Test
    public void onlyTheFoodsShareIsAttributedToEating() {
        new Sim().speed(5)
                .attack(0)          // ready at 5
                .eat(6, 3)          // one tick past that, so two of the three are the food's
                .attack(11)
                .assertLost(6)
                .assertLostToEating(2);
    }

    // --- eating adds to the cooldown wherever it already stands ---
    //
    // The cooldown runs on past zero, and food adds to it rather than starting a timer of its own. So the
    // longer you have been ready, the less a mouthful costs. All of these are measured shapes from
    // a recorded raid.

    /** Ready for one tick, then hard food: the counter goes to 2, so two ticks are lost. */
    @Test
    public void hardFoodOneTickPastReadyCostsTwo() {
        new Sim().speed(5)
                .attack(0)          // ready at 5
                .eat(6, 3)
                .attack(8)
                .assertLost(3)      // one idle before the eat, two to the food
                .assertLostToEating(2);
    }

    /** Ready for two ticks, then hard food: the counter goes to 1, so only one tick is lost. */
    @Test
    public void hardFoodTwoTicksPastReadyCostsOne() {
        new Sim().speed(5)
                .attack(0)
                .eat(7, 3)
                .attack(8)
                .assertLost(3)      // two idle before the eat, one to the food
                .assertLostToEating(1);
    }

    /** Ready for two ticks, then a karambwan: the counter lands on zero, so you attack immediately. */
    @Test
    public void karambwanTwoTicksPastReadyIsFree() {
        new Sim().speed(5)
                .attack(0)
                .eatAndAttack(7, 2)
                .assertLostToEating(0);
    }

    /** Ready for two ticks, then a combo: the counter goes to 3. */
    @Test
    public void aComboTwoTicksPastReadyCostsThree() {
        new Sim().speed(5)
                .attack(0)
                .eat(7, 3, 2)
                .attack(10)
                .assertLost(5)      // two idle before the eat, three to the food
                .assertLostToEating(3);
    }

    /** Ready for five ticks, then a combo: the counter lands on zero, so it is instant. */
    @Test
    public void aComboFiveTicksPastReadyIsFree() {
        new Sim().speed(5)
                .attack(0)
                .eatAndAttack(10, 3, 2)
                .assertLostToEating(0);
    }

    /**
     * Two foods on consecutive ticks stack on the counter, which is what nothing simpler could explain:
     * ready 386, anglerfish at 387, karambwan at 388, bow at 391.
     */
    @Test
    public void foodsOnConsecutiveTicksStackOnTheCounter() {
        new Sim().speed(5)
                .attack(0)          // 386, ready at 5
                .eat(1, 3)          // 387
                .eat(2, 2)          // 388
                .attack(5)          // 391
                .assertLost(0);     // the counter reached exactly 5, so the bow was on time
    }

    // --- the fight window ---

    @Test
    public void shutWindowCountsNothing() {
        new Sim().speed(5)
                .windowShut()
                .idleUntil(10)
                .windowOpen()
                .attack(11)
                .assertCounted(1)
                .assertLost(0);
    }

    /**
     * An attack made while the window was shut still seeds the cooldown, because it proves the exclusion's
     * edge is a tick or two off. Ocellus P3 in a recorded raid: the exclusion ran to
     * 1671, an attack at 1669 was dropped, and 1673 was billed 2 ticks it should not have been.
     */
    @Test
    public void attackWhileShutStillSeedsTheCooldown() {
        new Sim().speed(5)
                .windowShut()
                .attack(0)
                .windowOpen()
                .attack(5)
                .assertAttacks(1)   // only the one inside the window counts
                .assertLost(0);     // but it was on time, because the shut one seeded the cooldown
    }

    /** A cooldown already running survives the window opening, so the wait is not charged as idling. */
    @Test
    public void runningCooldownSurvivesTheWindowOpening() {
        new Sim().speed(5)
                .windowShut()
                .attack(0)
                .windowOpen()
                .attack(5)
                .assertLost(0);
    }

    /** Start grace forgives the opening attack, for rooms you walk into. */
    @Test
    public void startGraceForgivesTheOpeningAttack() {
        new Sim().speed(5).grace(4)
                .attack(3)
                .assertLost(0);
    }

    @Test
    public void startGraceIsSpentOnce() {
        new Sim().speed(5).grace(4)
                .attack(3)
                .attack(12)         // 4 late, and the grace is gone
                .assertLost(4);
    }

    /** No grace, as at the Archive Guardian and Ocellus, so the opening tick is charged. */
    @Test
    public void withoutGraceTheOpeningAttackIsCharged() {
        new Sim().speed(5)
                .attack(3)
                .assertLost(3);
    }

    // --- the fight ending while a cooldown has already run out ---------------------------------------
    //
    // Overshoot is charged by the next attack, so when the boss dies there is nothing to charge it to. These
    // pin down what the close itself settles. Measured across the frame-logged raids in the recorded raids, this is
    // about 150 ticks over 17 raids, nearly all of it one tick at an Ocellus P1 handover or a P3 quadrant.

    /** The case this came from: three ticks off cooldown when the quadrant dies is three ticks lost. */
    @Test
    public void idlingWhenTheFightEndsIsSettled() {
        new Sim().speed(5)
                .attack(0)          // ready again at 5
                .fightEnds(8)       // 5, 6 and 7 were attackable and unused
                .assertLost(3)
                .assertAttacks(1);
    }

    /** The same gap, charged the ordinary way, so the close cannot be a different number. */
    @Test
    public void aSettlementMatchesWhatTheNextAttackWouldHaveCharged() {
        new Sim().speed(5).attack(0).attack(8).assertLost(3);
        new Sim().speed(5).attack(0).fightEnds(8).assertLost(3);
    }

    /** Dying mid cooldown owes nothing: those ticks were never yours to use. */
    @Test
    public void aFightEndingOnCooldownCostsNothing() {
        new Sim().speed(5)
                .attack(0)
                .fightEnds(3)       // still two ticks of cooldown to run
                .assertLost(0);
    }

    /** Leaving the raid, changing room or switching off is the MEASUREMENT stopping. Never billed. */
    @Test
    public void theMeasurementStoppingIsNotSettled() {
        new Sim().speed(5)
                .attack(0)
                .idleUntil(7)
                .windowShut()
                .idleUntil(12)
                .assertLost(0);
    }

    /** Settled once, not once per tick of the boss lying there dead. */
    @Test
    public void aFightEndsOnlyOnce() {
        new Sim().speed(5)
                .attack(0)
                .fightEnds(8)
                .idleUntil(30)
                .assertLost(3);
    }

    /** And the next phase starts clean rather than re-charging the gap it already settled. */
    @Test
    public void thePhaseAfterASettlementStartsClean() {
        new Sim().speed(5)
                .attack(0)
                .fightEnds(8)
                .idleUntil(20)
                .windowOpen()
                .attack(21)
                .assertLost(3);
    }

    /** Start grace covers a close as well, for a phase that opens and ends without an attack. */
    @Test
    public void graceForgivesASettlement() {
        new Sim().speed(5).grace(4)
                .idleUntil(2)
                .fightEnds(3)
                .assertLost(0);
    }

    // --- food at a close: charged only for the ticks the fight actually had ---------------------------

    /**
     * One tick off cooldown, eat, and the boss dies the tick after.
     * <p>
     * The anglerfish banks two ticks the moment it is swallowed (-1 + 3 = 2), but only one tick of fight
     * remained to spend them in. The other was going to be spent in a fight that no longer existed, so it is
     * not charged -- "there was only one in reality".
     */
    @Test
    public void foodIsChargedOnlyForTheTicksTheFightStillHad() {
        new Sim().speed(5)
                .attack(0)          // ready at 5
                .eat(6, 3)          // one tick late already, so the angler banks 2
                .fightEnds(7)
                .assertLost(2)      // ticks 5 and 6 were both wasted
                .assertLostToEating(1);
    }

    /** Eating on the very tick the boss dies costs nothing at all -- there was no tick left to lose. */
    @Test
    public void foodSwallowedAsTheFightEndsCostsNothing() {
        new Sim().speed(5)
                .attack(0)
                .idleUntil(5)
                .eatAsFightEnds(6, 3)
                .assertLost(1)              // tick 5, which was wasted before the food
                .assertLostToEating(0);
    }

    /** Given room to run, the same mouthful is charged in full. */
    @Test
    public void foodIsChargedInFullWhenTheFightOutlastsIt() {
        new Sim().speed(5)
                .attack(0)
                .eat(6, 3)          // banks 2, and this time both ticks arrive
                .fightEnds(9)
                .assertLost(4)      // 5 and 6 idle-then-eating, 7 and 8 held by the food
                .assertLostToEating(2);
    }

    /** A combo eat trimmed the same way: 5 ticks banked, two ticks of fight left to spend them in. */
    @Test
    public void aComboEatIsTrimmedToTheFightThatRemained() {
        new Sim().speed(5)
                .attack(0)
                .eat(5, 3, 2)       // exactly on cooldown, so the pair banks its whole 5
                .fightEnds(7)
                .assertLost(2)
                .assertLostToEating(2);
    }

    /** Eating while deep in the negative is free, and stays free when the fight then ends. */
    @Test
    public void aFreeEatStaysFreeWhenTheFightEnds() {
        new Sim().speed(5)
                .attack(0)
                .idleUntil(9)       // ready since 5, so five ticks in hand
                .eat(10, 3)         // -5 + 3, instant
                .fightEnds(11)
                .assertLost(6)      // ticks 5..10, all of it idling
                .assertLostToEating(0);
    }

    // --- firing a bow with no ammo: no shot, but the cooldown runs -------------------------------------
    //
    // Straight from a recorded raid, where it was found twice in marked Bork
    // segments and then deliberately reproduced nine more times at the Archive Guardian. Nothing visible
    // marks the tick, so before this it read as idling -- and worse, it made food that landed on the dud's
    // cooldown look free, because the weapon clock said we had been ready for ages.

    /** The whole cooldown goes nowhere, and it is charged to the dud rather than to idling. */
    @Test
    public void aDudShotBurnsTheWholeCooldownForNothing() {
        new Sim().speed(5)
                .attack(0)
                .dudShot(5)         // ready at 5, fires, nothing comes out
                .attack(10)
                .assertLost(5)
                .assertLostToDudShots(5)
                .assertAttacks(2);  // the dud is NOT one of them
    }

    /** Idling before the dud is still idling: the dud only owns the cooldown it started. */
    @Test
    public void idlingBeforeADudShotIsNotTheDudsFault() {
        new Sim().speed(5)
                .attack(0)
                .dudShot(7)         // two ticks idle first
                .attack(12)
                .assertLost(7)
                .assertLostToDudShots(5);
    }

    /**
     * The case from the raid: a dud, then an anglerfish onto the cooldown it started.
     * <p>
     * Tick 4740 of the log is the dud with a Zaryte crossbow (5t), the angler goes down at 4743 with two
     * ticks still to run, and the next attack lands at 4748 -- which is 2 + 3, exactly on time. The food
     * really did cost its full three ticks, and the overlay said zero before this.
     */
    @Test
    public void foodOnTopOfADudShotIsChargedInFull() {
        new Sim().speed(5)
                .attack(0)          // ready at 5
                .dudShot(5)         // cooldown runs to 10 for nothing
                .eat(8, 3)          // two ticks left to run, so the angler costs its whole 3
                .attack(13)         // 10 + 3, played perfectly
                .assertLost(8)
                .assertLostToDudShots(5)
                .assertLostToEating(3);
    }

    /** Without the dud known, that same gap blames nothing -- the bug this fixes. */
    @Test
    public void withoutTheDudTheSameGapLooksLikeAFreeEat() {
        new Sim().speed(5)
                .attack(0)
                .idleUntil(7)       // the dud's ticks, read as idling
                .eat(8, 3)          // three ticks past ready, so absorbed
                .attack(13)
                .assertLost(8)      // same total
                .assertLostToEating(0)
                .assertLostToDudShots(0);
    }

    /** Two duds in a row, which is what the Archive Guardian repro looks like. */
    @Test
    public void backToBackDudShotsEachCostTheirOwnCooldown() {
        new Sim().speed(5)
                .attack(0)
                .dudShot(5)
                .dudShot(10)
                .attack(15)
                .assertLost(10)
                .assertLostToDudShots(10)
                .assertAttacks(2);
    }

    /**
     * A dud mid cooldown is not a case, it is a guard: the game will not let you fire on cooldown.
     * <p>
     * If one arrives anyway our cooldown is wrong, so the only requirement is that it charges something
     * sane and never drags the counter backwards.
     */
    @Test
    public void aDudShotOnCooldownIsClampedRatherThanTrusted() {
        new Sim().speed(5)
                .attack(0)
                .dudShot(2)         // three ticks of cooldown still to run
                .attack(10)
                .assertLost(5)
                .assertLostToDudShots(2);
    }

    /** And a dud is only charged for the ticks the fight actually had, the same as food. */
    @Test
    public void aDudShotIsTrimmedWhenTheFightEnds() {
        new Sim().speed(5)
                .attack(0)
                .dudShot(5)         // would cost 5
                .fightEnds(7)       // but only two of them ever arrived
                .assertLost(2)
                .assertLostToDudShots(2);
    }

    /**
     * A forced idle before the dodge must not be charged, and the blast tick is not attackable.
     * <p>
     * Ocellus P3 at tick 3949 of a recorded raid: a 5t attack, one tick of
     * animation, then the explosion at 3951 with the next attack at 3954. The reset makes 3952 the first
     * usable tick, so 3952 and 3953 are the loss -- two, not the three it charged when the blast tick
     * counted as available.
     */
    @Test
    public void aVoidResetMakesTheTickAfterTheBlastTheFirstUsableOne() {
        new Sim().speed(5)
                .attack(0)          // 3949 in the log
                .voidExplosion(2)   // 3951, with a tick of animation in between
                .attack(5)          // 3954
                .assertLost(2);     // ticks 3 and 4 only; the blast tick was never on offer
    }

    /** Taking the slot straight after the blast is still free, however long the gap before it was. */
    @Test
    public void takingTheSlotAfterAForcedIdleCostsNothing() {
        new Sim().speed(5)
                .attack(0)
                .voidExplosion(2)   // a tick of forced idle between the attack and the dodge
                .attack(3)
                .assertLost(0);
    }

    /**
     * Two ticks of cooldown, then the dodge, then the attack: nothing lost.
     * <p>
     * A 5t attack on tick 1 is ready again on 6. The blast on 4 cuts that to 5, which is the first tick
     * after it, so firing on 5 is perfect play. Ticks 2 to 4 were never available -- they were cooldown.
     */
    @Test
    public void dodgingLateInTheCooldownAndFiringNextTickCostsNothing() {
        new Sim().speed(5)
                .attack(0)          // attack, two ticks of cooldown, dodge, attack
                .voidExplosion(3)
                .attack(4)
                .assertLost(0)
                .assertAttacks(2);
    }
}

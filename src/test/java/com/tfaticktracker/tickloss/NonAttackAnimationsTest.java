package com.tfaticktracker.tickloss;

import net.runelite.api.gameval.AnimationID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The veto list: animations that mean "busy, but not attacking".
 * <p>
 * It is a denylist and always will be. The alternative -- listing the attack animations instead -- fails in
 * the worse direction: an unknown animation would then read as "not an attack", so a weapon nobody has listed
 * yet would register no attacks at all and the whole fight would read as one enormous gap. Here an unknown
 * animation reads as an attack, so a new weapon works out of the box and a missing block merely hides a few
 * ticks of loss rather than inventing hundreds.
 * <p>
 * These cases are the ones that were actually wrong at some point, each found by measuring how often an
 * animation began on a tick we were being hit. They are pinned so that a later tidy-up of the list cannot
 * quietly undo them.
 */
public class NonAttackAnimationsTest {

    /** The dominant false positive: a block re-seeding the cooldown off a tick spent defending. */
    @Test
    public void blocksAreNotAttacks() {
        assertTrue(NonAttackAnimations.isNonAttack(AnimationID.HUMAN_UNARMEDBLOCK));
        assertTrue(NonAttackAnimations.isNonAttack(AnimationID.HUMAN_SCYTHE_BLOCK));
        assertTrue(NonAttackAnimations.isNonAttack(AnimationID.HUMAN_ELDER_MAUL_BLOCK));
    }

    /**
     * Spelled DEFENCE rather than BLOCK or DEFEND, so the generated list missed both.
     * <p>
     * Shield blocks alone accounted for 217 of the "early attacks" across the recorded raids before this was
     * found -- each one re-seeding the cooldown off a tick spent defending, which hides real loss.
     */
    @Test
    public void defenceIsSpeltThreeWaysAndAllThreeAreCovered() {
        assertTrue(NonAttackAnimations.isNonAttack(AnimationID.HUMAN_SHIELD_DEFENCE));
        assertTrue(NonAttackAnimations.isNonAttack(AnimationID.HUMAN_SHADE_DEFENCE));
        assertTrue(NonAttackAnimations.isNonAttack(AnimationID.HUMAN_APE_DEFEND));
    }

    /** Eating and dying both replace the idle animation, so both read as attacks without this. */
    @Test
    public void eatingAndDyingAreNotAttacks() {
        assertTrue(NonAttackAnimations.isNonAttack(AnimationID.HUMAN_EAT));
        assertTrue(NonAttackAnimations.isNonAttack(AnimationID.HUMAN_DEATH));
        assertTrue(NonAttackAnimations.isNonAttack(AnimationID.PREPOT_DEVICE_DRINK_FULL));
    }

    /** Summoning a thrall is the classic case of a real attack animation being masked by another. */
    @Test
    public void summoningAThrallIsNotAnAttack() {
        assertTrue(NonAttackAnimations.isNonAttack(AnimationID.HUMAN_SPELLCAST_RESURRECT));
        assertTrue("imbuing yourself is not an attack on anything",
                NonAttackAnimations.isNonAttack(AnimationID.HUMAN_CAST_SELFIMBUE));
    }

    /**
     * Real attacks stay out, including the ones whose names match the list's own patterns.
     * <p>
     * A wrong entry here over-reports loss, which is worse than missing one: it accuses you of ticks you did
     * not lose and you have no way to check. Tele Block reads like a block and is an attack.
     */
    @Test
    public void realAttacksAreNotVetoed() {
        assertFalse(NonAttackAnimations.isNonAttack(AnimationID.HUMAN_ELDER_MAUL_ATTACK));
        assertFalse(NonAttackAnimations.isNonAttack(AnimationID.HUMAN_GLAIVE_RALOS01_CHARGED_THROW));
        assertFalse("an animation nobody has listed is an attack, so new weapons work untouched",
                NonAttackAnimations.isNonAttack(-1));
    }

    /**
     * Combat spells are deliberately absent, freezing a corpse included.
     * <p>
     * Such a cast ignores the cooldown you are on but sets its own, which is exactly what the ordinary attack
     * path does: off cooldown it is a normal attack, on cooldown it is a reset, and either way it commits the
     * cast's own speed. Keeping them out means any freeze spell works without being named.
     */
    @Test
    public void combatSpellsAreLeftToTheOrdinaryAttackPath() {
        assertFalse(NonAttackAnimations.isNonAttack(AnimationID.HUMAN_SPELLCAST_GRASP));
    }
}

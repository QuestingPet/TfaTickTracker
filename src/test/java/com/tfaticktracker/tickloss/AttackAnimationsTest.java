package com.tfaticktracker.tickloss;

import net.runelite.api.gameval.AnimationID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The allowlist, and the three-way answer underneath it.
 * <p>
 * An animation is an attack, a known non-attack, or neither -- and the third case is handled rather than
 * guessed. These tests exist because the switch from a denylist to an allowlist moved the failure mode: it
 * used to be "a missing entry quietly hides loss", and it is now "a missing entry loudly invents it". The
 * second is survivable only if the gap is visible, so the unrecognised case is pinned as hard as the other two.
 */
public class AttackAnimationsTest {

    /**
     * No animation can be both, which is the one way the two lists could silently contradict each other.
     * <p>
     * Swept over the whole id space the game actually uses rather than spot-checked, because the lists are
     * long, are edited by hand, and were built from two different sources.
     */
    @Test
    public void nothingIsAnAttackAndANonAttackAtOnce() {
        for (int id = 0; id < 20000; id++) {
            if (AttackAnimations.isAttack(id) && NonAttackAnimations.isNonAttack(id)) {
                throw new AssertionError("animation " + id + " is in both lists");
            }
        }
    }

    /**
     * Every animation the audit actually saw, classified the way the audit says.
     * <p>
     * Measured over 44 recorded raids, 11-18 November: these 22 are every animation the local player showed
     * while targeting an NPC inside the raid. Nothing else occurred in a week of real raiding, which is what
     * makes an allowlist tractable here at all. If a later tidy-up of either list moves one of these, this
     * fails with the id.
     */
    @Test
    public void everyAnimationSeenInAWeekOfRaidsIsClassifiedTheSameWay() {
        // Weapons, by how often they turned up
        assertAttack(AnimationID.HUMAN_BOW);
        assertAttack(AnimationID.SCYTHE_OF_VITUR_ATTACK);
        assertAttack(AnimationID.SNAKEBOSS_BLOWPIPE_ATTACK);
        assertAttack(AnimationID.ZCB_ATTACK_PVN);
        assertAttack(AnimationID.HUMAN_WEAPON_BOW_VENATOR01_SHOOT);
        assertAttack(AnimationID.HUMAN_GLAIVE_RALOS01_CHARGED_SPECIAL);
        assertAttack(AnimationID.HUMAN_GLAIVE_RALOS01_CHARGED_THROW);
        assertAttack(AnimationID.HUMAN_UNARMEDKICK);
        assertAttack(AnimationID.TOA_SOT_CAST_B);

        // Casts that commit their own cooldown, so they go down the ordinary attack path
        assertAttack(AnimationID.HUMAN_SPELLCAST_GRASP);

        // Blocks, and the two spells that are not attacks on anything
        assertNonAttack(AnimationID.HUMAN_UNARMEDBLOCK);
        assertNonAttack(AnimationID.HUMAN_SCYTHE_BLOCK);
        assertNonAttack(AnimationID.HUMAN_SPEAR_BLOCK);
        assertNonAttack(AnimationID.HUMAN_STAFFORB_BLOCK);
        assertNonAttack(AnimationID.HUMAN_STAFF_BLOCK);
        assertNonAttack(AnimationID.HUMAN_WEAPONS_CRIMSON_KISTEN_DEF);
        assertNonAttack(AnimationID.HUMAN_SPELLCAST_RESURRECT);
        assertNonAttack(AnimationID.HUMAN_CAST_SELFIMBUE);
        assertNonAttack(AnimationID.HUMAN_EAT);
    }

    /**
     * The Crimson Kisten, which is why the allowlist could not just be copied.
     * <p>
     * attacktimer has the Kisten's BLOCK but neither of its attacks, and the audit found both in 9 of the 44
     * raids -- 218 attack starts between them. Taking that list as it stands would have told a Kisten user
     * they lost every tick they attacked in.
     */
    @Test
    public void theKistenAttacksAreAttacks() {
        assertAttack(AnimationID.HUMAN_WEAPONS_CRIMSON_KISTEN_ATTACK);
        assertAttack(AnimationID.HUMAN_WEAPONS_CRIMSON_KISTEN_SPECIAL);
    }

    /**
     * Opening a chest mid-fight, which the denylist counted as an attack.
     * <p>
     * A live bug the audit turned up: it re-seeded the attack cooldown off a tick spent opening a chest,
     * which hid real loss. Under the allowlist it is unrecognised -- not an attack, and reported -- which is
     * the correct answer for something neither list has an opinion about.
     */
    @Test
    public void openingAChestIsNotAnAttack() {
        assertFalse(AttackAnimations.isAttack(AnimationID.HUMAN_TFA_SMALL_CHEST_OPEN));
        assertTrue(AttackAnimations.isUnrecognised(AnimationID.HUMAN_TFA_SMALL_CHEST_OPEN));
    }

    /**
     * The things a denylist can never catch, because there is no end to them.
     * <p>
     * Alchemy, teleporting and vengeance all animate the player mid-fight and none of them is an attack.
     * Under a denylist every one re-seeded the cooldown and hid loss, silently and in the flattering
     * direction. They are not attacks now whether or not anybody ever lists them.
     */
    @Test
    public void alchingTeleportingAndVengeanceAreNotAttacks() {
        assertFalse(AttackAnimations.isAttack(AnimationID.HUMAN_CASTHIGHLVLALCHEMY));
        assertFalse(AttackAnimations.isAttack(AnimationID.VENGEANCE_SPELL_ANIM_NOSTALLING));
        assertFalse(AttackAnimations.isAttack(AnimationID.TELEPORT_NARDAH_HUMAN));
        assertFalse(AttackAnimations.isAttack(AnimationID.HUMAN_PICKPOCKET));
    }

    /**
     * An animation nobody has listed is "we do not know", and says so.
     * <p>
     * This is the whole trade. Under the old denylist an unknown animation was assumed to be an attack and
     * vanished into the numbers; here it is assumed to be nothing, and surfaces.
     */
    @Test
    public void anUnlistedAnimationIsUnrecognisedRatherThanAssumed() {
        assertFalse("a brand new weapon is not silently an attack", AttackAnimations.isAttack(19999));
        assertTrue("and it does not vanish either", AttackAnimations.isUnrecognised(19999));

        assertFalse("no animation at all is not something to report",
                AttackAnimations.isUnrecognised(-1));
        assertFalse("a known attack is not unrecognised",
                AttackAnimations.isUnrecognised(AnimationID.HUMAN_BOW));
        assertFalse("nor is a known non-attack",
                AttackAnimations.isUnrecognised(AnimationID.HUMAN_EAT));
    }

    private static void assertAttack(int id) {
        assertTrue("animation " + id + " should be an attack", AttackAnimations.isAttack(id));
        assertFalse("animation " + id + " should not be unrecognised",
                AttackAnimations.isUnrecognised(id));
    }

    private static void assertNonAttack(int id) {
        assertFalse("animation " + id + " should not be an attack", AttackAnimations.isAttack(id));
        assertTrue("animation " + id + " should be a known non-attack",
                NonAttackAnimations.isNonAttack(id));
        assertFalse("animation " + id + " should not be unrecognised",
                AttackAnimations.isUnrecognised(id));
    }
}

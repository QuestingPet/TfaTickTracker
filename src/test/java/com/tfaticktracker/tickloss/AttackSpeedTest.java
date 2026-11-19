package com.tfaticktracker.tickloss;

import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Weapon speed, and the round trip that keeps the logger and the replay in step.
 * <p>
 * {@code EventLogger} writes {@link AttackSpeed#describe} onto every TICK line and
 * {@link LogReplay#parseFacts} reads it back. Nothing else couples those two, so a format change on one side
 * would otherwise just quietly stop being read and every replayed raid would silently fall back to guessing
 * the speed from the animation.
 */
public class AttackSpeedTest {

    private static AttackSpeed.Facts facts() {
        return new AttackSpeed.Facts();
    }

    /** What the logger writes is exactly what the replay reads, for every field. */
    @Test
    public void whatTheLoggerWritesIsWhatTheReplayReads() {
        final AttackSpeed.Facts original = facts()
                .weaponId(ItemID.TWISTED_BOW)
                .animation(AnimationID.HUMAN_BOW)
                .aspeed(6)
                .style(AttackStyles.Style.RANGING)
                .rapid(true)
                .manualCasting(false)
                .autoCasting(true)
                .castingStyle(false);

        final String line = "x=1 y=2 anim=426 " + AttackSpeed.describe(original) + " target=5 targetatk=1";
        final AttackSpeed.Facts parsed = LogReplay.parseFacts(line, AnimationID.HUMAN_BOW);

        assertEquals("weapon", original.weaponId(), parsed.weaponId());
        assertEquals("aspeed", original.aspeed(), parsed.aspeed());
        assertEquals("style", original.style(), parsed.style());
        assertEquals("rapid", original.rapid(), parsed.rapid());
        assertEquals("manual casting", original.manualCasting(), parsed.manualCasting());
        assertEquals("autocasting", original.autoCasting(), parsed.autoCasting());
        assertEquals("casting style", original.castingStyle(), parsed.castingStyle());
        assertEquals("the speed the replay would use", AttackSpeed.compute(original), AttackSpeed.compute(parsed));
    }

    @Test
    public void aNullStyleSurvivesTheRoundTrip() {
        final AttackSpeed.Facts original = facts().weaponId(1).aspeed(4).style(null);
        assertNull(LogReplay.parseFacts(AttackSpeed.describe(original), -1).style());
    }

    /** A log written before these facts were recorded reads as absent, not as zeroes. */
    @Test
    public void anOldTickLineHasNoFacts() {
        assertNull(LogReplay.parseFacts("x=1 y=2 anim=426 pose=808 players=4 npcs=5", 426));
    }

    // --- the speeds themselves ---

    @Test
    public void rapidIsOneTickFaster() {
        assertEquals(5, AttackSpeed.compute(facts().weaponId(ItemID.TWISTED_BOW).aspeed(6).rapid(true)));
        assertEquals(6, AttackSpeed.compute(facts().weaponId(ItemID.TWISTED_BOW).aspeed(6).rapid(false)));
    }

    @Test
    public void unarmedHasItsOwnSpeed() {
        assertEquals(AttackSpeed.UNARMED_SPEED, AttackSpeed.compute(facts().weaponId(0).aspeed(0)));
        assertEquals(AttackSpeed.UNARMED_SPEED, AttackSpeed.compute(facts().weaponId(-1).aspeed(6)));
    }

    /** Casting is paced by the spell, not the staff, so a 4t wand still casts at 5t. */
    @Test
    public void autocastingIsFiveRegardlessOfTheStaff() {
        assertEquals(5, AttackSpeed.compute(facts()
                .weaponId(ItemID.SANGUINESTI_STAFF).aspeed(4).autoCasting(true).castingStyle(true)));
    }

    @Test
    public void harmonisedCastsFaster() {
        assertEquals(4, AttackSpeed.compute(facts()
                .weaponId(ItemID.NIGHTMARE_STAFF_HARMONISED).aspeed(4).autoCasting(true).castingStyle(true)));
    }

    /**
     * A powered staff with no autocast spell set falls through to its own speed -- gating on the varbit rather
     * than on the style alone is what keeps a trident at 4t.
     */
    @Test
    public void aPoweredStaffWithoutAutocastKeepsItsOwnSpeed() {
        assertEquals(4, AttackSpeed.compute(facts()
                .weaponId(ItemID.MERFOLK_TRIDENT).aspeed(4).autoCasting(false).castingStyle(true)));
    }

    @Test
    public void manualCastingIsFiveEvenWithoutAutocast() {
        assertEquals(5, AttackSpeed.compute(facts()
                .weaponId(ItemID.MERFOLK_TRIDENT).aspeed(4).manualCasting(true)));
    }

    /** The Obligator smash is 3t and the multi-target thrust is 5t, told apart by the animation. */
    @Test
    public void obligatorDependsOnWhichAttackWasUsed() {
        assertEquals(3, AttackSpeed.compute(facts()
                .weaponId(ItemID.OBLIGATOR).animation(AnimationID.TFA_HUMAN_OBLIGATOR_ATTACK)));
        assertEquals(5, AttackSpeed.compute(facts()
                .weaponId(ItemID.OBLIGATOR).animation(AnimationID.TFA_HUMAN_OBLIGATOR_MULTIHIT)));
    }

    /** With the animation masked -- a thrall summon, or eating on the same tick -- the style decides. */
    @Test
    public void obligatorFallsBackToTheAttackStyleWhenMasked() {
        assertEquals(3, AttackSpeed.compute(facts()
                .weaponId(ItemID.OBLIGATOR).animation(-1).style(AttackStyles.Style.ACCURATE)));
        assertEquals(5, AttackSpeed.compute(facts()
                .weaponId(ItemID.OBLIGATOR).animation(-1).style(AttackStyles.Style.CONTROLLED)));
    }

    @Test
    public void speedIsNeverZero() {
        assertEquals(1, AttackSpeed.compute(facts().weaponId(ItemID.TWISTED_BOW).aspeed(1).rapid(true)));
    }
}

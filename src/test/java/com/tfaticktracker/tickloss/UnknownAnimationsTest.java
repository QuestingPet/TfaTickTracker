package com.tfaticktracker.tickloss;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Which animations we counted as attacks without really knowing, and which of those are worth mentioning.
 * <p>
 * The denylist decides, and its default is "attack". That default is usually right and is silent when it is
 * not: a thing that merely animates you mid-fight re-seeds your cooldown and HIDES loss, so the number comes
 * out flattering and nobody reports it. This is the only thing standing between that and shipping, so what
 * does and does not set it off is pinned here rather than left to a raid.
 */
public class UnknownAnimationsTest {

    private static final int ALCH = 713;

    /** Counted as an attack again and again, never once dealt damage. The case it exists for. */
    @Test
    public void reportsAnAnimationThatNeverDealsDamage() {
        final UnknownAnimations unknown = new UnknownAnimations();
        for (int i = 0; i < UnknownAnimations.STARTS_BEFORE_REPORTING; i++) {
            unknown.countedAsAttack(ALCH, 100 + i * 10);
        }

        assertEquals(java.util.Collections.singletonList(ALCH), unknown.settle());
    }

    /**
     * Four misses say very little. Real weapons miss, and a streak at a raid boss is ordinary.
     * <p>
     * Reporting too eagerly would put a line on the overlay in every raid and teach everybody to ignore it,
     * which costs more than the occasional gap this stays quiet about.
     */
    @Test
    public void saysNothingUntilItHasHappenedEnoughTimes() {
        final UnknownAnimations unknown = new UnknownAnimations();
        for (int i = 0; i < UnknownAnimations.STARTS_BEFORE_REPORTING - 1; i++) {
            unknown.countedAsAttack(ALCH, 100 + i * 10);
        }

        assertTrue(unknown.settle().isEmpty());
    }

    /**
     * Damage once is enough for good: it is an attack, and its later misses say nothing new.
     * <p>
     * This is what keeps a weapon nobody has listed out of the report. It is being counted correctly, which
     * is the denylist default working rather than failing.
     */
    @Test
    public void staysQuietAboutAnythingThatHasEverHit() {
        final UnknownAnimations unknown = new UnknownAnimations();

        unknown.countedAsAttack(14253, 100);
        unknown.damageDealt(100);
        for (int i = 0; i < 10; i++) unknown.countedAsAttack(14253, 110 + i * 10);

        assertTrue(unknown.settle().isEmpty());
    }

    /** Experience lands on the attack tick, with a tick of slack and no more. */
    @Test
    public void countsDamageOnTheAttackTickOrOneAfter() {
        final UnknownAnimations onTime = new UnknownAnimations();
        onTime.countedAsAttack(14253, 100);
        onTime.damageDealt(100 + UnknownAnimations.WINDOW_TICKS);
        for (int i = 0; i < 5; i++) onTime.countedAsAttack(14253, 200 + i * 10);
        assertTrue("backed on the last allowed tick", onTime.settle().isEmpty());

        final UnknownAnimations tooLate = new UnknownAnimations();
        tooLate.countedAsAttack(ALCH, 100);
        tooLate.damageDealt(100 + UnknownAnimations.WINDOW_TICKS + 1);
        for (int i = 0; i < 5; i++) tooLate.countedAsAttack(ALCH, 200 + i * 10);
        assertEquals("somebody else's hit, far too late to be this animation's",
                1, tooLate.settle().size());
    }

    /** Damage before the animation is somebody else's, or a previous attack of ours landing. */
    @Test
    public void ignoresDamageFromBeforeTheAnimation() {
        final UnknownAnimations unknown = new UnknownAnimations();
        unknown.countedAsAttack(ALCH, 100);
        unknown.damageDealt(99);
        for (int i = 0; i < 5; i++) unknown.countedAsAttack(ALCH, 200 + i * 10);

        assertEquals(1, unknown.settle().size());
    }

    /**
     * Said once per raid, not once per room.
     * <p>
     * Repeating it every room would make the overlay noisier the longer the problem went unfixed, which is
     * precisely backwards.
     */
    @Test
    public void mentionsEachAnimationOnlyOnce() {
        final UnknownAnimations unknown = new UnknownAnimations();
        for (int i = 0; i < 10; i++) unknown.countedAsAttack(ALCH, 100 + i * 10);

        assertEquals(1, unknown.settle().size());
        assertTrue("the next room does not say it again", unknown.settle().isEmpty());
    }

    /**
     * Tallies run across the raid, because one alch a room would never reach the threshold inside one.
     * <p>
     * {@code settle} is called as each room closes, so this is what makes a slow drip add up at all.
     */
    @Test
    public void addsUpAcrossRoomsRatherThanWithinOne() {
        final UnknownAnimations unknown = new UnknownAnimations();

        for (int room = 0; room < UnknownAnimations.STARTS_BEFORE_REPORTING - 1; room++) {
            unknown.countedAsAttack(ALCH, 100 + room * 400);
            assertTrue("one a room never reaches the threshold inside a room",
                    unknown.settle().isEmpty());
        }
        unknown.countedAsAttack(ALCH, 9000);

        assertEquals("the room that tips it over is where it is said", 1, unknown.settle().size());
    }

    /**
     * A flood of unknown animations says its piece and stops.
     * <p>
     * Not really a memory bound -- a raid clears everything -- but an overlay one. Five ids is already past
     * what anybody reads, and a build wrong enough to produce six does not need a sixth line to prove it.
     */
    @Test
    public void mentionsOnlyAHandfulPerRaid() {
        final UnknownAnimations unknown = new UnknownAnimations();
        for (int i = 0; i < 500; i++) {
            for (int n = 0; n < UnknownAnimations.STARTS_BEFORE_REPORTING; n++) {
                unknown.countedAsAttack(90000 + i, 100 + n);
            }
        }

        assertTrue("capped", unknown.settle().size() <= 5);
        assertTrue("and it does not resume in the next room", unknown.settle().isEmpty());
    }

    /** A raid ending forgets everything, so the next one starts from nothing. */
    @Test
    public void startsCleanEachRaid() {
        final UnknownAnimations unknown = new UnknownAnimations();
        for (int i = 0; i < UnknownAnimations.STARTS_BEFORE_REPORTING; i++) {
            unknown.countedAsAttack(ALCH, 100 + i);
        }

        unknown.reset();

        assertEquals(0, unknown.startsOf(ALCH));
        assertTrue(unknown.settle().isEmpty());
    }
}

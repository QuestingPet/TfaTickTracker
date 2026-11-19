package com.tfaticktracker.tickloss;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Telling a fresh attack from one still animating. Every case here is a real tick sequence that this got
 * wrong at some point.
 */
public class AttackStartTest {

    private final AttackStart start = new AttackStart();

    private boolean tick(int animation, int frame) {
        return start.started(animation, frame, false);
    }

    private boolean tickRetargeting(int animation, int frame) {
        return start.started(animation, frame, true);
    }

    @Test
    public void idleIsNotAnAttack() {
        assertFalse(tick(-1, -1));
        assertFalse(tick(-1, -1));
    }

    @Test
    public void aNewAnimationIsAnAttack() {
        assertFalse(tick(-1, -1));
        assertTrue(tick(426, 0));
    }

    /**
     * The animation lingers after the shot. Tick 342 of debug-logs/tfa-events-20261113-172610.log was
     * credited as an attack that never happened because the blowpipe animation was still running.
     */
    @Test
    public void aLingeringAnimationIsNotAnAttack() {
        assertTrue(tick(5061, 0));
        assertFalse(tick(5061, 4));
        assertFalse(tick(5061, 8));
    }

    /**
     * A repeat with the same animation id, which AnimationChanged does not report at all. The frame
     * restarting is what gives it away: a blowpipe firing every 2 ticks.
     */
    @Test
    public void aRepeatWithTheSameAnimationIsAnAttack() {
        assertTrue(tick(5061, 0));
        assertFalse(tick(5061, 4));
        assertTrue("the frame went backwards, so the animation restarted", tick(5061, 0));
        assertFalse(tick(5061, 4));
        assertTrue(tick(5061, 0));
    }

    /**
     * Two undead grasps back to back, both reading frame 0 because frames do not advance one per tick.
     * Ticks 2630 and 2631 of debug-logs/tfa-events-20261115-000949.log, each picking a different corpse --
     * 54676 then 54687. The second was missed, its five ticks were never committed, and the bow at 2636 was
     * billed a tick it could not have saved.
     */
    @Test
    public void aRepeatWhoseFrameHasNotMovedIsFoundByTheRetarget() {
        assertTrue("2630, the first grasp", tick(8972, 0));
        assertTrue("2631, the second, on a different corpse", tickRetargeting(8972, 0));
        assertFalse("2632, the same cast running on", tick(8972, 5));
    }

    /** Without the retarget there is nothing to go on, so the same two ticks read as one cast. */
    @Test
    public void thatSameRepeatIsInvisibleWithoutTheRetarget() {
        assertTrue(tick(8972, 0));
        assertFalse(tick(8972, 0));
    }

    /**
     * A re-click is NOT an attack on its own. Used as a signal in its own right it added thousands of attacks
     * across debug-logs/ and quietly erased real loss, so it is consulted only when the frame says nothing.
     */
    @Test
    public void retargetingWhileAnAnimationRunsOnIsNotAnAttack() {
        assertTrue(tick(426, 0));
        assertFalse("the frame moved on, so this is the same shot", tickRetargeting(426, 4));
        assertFalse(tickRetargeting(426, 8));
    }

    /**
     * Ocellus P3 resets the cooldown, so a scythe swings again two ticks later. Same animation id both
     * times, so only the frame restarting shows it -- ticks 1635, 1637 and 1639 of
     * debug-logs/tfa-events-20261115-000949.log, during a void explosion run.
     */
    @Test
    public void ocellusP3ScytheSwingsBackToBack() {
        assertTrue("1635", tick(8056, 0));
        assertFalse(tick(8056, 11));
        assertTrue("1637, two ticks later, reset by the explosion", tick(8056, 0));
        assertFalse(tick(8056, 11));
        assertTrue("1639", tick(8056, 0));
    }

    /** Swapping weapon mid fight is a new animation, so it needs no help. */
    @Test
    public void aWeaponSwapIsAnAttack() {
        assertTrue(tick(5061, 0));
        assertFalse(tick(5061, 4));
        assertTrue(tick(426, 0));
    }

    /** Going idle and attacking again is two attacks, not three. */
    @Test
    public void goingIdleBetweenAttacks() {
        assertTrue(tick(426, 0));
        assertFalse(tick(426, 4));
        assertFalse(tick(-1, -1));
        assertFalse(tick(-1, -1));
        assertTrue(tick(426, 0));
    }

    /**
     * A log written before frames were recorded has none, so -1 stands in and only the animation changing is
     * left. Repeats sharing an id are missed, which is a known limit of replaying old logs.
     */
    @Test
    public void withoutFramesOnlyAnAnimationChangeShows() {
        assertTrue(tick(426, -1));
        assertFalse(tick(426, -1));
        assertTrue(tick(5061, -1));
    }

    /**
     * The case that worried us most: attack something, then click something else next tick while still on
     * cooldown. The frame has moved on, so the retarget is never consulted and no attack is invented.
     * Measured across every log with frames, the tie-break fires on HUMAN_SPELLCAST_GRASP and nothing else.
     */
    @Test
    public void clickingAnotherTargetMidAttackInventsNothing() {
        assertTrue("tick 1, bow fires", tick(426, 0));
        assertFalse("tick 2, clicked a different npc, but the bow animation moved on",
                tickRetargeting(426, 4));
        assertFalse(tickRetargeting(426, 8));
        assertFalse(tickRetargeting(-1, -1));
    }

    @Test
    public void resetForgetsTheLastAnimation() {
        assertTrue(tick(426, 0));
        assertFalse(tick(426, 0));
        start.reset();
        assertTrue(tick(426, 0));
    }
}

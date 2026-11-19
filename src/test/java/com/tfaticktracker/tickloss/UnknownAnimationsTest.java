package com.tfaticktracker.tickloss;

import java.util.List;

import com.tfaticktracker.RaidRoom;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Which unrecognised animations are worth telling somebody about.
 * <p>
 * The allowlist is only safe because a gap in it is visible, and this is what decides whether a gap is
 * shown. Too eager and every boss mechanic that animates the player becomes a report; too shy and a weapon
 * nobody has listed goes on quietly inflating a room's loss. Both failures are silent in a raid, so they
 * are pinned here instead.
 */
public class UnknownAnimationsTest {

    private static SegmentStats segment() {
        return new SegmentStats(RaidRoom.BORK, 0, null, 0);
    }

    /** The case it exists for: an animation nobody listed, which then landed a hit. */
    @Test
    public void reportsAnAnimationThatWentOnToDealDamage() {
        final UnknownAnimations unknown = new UnknownAnimations();
        final SegmentStats room = segment();

        unknown.seen(14253, 100, room);
        final List<UnknownAnimations.Entry> reported = unknown.takeBackedByDamage(100);

        assertEquals(1, reported.size());
        assertEquals(14253, reported.get(0).animation);
        assertEquals(room, reported.get(0).segment);
    }

    /**
     * Damage is allowed to arrive a few ticks late, because a projectile has to get there.
     * <p>
     * Melee resolves on the tick itself; a bow or a cast does not. Charging the animation only for
     * same-tick damage would report melee weapons and no others.
     */
    @Test
    public void allowsForTheTimeAProjectileTakes() {
        final UnknownAnimations unknown = new UnknownAnimations();
        unknown.seen(14253, 100, segment());

        assertEquals(1, unknown.takeBackedByDamage(100 + UnknownAnimations.WINDOW_TICKS).size());
    }

    /**
     * Damage long afterwards says nothing about an animation, and the animation is dropped.
     * <p>
     * Otherwise one knockback early in a fight would be reported by the first hit anybody landed for the
     * rest of the room.
     */
    @Test
    public void forgetsAnAnimationNothingEverCameOf() {
        final UnknownAnimations unknown = new UnknownAnimations();
        unknown.seen(99999, 100, segment());

        assertTrue("too late to be this animation's doing",
                unknown.takeBackedByDamage(100 + UnknownAnimations.WINDOW_TICKS + 1).isEmpty());
        assertTrue("and it is not kept around to be reported by the next hit",
                unknown.takeBackedByDamage(103).isEmpty());
    }

    /** Reported once and then let go, so a long fight does not repeat it on every hit. */
    @Test
    public void reportsACandidateOnceAndThenForgetsIt() {
        final UnknownAnimations unknown = new UnknownAnimations();
        unknown.seen(99999, 100, segment());

        assertEquals(1, unknown.takeBackedByDamage(101).size());
        assertTrue("the next hit does not report it all over again",
                unknown.takeBackedByDamage(102).isEmpty());
    }

    /** An animation lasting several ticks is one candidate, not one per tick. */
    @Test
    public void countsAnAnimationOncePerRoom() {
        final UnknownAnimations unknown = new UnknownAnimations();
        final SegmentStats room = segment();

        unknown.seen(14253, 100, room);
        unknown.seen(14253, 101, room);
        unknown.seen(14253, 102, room);

        assertEquals("one candidate, and still live on its last tick",
                1, unknown.takeBackedByDamage(102).size());
    }

    /** The same animation in a later room is worth reporting again, against that room. */
    @Test
    public void treatsEachRoomSeparately() {
        final UnknownAnimations unknown = new UnknownAnimations();
        final SegmentStats first = segment();
        final SegmentStats second = segment();

        unknown.seen(14253, 100, first);
        unknown.seen(14253, 101, second);

        assertEquals(2, unknown.takeBackedByDamage(101).size());
    }

    /**
     * A flood of unknown animations cannot grow the list without bound.
     * <p>
     * Nothing normal produces this. A bug in the lists, or a boss that animates the player every tick,
     * would -- and the cost of believing it is memory that only grows.
     */
    @Test
    public void holdsOnlyAHandfulAtATime() {
        final UnknownAnimations unknown = new UnknownAnimations();
        final SegmentStats room = segment();
        for (int i = 0; i < 500; i++) unknown.seen(90000 + i, 100, room);

        assertTrue("bounded well above anything real", unknown.takeBackedByDamage(101).size() <= 16);
    }

    /** Nothing is reported before it happened, which a tick counter reset would otherwise cause. */
    @Test
    public void ignoresDamageFromBeforeTheAnimation() {
        final UnknownAnimations unknown = new UnknownAnimations();
        unknown.seen(14253, 100, segment());

        assertTrue(unknown.takeBackedByDamage(99).isEmpty());
    }
}

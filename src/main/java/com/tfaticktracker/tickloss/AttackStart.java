package com.tfaticktracker.tickloss;

/**
 * Did an attack START this tick, or is one simply still animating?
 * <p>
 * Harder than it sounds, and got wrong three times before it was pulled out here where it could be tested:
 * <ul>
 *     <li><b>The animation alone cannot say.</b> It lingers after the shot that produced it, so "an attack
 *     animation is showing" is true both for a fresh attack and for one two ticks old. Polling that way
 *     invented an attack at tick 342 of a recorded raid that never happened.</li>
 *     <li><b>{@code AnimationChanged} does not re-fire</b> when an animation repeats with the same id, so a
 *     blowpipe firing every 2 ticks produces one event and eight attacks.</li>
 *     <li><b>A spotanim is not an attack signal.</b> Plenty of graphics have nothing to do with attacking:
 *     spotanim 4200 goes off every 5 ticks all through a recorded raid and invented
 *     three early attacks during a blowpipe stretch.</li>
 *     <li><b>A re-click is not an attack either</b>, on its own. Used as a signal in its own right it added
 *     thousands of attacks across the recorded raids and quietly erased real loss.</li>
 * </ul>
 * What works is the animation FRAME, which restarts on a new attack -- with one gap. Frames do not advance
 * one per tick, so a repeat can show the same frame twice: two undead grasps go out back to back at 2630 and
 * 2631 of a recorded raid and BOTH read frame 0, with 5 only arriving at 2632.
 * Only there, where the frame says nothing either way, does picking out a different NPC break the tie.
 */
public final class AttackStart {

    private int lastAnimation = -1;
    private int lastFrame = -1;

    public void reset() {
        lastAnimation = -1;
        lastFrame = -1;
    }

    /**
     * @param animation             the local player's animation, or -1 for none
     * @param frame                 its current frame, which restarts when the animation does
     * @param retargetedToOtherNpc  whether we picked out a DIFFERENT npc this tick, which is a fresh attack
     *                              command. Requiring a different one keeps a re-click on the thing you are
     *                              already fighting from counting as an attack you did not make.
     */
    public boolean started(int animation, int frame, boolean retargetedToOtherNpc) {
        final boolean started = animation != -1
                && (animation != lastAnimation
                    || frame < lastFrame
                    // The retarget only breaks a TIE. If the frame moved on, the animation is still running
                    // and a re-click is not an attack.
                    || (frame == lastFrame && retargetedToOtherNpc));

        lastAnimation = animation;
        lastFrame = frame;
        return started;
    }
}

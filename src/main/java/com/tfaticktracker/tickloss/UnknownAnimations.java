package com.tfaticktracker.tickloss;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Animations in neither list, held back until there is a reason to think they were an attack.
 * <p>
 * Being unrecognised is not on its own worth telling anybody about. A boss mechanic that animates the
 * player -- a knockback, a stun, being dragged somewhere -- is unrecognised too, and reporting every one of
 * those would turn the signal into noise inside one raid.
 * <p>
 * What separates the two is whether the animation coincided with damage WE dealt, which is what hitpoints
 * experience means: every combat attack that connects grants it, and in a raid nothing else does -- thralls
 * deal damage and grant none. So an unknown animation is reported only once hitpoints experience turns up
 * soon after it.
 * <p>
 * <b>This cannot be the attack detector</b>, which is exactly why it only gates the report. A miss or a zero
 * grants no experience at all, and a cooldown must still be re-seeded on a miss -- a detector built on this
 * would ignore every attack that failed to connect, which at a raid boss is a great many of them. As a hint
 * about a weapon nobody has listed it is much safer: use it enough times and one of them lands.
 * <p>
 * Pure, with the client kept out, so the windowing can be read in a test rather than inferred from a raid.
 */
final class UnknownAnimations {

    /**
     * How long after an animation its experience can still arrive.
     * <p>
     * Hitpoints experience lands on the tick of the attack itself, not when the hitsplat reaches the NPC --
     * confirmed by the player, who is the only one able to confirm in-game behaviour here. One tick of slack
     * is kept because attacktimer allows the same, which suggests it can occasionally arrive late.
     * <p>
     * The project's own measurement of animation-to-hitsplat is therefore NOT what sizes this, and is worth
     * writing down only to say why: over the 44 recorded raids our own hitsplat lands at +1 for 85% of 4384
     * scythe swings, +0 for 86% of blowpipe shots, and anywhere from +0 to +4 for a bow. Experience that
     * waited for the hit would need a window of five. It does not.
     * <p>
     * <b>Hitsplats were considered as the signal instead and rejected.</b> They are delayed by exactly that
     * travel time, they are not guaranteed to appear in every case, and with attacks in flight they can
     * arrive out of order. Experience is immediate and unambiguous, which is the whole reason to use it.
     */
    static final int WINDOW_TICKS = 1;

    /** At most this many held at once, so a bad tick cannot grow the list without bound. */
    private static final int MAX_PENDING = 16;

    static final class Entry {
        final int animation;
        final int tick;
        final SegmentStats segment;

        Entry(int animation, int tick, SegmentStats segment) {
            this.animation = animation;
            this.tick = tick;
            this.segment = segment;
        }
    }

    private final List<Entry> pending = new ArrayList<>();

    void reset() {
        pending.clear();
    }

    /**
     * An animation we did not recognise, during a fight. Held, not reported.
     * <p>
     * One candidate per animation per segment, re-stamped each tick it is still showing. An animation
     * lasting several ticks would otherwise fill the list on its own and crowd out everything after it,
     * and with a one tick window its first sighting would go stale before the tick it mattered on.
     */
    void seen(int animation, int tick, SegmentStats segment) {
        if (segment == null) return;

        final Iterator<Entry> entries = pending.iterator();
        while (entries.hasNext()) {
            final Entry entry = entries.next();
            if (entry.animation == animation && entry.segment == segment) {
                // Still showing. Re-stamp rather than keep the first sighting, or an animation lasting
                // several ticks would fall outside the window before the tick it earned anything on.
                entries.remove();
                break;
            }
        }
        if (pending.size() >= MAX_PENDING) return;
        pending.add(new Entry(animation, tick, segment));
    }

    /**
     * We just dealt damage. Anything unrecognised in the last few ticks is now a candidate.
     * <p>
     * Also the only place stale entries are dropped, which is enough: an animation that never once coincided
     * with damage is not a weapon, and holding it costs a slot that {@link #MAX_PENDING} already bounds.
     */
    List<Entry> takeBackedByDamage(int tick) {
        final List<Entry> reported = new ArrayList<>();
        final Iterator<Entry> entries = pending.iterator();
        while (entries.hasNext()) {
            final Entry entry = entries.next();
            final int age = tick - entry.tick;
            if (age < 0 || age > WINDOW_TICKS) {
                if (age > WINDOW_TICKS) entries.remove();
                continue;
            }
            reported.add(entry);
            entries.remove();
        }
        return reported;
    }
}

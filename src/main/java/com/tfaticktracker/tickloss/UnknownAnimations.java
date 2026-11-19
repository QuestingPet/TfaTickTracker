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
     * How long after an animation a hitsplat of ours can still land.
     * <p>
     * Melee resolves on the tick itself, but a bow or a cast has to travel, so the experience arrives a few
     * ticks later. Five covers the longest travel in the recorded raids with room to spare, and being
     * generous costs at most an over-eager candidate in a report, never a measured number.
     */
    static final int WINDOW_TICKS = 5;

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
     * Deduplicated per segment: a two tick animation shows on both of its ticks, and a weapon swung all
     * fight would otherwise fill the list on its own and crowd out everything after it.
     */
    void seen(int animation, int tick, SegmentStats segment) {
        if (segment == null || pending.size() >= MAX_PENDING) return;
        for (Entry entry : pending) {
            if (entry.animation == animation && entry.segment == segment) return;
        }
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

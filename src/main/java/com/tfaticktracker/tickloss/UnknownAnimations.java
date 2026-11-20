package com.tfaticktracker.tickloss;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Animations in neither list, counted as attacks, watched for signs that they should not have been.
 * <p>
 * {@link NonAttackAnimations} decides, and its default is that an animation nobody has listed is an attack.
 * That default is right far more often than not -- it survives a weapon nobody has listed, and an attack
 * whose animation was overridden by something else -- but it is wrong for anything that merely animates you
 * while you fight, and when it is wrong it re-seeds the attack cooldown and HIDES loss. A flattering number
 * is one nobody reports, so the gap has to find itself.
 * <p>
 * <b>What gives it away is damage.</b> Every attack that connects grants hitpoints experience, on the tick
 * of the attack, and in a raid nothing else does -- thralls deal damage and grant none. One attack can miss,
 * and two can, but an animation counted as an attack {@link #STARTS_BEFORE_REPORTING} times that has never
 * once produced damage is much more likely to be something that is not an attack at all.
 * <p>
 * So it reports rather than decides, and only on the way out of a room. Nothing here changes a number.
 * <p>
 * Tallies run across the whole raid, not per room: an animation used once a room would never reach the
 * threshold inside one. Pure, with the client kept out, so the rule can be read in a test rather than
 * inferred from a raid.
 */
final class UnknownAnimations {

    /**
     * How long after an attack its experience can still arrive.
     * <p>
     * Hitpoints experience lands on the tick of the attack itself, not when the hitsplat reaches the NPC.
     * Stated by the player and then measured, once the logger started recording xp: in
     * a recorded raid all 11 hitpoints gains fall on the exact tick a glaive
     * throw started, offset zero, none later. One tick of slack is kept anyway because attacktimer allows
     * the same, which suggests it can occasionally arrive late.
     * <p>
     * <b>Hitsplats were considered as the signal instead and rejected.</b> They are delayed by the time a
     * projectile takes, they are not guaranteed to appear in every case, and with attacks in flight they can
     * arrive out of order. Experience is immediate and unambiguous, which is the whole reason to use it.
     */
    static final int WINDOW_TICKS = 1;

    /**
     * How many times an animation has to be counted as an attack, having never produced damage, before it
     * is worth mentioning.
     * <p>
     * Five, counted across the whole raid rather than within a room. A real weapon that has swung five
     * times and connected on none of them is still possible -- which is exactly why this ends in a line of
     * text rather than in a different number -- but it is unlikely enough that five misses in a row is
     * worth a sentence. Three was tried first and is too eager: a bad streak at a raid boss is ordinary.
     */
    static final int STARTS_BEFORE_REPORTING = 5;

    /**
     * At most this many watched at once, and at most this many ever mentioned, per raid.
     * <p>
     * Everything here is cleared when a raid starts, so neither bound is really about memory -- a raid is
     * an hour at the outside. They are about the overlay. Five ids on screen is already past the point
     * where anybody reads them, and a build with a list so wrong that six distinct animations are being
     * miscounted does not need a sixth line to say so.
     * <p>
     * In a week of recorded raids the number of unknown animations was three, two of which were one weapon.
     */
    private static final int MAX_WATCHED = 8;
    private static final int MAX_REPORTED = 5;

    private static final class Candidate {
        private int starts;
        private int backedByDamage;
        private int lastStartTick = Integer.MIN_VALUE;
        private boolean reported;
    }

    private final Map<Integer, Candidate> tracked = new LinkedHashMap<>();
    private int reported;
    /** The last tick we dealt damage on, so an attack recorded AFTER its own xp is still matched. */
    private int lastDamageTick = Integer.MIN_VALUE;

    /** A new raid. Nothing carries over, so a list fixed between raids stops being mentioned. */
    void reset() {
        tracked.clear();
        reported = 0;
        lastDamageTick = Integer.MIN_VALUE;
    }

    /** An animation neither list knows, which we have just counted as an attack. */
    void countedAsAttack(int animation, int tick) {
        if (reported >= MAX_REPORTED) return;

        Candidate candidate = tracked.get(animation);
        if (candidate == null) {
            if (tracked.size() >= MAX_WATCHED) return;
            candidate = new Candidate();
            tracked.put(animation, candidate);
        }
        candidate.starts++;
        candidate.lastStartTick = tick;

        // Matched in BOTH directions, because the two events arrive in the opposite order to the obvious
        // one: a StatChanged is processed with the rest of the tick's packets, which is BEFORE GameTick,
        // so on the tick of an attack the experience lands before the attack is recorded. Looking only
        // backwards from the experience meant a weapon's xp was always compared against the PREVIOUS
        // attack, five ticks earlier, and no attack was ever matched. That shipped: the uncharged Ralos
        // glaive was reported in a live raid having hit twice in eight throws.
        if (within(tick, lastDamageTick)) candidate.backedByDamage++;
    }

    /**
     * We dealt damage. Anything counted as an attack on this tick has just proved itself.
     * <p>
     * Proving one occurrence is enough for good, and that is deliberate rather than lazy. Experience lands
     * on the tick of the attack, so experience on this animation's tick means an attack really was made
     * then -- either this animation is one, or it masked one. In both cases counting it as an attack was
     * the right call, which is the only question being asked.
     * <p>
     * <b>The blind spot.</b> One such coincidence excuses the animation for the rest of the raid, so
     * something that masks a real attack once and then animates you harmlessly fifty times is never
     * mentioned. The alternative -- needing a recent hit, or a ratio -- reports every unlisted weapon that
     * misses a few times in a row, which at a raid boss is all of them. Between a rule that occasionally
     * stays quiet and one that cries wolf every raid, quiet is the one people keep reading.
     */
    void damageDealt(int tick) {
        lastDamageTick = tick;
        for (Candidate candidate : tracked.values()) {
            if (within(tick, candidate.lastStartTick)) candidate.backedByDamage++;
        }
    }

    /** Whether these two ticks are close enough to be the same attack, in either order. */
    private static boolean within(int later, int earlier) {
        final int age = later - earlier;
        return age >= 0 && age <= WINDOW_TICKS;
    }

    /**
     * The room is over. Which animations have earned a mention.
     * <p>
     * Each is reported once per raid, and at most {@link #MAX_REPORTED} in all. Saying it again every room
     * would make the overlay noisier the longer the problem goes unfixed, which is precisely backwards.
     */
    List<Integer> settle() {
        final List<Integer> report = new ArrayList<>();
        if (reported >= MAX_REPORTED) return report;

        for (Map.Entry<Integer, Candidate> entry : tracked.entrySet()) {
            final Candidate candidate = entry.getValue();
            if (candidate.reported) continue;
            if (candidate.backedByDamage > 0) continue;
            if (candidate.starts < STARTS_BEFORE_REPORTING) continue;

            candidate.reported = true;
            report.add(entry.getKey());
            if (++reported >= MAX_REPORTED) break;
        }
        return report;
    }

    /** How many times this animation has been counted as an attack. For tests and the debug overlay. */
    int startsOf(int animation) {
        final Candidate candidate = tracked.get(animation);
        return candidate == null ? 0 : candidate.starts;
    }
}

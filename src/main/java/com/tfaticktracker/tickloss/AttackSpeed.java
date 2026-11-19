package com.tfaticktracker.tickloss;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;

/**
 * How long an attack puts you on cooldown, from facts alone. No client, no item manager.
 * <p>
 * {@link WeaponSpeeds} gathers these facts from the live client; {@code EventLogger} writes the same ones onto
 * every TICK line so a recorded raid can be replayed through this exact method. That is the point: the speed a
 * replay uses is the speed the plugin used, not a second table that drifts.
 */
public final class AttackSpeed {

    static final int UNARMED_SPEED = 4;

    /** Everything the answer depends on, so it can be logged and fed back in. */
    @Getter
    @Setter
    @Accessors(fluent = true, chain = true)
    public static final class Facts {
        /** Equipped weapon's item id, or <= 0 for unarmed. */
        private int weaponId;
        /** The animation playing, which is what distinguishes an Obligator smash from a thrust. */
        private int animation = -1;
        /** The weapon's own attack speed from its equipment stats, or <= 0 if unknown. */
        private int aspeed;
        /** Current attack style, or null when the weapon's styles could not be resolved. */
        private AttackStyles.Style style;
        /** Whether the -1 tick Rapid adjustment applies. See {@link AttackStyles#isRapid}. */
        private boolean rapid;
        /** A spell cast by clicking it, which is one attack per click. */
        private boolean manualCasting;
        /** An autocast spell is set on the weapon. */
        private boolean autoCasting;
        /** The attack style is a casting one. */
        private boolean castingStyle;
    }

    private AttackSpeed() {
    }

    public static int compute(Facts f) {
        // Weapons whose speed depends on which attack was used, not on the item's own stats
        final Integer special = computeSpecial(f);
        if (special != null) return special;

        int speed = f.aspeed <= 0 ? UNARMED_SPEED : f.aspeed;
        if (f.weaponId <= 0) speed = UNARMED_SPEED;

        // Rapid is one tick faster; Accurate and Longrange are not. Both Accurate and Rapid report RANGING on a
        // ranged weapon, so the style alone is not enough -- see AttackStyles#isRapid.
        if (f.rapid) {
            speed--;
        }

        return Math.max(1, speed);
    }

    private static Integer computeSpecial(Facts f) {
        if (f.weaponId == ItemID.OBLIGATOR || f.weaponId == ItemID.OBLIGATOR_UNCHARGED) {
            // The smash is 3t and the multi-target thrust is 5t. When the animation is masked (thrall summon,
            // eating on the same tick) fall back to the attack option, which is readable for the local player.
            switch (f.animation) {
                case AnimationID.TFA_HUMAN_OBLIGATOR_ATTACK:
                case AnimationID.TFA_HUMAN_OBLIGATOR_ATTACK_FEMALE:
                    return 3;
                case AnimationID.TFA_HUMAN_OBLIGATOR_MULTIHIT:
                case AnimationID.TFA_HUMAN_OBLIGATOR_MULTIHIT_FEMALE:
                    return 5;
                default:
                    return f.style == AttackStyles.Style.ACCURATE ? 3 : 5;
            }
        }

        // Casting is paced by the spell, not by the weapon: 5t whatever the staff's own attack speed is, so a
        // wand you could bash with at 4t still casts at 5t. The Harmonised nightmare staff is the exception.
        // Same model as DamageHistory: a manual cast is one attack per click, autocast is the varbit plus a
        // casting style. Gating on those rather than on the style alone is what keeps powered staves on their
        // own 4t speed -- a trident has no autocast spell set, so it falls through to its aspeed.
        if (f.manualCasting || (f.autoCasting && f.castingStyle)) {
            return f.weaponId == ItemID.NIGHTMARE_STAFF_HARMONISED ? 4 : 5;
        }

        if (f.weaponId == ItemID.ZORYAS_TOME || f.weaponId == ItemID.ZORYAS_TOME_UNCHARGED) {
            // PROTOTYPE: 3t always. It is 2t for the three attacks after a landed spec, which needs the spec
            // and the hit tracked together -- not built yet, so a specced Zorya reads as a few ticks of loss.
            return 3;
        }

        return null;
    }

    /** Written onto every TICK line, and parsed straight back into {@link Facts} by the replay. */
    public static String describe(Facts f) {
        return "weapon=" + f.weaponId
                + " aspeed=" + f.aspeed
                + " style=" + (f.style == null ? "null" : f.style.name())
                + " rapid=" + (f.rapid ? 1 : 0)
                + " mcast=" + (f.manualCasting ? 1 : 0)
                + " acast=" + (f.autoCasting ? 1 : 0)
                + " cstyle=" + (f.castingStyle ? 1 : 0);
    }
}

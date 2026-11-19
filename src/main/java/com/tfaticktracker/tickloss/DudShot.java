package com.tfaticktracker.tickloss;

import java.util.Locale;

/**
 * Firing a bow or crossbow with no usable ammo: the shot never happens, but the cooldown runs anyway.
 * <p>
 * Rare and expensive. Nothing visible marks it -- there is no animation, no projectile and no hitsplat, so
 * the tick reads as idling while the game has quietly put you on the weapon's full cooldown. The only signal
 * is the chat message, which is why this exists.
 * <p>
 * It was found from a raid: at tick 4740 of debug-logs/tfa-events-20261116-215240.log a Zaryte crossbow
 * (aspeed 6, rapid, so 5t) was fired with the wrong bolts equipped. The cooldown ran to 4745; an anglerfish
 * two ticks later pushed it to 4748; the next attack landed at 4748 exactly. Without knowing about the dud,
 * the three ticks the food cost read as nothing at all, because the cooldown looked long expired. The same
 * thing happens again at tick 5362 of the same log, and seven more times in a stretch where it was
 * deliberately reproduced.
 * <p>
 * Matched on two stems rather than the four exact sentences, so arrows, bolts, darts and whatever else is
 * added later are all covered:
 * <ul>
 *     <li>"You'll need to equip some suitable <i>crossbow bolts / arrows</i> to use the <i>crossbow /
 *     bow</i> you're holding." -- nothing equipped in the ammo slot at all</li>
 *     <li>"You can't use that ammo with your <i>crossbow / bow</i>." -- something equipped, but wrong</li>
 * </ul>
 */
final class DudShot {

    private static final String[] STEMS = {
            "need to equip some suitable",
            "can't use that ammo with your",
    };

    private DudShot() {
    }

    /**
     * Whether this chat line is the game refusing a shot for want of ammo.
     *
     * @param message the raw message, colour tags and all -- live it arrives with {@code @mes_hl_red@} on
     *                the first form, which is why this matches a substring rather than the whole line
     */
    static boolean isNoAmmoMessage(String message) {
        if (message == null) return false;

        final String lower = message.toLowerCase(Locale.ROOT);
        for (String stem : STEMS) {
            if (lower.contains(stem)) return true;
        }
        return false;
    }
}

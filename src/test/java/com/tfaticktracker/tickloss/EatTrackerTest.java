package com.tfaticktracker.tickloss;

import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What counts as a mouthful, and what does not.
 * <p>
 * Drinking does not delay an attack. That is easy to doubt, because eating plainly does and a potion looks
 * like the same kind of action, so this exists to stop the matcher being "helpfully" widened to cover drinks.
 * Doing so would be expensive as well as wrong: across three raids in the recorded raids there are 1079 confirmed
 * drinks against 230 eats, so charging them would invent several times more cost than eating has ever
 * accounted for, all of it fictional.
 * <p>
 * The strings are the real menu options as the event logger recorded them.
 */
public class EatTrackerTest {

    @Test
    public void foodCounts() {
        assertTrue(EatTracker.isFoodOption("Eat"));
        assertTrue(EatTracker.isFoodOption("eat"));
        assertTrue(EatTracker.isFoodOption("Eat-all"));
    }

    /** The whole point. A potion is free, so it must not reach the delay bookkeeping at all. */
    @Test
    public void drinkingIsFree() {
        assertFalse(EatTracker.isFoodOption("Drink"));
        assertFalse(EatTracker.isFoodOption("drink"));
        assertFalse(EatTracker.isFoodOption("Drink-1"));
    }

    @Test
    public void everythingElseIsIgnored() {
        assertFalse(EatTracker.isFoodOption(null));
        assertFalse(EatTracker.isFoodOption(""));
        assertFalse(EatTracker.isFoodOption("Attack"));
        assertFalse(EatTracker.isFoodOption("Wield"));
        assertFalse(EatTracker.isFoodOption("Wear"));
        assertFalse(EatTracker.isFoodOption("Activate"));
        assertFalse(EatTracker.isFoodOption("Walk here"));
        assertFalse(EatTracker.isFoodOption("Withdraw-1"));
        assertFalse(EatTracker.isFoodOption("Cast"));
    }

    /**
     * What a mouthful costs, now that there is no config field to correct it with.
     * <p>
     * Two ticks for the foods on the wiki's fast foods list, three for everything else, and nothing is
     * four. Measured independently in a recorded raid: elder maul attacks at ticks
     * 333 and 344 around a combo eat at 336, an eleven tick gap, which is the weapon's six plus a
     * karambwan's two plus an anglerfish's three.
     */
    @Test
    public void fastFoodCostsTwoTicksAndEverythingElseCostsThree() {
        assertEquals(2, EatTracker.delayForItem(ItemID.TBWT_COOKED_KARAMBWAN));
        assertEquals(2, EatTracker.delayForItem(ItemID.HALIBUT));
        assertEquals(2, EatTracker.delayForItem(ItemID.GAUNTLET_COMBO_FOOD));
        assertEquals(2, EatTracker.delayForItem(ItemID.PREMADE_CHOCOLATE_BOMB));
        assertEquals(3, EatTracker.delayForItem(ItemID.SHARK));
        assertEquals("an item nobody has listed is ordinary food, not free",
                3, EatTracker.delayForItem(-1));
    }

    /**
     * Pizza is ordinary food, and was listed as fast for weeks.
     * <p>
     * It reads like combo food because it is eaten in two halves, but the wiki's attack delay column says
     * three. Calling it two moved a tick per slice out of "lost to eating" and into plain loss, so the
     * breakdown blamed the player for a tick the food had taken. Pinned rather than left to a future tidy
     * of the list.
     */
    @Test
    public void pizzaIsNotFastFood() {
        assertEquals(3, EatTracker.delayForItem(ItemID.PLAIN_PIZZA));
        assertEquals(3, EatTracker.delayForItem(ItemID.HALF_PLAIN_PIZZA));
        assertEquals(3, EatTracker.delayForItem(ItemID.PINEAPPLE_PIZZA));
    }
}

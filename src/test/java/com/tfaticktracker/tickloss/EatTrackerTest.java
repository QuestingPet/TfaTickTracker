package com.tfaticktracker.tickloss;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What counts as a mouthful, and what does not.
 * <p>
 * Drinking does not delay an attack. That is easy to doubt, because eating plainly does and a potion looks
 * like the same kind of action, so this exists to stop the matcher being "helpfully" widened to cover drinks.
 * Doing so would be expensive as well as wrong: across three raids in debug-logs/ there are 1079 confirmed
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
}

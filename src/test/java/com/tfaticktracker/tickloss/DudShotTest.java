package com.tfaticktracker.tickloss;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The four chat lines that mean a shot did not happen, exactly as the recorded raids records them.
 * <p>
 * All four are in a recorded raid. The first form arrives with a colour tag on it, which is
 * why {@link DudShot} matches a substring and not the whole line.
 */
public class DudShotTest {

    @Test
    public void recognisesNothingEquipped() {
        assertTrue(DudShot.isNoAmmoMessage("@mes_hl_red@You'll need to equip some suitable crossbow bolts "
                + "to use the crossbow you're holding."));
        assertTrue(DudShot.isNoAmmoMessage("@mes_hl_red@You'll need to equip some suitable arrows to use "
                + "the bow you're holding."));
    }

    @Test
    public void recognisesTheWrongAmmoEquipped() {
        assertTrue(DudShot.isNoAmmoMessage("You can't use that ammo with your crossbow."));
        assertTrue(DudShot.isNoAmmoMessage("You can't use that ammo with your bow."));
    }

    /** Matched on the stem, so ammo types the game has not shipped yet are covered too. */
    @Test
    public void recognisesOtherAmmoTypes() {
        assertTrue(DudShot.isNoAmmoMessage("You'll need to equip some suitable darts to use the blowpipe "
                + "you're holding."));
        assertTrue(DudShot.isNoAmmoMessage("You can't use that ammo with your ballista."));
    }

    /** Everything else the raid says, including the lines that sit next to these in the same log. */
    @Test
    public void ignoresEverythingElse() {
        assertFalse(DudShot.isNoAmmoMessage(null));
        assertFalse(DudShot.isNoAmmoMessage(""));
        assertFalse(DudShot.isNoAmmoMessage("You put the ammo into Dizana's Quiver."));
        assertFalse(DudShot.isNoAmmoMessage("You remove the ammo from Dizana's Quiver."));
        assertFalse(DudShot.isNoAmmoMessage("You have run out of prayer points, you can recharge at an "
                + "altar."));
        assertFalse(DudShot.isNoAmmoMessage("Drinking this would have no effect."));
        assertFalse(DudShot.isNoAmmoMessage("Your Mark of Darkness is about to run out."));
    }
}

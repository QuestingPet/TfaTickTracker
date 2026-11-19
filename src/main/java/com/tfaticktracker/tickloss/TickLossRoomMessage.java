package com.tfaticktracker.tickloss;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * One finished room, broadcast to the party. The only thing this plugin ever puts on the wire.
 * <p>
 * Sent when a segment closes, which is the first moment its numbers are final -- roughly sixteen messages
 * across a raid, against the party panel's every two ticks. Nothing per-attack travels: no cooldown, no
 * animation, no weapon, no varbit, no {@code Moment}. Those are the debug vocabulary, and the published
 * overlay does not show them even for yourself.
 * <p>
 * Numbers for other raiders have to be self-reported like this rather than observed, and that is not a
 * preference. You cannot read another player's attack style, spellbook or autocast, so their weapon speed is
 * a guess; a masked animation records no attack at all, which UNDERSTATES their ticks used and so
 * OVERSTATES their loss. That is the worst direction of error for a number people compare in a team. Each
 * client therefore computes its own with full varbit access and sends the summary.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TickLossRoomMessage extends PartyMemberMessage {

    /**
     * What this build puts on the wire.
     * <p>
     * Party members will run different builds, and the dangerous direction is an OLD sender to a NEW
     * receiver: a field the sender never had deserialises to 0, and the receiver renders a confident "0 due
     * to no ammo" that is a lie by omission. A receiver compares this against the version a field was added
     * in and shows nothing where the sender could not have known. A message from before versioning existed
     * arrives as 0, which is treated as the oldest version rather than as an error.
     */
    static final int VERSION = 1;

    /** Protocol version of the sender. Set explicitly on send, never left to a field initialiser. */
    private int v;

    /**
     * The room's enum NAME, e.g. "BORK".
     * <p>
     * Never the ordinal: reordering {@link com.tfaticktracker.RaidRoom} would silently relabel everyone's
     * history. A receiver that cannot parse it drops the message rather than guessing.
     */
    private String room;
    /** Phase within the room, e.g. "P3", or null for rooms measured as one segment. */
    private String phase;

    private int counted;
    private int lost;
    private int lostToEating;
    private int lostToDudShots;
    private int attacks;
    private int earlyAttacks;

    /**
     * Whether this could have come from a client running this plugin honestly.
     * <p>
     * A party message is another client's word for it, so it is untrusted input: anything on the far end can
     * send anything at all. This is the cheap arithmetic gate, and it is the only reason the numbers are safe
     * to render -- they are never allowed to touch our own figures, and a member who fails this is ignored
     * rather than clamped, because a sender this wrong is not one whose other fields mean anything.
     */
    boolean isPlausible() {
        if (room == null || room.isEmpty() || room.length() > 32) return false;
        if (phase != null && phase.length() > 8) return false;
        if (counted < 0 || lost < 0 || attacks < 0 || earlyAttacks < 0) return false;
        if (lostToEating < 0 || lostToDudShots < 0) return false;
        if (lost > counted) return false;
        if (lostToEating > lost || lostToDudShots > lost) return false;
        if (lostToEating + lostToDudShots > lost) return false;
        if (earlyAttacks > attacks) return false;

        // A single room cannot plausibly run for hours. Bounds the damage a hostile sender can do to the
        // totals we display, since those are a running sum.
        return counted <= 100_000 && attacks <= 100_000;
    }
}

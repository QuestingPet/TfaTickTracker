package com.tfaticktracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(TfaTickTrackerPlugin.CONFIG_GROUP)
public interface TfaTickTrackerConfig extends Config {

    /**
     * The two things somebody turns on or off without thinking about measurement: where they want to read
     * this, and whether their numbers leave the client. They sit above the sections because they are the
     * only settings most people will ever touch.
     */
    @ConfigItem(
            keyName = "showSidePanel",
            name = "Show Side Panel",
            description = "A sidebar panel with every raider's room-by-room breakdown.",
            position = 1
    )
    default boolean showSidePanel() {
        return true;
    }

    @ConfigItem(
            keyName = "shareWithParty",
            name = "Share With Party",
            description = "Send your own room summaries to your party when a room finishes. Room totals "
                    + "only, never anything per-attack.",
            position = 2
    )
    default boolean shareWithParty() {
        return true;
    }

    /**
     * Ticks of leeway at the start of a fight.
     * <p>
     * Top level rather than buried, because it is the one number that changes what everybody's figure means:
     * two people in a party comparing efficiency are only comparing like for like if they agree on it.
     */
    @ConfigItem(
            keyName = "startGraceTicks",
            name = "Start Grace Ticks",
            description = "Ticks of leeway at the start of each fight, so walking into range is not charged "
                    + "as loss.",
            position = 3
    )
    default int startGraceTicks() {
        return 4;
    }

    /**
     * The in-game overlay, as separate switches.
     * <p>
     * Separate rather than one, because people want very different amounts of it. Somebody learning a room
     * wants the live block and nothing else; somebody comparing a team wants the party table and does not
     * care what their own last attack cost. Turning them all off hides the overlay entirely, which is the
     * same thing as unchecking the first one, so there is no broken state to land in.
     */
    @ConfigSection(
            name = "Overlay",
            description = "Which parts of the in-game overlay to show.",
            position = 4
    )
    String overlaySettings = "overlaySettings";

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show Overlay",
            description = "The in-game overlay. Unchecking this hides all of it, whatever the three below say.",
            section = overlaySettings,
            position = 1
    )
    default boolean showOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "showCurrentRoom",
            name = "Show Current Room",
            description = "The live part: what your last attack cost and what this room has cost so far. "
                    + "Stays up between rooms so you can read it on the way to the next.",
            section = overlaySettings,
            position = 2
    )
    default boolean showCurrentRoom() {
        return true;
    }

    @ConfigItem(
            keyName = "showRoomSummary",
            name = "Show Room Summary",
            description = "Every room of the raid with what each one cost. Rooms you have not reached yet "
                    + "are listed too, so the overlay never changes height.",
            section = overlaySettings,
            position = 3
    )
    default boolean showRoomSummary() {
        return true;
    }

    @ConfigItem(
            keyName = "showParty",
            name = "Show Party",
            description = "Each other raider's last room, raid total and efficiency. Only raiders who are "
                    + "also in your RuneLite party appear.",
            section = overlaySettings,
            position = 4
    )
    default boolean showParty() {
        // Off by default. The overlay out of the box is your own fight and your own rooms; a table of
        // everyone else is a thing to turn on, not a thing to turn off, and it is the one section whose
        // height depends on how many people happen to be in the party.
        return false;
    }
}

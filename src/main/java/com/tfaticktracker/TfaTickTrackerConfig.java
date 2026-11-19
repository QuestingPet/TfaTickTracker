package com.tfaticktracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.api.annotations.VisibleForDevtools;

@ConfigGroup(TfaTickTrackerPlugin.CONFIG_GROUP)
public interface TfaTickTrackerConfig extends Config {

    /**
     * Which parts of the UI to show, as separate switches.
     * <p>
     * Separate rather than one, because people want very different amounts of this. Somebody learning a room
     * wants the live block and nothing else; somebody comparing a team wants the party table and does not
     * care what their own last attack cost. Turning them all off hides the overlay entirely, which is the
     * same thing as unchecking the first one, so there is no broken state to land in.
     */
    @ConfigSection(
            name = "Display",
            description = "Which parts of the overlay and panel to show.",
            position = 1
    )
    String displaySettings = "displaySettings";

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show Overlay",
            description = "The in-game overlay. Unchecking this hides all of it, whatever the three below say.",
            section = displaySettings,
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
            section = displaySettings,
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
            section = displaySettings,
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
            section = displaySettings,
            position = 4
    )
    default boolean showParty() {
        // Off by default. The overlay out of the box is your own fight and your own rooms; a table of
        // everyone else is a thing to turn on, not a thing to turn off, and it is the one section whose
        // height depends on how many people happen to be in the party.
        return false;
    }

    @ConfigItem(
            keyName = "showSidePanel",
            name = "Show Side Panel",
            description = "A sidebar panel with every raider's room-by-room breakdown.",
            section = displaySettings,
            position = 5
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
     * Measurement tuning. Everything here changes what gets counted, so it is deliberately separate from
     * the display switches, which only change what you look at.
     */
    @ConfigSection(
            name = "Measurement",
            description = "What counts as a lost tick.",
            position = 3,
            closedByDefault = true
    )
    String measurementSettings = "measurementSettings";

    @ConfigItem(
            keyName = "startGraceTicks",
            name = "Start Grace Ticks",
            description = "Ticks of leeway at the start of each fight, so walking into range is not charged "
                    + "as loss.",
            section = measurementSettings,
            position = 1
    )
    default int startGraceTicks() {
        return 4;
    }

    @ConfigItem(
            keyName = "fastFoodIds",
            name = "2-Tick Food Ids",
            description = "Comma separated item ids that only delay attacks by 2 ticks (combo food).",
            section = measurementSettings,
            position = 2
    )
    default String fastFoodIds() {
        return "";
    }

    @ConfigItem(
            keyName = "slowFoodIds",
            name = "4-Tick Food Ids",
            description = "Comma separated item ids that delay attacks by 4 ticks.",
            section = measurementSettings,
            position = 3
    )
    default String slowFoodIds() {
        return "";
    }

    /**
     * Developer tooling. None of it is useful to a player, and all of it is how every number in this plugin
     * was arrived at: the logger writes the raids that the test fixtures are cut from, and the debug overlay
     * shows the state machine behind a decision rather than its result.
     */
    @ConfigSection(
            name = "Debug",
            description = "Developer tools. Only work in developer mode.",
            position = 4,
            closedByDefault = true
    )
    String debugSettings = "debugSettings";

    @VisibleForDevtools
    @ConfigItem(
            keyName = "debugOverlay",
            name = "Debug Overlay",
            description = "Developer view: the state machine, raw counters and the inputs behind them.",
            section = debugSettings,
            position = 1
    )
    default boolean debugOverlay() {
        return false;
    }

    @VisibleForDevtools
    @ConfigItem(
            keyName = "eventLogger",
            name = "Event Logger",
            description = "Log game events and their ids to debug-logs/ in the working directory. Only works "
                    + "in developer mode.",
            section = debugSettings,
            position = 2
    )
    default boolean eventLogger() {
        return false;
    }

    @VisibleForDevtools
    @ConfigItem(
            keyName = "eventLoggerScripts",
            name = "Event Logger Scripts",
            description = "Also log every script that runs. Very noisy.",
            section = debugSettings,
            position = 3
    )
    default boolean eventLoggerScripts() {
        return false;
    }

    @VisibleForDevtools
    @ConfigItem(
            keyName = "eventLoggerMarkerKey",
            name = "Event Logger Marker Key",
            description = "Press to write a numbered marker line into the event log.",
            section = debugSettings,
            position = 4
    )
    default Keybind eventLoggerMarkerKey() {
        return Keybind.NOT_SET;
    }
}

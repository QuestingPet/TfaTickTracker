package com.tfaticktracker.events;

import lombok.Value;

/**
 * An event denoting that a raid has ended, posted after the final {@link InRaidChanged}.
 */
@Value
public class RaidFinished {

    /**
     * Whether the loot room was reached, rather than the raid being abandoned or wiped.
     */
    boolean completed;

    /**
     * Ticks from entering the raid to leaving it, or -1 if the start was never seen -- for instance when the
     * plugin was enabled partway through.
     */
    int durationTicks;
}

package com.tfaticktracker.events;

import com.tfaticktracker.RaidRoom;
import com.tfaticktracker.RaidState;
import lombok.Builder;
import lombok.Value;

/**
 * An event where the current {@link RaidRoom} has changed in the {@link RaidState}.
 */
@Value
@Builder
public class RaidRoomChanged {

    RaidRoom newRaidRoom;

    RaidRoom prevRaidRoom;
}
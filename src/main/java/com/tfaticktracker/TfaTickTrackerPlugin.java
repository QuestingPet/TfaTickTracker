package com.tfaticktracker;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
        name = "Tfa Tick Tracker",
        internalName = "tfa-tick-tracker",
        description = "Measures your unused attack ticks, room by room, in The Fractured Archives"
)
public class TfaTickTrackerPlugin extends Plugin {

    public static final String CONFIG_GROUP = "tfaTickTracker";

    @Override
    protected void startUp() throws Exception {
        log.debug("Tfa Tick Tracker started");
    }

    @Override
    protected void shutDown() throws Exception {
        log.debug("Tfa Tick Tracker stopped");
    }

    @Provides
    TfaTickTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TfaTickTrackerConfig.class);
    }
}

package com.tfaticktracker;

import com.google.inject.Provides;
import com.tfaticktracker.tickloss.TickLossPanel;
import com.tfaticktracker.tickloss.TickLossSummaryOverlay;
import com.tfaticktracker.tickloss.TickLossTracker;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Constants;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;

@Slf4j
@PluginDescriptor(
        name = "Tfa Tick Tracker",
        internalName = "tfa-tick-tracker",
        description = "Measures your unused attack ticks, room by room, in The Fractured Archives"
)
public class TfaTickTrackerPlugin extends Plugin {

    /** Client cycles per game tick, for turning a cycle countdown into ticks. */
    public static final int CYCLES_PER_GAME_TICK = Constants.GAME_TICK_LENGTH / Constants.CLIENT_TICK_LENGTH;

    public static final String CONFIG_GROUP = "tfaTickTracker";
    private static final String SHOW_SIDE_PANEL_CONFIG_KEY = "showSidePanel";

    @Inject
    private ClientThread clientThread;

    @Inject
    private TfaTickTrackerConfig config;

    @Inject
    private EventBus eventBus;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ClientToolbar clientToolbar;

    /**
     * Where the raid itself is read: which room you are in, who is raiding with you, when it starts and ends.
     * <p>
     */
    @Inject
    private RaidState raidState;

    @Inject
    private TickLossTracker tickLossTracker;

    @Inject
    private TickLossSummaryOverlay tickLossSummaryOverlay;

    private final BufferedImage icon = ImageUtil.loadImageResource(TfaTickTrackerPlugin.class, "panel-icon.png");
    private TickLossPanel panel;
    private NavigationButton navButton;

    @Override
    protected void startUp() throws Exception {
        // Not @Inject, because it is nulled out on shutdown and has to be rebuilt on the next start.
        panel = injector.getInstance(TickLossPanel.class);
        eventBus.register(panel);

        navButton = NavigationButton.builder()
                .tooltip("Tfa Tick Tracker")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();
        updateSidePanel();

        overlayManager.add(tickLossSummaryOverlay);

        tickLossTracker.startUp();

        clientThread.invoke(() -> raidState.startUp());
    }

    @Override
    protected void shutDown() throws Exception {
        raidState.shutDown();

        overlayManager.remove(tickLossSummaryOverlay);

        tickLossTracker.shutDown();

        clientToolbar.removeNavigation(navButton);
        eventBus.unregister(panel);
        panel = null;
    }

    private void updateSidePanel() {
        if (config.showSidePanel()) {
            clientToolbar.addNavigation(navButton);
        } else {
            clientToolbar.removeNavigation(navButton);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!CONFIG_GROUP.equals(event.getGroup())) return;

        if (SHOW_SIDE_PANEL_CONFIG_KEY.equals(event.getKey())) {
            updateSidePanel();
        }
    }

    @Provides
    TfaTickTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TfaTickTrackerConfig.class);
    }
}

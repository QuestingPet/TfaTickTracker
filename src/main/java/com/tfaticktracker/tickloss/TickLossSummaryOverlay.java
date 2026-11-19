package com.tfaticktracker.tickloss;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;

import javax.inject.Inject;

import com.tfaticktracker.TfaTickTrackerConfig;
import com.tfaticktracker.TfaTickTrackerPlugin;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Tick loss as a PLAYER sees it: what the last attack cost, and how each room went.
 * <p>
 * Deliberately not the debug overlay, which exists to be checked against a log and shows animation ids,
 * varbit states and a live cooldown counter. None of that is useful to someone raiding, and the live counter is
 * actively bad: it ticks into the negatives while you are still deciding what to do, so it reads as nagging.
 * <p>
 * All of the wording and layout lives in {@link TickLossSummary}, which needs no client and so can be read in a
 * test rather than only in a raid. This class reads the tracker, and turns a {@link TickLossSummary.Tone} into
 * a colour. That is the whole of it, so what the test prints is what a player sees.
 */
public class TickLossSummaryOverlay extends OverlayPanel {

    /** The last-attack row only. Every other number is white; see {@link TickLossSummary.Tone}. */
    private static final Color GOOD = new Color(0x66, 0xBB, 0x6A);
    private static final Color BAD = new Color(0xEF, 0x53, 0x50);
    private static final Color HEADING = new Color(0xBD, 0xBD, 0xBD);
    private static final Color QUIET = new Color(0x9E, 0x9E, 0x9E);

    private final Client client;
    private final TfaTickTrackerConfig config;
    private final TickLossTracker tracker;

    @Inject
    public TickLossSummaryOverlay(Client client, TfaTickTrackerPlugin plugin,
                                  TfaTickTrackerConfig config, TickLossTracker tracker) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(PRIORITY_MED);

        this.client = client;
        this.config = config;
        this.tracker = tracker;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay()) return null;

        // The room to show at the top outlives the fight: once a room ends its numbers stay up until the
        // next one opens, so they are still readable while you run between rooms.
        final SegmentStats live = tracker.getCurrent();
        final SegmentStats active = live != null ? live : tracker.getLastCountingSegment();

        final List<TickLossSummary.Row> rows = TickLossSummary.rows(new TickLossSummary.View(
                active,
                live != null,
                tracker.getCountedSegments(),
                tracker.getLastMoment(),
                tracker.isRaidFinished(),
                tracker.isRaidCompleted(),
                config.showCurrentRoom(),
                config.showRoomSummary(),
                tracker.getParty().getParty()));

        if (rows.isEmpty()) return null;

        // Fixed width, so the numbers in the room table line up instead of jittering as rooms are added.
        panelComponent.setPreferredSize(new Dimension(200, 0));

        for (TickLossSummary.Row row : rows) {
            if (row.getTone() == TickLossSummary.Tone.TITLE) {
                panelComponent.getChildren().add(TitleComponent.builder()
                        .text(row.getLeft())
                        .color(Color.WHITE)
                        .build());
                continue;
            }

            final LineComponent.LineComponentBuilder line = LineComponent.builder().left(row.getLeft());
            if (row.getTone() == TickLossSummary.Tone.HEADING) {
                line.leftColor(HEADING);
            }
            if (row.getRight() != null) {
                line.right(row.getRight()).rightColor(colourOf(row.getTone()));
            }
            panelComponent.getChildren().add(line.build());
        }

        return super.render(graphics);
    }

    private static Color colourOf(TickLossSummary.Tone tone) {
        switch (tone) {
            case GOOD:
                return GOOD;
            case BAD:
                return BAD;
            case QUIET:
                return QUIET;
            case HEADING:
                return HEADING;
            case PLAIN:
            default:
                return Color.WHITE;
        }
    }
}

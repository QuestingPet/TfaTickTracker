package com.tfaticktracker.tickloss;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Every raider's raid, room by room, in the sidebar.
 * <p>
 * The overlay can only afford a label and two numbers on a line, which is enough for the room you are in
 * and a running list, but not for four raiders across seven rooms. A {@link PluginPanel} is about 225px
 * wide and as tall as the client, so the grid goes vertical instead: one collapsible card per raider, the
 * same shape {@code DamageHistory}'s player panel uses. Yours opens by default and the rest are a click
 * each.
 * <p>
 * Nothing new crosses the wire for this. It is the same per-room party messages the overlay already
 * receives, shown somewhere with room for them.
 */
public class TickLossPanel extends PluginPanel {

    /**
     * One colour for every number, whatever it says.
     * <p>
     * Green for a clean room and red for a lossy one was a verdict on each line, and seven rooms' worth of
     * verdicts is a wall of red that stops carrying information. The numbers say how it went; the colour
     * only has to make them readable.
     */
    private static final Color NUMBER = Color.WHITE;
    /** Rows, slightly below the numbers so a label never competes with its own figure. */
    private static final Color TEXT = new Color(0xD0, 0xD0, 0xD0);
    /** Column headings, and rooms nobody has reached yet. */
    private static final Color MUTED = new Color(0x9E, 0x9E, 0x9E);
    /** A note that something is missing or wrong. The only colour left in the panel, so it still reads as one. */
    private static final Color WARNING = new Color(0xE0, 0x5C, 0x5C);
    /** Cards sit a shade ABOVE the sidebar rather than below it, which is what makes the text legible. */
    private static final Color CARD = new Color(0x32, 0x32, 0x32);
    /** Sidebar-coloured space under each card, so one raider's block is visibly not the next one's. */
    private static final int CARD_GAP = 8;

    private final TickLossTracker tracker;
    /** The revision last drawn, so a tick with no finished room costs nothing. */
    private int drawn = -1;
    /**
     * Which way round to group: raiders holding rooms, or rooms holding raiders.
     * <p>
     * One pivot of the same data, and the two answer different questions -- "how did I do" against "who
     * struggled where". Neither is more correct, so it is a toggle rather than a decision.
     */
    private boolean byRoom;

    private final JLabel subtitle = new JLabel();
    private final JPanel cards = new JPanel();
    /** Who is collapsed, by name, so a redraw does not reopen everything someone just closed. */
    private final Set<String> collapsed = new LinkedHashSet<>();

    @Inject
    public TickLossPanel(TickLossTracker tracker) {
        // Wrapped, so the sidebar gives it a scrollbar: four raiders of seven rooms each is taller than
        // the client at any sensible font size.
        super();
        this.tracker = tracker;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        final JPanel header = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setBorder(new EmptyBorder(0, 2, 8, 2));

        // Fonts come from FontManager, the same as the mistake tracker's own panel, rather than from
        // deriveFont on whatever the label inherited. Every RuneLite font is size 16, so any deriveFont
        // with a number in it is shrinking the panel below the client's own text, which is how this ended
        // up unreadable the first time.
        final JLabel title = new JLabel("TFA Tick Tracker");
        title.setForeground(ColorScheme.BRAND_ORANGE);
        title.setFont(FontManager.getRunescapeBoldFont());
        header.add(title);

        subtitle.setForeground(TEXT);
        subtitle.setFont(FontManager.getRunescapeSmallFont());
        header.add(subtitle);

        add(header, BorderLayout.NORTH);

        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        cards.setBackground(ColorScheme.DARK_GRAY_COLOR);
        add(cards, BorderLayout.CENTER);

        rebuild();
    }

    /**
     * Redraw if anything has actually changed since the last time.
     * <p>
     * Called on the game thread, so the redraw itself is handed to Swing.
     */
    /**
     * Redraw only when a room has finished, ours or somebody else's.
     * <p>
     * The revision bumps when a room closes, a party message lands, or the raid resets, and at no other
     * time -- so the panel changes once per room and then holds still. That is the point of it: the
     * overlay is the live readout, and a panel that moved while you fought would be a second one competing
     * for the same glance, as well as eating clicks on the cards and throwing away the scroll position.
     */
    @Subscribe
    public void onGameTick(GameTick e) {
        final int revision = tracker.getParty().getRevision();
        if (revision == drawn) return;

        drawn = revision;
        SwingUtilities.invokeLater(this::rebuild);
    }

    /** Redraw from whatever the tracker and the party currently hold. Call on the Swing thread. */
    public void rebuild() {
        cards.removeAll();

        final List<SegmentStats> mine = finishedRooms();
        final List<TickLossParty.Progress> others = tracker.getParty().getOthers();

        if (mine.isEmpty() && others.isEmpty()) {
            subtitle.setText("No raid measured yet");
            cards.add(message("Numbers appear here as each room finishes."));
            revalidate();
            repaint();
            return;
        }

        subtitle.setText(describeRaid(1 + others.size()));
        cards.add(grouping());

        if (byRoom) {
            addRoomCards(mine, others);
        } else {
            // You first, then everyone else in the raid party's own order.
            final List<RaidRooms.Slot> slots = RaidRooms.layOut(mine);
            cards.add(card("You", mine, RaidRooms.lost(slots), RaidRooms.counted(slots)));
            for (TickLossParty.Progress other : others) {
                cards.add(card(other.getName(), new ArrayList<>(), other.getRaidLost(),
                        other.getRaidCounted(), other.getRooms()));
            }
        }

        addUnrecognisedNote(mine);
        addMissingRaidersNote(others.size());

        revalidate();
        repaint();
    }

    /**
     * Explains the empty space where the rest of the team should be.
     * <p>
     * Two separate things have to be true before somebody else's numbers can appear here: they have to be
     * running this plugin, and they have to be in the same RuneLite party. Neither is visible from in here,
     * so a raider who has done neither looks exactly like a raider who has done one of them -- absent. A
     * panel that just shows fewer people than are in the raid reads as broken, and this is the difference
     * between "broken" and "they need the plugin too".
     */
    private void addMissingRaidersNote(int showing) {
        final int raiders = tracker.getParty().getRaidSize();
        if (raiders <= 1 || showing >= raiders - 1) return;

        cards.add(warning("Only raiders running this plugin and in your RuneLite party appear here. "
                + showing + " of " + (raiders - 1) + " others are sharing."));
    }

    /**
     * Says so when a number above it was measured with an animation we did not understand.
     * <p>
     * Attacks are decided from an allowlist, so an animation nobody has listed registers no attack and the
     * room it happened in reads high. That is the deliberate trade -- a loud wrong answer rather than a
     * quiet one -- and this is the half that makes it loud.
     */
    private void addUnrecognisedNote(List<SegmentStats> mine) {
        final Set<Integer> ids = new LinkedHashSet<>();
        for (SegmentStats segment : mine) ids.addAll(segment.getUnrecognisedAnimations());
        if (tracker.getCurrent() != null) ids.addAll(tracker.getCurrent().getUnrecognisedAnimations());
        if (ids.isEmpty()) return;

        final StringBuilder list = new StringBuilder();
        for (Integer id : ids) {
            if (list.length() > 0) list.append(", ");
            list.append(id);
        }
        cards.add(message("Unrecognised animations: " + list + ". These were not counted as attacks, so "
                + "those rooms may read as more loss than you had. Worth reporting."));
    }

    /** The by-raider / by-room switch, as two halves of one control. */
    private JPanel grouping() {
        final JPanel row = new JPanel(new java.awt.GridLayout(1, 2));
        row.setBorder(new EmptyBorder(0, 0, 6, 0));
        row.add(groupingHalf("By raider", !byRoom));
        row.add(groupingHalf("By room", byRoom));
        return row;
    }

    private JLabel groupingHalf(String text, boolean on) {
        final JLabel half = new JLabel(text, SwingConstants.CENTER);
        half.setOpaque(true);
        half.setFont(FontManager.getRunescapeFont());
        half.setBackground(on ? ColorScheme.MEDIUM_GRAY_COLOR : CARD);
        half.setForeground(on ? Color.WHITE : TEXT);
        half.setBorder(new EmptyBorder(4, 0, 4, 0));
        half.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                byRoom = !byRoom;
                rebuild();
            }
        });
        return half;
    }

    /**
     * The same data pivoted: one card per room, each listing every raider.
     * <p>
     * Rooms keep the raid's own order, so the cards read top to bottom as the raid went. A raider who has
     * not reached a room yet shows dashes in it rather than being left out, which keeps every card the
     * same shape.
     */
    private void addRoomCards(List<SegmentStats> mine, List<TickLossParty.Progress> others) {
        final List<RaidRooms.Slot> slots = RaidRooms.layOut(mine);

        for (int i = 0; i < slots.size(); i++) {
            final RaidRooms.Slot slot = slots.get(i);

            final JPanel card = new JPanel(new BorderLayout());
            card.setBackground(CARD);
            card.setBorder(new MatteBorder(0, 0, CARD_GAP, 0, ColorScheme.DARK_GRAY_COLOR));

            final JPanel head = new JPanel(new BorderLayout());
            head.setBackground(CARD);
            head.setBorder(new EmptyBorder(5, 8, 5, 8));
            final JLabel name = new JLabel(slot.label);
            name.setForeground(slot.done == null ? MUTED : Color.WHITE);
            name.setFont(FontManager.getRunescapeBoldFont());
            head.add(name, BorderLayout.WEST);
            card.add(head, BorderLayout.NORTH);

            final JPanel body = new JPanel(new DynamicGridLayout(0, 1, 0, 1));
            body.setBackground(CARD);
            body.setBorder(new EmptyBorder(3, 8, 6, 8));
            body.add(row("Raider", "Lost", "Efficiency", MUTED, true));

            body.add(slot.done == null
                    ? row("You", "--", "", MUTED, false)
                    : row("You", String.valueOf(slot.done.getLost()),
                            TickLossLabels.percent(slot.done.getEfficiency()),
                            NUMBER, false));

            for (TickLossParty.Progress other : others) {
                final TickLossParty.Room room = roomOf(other.getRooms(), slot, i);
                body.add(room == null
                        ? row(other.getName(), "--", "", MUTED, false)
                        : row(other.getName(), String.valueOf(room.getLost()),
                                TickLossLabels.percent(room.getEfficiency()),
                                NUMBER, false));
            }

            card.add(body, BorderLayout.CENTER);
            cards.add(card);
        }
    }

    /**
     * Which of a raider's reported rooms belongs in this slot.
     * <p>
     * By room and phase where we know them, because a raider who missed a room would otherwise have every
     * room after it shifted up a line. Falling back to position only covers the rotating bosses before we
     * have done them ourselves, where there is no name to match on yet.
     */
    private static TickLossParty.Room roomOf(List<TickLossParty.Room> reported, RaidRooms.Slot slot,
                                             int position) {
        if (slot.done != null) {
            for (TickLossParty.Room room : reported) {
                if (room.getRoom() == slot.done.getRoom()
                        && java.util.Objects.equals(room.getPhase(), slot.done.getPhaseName())) {
                    return room;
                }
            }
            return null;
        }
        return position < reported.size() ? reported.get(position) : null;
    }

    private String describeRaid(int raiders) {
        final String state = tracker.isRaidFinished()
                ? (tracker.isRaidCompleted() ? "Raid complete" : "Raid abandoned")
                : "Raid in progress";
        final int seconds = Math.round(tracker.getRaidDurationTicks() * 0.6f);
        final String time = tracker.getRaidDurationTicks() > 0
                ? String.format(" · %d:%02d", seconds / 60, seconds % 60) : "";
        return state + time + " · " + raiders + (raiders == 1 ? " raider" : " raiders");
    }

    private JPanel card(String name, List<SegmentStats> ours, int lost, int counted) {
        return card(name, ours, lost, counted, null);
    }

    /**
     * One raider: a header that is always visible, and a body of rooms that can be collapsed.
     * <p>
     * Either {@code ours} (our own segments) or {@code theirs} (what they reported) supplies the rooms;
     * both are laid out through {@link RaidRooms} so every card lists the raid in the same order, with the
     * rooms nobody has reached yet still showing.
     */
    private JPanel card(String name, List<SegmentStats> ours, int lost, int counted,
                        List<TickLossParty.Room> theirs) {
        final JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD);
        // A MatteBorder rather than an EmptyBorder: a component paints its background across its whole
        // bounds, border included, so empty space at the bottom of a card is still card-coloured and the
        // cards run into each other. This paints the gap in the sidebar's own colour instead.
        card.setBorder(new MatteBorder(0, 0, CARD_GAP, 0, ColorScheme.DARK_GRAY_COLOR));

        final boolean open = !collapsed.contains(name);

        final JPanel head = new JPanel(new BorderLayout());
        head.setBackground(CARD);
        head.setBorder(new EmptyBorder(5, 8, 5, 8));

        final JLabel who = new JLabel((open ? "▾ " : "▸ ") + name);
        who.setForeground(Color.WHITE);
        who.setFont(FontManager.getRunescapeBoldFont());
        head.add(who, BorderLayout.WEST);

        final JLabel totals = new JLabel(counted <= 0 ? "--"
                : lost + "   " + TickLossLabels.percent(efficiency(lost, counted)));
        totals.setForeground(counted <= 0 ? MUTED : NUMBER);
        totals.setFont(FontManager.getRunescapeFont());
        totals.setHorizontalAlignment(SwingConstants.RIGHT);
        head.add(totals, BorderLayout.EAST);

        head.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!collapsed.remove(name)) collapsed.add(name);
                rebuild();
            }
        });
        card.add(head, BorderLayout.NORTH);

        if (open) {
            final JPanel body = new JPanel(new DynamicGridLayout(0, 1, 0, 1));
            body.setBackground(CARD);
            body.setBorder(new EmptyBorder(3, 8, 6, 8));

            // Column labels, because two bare numbers on a row say nothing about what they are.
            body.add(row("Room", "Lost", "Efficiency", MUTED, true));

            final List<RaidRooms.Slot> slots = RaidRooms.layOut(ours);
            for (int i = 0; i < slots.size(); i++) {
                final RaidRooms.Slot slot = slots.get(i);
                if (theirs == null) {
                    body.add(slot.done == null
                            ? row(slot.label, "--", "", MUTED, false)
                            : row(slot.label, String.valueOf(slot.done.getLost()),
                                    TickLossLabels.percent(slot.done.getEfficiency()),
                                    NUMBER, false));
                } else {
                    final TickLossParty.Room room = roomOf(theirs, slot, i);
                    body.add(room == null
                            ? row(slot.label, "--", "", MUTED, false)
                            : row(room.getLabel(), String.valueOf(room.getLost()),
                                    TickLossLabels.percent(room.getEfficiency()),
                                    NUMBER, false));
                }
            }

            card.add(body, BorderLayout.CENTER);
        }

        return card;
    }

    /** A room line: name on the left, ticks lost and efficiency right-aligned in fixed columns. */
    private JPanel row(String label, String lost, String efficiency, Color colour, boolean heading) {
        final JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(CARD);

        final JLabel left = new JLabel(label);
        left.setForeground(heading ? MUTED : TEXT);
        left.setFont(heading ? FontManager.getRunescapeSmallFont() : FontManager.getRunescapeFont());
        row.add(left, BorderLayout.WEST);

        final JPanel right = new JPanel(new BorderLayout(6, 0));
        right.setBackground(CARD);

        final JLabel lostLabel = new JLabel(lost, SwingConstants.RIGHT);
        lostLabel.setForeground(colour);
        lostLabel.setFont(heading ? FontManager.getRunescapeSmallFont() : FontManager.getRunescapeFont());
        lostLabel.setPreferredSize(new Dimension(34, 18));
        right.add(lostLabel, BorderLayout.WEST);

        final JLabel efficiencyLabel = new JLabel(efficiency, SwingConstants.RIGHT);
        efficiencyLabel.setForeground(colour);
        efficiencyLabel.setFont(heading ? FontManager.getRunescapeSmallFont() : FontManager.getRunescapeFont());
        efficiencyLabel.setPreferredSize(new Dimension(66, 18));
        right.add(efficiencyLabel, BorderLayout.EAST);

        row.add(right, BorderLayout.EAST);
        return row;
    }

    /**
     * A note that something is missing, in the one colour this panel still uses.
     * <p>
     * Bigger and red rather than the muted small type the other notes use, because this one answers a
     * question the player is already asking -- "where is everybody" -- and at the size of a footnote it was
     * being read as decoration.
     */
    private JPanel warning(String text) {
        final JPanel panel = message(text);
        for (java.awt.Component child : panel.getComponents()) {
            if (child instanceof JLabel) {
                child.setForeground(WARNING);
                child.setFont(FontManager.getRunescapeFont());
            }
        }
        return panel;
    }

    private JPanel message(String text) {
        final JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(6, 2, 0, 2));

        final JLabel label = new JLabel("<html><body style='width:170px'>" + text + "</body></html>");
        label.setForeground(MUTED);
        label.setFont(FontManager.getRunescapeSmallFont());
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Our own rooms that are OVER, which is everything this panel reports.
     * <p>
     * A room appears here when it is finished one way or the other -- the boss dead, or the raid wiped --
     * and never before. Not when you die, either: dying bills whatever was outstanding right then, but the
     * room is still going, and a number that lands mid-room and then keeps moving is the overlay's job.
     * This is the settled view, so it only ever changes once per room.
     */
    private List<SegmentStats> finishedRooms() {
        final List<SegmentStats> finished = new ArrayList<>();
        for (SegmentStats segment : tracker.getCountedSegments()) {
            if (segment != tracker.getCurrent()) finished.add(segment);
        }
        return finished;
    }

    private static int raidLost(List<SegmentStats> segments) {
        int total = 0;
        for (SegmentStats segment : segments) total += segment.getLost();
        return total;
    }

    private static int raidCounted(List<SegmentStats> segments) {
        int total = 0;
        for (SegmentStats segment : segments) total += segment.getTicksCounting();
        return total;
    }

    private static double efficiency(int lost, int counted) {
        if (counted <= 0) return 1.0d;
        return Math.max(0.0d, 1.0d - ((double) lost / counted));
    }
}

package com.tfaticktracker.tickloss;

import com.tfaticktracker.TfaTickTrackerConfig;
import com.tfaticktracker.TfaTickTrackerPlugin;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import javax.inject.Named;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;

/**
 * Shows whether we are counting right now, why not when we are not, and the inputs behind
 * the numbers so they can be checked against what actually happened on screen.
 */
public class TickLossOverlay extends OverlayPanel {

    private static final Color COUNTING = new Color(0x4C, 0xAF, 0x50);
    private static final Color BLOCKED = new Color(0xE5, 0x73, 0x73);
    private static final Color MUTED = new Color(0x9E, 0x9E, 0x9E);

    private final Client client;
    private final TfaTickTrackerConfig config;
    private final TickLossTracker tracker;
    private final FightWindow fightWindow;
    private final EatTracker eatTracker;
    private final WeaponSpeeds weaponSpeeds;
    private final boolean developerMode;

    @Inject
    public TickLossOverlay(Client client, TfaTickTrackerPlugin plugin, TfaTickTrackerConfig config,
                           TickLossTracker tracker, FightWindow fightWindow, EatTracker eatTracker,
                           WeaponSpeeds weaponSpeeds, @Named("developerMode") boolean developerMode) {
        super(plugin);
        setPosition(OverlayPosition.BOTTOM_LEFT);
        setPriority(PRIORITY_MED);

        this.client = client;
        this.config = config;
        this.tracker = tracker;
        this.fightWindow = fightWindow;
        this.eatTracker = eatTracker;
        this.weaponSpeeds = weaponSpeeds;
        this.developerMode = developerMode;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!developerMode) return null;
        if (!config.debugOverlay()) return null;

        final boolean counting = fightWindow.isCounting();
        final int tick = client.getTickCount();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text(counting ? "COUNTING" : fightWindow.getState().name())
                .color(counting ? COUNTING : BLOCKED)
                .build());

        // Current segment
        SegmentStats current = tracker.getCurrent();
        if (current != null) {
            line("Segment", current.getLabel());
            line("Counted", current.getTicksCounting() + " / " + current.getTicksInRoom() + " in room");
            line("Attacks", String.valueOf(current.getAttacks()));
            line("Lost", current.getLost() + " (" + current.getLostToEating() + " eat)",
                    current.getLost() > 0 ? BLOCKED : COUNTING);
            line("Efficiency", String.format("%.1f%%", current.getEfficiency() * 100f));
            if (current.getLongestGap() > 0) {
                line("Longest gap", current.getLongestGap() + "t");
            }
            if (current.getEarlyAttacks() > 0) {
                line("Early attacks", String.valueOf(current.getEarlyAttacks()));
            }
        } else {
            line("Segment", "none", MUTED);
        }

        // Cooldown state -- the inputs behind every number above
        if (counting) {
            // Counted the way an attack-tick counter over your head does, i.e. attacktimer's
            // getTicksUntilNextAttack() == 1 + holdoff. "1t" in green means the next attack can go out next
            // tick. Past that it keeps counting into the negatives in red -- -1t, -2t -- so ticks going by
            // unused are visible as they happen rather than only afterwards in the Lost total.
            final int until = tracker.getNextAttackTick() - tick;
            if (until >= 0) {
                line("Cooldown", (until + 1) + "t", until > 0 ? MUTED : COUNTING);
            } else {
                line("Cooldown", until + "t", BLOCKED);
            }
        } else {
            // While the window is shut the cooldown is parked at the current tick, not counting down
            line("Cooldown", "-", MUTED);
        }
        // Live speed for the equipped weapon, with the last attack's speed alongside it when they differ
        final int liveSpeed = tracker.getCurrentSpeed();
        final int lastSpeed = tracker.getLastSpeed();
        line("Speed", (liveSpeed > 0 ? liveSpeed + "t" : "-")
                + (lastSpeed > 0 && lastSpeed != liveSpeed ? " (last " + lastSpeed + "t)" : ""));
        // The two values that decide whether a first attack is charged: where the cooldown baseline was set,
        // and how much leeway was left. Without them a start-of-fight charge cannot be diagnosed from a log.
        if (counting) {
            line("Window opened", "tick " + fightWindow.getOpenedAtTick()
                    + (tracker.getGraceTicksRemaining() > 0
                       ? " (" + tracker.getGraceTicksRemaining() + "t grace left)" : ""), MUTED);
        }
        if (tracker.getLastAttackTick() >= 0) {
            // Which animation the last attack was recognised by, for checking a tick delay that looks wrong
            line("Last attack", "tick " + tracker.getLastAttackTick()
                    + " anim " + tracker.getLastAttackAnimation(), MUTED);
        }
        line("Weapon", String.valueOf(weaponSpeeds.getWeaponId()));
        AttackStyles.Style style = weaponSpeeds.getStyle();
        boolean rapid = weaponSpeeds.isRapid();
        line("Attack style", (style == null ? "unknown" : style.name())
                        + " [" + weaponSpeeds.getAttackStyleIndex() + "]"
                        + (rapid ? " rapid -1t" : ""),
                rapid ? COUNTING : Color.WHITE);
        if (weaponSpeeds.isManualCasting() || weaponSpeeds.isAutoCasting()) {
            line("Casting", weaponSpeeds.isManualCasting() ? "manual" : "autocast", COUNTING);
        }
        line("Attacking", String.valueOf(tracker.isAttacking()),
                tracker.isAttacking() ? COUNTING : MUTED);
        line("Target", fightWindow.hasAttackableTarget() ? "attackable" : "none",
                fightWindow.hasAttackableTarget() ? COUNTING : MUTED);

        // Window inputs
        line("Boss bar", fightWindow.isBossBarUp() ? "up" : "down",
                fightWindow.isBossBarUp() ? COUNTING : MUTED);
        line("Boss hp", fightWindow.isBossBarUp()
                ? fightWindow.getBossHp() + " / " + fightWindow.getBossBaseHp()
                : "-", fightWindow.isBossBarUp() ? Color.WHITE : MUTED);
        line("Required npcs", String.valueOf(fightWindow.getRequiredAliveCount()));
        if (fightWindow.isBossDeathSeen()) {
            line("Boss death", "seen", BLOCKED);
        } else if (!fightWindow.isFightStarted() && fightWindow.getRequiredAliveCount() > 0) {
            line("Fight", "not started", MUTED);
        }

        // Eating
        int pendingEat = eatTracker.getUnattributedDelayTicks();
        if (pendingEat > 0) {
            line("Eat pending", pendingEat + "t", BLOCKED);
        }
        if (eatTracker.getLastEatTick() >= 0) {
            line("Last eat", "tick " + eatTracker.getLastEatTick() + " (" + eatTracker.getLastEatDelay() + "t)", MUTED);
        }
        if (tracker.getResetCandidates() > 0) {
            line("Reset candidates", String.valueOf(tracker.getResetCandidates()), MUTED);
        }

        // Raid totals so far
        List<SegmentStats> counted = tracker.getCountedSegments();
        if (!counted.isEmpty()) {
            final boolean finished = tracker.isRaidFinished();

            panelComponent.getChildren().add(TitleComponent.builder()
                    .text(finished ? (tracker.isRaidCompleted() ? "Raid complete" : "Raid abandoned") : "Raid so far")
                    .color(finished ? (tracker.isRaidCompleted() ? COUNTING : BLOCKED) : Color.WHITE)
                    .build());

            for (SegmentStats s : counted) {
                line(s.getLabel(), s.getLost() + "t lost / " + s.getTicksCounting() + "t");
            }

            line("Total", tracker.getTotalLost() + "t lost / "
                    + tracker.getTotalCountingTicks() + "t, " + tracker.getTotalAttacks() + " attacks");

            if (finished) {
                line("Efficiency", String.format("%.1f%%", tracker.getTotalEfficiency() * 100f));
                if (tracker.getRaidDurationTicks() > 0) {
                    // Ticks are 600ms, so this is the wall-clock length of the raid
                    int seconds = Math.round(tracker.getRaidDurationTicks() * 0.6f);
                    line("Raid time", String.format("%d:%02d", seconds / 60, seconds % 60));
                }
            }
        }

        return super.render(graphics);
    }

    private void line(String left, String right) {
        line(left, right, Color.WHITE);
    }

    private void line(String left, String right, Color rightColor) {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(left)
                .right(right)
                .rightColor(rightColor)
                .build());
    }
}

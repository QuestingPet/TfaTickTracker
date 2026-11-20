package com.tfaticktracker.tickloss;

import com.tfaticktracker.RaidRoom;
import com.tfaticktracker.RaidState;
import com.tfaticktracker.TfaTickTrackerConfig;
import com.tfaticktracker.TfaTickTrackerPlugin;
import com.tfaticktracker.events.InRaidChanged;
import com.tfaticktracker.events.RaidFinished;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.FakeXpDrop;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.InteractingChanged;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Measures the local player's unused attack ticks, per room and per phase.
 * <p>
 * Everything that decides what a lost tick is lives in this package, reading only {@link RaidState},
 * {@link RaidRoom} and the raid events. That boundary is what lets the whole measurement run off a recorded
 * log in the tests without a client.
 * Nothing outside this package should import it either, so it can be lifted into its own plugin with a package
 * rename once the numbers are trustworthy.
 * <p>
 * Local player only. Attack style, spec energy and the attack option are not readable for other raiders, so their
 * numbers have to be self-reported over the party rather than observed -- which is not built yet.
 */
@Slf4j
@Singleton
public class TickLossTracker {

    /** An eat costs the tick it happened on, and only if an attack could have gone out instead. */

    private final Client client;
    private final EventBus eventBus;
    private final RaidState raidState;
    private final TfaTickTrackerConfig config;
    private final FightWindow fightWindow;
    private final WeaponSpeeds weaponSpeeds;
    private final EatTracker eatTracker;

    @Getter
    private final List<SegmentStats> segments = new ArrayList<>();

    @Getter
    private SegmentStats current;

    /** The cooldown itself. Pure, and covered by AttackTimelineTest. */
    private final AttackTimeline timeline = new AttackTimeline();

    /** Speed of the most recently registered attack. */
    @Getter
    private int lastSpeed;
    /** Animation the last registered attack was recognised by, so a suspicious one can be identified in game. */
    @Getter
    private int lastAttackAnimation;
    /** The current weapon's speed, recomputed every tick so the overlay is live outside a fight too. */
    @Getter
    private int currentSpeed;
    /** Whether an attack STARTED this tick, as opposed to one still animating. Pure, and unit tested. */
    private final AttackStart attackStart = new AttackStart();
    /**
     * The segment the last counted tick belonged to, which is where a settlement is booked.
     * <p>
     * Also what the overlay keeps showing once a room ends and before the next one opens, so its numbers
     * are still readable while you run between rooms.
     */
    @Getter
    private SegmentStats lastCountingSegment;
    /**
     * The last time anything was charged or forgiven, for the player-facing overlay to report.
     * <p>
     * Loss only becomes known at the moment something closes a gap -- an attack, or the fight ending -- so
     * this is the only honest point at which to tell the player about it. A running cooldown counter, which
     * is what the debug overlay shows, would be telling them off for a tick they are still in the middle of.
     */
    @Getter
    private Moment lastMoment;
    /** Whether a bow or crossbow was fired with no usable ammo this tick. See {@link DudShot}. */
    private boolean dudShotThisTick;
    /** Whether an Ocellus void explosion went off this tick, which knocks ticks off the cooldown. */
    private boolean voidExplosionThisTick;
    /** Whether we picked out a different npc this tick. */
    private boolean retargetedThisTick;
    private int lastTargetIndex = -1;
    @Getter
    private int resetCandidates;
    /** Set once the raid ends, so the panel of segments reads as a finished summary rather than a running total. */
    @Getter
    private boolean raidFinished;
    @Getter
    private boolean raidCompleted;
    @Getter
    private int raidDurationTicks;

    /** Everyone else's room summaries. This class never reads them; the overlay does. */
    @Getter
    private final TickLossParty party;

    @Inject
    public TickLossTracker(Client client, EventBus eventBus, RaidState raidState, TfaTickTrackerConfig config,
                           FightWindow fightWindow, WeaponSpeeds weaponSpeeds, EatTracker eatTracker,
                           TickLossParty party) {
        this.client = client;
        this.eventBus = eventBus;
        this.raidState = raidState;
        this.config = config;
        this.fightWindow = fightWindow;
        this.weaponSpeeds = weaponSpeeds;
        this.eatTracker = eatTracker;
        this.party = party;
    }

    public void startUp() {
        reset();
        weaponSpeeds.startUp();
        eventBus.register(this);
        eventBus.register(fightWindow);
        eventBus.register(party);
        party.startUp();
        eventBus.register(eatTracker);
        eventBus.register(weaponSpeeds);
    }

    public void shutDown() {
        eventBus.unregister(this);
        eventBus.unregister(fightWindow);
        eventBus.unregister(party);
        party.shutDown();
        eventBus.unregister(eatTracker);
        eventBus.unregister(weaponSpeeds);
        reset();
    }

    public void reset() {
        segments.clear();
        party.reset();
        current = null;
        lastCountingSegment = null;
        lastMoment = null;
        lastSpeed = 0;
        lastAttackAnimation = -1;
        currentSpeed = 0;
        resetCandidates = 0;
        attackStart.reset();
        unknownAnimations.reset();
        hitpointsXp = -1;
        retargetedThisTick = false;
        voidExplosionThisTick = false;
        dudShotThisTick = false;
        lastTargetIndex = -1;
        raidFinished = false;
        raidCompleted = false;
        raidDurationTicks = 0;
        fightWindow.reset();
        timeline.reset();
        eatTracker.reset();
    }

    /** What closed the gap, which decides how the overlay words it. */
    public enum Cause {
        /** An attack landed, and the wait before it is now known. */
        ATTACK,
        /** The boss died, the phase handed over, or it stepped out of reach with the wait still running. */
        FIGHT_ENDED,
        /** We did. Anything outstanding at that point was still ours. */
        DIED,
    }

    /** One moment of reckoning: what closed the gap, how long it was, and how much of it is explained. */
    @Getter
    @RequiredArgsConstructor
    public static final class Moment {
        private final int tick;
        private final Cause cause;
        private final String fight;
        private final int lost;
        private final int fromEating;
        private final int fromDudShot;
    }

    public int getNextAttackTick() {
        return timeline.getNextAttackTick();
    }

    public int getLastAttackTick() {
        return timeline.getLastAttackTick();
    }

    public int getGraceTicksRemaining() {
        return timeline.getGraceTicksRemaining();
    }

    /**
     * Runs after the detectors and after {@link TfaTickTrackerPlugin}, so the room state for this tick is settled.
     */
    @Subscribe(priority = -2)
    public void onGameTick(GameTick e) {
        final int tick = client.getTickCount();

        // Always on: the plugin being enabled IS the switch, and a second one would only be a way to
        // leave it installed and silently measuring nothing.
        fightWindow.update(true);

        currentSpeed = weaponSpeeds.compute();

        rollSegmentIfNeeded();

        if (current != null) {
            current.tickInRoom();
        }

        final boolean counting = fightWindow.isCounting();
        final boolean attacking = isAttacking();
        final boolean started = attackStarted();
        if (attacking && started) watchIfUnrecognised();

        final AttackTimeline.Result result = timeline.onTick(new AttackTimeline.Input()
                .tick(tick)
                .counting(counting)
                .attacking(attacking)
                .attackStarted(started)
                .weaponSpeed(currentSpeed)
                .eatDelay(eatTracker.takeUnattributedDelay())
                .graceOnOpen(RaidNpcs.hasEntryGrace(raidState.getCurrentRoom())
                        ? config.graceTicksOnEnter() : 0)
                .cooldownReduction(voidExplosionThisTick
                        ? RaidNpcs.VOID_EXPLOSION_COOLDOWN_REDUCTION : 0)
                .fightEnded(FightWindowState.endsTheFight(fightWindow.getState()))
                .dudShot(dudShotThisTick));

        dudShotThisTick = false;

        voidExplosionThisTick = false;

        if (result.getClosedOut() > 0 && lastCountingSegment != null) {
            lastCountingSegment.addClosedOut(result.getClosedOut(), result.getClosedOutFromEating(),
                    result.getClosedOutFromDudShot());
            lastMoment = new Moment(tick,
                    fightWindow.getState() == FightWindowState.State.PLAYER_DEAD
                            ? Cause.DIED : Cause.FIGHT_ENDED,
                    labelOf(lastCountingSegment),
                    result.getClosedOut(), result.getClosedOutFromEating(),
                    result.getClosedOutFromDudShot());
        }

        if (current == null) {
            return;
        }

        if (result.isCounted()) {
            current.tickCounting();
            // The segment the ticks being measured belong to. Held separately because a settlement lands on
            // the tick AFTER the last counted one, by which point the phase label may already have rolled
            // this field's successor into place -- and the ticks being settled are the old phase's.
            lastCountingSegment = current;
        }
        if (result.isEarlyAttack()) {
            resetCandidates++;
            current.addEarlyAttack();
        } else if (result.isAttack()) {
            current.addAttack(result.getOvershoot(), result.getOvershootFromEating(),
                    result.getOvershootFromDudShot());
        }
        if (result.isAttack()) {
            // An early attack is never charged, so it reports as on time rather than not at all: silence
            // after a reset reads as the overlay having missed the attack.
            lastMoment = new Moment(tick, Cause.ATTACK, labelOf(current),
                    result.getOvershoot(), result.getOvershootFromEating(),
                    result.getOvershootFromDudShot());
        }
        if (result.isDudShot()) {
            current.addDudShot();
        }
        if (result.isAttack()) {
            onAttackRegistered();
        }

        // Last, so everything this tick produced is already in the segment before it is published. The
        // settlement above is the whole reason: it lands on the tick the fight ends, and closing any
        // earlier would tell the party a total that is about to grow.
        if (FightWindowState.endsTheRoom(fightWindow.getState())) {
            closeCurrent();
        }
    }

    /**
     * End the room that is open, if any, and tell everything that cares.
     * <p>
     * The only way a segment is ever closed. A room ends when the boss dies, when the phase hands over,
     * when you die, or when you leave the raid -- a wipe is the last of those -- and all of them have to
     * settle it the same way, because that moment is when its numbers become final and therefore the
     * moment the panel redraws and the party is told.
     * <p>
     * The first three are recognised from the fight window's own state rather than from their events, in
     * {@link #onGameTick} and at the very end of it. An {@code ActorDeath} handler looked like the obvious
     * home for the death case and was wrong twice over: it fires before the tick is evaluated, so the room
     * went out to the party without the ticks you died owing, and it says nothing about the boss dying,
     * which is the common case and used to wait for the room change -- leaving a finished kill showing
     * stale numbers until you walked out.
     * <p>
     * Your own death ending the room is also how practice mode retries a boss. It re-runs a room without
     * ever leaving it, so the room change that used to be the only close never came: in
     * a recorded raid the Archive Guardian ran from tick 35 to 372 with deaths at
     * 220 and 340 and no room change between them, and all three attempts piled into one segment reading
     * 105 lost over 197 ticks.
     */
    private void closeCurrent() {
        if (current == null) return;

        current.close(client.getTickCount());
        for (Integer animation : unknownAnimations.settle()) {
            current.noteUnrecognisedAnimation(animation);
            log.debug("Tick loss: animation {} was counted as an attack {} times and never once dealt "
                    + "damage, so it may not be an attack", animation, unknownAnimations.startsOf(animation));
        }
        if (current.getTicksCounting() > 0) {
            log.debug("Tick loss: {} -> {} counted ticks, {} lost ({} to eating), {} attacks",
                    current.getLabel(), current.getTicksCounting(), current.getLost(),
                    current.getLostToEating(), current.getAttacks());
            party.onSegmentClosed(current);
        }
        current = null;
    }

    private static String labelOf(SegmentStats segment) {
        return segment == null ? null
                : TickLossLabels.fight(segment.getRoom(), segment.getPhaseName());
    }

    /** Bookkeeping that is not the timeline's business: which weapon, and what the overlay shows. */
    private void onAttackRegistered() {
        weaponSpeeds.onAttackRegistered();
        final Player local = client.getLocalPlayer();
        lastAttackAnimation = local == null ? -1 : local.getAnimation();
        lastSpeed = currentSpeed;
    }

    /**
     * Start a new segment whenever the room or the phase changes, and close the previous one.
     */
    private void rollSegmentIfNeeded() {
        final RaidRoom room = raidState.getCurrentRoom();
        final int phase = fightWindow.getPhase();

        final String phaseName = fightWindow.getPhaseLabel();
        if (current != null && current.getRoom() == room && current.getPhase() == phase
                && Objects.equals(current.getPhaseName(), phaseName)) {
            return;
        }

        closeCurrent();

        if (!RaidNpcs.isFightRoom(room)) return;
        // The boss is dead, or we are, or the phase has handed over. The room is still under our feet and
        // will be for a while yet, but there is nothing left in it to measure, and a fresh segment here
        // would replace the finished one on the overlay with a row of zeroes. A practice-mode retry opens
        // the next one, because starting a new fight is what clears these states.
        if (FightWindowState.endsTheRoom(fightWindow.getState())) return;

        current = new SegmentStats(room, phase, phaseName, client.getTickCount());
        segments.add(current);
    }

    /**
     * Compact version of the attack test: an attackable NPC targeted and not standing idle. Animation is only a
     * "doing something" gate, not an allow list, so new weapons do not need to be added here.
     */
    /**
     * Decide whether an attack STARTED this tick.
     * <p>
     * The animation alone cannot say. It lingers after the shot that produced it, and it does not re-fire
     * AnimationChanged when an attack repeats with the same id, so "an attack animation is showing" is true
     * both for a fresh attack and for one that happened two ticks ago. Polling that way invents attacks: in
     * a recorded raid the blowpipe animation runs unbroken from 338 to 342 over
     * shots at 338 and 340 -- the hitsplats land at 340, 342 and 347, so the shots are at 338, 340 and 345 --
     * and tick 342 was credited as an attack that never happened, which swallowed two of the three ticks
     * actually lost to the eat.
     * <p>
     * The animation FRAME settles it: it restarts on a new attack and advances while one plays on.
     */
    private boolean attackStarted() {
        final Player local = client.getLocalPlayer();
        final int animation = local == null ? -1 : local.getAnimation();
        final int frame = local == null ? -1 : local.getAnimationFrame();

        final boolean started = attackStart.started(animation, frame, retargetedThisTick);
        retargetedThisTick = false;
        return started;
    }

    /**
     * A bow or crossbow fired with no usable ammo, which burns the weapon's cooldown for nothing.
     * <p>
     * The chat message is the only signal there is: no animation plays, so the tick looks like idling. See
     * {@link DudShot}.
     */
    @Subscribe
    public void onChatMessage(ChatMessage e) {
        if ((e.getType() == ChatMessageType.GAMEMESSAGE || e.getType() == ChatMessageType.SPAM)
                && DudShot.isNoAmmoMessage(e.getMessage())) {
            dudShotThisTick = true;
        }
    }

    /**
     * Ocellus P4's void explosion, which takes ticks off the cooldown when you step off the tile it lands on.
     * <p>
     * You cannot see another raider's, so any one of these is yours. Applied whether or not you actually
     * stepped off it: that is the point, because an explosion you failed to use is tick loss and should read
     * as such.
     */
    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated e) {
        if (e.getGraphicsObject().getId() == RaidNpcs.VOID_EXPLOSION_SPOTANIM) {
            voidExplosionThisTick = true;
        }
    }

    /**
     * Picking out a DIFFERENT npc, which is a fresh attack command. Requiring it to be a different one keeps
     * a re-click on the thing you are already fighting from counting as an attack you did not make.
     */
    @Subscribe
    public void onInteractingChanged(InteractingChanged e) {
        if (e.getSource() != client.getLocalPlayer()) return;

        final int index = e.getTarget() instanceof NPC ? ((NPC) e.getTarget()).getIndex() : -1;
        if (index == -1) return;

        if (index != lastTargetIndex) {
            retargetedThisTick = true;
        }
        lastTargetIndex = index;
    }

    public boolean isAttacking() {
        final Player local = client.getLocalPlayer();
        if (local == null) return false;

        final Actor target = local.getInteracting();
        if (!(target instanceof NPC)) return false;

        final NPCComposition comp = ((NPC) target).getTransformedComposition();
        if (comp == null) return false;

        boolean attackable = false;
        for (String action : comp.getActions()) {
            if ("Attack".equals(action)) {
                attackable = true;
                break;
            }
        }
        if (!attackable) return false;

        final int animation = local.getAnimation();
        if (animation == -1) return false;

        // The denylist decides, so an animation nobody has listed counts as an attack. That default
        // survives a weapon nobody has listed and an attack whose animation was overridden by something
        // else, both of which an allowlist gets badly wrong. What it does not survive is something that
        // merely animates you mid-fight, and UnknownAnimations is what makes that case speak up.
        return !NonAttackAnimations.isNonAttack(animation);
    }

    /** Animations neither list knows, waiting to see whether any of them ever hit something. */
    private final UnknownAnimations unknownAnimations = new UnknownAnimations();
    /**
     * Hitpoints experience as of the last reading, or -1 before the first one.
     * <p>
     * Hitpoints and nothing else. Widening this to the other combat skills looks free and is not: magic
     * experience comes from alchemy and teleports as readily as from a combat spell, so an alch-shaped
     * animation would produce "damage", excuse itself, and never be reported -- which is the exact case
     * this whole mechanism exists to catch. Hitpoints tracks damage dealt and only damage dealt.
     * <p>
     * The cost is real but small: an uncharged Ralos glaive gave ranged experience on some throws with no
     * hitpoints drop, so those hits go unseen. One hit anywhere in the raid is enough to excuse an
     * animation, so missing some of them changes nothing.
     */
    private int hitpointsXp = -1;

    /**
     * An animation in neither list, which we have just counted as an attack. Watched, not changed.
     * <p>
     * See {@link UnknownAnimations}: the count is only ever used to decide whether to mention it on the way
     * out of the room, never to decide whether it was an attack.
     */
    private void watchIfUnrecognised() {
        final Player local = client.getLocalPlayer();
        final int animation = local == null ? -1 : local.getAnimation();
        if (!AttackAnimations.isUnrecognised(animation)) return;

        unknownAnimations.countedAsAttack(animation, client.getTickCount());
        log.debug("Tick loss: animation {} is in neither list and was counted as an attack", animation);
    }

    /**
     * Damage we dealt, which is what tells an attack from something that merely animates you.
     * <p>
     * Both events are needed: {@link StatChanged} carries the ordinary drop, and {@link FakeXpDrop} is what
     * arrives instead once the skill is at maximum experience, where the total stops moving.
     */
    @Subscribe
    public void onStatChanged(StatChanged e) {
        if (e.getSkill() != Skill.HITPOINTS) return;

        final int xp = e.getXp();
        if (hitpointsXp >= 0 && xp > hitpointsXp) unknownAnimations.damageDealt(client.getTickCount());
        hitpointsXp = xp;
    }

    @Subscribe
    public void onFakeXpDrop(FakeXpDrop e) {
        if (e.getSkill() == Skill.HITPOINTS) unknownAnimations.damageDealt(client.getTickCount());
    }

    /**
     * The end of the raid. Segments are deliberately kept so the summary survives into the lobby; the next raid's
     * InRaidChanged is what clears them.
     */
    @Subscribe
    public void onRaidFinished(RaidFinished e) {
        raidFinished = true;
        raidCompleted = e.isCompleted();
        raidDurationTicks = e.getDurationTicks();

        for (SegmentStats s : getCountedSegments()) {
            log.debug("Tick loss: {} -- {} lost ({} eating) over {} counted ticks, {} attacks, {}% efficiency",
                    s.getLabel(), s.getLost(), s.getLostToEating(), s.getTicksCounting(),
                    s.getAttacks(), Math.round(s.getEfficiency() * 100));
        }
        log.debug("Tick loss: raid {} in {} ticks -- {} lost over {} counted ticks, {} attacks",
                raidCompleted ? "completed" : "abandoned", raidDurationTicks, getTotalLost(),
                getTotalCountingTicks(), getTotalAttacks());
    }

    /** Efficiency across every counted segment of the raid. */
    public double getTotalEfficiency() {
        final int counted = getTotalCountingTicks();
        if (counted <= 0) return 1.0d;
        return Math.max(0.0d, 1.0d - ((double) getTotalLost() / counted));
    }

    @Subscribe
    public void onInRaidChanged(InRaidChanged e) {
        if (e.isInRaid()) {
            log.debug("Tick loss: new raid, clearing segments");
            reset();
        } else {
            // Leaving the raid ends whatever room was open, wipe included. It has to go through the same
            // close as a room change or it is a room that finished and nobody was told: the panel would
            // never redraw for it and the party would never hear about it. Two code paths closing a
            // segment differently is how that happened in the first place.
            closeCurrent();
        }
    }

    /** Totals across every segment of the current raid. */
    public int getTotalCountingTicks() {
        return segments.stream().mapToInt(SegmentStats::getTicksCounting).sum();
    }

    public int getTotalLost() {
        return segments.stream().mapToInt(SegmentStats::getLost).sum();
    }

    public int getTotalAttacks() {
        return segments.stream().mapToInt(SegmentStats::getAttacks).sum();
    }

    public List<SegmentStats> getCountedSegments() {
        return segments.stream().filter(s -> s.getTicksCounting() > 0).collect(Collectors.toList());
    }
}

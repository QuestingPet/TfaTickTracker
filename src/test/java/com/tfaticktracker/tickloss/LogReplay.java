package com.tfaticktracker.tickloss;

import com.tfaticktracker.RaidRoom;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.VarbitID;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replays a debug log through the REAL {@link FightWindowState} and {@link AttackTimeline}.
 * <p>
 * This exists because the thing that previously checked rule changes against past raids was a parallel
 * reimplementation in another language, and it was wrong three separate times: it counted the whole team's
 * attacks as the player's, it detected attacks from events while the plugin polled per tick, and it had no
 * cooldown-reset branch at all. Every time it "verified" a fix across sixty logs it was measuring something
 * other than the plugin. There is now one implementation, driven either by a live client or by a log.
 * <p>
 * Two things a log cannot supply, both deliberate:
 * <ul>
 *     <li><b>Weapon speed.</b> The log records no equipment, so speed is inferred from the attack animation
 *     via {@link #SPEED_BY_ANIMATION}. Good enough for the weapons actually used; the real speed logic lives
 *     in {@link WeaponSpeeds} and is not exercised here.</li>
 *     <li><b>Eating.</b> {@code EventLogger} records no menu clicks or inventory changes, so a confirmed eat
 *     cannot be reconstructed. Eat attribution is covered by scenarios in {@code AttackTimelineTest} instead.</li>
 * </ul>
 */
public final class LogReplay {

    /** Attack speeds keyed on animation, since a log has no equipment in it. Test scope only. */
    private static final Map<Integer, Integer> SPEED_BY_ANIMATION = new HashMap<>();

    static {
        SPEED_BY_ANIMATION.put(AnimationID.HUMAN_BOW, 5);
        SPEED_BY_ANIMATION.put(AnimationID.SCYTHE_OF_VITUR_ATTACK, 5);
        SPEED_BY_ANIMATION.put(AnimationID.ZCB_ATTACK_PVN, 5);
        SPEED_BY_ANIMATION.put(AnimationID.HUMAN_GLAIVE_RALOS01_CHARGED_SPECIAL, 6);
        SPEED_BY_ANIMATION.put(AnimationID.HUMAN_GLAIVE_RALOS01_CHARGED_THROW, 6);
        SPEED_BY_ANIMATION.put(AnimationID.SNAKEBOSS_BLOWPIPE_ATTACK, 2);
        SPEED_BY_ANIMATION.put(AnimationID.HUMAN_WEAPON_BOW_VENATOR01_SHOOT, 4);
        SPEED_BY_ANIMATION.put(AnimationID.HUMAN_ELDER_MAUL_ATTACK, 6);
        SPEED_BY_ANIMATION.put(AnimationID.HUMAN_WEAPONS_CRIMSON_KISTEN_SPECIAL, 4);
        SPEED_BY_ANIMATION.put(AnimationID.HUMAN_SPELLCAST_GRASP, 5);
    }

    private static final int DEFAULT_SPEED = 5;
    /** A click either consumes the item within a tick or two, or never will. */
    private static final int EAT_CONFIRM_TICKS = 2;

    private static final Pattern INDEX = Pattern.compile("index=(\\d+)");
    private static final Pattern ID = Pattern.compile("\\bid=(\\d+)");
    private static final Pattern ANIM = Pattern.compile("anim=(-?\\d+)");
    private static final Pattern FRAME = Pattern.compile("frame=(-?\\d+)");
    private static final Pattern XY = Pattern.compile(" x=(\\d+) y=(\\d+)");
    private static final Pattern NEW_ROOM = Pattern.compile("new=(\\w+)");
    private static final Pattern VARBIT = Pattern.compile("varbit=(-?\\d+) varp=\\d+ value=(-?\\d+)");
    private static final Pattern MINE_HITSPLAT = Pattern.compile("player name=(.+?) type=");
    private static final Pattern MENU_OPTION = Pattern.compile("option=(\\S+)");
    private static final Pattern PARAM0 = Pattern.compile("param0=(-?\\d+)");
    private static final Pattern ITEM_ID = Pattern.compile("itemId=(-?\\d+)");
    private static final Pattern SLOT_CHANGE = Pattern.compile("(\\d+):(-?\\d+)->(-?\\d+)");
    private static final Pattern WEAPON = Pattern.compile("weapon=(-?\\d+)");
    private static final Pattern ASPEED = Pattern.compile("aspeed=(-?\\d+)");
    private static final Pattern STYLE = Pattern.compile("style=(\\w+)");
    private static final Pattern RAPID = Pattern.compile("rapid=(\\d)");
    private static final Pattern MCAST = Pattern.compile("mcast=(\\d)");
    private static final Pattern ACAST = Pattern.compile("acast=(\\d)");
    private static final Pattern CSTYLE = Pattern.compile("cstyle=(\\d)");
    private static final Pattern TARGET_ATK = Pattern.compile("targetatk=(\\d)");
    private static final Pattern MY_TARGET = Pattern.compile("target=\\[npc [^\\]]*?index=(\\d+)");
    private static final Pattern NPC_SOURCE = Pattern.compile("source=\\[npc [^\\]]*?index=(\\d+)");

    /** One room, or one phase of one, exactly as the plugin segments them. */
    public static final class Segment {
        public RaidRoom room;
        public String phaseLabel;
        public int startTick;
        public int endTick;
        public int counted;
        public int lost;
        public int attacks;
        public int earlyAttacks;
        public int lostToEating;
        public int longestGap;
        /** The part of {@link #lost} that no attack closed -- settled because the fight ended. */
        public int closedOut;
        /** The part of {@link #lost} spent on a cooldown a dud shot started. */
        public int lostToDudShots;
        /** Bows or crossbows fired with no usable ammo. */
        public int dudShots;

        public String label() {
            final String name = room == null ? "?" : room.name();
            return phaseLabel == null ? name : name + " " + phaseLabel;
        }
    }

    public static final class Result {
        public final List<Segment> segments = new ArrayList<>();
        /** Attacks made while the window said we could not attack, keyed "ROOM/STATE". */
        public final Map<String, Integer> blocked = new HashMap<>();
        /** Attacks that landed before the cooldown expected them, keyed by animation id. */
        public final Map<Integer, Integer> earlyByAnimation = new HashMap<>();
        /** The same, keyed "ROOM PHASE/animation", so a reset can be traced to where it happens. */
        public final Map<String, Integer> earlyByRoom = new HashMap<>();
        /** Marker hotkey presses, as {tick, number} -- where the player wanted a closer look. */
        public final List<int[]> markers = new ArrayList<>();
        public String localPlayer;

        public int totalLost() {
            return segments.stream().mapToInt(s -> s.lost).sum();
        }

        public int totalCounted() {
            return segments.stream().mapToInt(s -> s.counted).sum();
        }

        public int totalAttacks() {
            return segments.stream().mapToInt(s -> s.attacks).sum();
        }
    }

    public Result replay(Path log) throws IOException {
        final Result result = new Result();
        result.localPlayer = findLocalPlayer(log);
        if (result.localPlayer == null) {
            return result;
        }

        final Run run = new Run(result);

        // Streamed, not loaded: the larger logs run to hundreds of thousands of lines and holding one in
        // memory is enough to exhaust the test JVM's heap.
        try (BufferedReader in = Files.newBufferedReader(log, StandardCharsets.UTF_8)) {
            String line;
            int currentTick = -1;
            while ((line = in.readLine()) != null) {
                final int tab = line.indexOf('	');
                if (tab <= 0) continue;

                final int tick;
                try {
                    tick = Integer.parseInt(line.substring(0, tab));
                } catch (NumberFormatException ignored) {
                    continue;
                }

                if (tick != currentTick) {
                    // Everything belonging to the previous tick has now been applied, so evaluate it.
                    run.endOfTick(currentTick);
                    currentTick = tick;
                }

                final int tab2 = line.indexOf('	', tab + 1);
                final String kind = tab2 < 0 ? line.substring(tab + 1) : line.substring(tab + 1, tab2);
                final String detail = tab2 < 0 ? "" : line.substring(tab2 + 1);
                run.event(tick, kind, detail);
            }
            run.endOfTick(currentTick);
        }

        return result;
    }

    /**
     * The live tracker's {@code rollSegmentIfNeeded}, to the letter. A room ends when its fight does, not
     * when you walk out of it, and nothing re-opens behind it until the next fight starts.
     */
    private static Segment rollSegment(Result result, Segment current, FightWindowState window, int tick,
                                       FightWindowState.State state) {
        final RaidRoom room = window.getRoom();
        if (room == null || !RaidNpcs.isFightRoom(room)) {
            return null;
        }
        if (current != null && current.room == room
                && java.util.Objects.equals(current.phaseLabel, window.getPhaseLabel())) {
            return current;
        }
        if (FightWindowState.endsTheRoom(state)) {
            return null;
        }

        final Segment next = new Segment();
        next.room = room;
        next.phaseLabel = window.getPhaseLabel();
        next.startTick = tick;
        next.endTick = tick;
        result.segments.add(next);
        return next;
    }

    private static void position(String detail, int index, Map<Integer, int[]> into) {
        final Matcher m = XY.matcher(detail);
        if (m.find()) {
            into.put(index, new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
        }
    }

    /**
     * Read back what {@link AttackSpeed#describe} wrote, or null for a log predating it.
     * <p>
     * Shared with {@code AttackSpeedTest}, which round-trips the pair so the logger and this parser cannot
     * drift apart silently -- a format change on one side would otherwise just quietly stop being read.
     */
    public static AttackSpeed.Facts parseFacts(String tickLine, int animation) {
        final Matcher w = WEAPON.matcher(tickLine);
        if (!w.find()) return null;

        return new AttackSpeed.Facts()
                .weaponId(Integer.parseInt(w.group(1)))
                .animation(animation)
                .aspeed(group(ASPEED, tickLine))
                .style(parseStyle(text(STYLE, tickLine)))
                .rapid(group(RAPID, tickLine) == 1)
                .manualCasting(group(MCAST, tickLine) == 1)
                .autoCasting(group(ACAST, tickLine) == 1)
                .castingStyle(group(CSTYLE, tickLine) == 1);
    }

    private static String text(Pattern p, String s) {
        final Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static AttackStyles.Style parseStyle(String name) {
        if (name == null || "null".equals(name)) return null;
        for (AttackStyles.Style style : AttackStyles.Style.values()) {
            if (style.name().equals(name)) return style;
        }
        return null;
    }

    private static int group(Pattern p, String s) {
        final Matcher m = p.matcher(s);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static RaidRoom parseRoom(String name) {
        for (RaidRoom room : RaidRoom.values()) {
            if (room.name().equals(name)) return room;
        }
        return null;
    }

    /**
     * A hitsplat ON a player carries mine=true only for the local one, which is the only line in the log that
     * names them. Getting this wrong counts the whole team's attacks as yours and makes loss all but vanish.
     */
    /** Package visible so {@link LogFixtures} identifies the local player exactly as the replay does. */
    static String findLocalPlayer(Path log) throws IOException {
        try (BufferedReader in = Files.newBufferedReader(log, StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.contains("\tHITSPLAT\tplayer") || !line.contains("mine=true")) continue;

                final Matcher m = MINE_HITSPLAT.matcher(line);
                if (m.find()) return m.group(1);
            }
        }
        return null;
    }

    /** One replay in progress. Holds the scene as the log has described it so far. */
    private static final class Run {

        private final Result result;
        private final FightWindowState window = new FightWindowState();
        private final AttackTimeline timeline = new AttackTimeline();

        private final Map<Integer, Integer> npcIds = new HashMap<>();
        private final Map<Integer, int[]> npcPositions = new HashMap<>();
        private final Map<Integer, Integer> varbits = new HashMap<>();

        private final String meSource;
        private final String meName;

        private RaidRoom currentRoom;
        private boolean inRaid;
        private boolean targetIsNpc;
        private boolean sawTick;
        private int myAnim = -1;
        private Integer myFrame;
        private final AttackStart attackStart = new AttackStart();
        private AttackSpeed.Facts facts;
        private boolean retargetedThisTick;
        private int lastTargetIndex = -1;
        /** Eat clicks awaiting confirmation: {clickTick, slot, itemId}. */
        private final List<int[]> pendingEats = new ArrayList<>();
        private int eatDelayThisTick;
        private boolean voidExplosionThisTick;
        private boolean dudShotThisTick;
        /** Our own death, which ends the attempt and starts a fresh segment for the retry. */
        private Segment lastCountingSegment;
        private final int traceFrom;
        private final int traceTo;
        private Boolean loggedTargetAttackable;
        private Segment current;

        Run(Result result) {
            final String range = System.getenv("TRACE_TICKS");
            final String[] bits = range == null ? null : range.split("-");
            this.traceFrom = bits == null ? Integer.MAX_VALUE : Integer.parseInt(bits[0].trim());
            this.traceTo = bits == null ? Integer.MIN_VALUE
                    : Integer.parseInt(bits[bits.length - 1].trim());
            this.result = result;
            this.meName = result.localPlayer;
            this.meSource = "source=[player name=" + result.localPlayer + "]";
        }

        void event(int tick, String kind, String detail) {
            switch (kind) {
                case "RAID_ROOM_CHANGED": {
                    final Matcher m = NEW_ROOM.matcher(detail);
                    final RaidRoom room = m.find() ? parseRoom(m.group(1)) : null;
                    currentRoom = room;
                    inRaid = room != null;
                    final List<int[]> present = new ArrayList<>();
                    npcIds.forEach((index, id) -> present.add(new int[]{index, id}));
                    window.roomChanged(room, present);
                    break;
                }
                case "NPC_SPAWNED":
                case "NPC_CHANGED": {
                    final int index = group(INDEX, detail);
                    final int id = group(ID, detail);
                    if (index < 0 || id < 0) break;
                    npcIds.put(index, id);
                    position(detail, index, npcPositions);
                    window.npcSeen(index, id);
                    break;
                }
                case "NPC_DESPAWNED": {
                    final int index = group(INDEX, detail);
                    if (index < 0) break;
                    npcIds.remove(index);
                    npcPositions.remove(index);
                    window.npcGone(index);
                    break;
                }
                case "MOVE": {
                    if (!detail.startsWith("npc")) break;
                    final int index = group(INDEX, detail);
                    if (index >= 0) position(detail, index, npcPositions);
                    break;
                }
                case "ANIMATION": {
                    if (!detail.startsWith("npc")) break;
                    final int index = group(INDEX, detail);
                    if (index >= 0) window.npcAnimation(index, group(ANIM, detail));
                    break;
                }
                case "INTERACTING": {
                    if (detail.startsWith("source=[npc")) {
                        if (detail.contains("target=[player")) {
                            final Matcher m = NPC_SOURCE.matcher(detail);
                            if (m.find()) window.npcTargetsPlayer(tick, Integer.parseInt(m.group(1)));
                        }
                    } else if (detail.startsWith(meSource)) {
                        targetIsNpc = detail.contains("target=[npc");

                        final Matcher mine = MY_TARGET.matcher(detail);
                        if (mine.find()) {
                            final int index = Integer.parseInt(mine.group(1));
                            if (index != lastTargetIndex) {
                                retargetedThisTick = true;
                            }
                            lastTargetIndex = index;
                        }
                    }
                    break;
                }
                case "CHAT": {
                    // The only trace a dud shot leaves. Nothing else about the tick looks different.
                    if (DudShot.isNoAmmoMessage(detail)) {
                        dudShotThisTick = true;
                    }
                    break;
                }
                case "MARK": {
                    // The event logger's marker hotkey. Whatever the player wanted a second look at is a
                    // few ticks BEFORE this, since it is pressed after noticing.
                    result.markers.add(new int[]{tick, result.markers.size() + 1});
                    break;
                }
                case "ACTOR_DEATH": {
                    if (detail.startsWith("player")) {
                        if (detail.contains("name=" + meName + " ")) {
                            // Your death ends the attempt, the same as it does live, but the close is
                            // driven off PLAYER_DEAD at the end of the tick rather than from here, so the
                            // ticks you died owing are billed to the room before it is published.
                            window.localPlayerDied();
                        }
                    } else if (detail.startsWith("npc")) {
                        final int index = group(INDEX, detail);
                        final int id = group(ID, detail);
                        if (index >= 0 && id >= 0) window.npcDied(index, id);
                    }
                    break;
                }
                case "MENU": {
                    final String option = text(MENU_OPTION, detail);
                    // The live path's own rule, not a copy of it: drinking costs nothing, so only food counts.
                    if (!EatTracker.isFoodOption(option)) break;

                    final int slot = group(PARAM0, detail);
                    final int itemId = group(ITEM_ID, detail);
                    if (slot >= 0 && itemId > 0) {
                        pendingEats.add(new int[]{tick, slot, itemId});
                    }
                    break;
                }
                case "INVENTORY": {
                    // A click is only an eat once the slot it was made on actually empties.
                    final Matcher m = SLOT_CHANGE.matcher(detail);
                    while (m.find()) {
                        final int slot = Integer.parseInt(m.group(1));
                        pendingEats.removeIf(eat -> {
                            if (eat[1] != slot || tick - eat[0] > EAT_CONFIRM_TICKS) return false;
                            eatDelayThisTick += EatTracker.delayForItem(eat[2]);
                            return true;
                        });
                    }
                    pendingEats.removeIf(eat -> tick - eat[0] > EAT_CONFIRM_TICKS);
                    break;
                }
                case "GRAPHICS_OBJECT": {
                    final int id = group(ID, detail);
                    if (id == RaidNpcs.VOID_EXPLOSION_SPOTANIM) {
                        voidExplosionThisTick = true;
                    }
                    break;
                }
                case "VAR": {
                    final Matcher m = VARBIT.matcher(detail);
                    if (m.find()) varbits.put(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
                    break;
                }
                case "TICK": {
                    sawTick = true;
                    myAnim = group(ANIM, detail);
                    final Matcher f = FRAME.matcher(detail);
                    myFrame = f.find() ? Integer.parseInt(f.group(1)) : null;

                    // The facts behind the speed, if this log is new enough to carry them.
                    facts = parseFacts(detail, myAnim);

                    final int atk = group(TARGET_ATK, detail);
                    loggedTargetAttackable = atk < 0 ? null : atk == 1;
                    break;
                }
                default:
                    break;
            }
        }

        /** Every event for this tick has been applied, so work out what the tick meant. */
        void endOfTick(int tick) {
            if (tick < 0 || !sawTick) {
                sawTick = false;
                return;
            }
            sawTick = false;

            final FightWindowState.Tick wt = new FightWindowState.Tick()
                    .tick(tick)
                    .room(currentRoom)
                    .enabled(true)
                    .inRaid(inRaid)
                    .localPlayerPresent(true)
                    .spectateMode(varbits.getOrDefault(VarbitID.TFA_CLIENT_SPECTATE_MODE, 0))
                    .bossBar(varbits.getOrDefault(VarbitID.HPBAR_HUD_BOSS, 0))
                    .bossBaseHp(varbits.getOrDefault(VarbitID.HPBAR_HUD_BASEHP, 0))
                    .bossHp(varbits.getOrDefault(VarbitID.HPBAR_HUD_HP, 0))
                    .minimapState(varbits.getOrDefault(VarbitID.MINIMAP_STATE, 0))
                    .extraBarBase(varbits.getOrDefault(VarbitID.HPBAR_HUD_EXTRA_BASE, 0))
                    .extraBarCurrent(varbits.getOrDefault(VarbitID.HPBAR_HUD_EXTRA_CURRENT, 0));

            final int[] at = npcPositions.get(window.getUnattackableNpcIndex());
            if (at != null) {
                wt.unattackableX(at[0]).unattackableY(at[1]);
            }

            final FightWindowState.State state = window.update(wt);

            // "Attackable" is a composition lookup, so an older log can only say the target is an NPC.
            final boolean hasTarget = loggedTargetAttackable != null ? loggedTargetAttackable : targetIsNpc;
            final boolean attacking = hasTarget && AttackAnimations.isAttack(myAnim);
            // Frames are what separate a re-attack from an animation still playing. Logs written before frame
            // logging have none, so fall back to the animation changing, which misses repeats sharing an id.
            // Frames do not advance one per tick, so a repeat can show the same frame twice -- two undead
            // grasps back to back both read frame 0. Switching to a different NPC is the third signal.
            // Logs written before frame logging have no frame at all. The retarget tie-break must not be
            // used there: with every frame reading -1 it would fire on every re-click, which across
            // debug-logs/ invented thousands of attacks and erased real loss. Those logs get the animation
            // changing and nothing else, so repeats sharing an id are missed -- a known limit of old logs.
            final boolean haveFrames = myFrame != null;
            final boolean started = attackStart.started(
                    myAnim, haveFrames ? myFrame : -1, haveFrames && retargetedThisTick);
            retargetedThisTick = false;

            final int readyBefore = timeline.getNextAttackTick();

            final AttackTimeline.Result r = timeline.onTick(new AttackTimeline.Input()
                    .tick(tick)
                    .counting(state == FightWindowState.State.COUNTING)
                    .attacking(attacking)
                    .attackStarted(started)
                    .eatDelay(eatDelayThisTick)
                    .cooldownReduction(voidExplosionThisTick
                            ? RaidNpcs.VOID_EXPLOSION_COOLDOWN_REDUCTION : 0)
                    .fightEnded(FightWindowState.endsTheFight(state))
                    .dudShot(dudShotThisTick)
                    .weaponSpeed(facts != null
                            ? AttackSpeed.compute(facts)
                            : SPEED_BY_ANIMATION.getOrDefault(myAnim, DEFAULT_SPEED)));

            // A tick range to walk through by hand, for a question about one moment in one raid:
            // TRACE_TICKS=4735-4765 PROBE_LOG=... ./gradlew test --tests '*LogReplayTest.replaysOne*' -i
            if (traceFrom <= tick && tick <= traceTo) {
                System.out.printf("  t%-6d %-18s anim=%-7d frame=%-4s atk=%-5s start=%-5s eat=%d "
                                + "| %-12s over=%d(%d eat,%d dud) settled=%d(%d eat,%d dud) "
                                + "| ready was %d, now %d%n",
                        tick, state.name() + (dudShotThisTick ? " DUD" : ""), myAnim,
                        haveFrames ? String.valueOf(myFrame) : "-",
                        attacking, started, eatDelayThisTick,
                        r.isEarlyAttack() ? "EARLY ATTACK" : r.isAttack() ? "ATTACK"
                                : r.isCounted() ? "" : "(not counted)",
                        r.getOvershoot(), r.getOvershootFromEating(), r.getOvershootFromDudShot(),
                        r.getClosedOut(), r.getClosedOutFromEating(), r.getClosedOutFromDudShot(),
                        readyBefore, timeline.getNextAttackTick());
            }

            eatDelayThisTick = 0;
            voidExplosionThisTick = false;
            dudShotThisTick = false;

            final RaidRoom room = window.getRoom();
            if (state != FightWindowState.State.COUNTING && attacking && started
                    && room != null && RaidNpcs.isFightRoom(room)) {
                result.blocked.merge(room.name() + "/" + state.name(), 1, Integer::sum);
            }

            // Booked before the roll, and against the segment the counted ticks belonged to: a settlement
            // lands on the tick after the last counted one, where the phase may already have moved on.
            if (r.getClosedOut() > 0 && lastCountingSegment != null) {
                lastCountingSegment.lost += r.getClosedOut();
                lastCountingSegment.lostToEating += r.getClosedOutFromEating();
                lastCountingSegment.lostToDudShots += r.getClosedOutFromDudShot();
                lastCountingSegment.closedOut += r.getClosedOut();
                lastCountingSegment.longestGap =
                        Math.max(lastCountingSegment.longestGap, r.getClosedOut());
            }

            current = rollSegment(result, current, window, tick, state);
            if (current == null) return;

            if (r.isCounted()) {
                current.counted++;
                lastCountingSegment = current;
            }
            if (r.isAttack()) current.attacks++;
            if (r.isEarlyAttack()) {
                current.earlyAttacks++;
                result.earlyByAnimation.merge(myAnim, 1, Integer::sum);
                result.earlyByRoom.merge(current.label() + "/" + myAnim, 1, Integer::sum);
            }
            current.lost += r.getOvershoot();
            current.lostToEating += r.getOvershootFromEating();
            current.lostToDudShots += r.getOvershootFromDudShot();
            if (r.isDudShot()) current.dudShots++;
            current.longestGap = Math.max(current.longestGap, r.getOvershoot());
            current.endTick = tick;

            // Closed last, so an attack landing on the tick the boss dies is still its room's.
            if (FightWindowState.endsTheRoom(state)) current = null;
        }
    }
}

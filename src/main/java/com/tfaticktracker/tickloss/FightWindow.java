package com.tfaticktracker.tickloss;

import com.tfaticktracker.RaidState;
import com.tfaticktracker.events.RaidRoomChanged;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * PROTOTYPE -- tick loss. The client-facing half of the fight window.
 * <p>
 * All this does is translate: RuneLite events and varbits in, {@link FightWindowState} calls out. Every rule
 * about whether a tick counts lives in that class, which has no client dependency and can therefore be driven
 * from a test or from a log instead of from a raid. Keep it that way -- these rules have been got wrong
 * repeatedly, and they are only cheap to check while they stay testable.
 */
@Slf4j
@Singleton
public class FightWindow {

    private final Client client;
    private final RaidState raidState;
    private final FightWindowState window = new FightWindowState();

    @Inject
    public FightWindow(Client client, RaidState raidState) {
        this.client = client;
        this.raidState = raidState;
    }

    public void reset() {
        window.reset();
    }

    // --- what the rest of the plugin reads ---

    public FightWindowState.State getState() {
        return window.getState();
    }

    public boolean isCounting() {
        return window.isCounting();
    }

    public boolean isBossBarUp() {
        return window.isBossBarUp();
    }

    public int getBossBaseHp() {
        return window.getBossBaseHp();
    }

    public int getBossHp() {
        return window.getBossHp();
    }

    public int getPhase() {
        return window.getPhase();
    }

    public String getPhaseLabel() {
        return window.getPhaseLabel();
    }

    public boolean isBossDeathSeen() {
        return window.isBossDeathSeen();
    }

    public int getRequiredAliveCount() {
        return window.getRequiredAliveCount();
    }

    public boolean isFightStarted() {
        return window.isFightStarted();
    }

    public boolean isBossUnattackable() {
        return window.isBossUnattackable();
    }

    public int getOpenedAtTick() {
        return window.getOpenedAtTick();
    }

    // --- the tick ---

    /** Recompute the window. Call once per tick, before anything reads {@link #isCounting()}. */
    void update(boolean enabled) {
        final FightWindowState.Tick in = new FightWindowState.Tick()
                .tick(client.getTickCount())
                .room(raidState.getCurrentRoom())
                .enabled(enabled)
                .inRaid(raidState.isInRaid())
                .localPlayerPresent(client.getLocalPlayer() != null)
                .spectateMode(client.getVarbitValue(VarbitID.TFA_CLIENT_SPECTATE_MODE))
                .bossBar(client.getVarbitValue(VarbitID.HPBAR_HUD_BOSS))
                .bossBaseHp(client.getVarbitValue(VarbitID.HPBAR_HUD_BASEHP))
                .bossHp(client.getVarbitValue(VarbitID.HPBAR_HUD_HP))
                .minimapState(client.getVarbitValue(VarbitID.MINIMAP_STATE))
                .extraBarBase(client.getVarbitValue(VarbitID.HPBAR_HUD_EXTRA_BASE))
                .extraBarCurrent(client.getVarbitValue(VarbitID.HPBAR_HUD_EXTRA_CURRENT));

        // A boss that left is tracked by where it stands, which is a move rather than an event, so its
        // position has to be looked up fresh every tick.
        final WorldPoint at = locate(window.getUnattackableNpcIndex());
        if (at != null) {
            in.unattackableX(at.getX()).unattackableY(at.getY());
        }

        window.update(in);
    }

    private WorldPoint locate(int npcIndex) {
        if (npcIndex == -1) return null;

        final WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null) return null;

        for (NPC npc : worldView.npcs()) {
            if (npc != null && npc.getIndex() == npcIndex) {
                return npc.getWorldLocation();
            }
        }
        return null;
    }

    // --- events, translated ---

    @Subscribe
    public void onRaidRoomChanged(RaidRoomChanged e) {
        // Every NPC in the scene, because a room's NPCs can spawn on the SAME tick as the room change and the
        // spawn is dispatched first, so they get tested against the previous room. FightWindowState rescans.
        final List<int[]> present = new ArrayList<>();
        final WorldView worldView = client.getTopLevelWorldView();
        if (worldView != null) {
            for (NPC npc : worldView.npcs()) {
                if (npc != null) {
                    present.add(new int[]{npc.getIndex(), npc.getId()});
                }
            }
        }
        window.roomChanged(raidState.getCurrentRoom(), present);
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned e) {
        window.npcSeen(e.getNpc().getIndex(), e.getNpc().getId());
    }

    @Subscribe
    public void onNpcChanged(NpcChanged e) {
        window.npcSeen(e.getNpc().getIndex(), e.getNpc().getId());
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned e) {
        window.npcGone(e.getNpc().getIndex());
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged e) {
        if (!(e.getActor() instanceof NPC)) return;

        final NPC npc = (NPC) e.getActor();
        window.npcAnimation(npc.getIndex(), npc.getAnimation());
    }

    @Subscribe
    public void onInteractingChanged(InteractingChanged e) {
        if (!(e.getSource() instanceof NPC)) return;
        if (!(e.getTarget() instanceof Player)) return;

        window.npcTargetsPlayer(client.getTickCount(), ((NPC) e.getSource()).getIndex());
    }

    @Subscribe
    public void onActorDeath(ActorDeath e) {
        final Actor actor = e.getActor();

        if (actor == client.getLocalPlayer()) {
            // This is how DeathDetector does it too. Player.isDead() is not usable here, and getHealthRatio()
            // is -1 unless a health bar is currently being transmitted for the actor.
            window.localPlayerDied();
            return;
        }

        if (!(actor instanceof NPC)) return;

        final NPC npc = (NPC) actor;
        window.npcDied(npc.getIndex(), npc.getId());
    }

    /**
     * Whether the local player currently has an attackable NPC targeted. Overlay and eat tracking only -- the
     * window deliberately does not depend on it, because losing your target is exactly what tick loss measures.
     */
    public boolean hasAttackableTarget() {
        final Player local = client.getLocalPlayer();
        if (local == null) return false;

        final Actor target = local.getInteracting();
        if (!(target instanceof NPC)) return false;

        final NPCComposition comp = ((NPC) target).getTransformedComposition();
        if (comp == null) return false;
        if (!comp.isInteractible()) return false;

        for (String action : comp.getActions()) {
            if ("Attack".equals(action)) return true;
        }
        return false;
    }
}

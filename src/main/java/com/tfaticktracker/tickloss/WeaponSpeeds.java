package com.tfaticktracker.tickloss;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.kit.KitType;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The local player's current attack speed in ticks.
 * <p>
 * Local player only, on purpose: this reads the attack style varp, which cannot be read for anyone else. That is the
 * whole reason other raiders' numbers have to arrive over the party rather than be observed from the scene.
 */
@Singleton
public class WeaponSpeeds {

    private final Client client;
    private final ItemManager itemManager;

    /** Set by clicking a spell on an NPC. Lasts one attack, exactly as in DamageHistory. */
    @Getter
    private boolean manualCasting;

    @Inject
    public WeaponSpeeds(Client client, ItemManager itemManager) {
        this.client = client;
        this.itemManager = itemManager;
    }

    public void startUp() {
        manualCasting = false;
    }

    /**
     * Whether an autocast spell is set. Read straight from the varbit rather than cached from VarbitChanged,
     * which only fires on a change and so would miss a spell set before the plugin started -- and caching it at
     * startUp is not an option, because a plugin's startUp runs on the AWT thread and reading a varbit there
     * trips "must be called on client thread".
     */
    public boolean isAutoCasting() {
        return client.getVarbitValue(VarbitID.AUTOCAST_SET) == 1;
    }

    /** Manual casting only lasts one attack per click. */
    void onAttackRegistered() {
        manualCasting = false;
    }

    private boolean isCastingStyle() {
        final AttackStyles.Style style = getStyle();
        return style == AttackStyles.Style.CASTING || style == AttackStyles.Style.DEFENSIVE_CASTING;
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked e) {
        // Any other click cancels a pending cast, same as DamageHistory
        manualCasting = "Cast".equals(e.getMenuOption())
                && e.getMenuAction() == MenuAction.WIDGET_TARGET_ON_NPC;
    }

    public int getWeaponId() {
        Player local = client.getLocalPlayer();
        if (local == null) return -1;

        PlayerComposition composition = local.getPlayerComposition();
        if (composition == null) return -1;

        return composition.getEquipmentId(KitType.WEAPON);
    }

    /**
     * @return the number of ticks the current attack puts us on cooldown for
     */
    public int compute() {
        return AttackSpeed.compute(facts());
    }

    /**
     * Everything the speed depends on, gathered from the live client.
     * <p>
     * Exposed so {@code EventLogger} can write the same facts into a log, which is what lets a recorded raid
     * be replayed through the real {@link AttackSpeed#compute} instead of a second table that drifts.
     */
    public AttackSpeed.Facts facts() {
        final int weaponId = getWeaponId();
        return new AttackSpeed.Facts()
                .weaponId(weaponId)
                .animation(getAnimation())
                .aspeed(baseSpeed(weaponId))
                .style(getStyle())
                .rapid(isRapid())
                .manualCasting(manualCasting)
                .autoCasting(isAutoCasting())
                .castingStyle(isCastingStyle());
    }

    private int baseSpeed(int weaponId) {
        if (weaponId <= 0) return AttackSpeed.UNARMED_SPEED;

        ItemEquipmentStats equipment = getEquipment(weaponId);
        if (equipment == null || equipment.getAspeed() <= 0) return AttackSpeed.UNARMED_SPEED;

        return equipment.getAspeed();
    }

    /** The current attack style, or null when the weapon's styles could not be resolved. */
    public AttackStyles.Style getStyle() {
        return AttackStyles.current(client);
    }

    public int getAttackStyleIndex() {
        return AttackStyles.styleIndex(client);
    }

    /** Whether the -1 tick Rapid adjustment is currently being applied. */
    public boolean isRapid() {
        return AttackStyles.isRapid(client);
    }

    private ItemEquipmentStats getEquipment(int weaponId) {
        ItemStats stats = itemManager.getItemStats(weaponId);
        return stats == null ? null : stats.getEquipment();
    }

    private int getAnimation() {
        Player local = client.getLocalPlayer();
        return local == null ? -1 : local.getAnimation();
    }
}

package com.tfaticktracker.tickloss;

import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.Set;

/**
 * Detects confirmed eats and how many ticks of attack delay each one costs.
 * <p>
 * The click alone is not enough: it can be swallowed by a server tick or an inventory shuffle. Following the pattern
 * in the Gauntlet Performance Tracker, the intent is pushed on click and only confirmed once the inventory slot
 * actually changes. Delays are attributed rather than erased, so "lost to eating" can be reported separately and the
 * headline number can exclude it.
 */
@Slf4j
@Singleton
public class EatTracker {

    static final int DEFAULT_FOOD_DELAY = 3;
    static final int FAST_FOOD_DELAY = 2;

    // A click either consumes the item within a tick or two, or never will
    private static final int PENDING_TIMEOUT_TICKS = 2;

    private static class PendingEat {
        final Item[] inventoryBefore;
        final int itemId;
        final int slot;
        final int clickTick;

        PendingEat(Item[] inventoryBefore, int itemId, int slot, int clickTick) {
            this.inventoryBefore = inventoryBefore;
            this.itemId = itemId;
            this.slot = slot;
            this.clickTick = clickTick;
        }
    }

    private final Client client;

    private final Deque<PendingEat> pending = new ArrayDeque<>();

    /**
     * Every food in the game that delays an attack by 2 ticks instead of the usual 3.
     * <p>
     * Taken from the wiki's fast foods table, the attack delay column, which is the one that matters here:
     * https://oldschool.runescape.wiki/w/Food/Fast_foods. Everything absent from this list is 3, and
     * nothing is 4 -- there is no slow food, which is why there is no third tier and no config field to
     * put one in.
     * <p>
     * The pizzas used to be in here and are not 2: they are an ordinary 3.
     * <p>
     * What a wrong entry costs is narrower than it looks. The headline lost figure is measured against the
     * WEAPON's cooldown and never moves, because {@code AttackTimeline.nextAttackTick} is deliberately not
     * pushed by food; so is efficiency, which is built from the same figure. What a delay decides is how
     * much of that gap is attributed to the mouthful in "lost to eating", which is the breakdown line, not
     * the verdict. A pizza at 2 moved one tick per slice out of the food's column and into your own.
     * <p>
     * Most of this is gnome food nobody brings to a raid. Being complete costs nothing and there is no
     * longer a config field to patch a gap with, so the list is the whole table rather than the likely
     * part of it.
     */
    private static final Set<Integer> FAST_FOOD = ImmutableSet.of(
            // Karambwan, in its ordinary, leagues and blighted forms
            ItemID.TBWT_COOKED_KARAMBWAN,
            ItemID.BR_TBWT_COOKED_KARAMBWAN,
            ItemID.BLIGHTED_KARAMBWAN,
            // The other two that turn up outside gnome cooking
            ItemID.HALIBUT,
            ItemID.VAMPYRIUM_MYSTERY_KEBAB,
            // Gauntlet combo food. Not the plain paddlefish, which is a normal 3.
            ItemID.GAUNTLET_COMBO_FOOD,
            ItemID.GAUNTLET_COMBO_FOOD_HM,
            // Toad's legs, and the gnome dishes made from them
            ItemID.TOADS_LEGS,
            ItemID.TANGLED_TOADS_LEGS,
            ItemID.PREMADE_TANGLED_TOADS_LEGS,
            // Crunchies
            ItemID.TOAD_CRUNCHIES,
            ItemID.SPICY_CRUNCHIES,
            ItemID.WORM_CRUNCHIES,
            ItemID.CHOCCHIP_CRUNCHIES,
            ItemID.PREMADE_TOAD_CRUNCHIES,
            ItemID.PREMADE_SPICY_CRUNCHIES,
            ItemID.PREMADE_WORM_CRUNCHIES,
            ItemID.PREMADE_CHOCCHIP_CRUNCHIES,
            // Battas
            ItemID.FRUIT_BATTA,
            ItemID.TOAD_BATTA,
            ItemID.WORM_BATTA,
            ItemID.VEGETABLE_BATTA,
            ItemID.CHEESE_TOM_BATTA,
            ItemID.PREMADE_FRUIT_BATTA,
            ItemID.PREMADE_TOAD_BATTA,
            ItemID.PREMADE_WORM_BATTA,
            ItemID.PREMADE_VEGETABLE_BATTA,
            ItemID.PREMADE_CHEESE_TOM_BATTA,
            // Gnomebowls
            ItemID.WORM_HOLE,
            ItemID.VEG_BALL,
            ItemID.CHOCOLATE_BOMB,
            ItemID.PREMADE_WORM_HOLE,
            ItemID.PREMADE_VEG_BALL,
            ItemID.PREMADE_CHOCOLATE_BOMB);

    /** Delay ticks from eats that have been confirmed but not yet charged against an attack gap. */
    @Getter
    private int unattributedDelayTicks;
    /** Tick of the most recently confirmed eat, for the debug overlay. */
    @Getter
    private int lastEatTick = -1;
    @Getter
    private int lastEatDelay;

    @Inject
    public EatTracker(Client client) {
        this.client = client;
    }

    public void reset() {
        pending.clear();
        unattributedDelayTicks = 0;
        lastEatTick = -1;
        lastEatDelay = 0;
    }

    /**
     * Consume the accrued eat delay, so a gap is only ever attributed to eating once.
     */
    int takeUnattributedDelay() {
        int delay = unattributedDelayTicks;
        unattributedDelayTicks = 0;
        return delay;
    }

    /**
     * Whether this menu option is a mouthful that costs attack ticks.
     * <p>
     * Food only. <b>Drinking does not delay an attack.</b> Brews, restores, potions and the rest are free,
     * confirmed by the player, who is the only one able to confirm in-game behaviour here. A "Drink" is
     * therefore not tracked at all, and that exclusion is load bearing rather than an oversight: across three
     * raids in debug-logs/ there are 1079 confirmed drinks against 230 eats, so charging them like food would
     * invent several times more cost than eating has ever accounted for.
     * <p>
     * Shared with {@code LogReplay} so the recorded-log path and the live path cannot drift apart on it. Two
     * copies of one rule is how this project has produced a wrong answer before.
     */
    static boolean isFoodOption(String option) {
        return option != null && option.toLowerCase(java.util.Locale.ROOT).startsWith("eat");
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked e) {
        String option = Text.removeTags(e.getMenuOption()).toLowerCase();
        if (!isFoodOption(option)) return;

        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null) return;

        int slot = e.getParam0();
        Item[] items = inventory.getItems();
        if (slot < 0 || slot >= items.length) return;

        pending.push(new PendingEat(items.clone(), items[slot].getId(), slot, client.getTickCount()));
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged e) {
        if (e.getContainerId() != InventoryID.INV) return;
        if (pending.isEmpty()) return;

        ItemContainer inventory = e.getItemContainer();
        Item[] now = inventory.getItems();
        final int now_ = client.getTickCount();
        final Set<Integer> confirmedSlots = new HashSet<>();

        while (!pending.isEmpty()) {
            PendingEat eat = pending.pop();
            if (eat.slot >= now.length || eat.slot >= eat.inventoryBefore.length) continue;

            // A click that never became an eat must not be confirmed by some later, unrelated inventory change --
            // and with a bow equipped the inventory changes on every shot as ammo is used
            if (now_ - eat.clickTick > PENDING_TIMEOUT_TICKS) continue;

            // Spam-clicking the same food pushes several intents; only the first one actually ate anything
            if (!confirmedSlots.add(eat.slot)) continue;

            // The slot changing is what confirms the item was actually consumed
            if (now[eat.slot].getId() == eat.inventoryBefore[eat.slot].getId()) continue;

            final int delay = delayForItem(eat.itemId);
            final int tick = client.getTickCount();

            // Combo eating sums: a karambwan plus a normal food on the same tick cost 5 delay ticks, not 3.
            // Measured in debug-logs/tfa-events-20261112-141632.log -- elder maul (6t) attacks at ticks 333 and
            // 344 around a combo eat at 336, an 11 tick gap. attacktimer accrues eat delays additively too.
            unattributedDelayTicks += delay;
            if (tick != lastEatTick) {
                lastEatTick = tick;
                lastEatDelay = delay;
            } else {
                lastEatDelay += delay;
            }
            log.debug("Tick loss: ate {} for {} delay ticks at tick {} (pending {})",
                    eat.itemId, delay, tick, unattributedDelayTicks);
        }
    }

    /**
     * How many ticks of attack delay this mouthful costs.
     * <p>
     * Three unless it is combo food, which is two. Shared with the log replay, which has the item ids from
     * the MENU lines, so the recorded path and the live path cannot disagree about what a food costs.
     */
    static int delayForItem(int itemId) {
        return FAST_FOOD.contains(itemId) ? FAST_FOOD_DELAY : DEFAULT_FOOD_DELAY;
    }

}

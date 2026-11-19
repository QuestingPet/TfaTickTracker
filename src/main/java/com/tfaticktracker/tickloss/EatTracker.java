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
     * Combo food: a 2 tick delay rather than the usual 3, so it can be eaten alongside normal food.
     * <p>
     * A fixed table rather than something a player types item ids into. Being missing from it costs one tick
     * of over-reported loss on that eat and nothing else, which is not worth a settings field, and an id
     * nobody can look up is not a setting anyone would ever fill in correctly.
     */
    private static final Set<Integer> FAST_FOOD = ImmutableSet.of(
            ItemID.TBWT_COOKED_KARAMBWAN,
            ItemID.BR_TBWT_COOKED_KARAMBWAN,
            ItemID.BLIGHTED_KARAMBWAN,
            ItemID.PLAIN_PIZZA,
            ItemID.HALF_PLAIN_PIZZA,
            ItemID.MEAT_PIZZA,
            ItemID.HALF_MEAT_PIZZA,
            ItemID.ANCHOVIE_PIZZA,
            ItemID.HALF_ANCHOVIE_PIZZA,
            ItemID.PINEAPPLE_PIZZA,
            ItemID.HALF_PINEAPPLE_PIZZA);

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

package com.tfaticktracker.tickloss;

import com.google.common.collect.ImmutableSet;
import net.runelite.api.gameval.AnimationID;

import java.util.Set;

/**
 * Animations that are known NOT to be an attack. No longer the decision; the known-innocent set.
 * <p>
 * {@link AttackAnimations} decides what an attack is. This list exists so that the things which obviously
 * are not one -- blocks, eating, dying, drinking, summoning a thrall -- are recognised as such rather than
 * coming out as "we have never heard of this", which would put an id on the overlay after every block you
 * ever make. Its job is now to keep the unrecognised report meaningful, not to steer the measurement.
 * <p>
 * It was the decision until the audit of 11-18 November. As a denylist it failed in the direction nobody
 * notices: anything not listed re-seeded the attack cooldown, so a missing entry HID loss and the number
 * came out flattering. The audit found two kinds of hole at once -- opening a chest mid-fight was being
 * counted as an attack, and attacktimer's own non-attack list contains 62 animations we had no entry for,
 * among them high alchemy, teleports, pickpocketing and vengeance, every one of which a raider can do
 * while fighting.
 * <p>
 * Keeping it has one more benefit: it cross-checks the allowlist. 24 of its entries are also marked
 * NON_ATTACK by attacktimer and NOT ONE of its entries is called an attack there, which is the strongest
 * statement available that the two lists built from different sources agree.
 * <p>
 * Scale of the original problem, measured from a real solo Archive Guardian attempt
 * (debug-logs/tfa-events-20261112-135412.log, ticks 513-750): 19 genuine attack animations against 31
 * non-attacks -- 13 HUMAN_UNARMEDBLOCK, 13 HUMAN_ELDER_MAUL_BLOCK and 5 HUMAN_EAT.
 * <p>
 * <b>How a gap in a list is found.</b> Ask, of every animation, how often it BEGAN on a tick we were being
 * hit: you only block because something hit you, so a block clusters near 100% while a real attack sits far
 * below. Measured across the recorded raids: scythe block 96%, shield defence 85%, unarmed block 89%,
 * against twisted bow 28%, scythe 33% and Ralos glaive 15%. HUMAN_SHIELD_DEFENCE, HUMAN_SHADE_DEFENCE and
 * PREPOT_DEVICE_DRINK_FULL were all found that way.
 * <p>
 * An earlier version of that check asked whether the animation ever DEALT damage, and failed completely: the
 * shield block scored 77%, because mid-fight there are always projectiles landing. Hitsplats say what reached
 * a target, never what you did on a given tick. If a list ever needs auditing again, measure "was I being
 * hit", not "did I deal damage".
 */
final class NonAttackAnimations {

    private static final Set<Integer> IDS = ImmutableSet.of(
            // Blocks and defence animations. The dominant false positive: a 237 tick Archive Guardian
            // fight in debug-logs/tfa-events-20261112-135412.log had 26 of these against 19 real attacks.
            AnimationID.HUMAN_2H_AXE_DEFEND,
            AnimationID.HUMAN_APE_DEFEND,
            AnimationID.HUMAN_AXE_BLOCK,
            AnimationID.HUMAN_AXE_DEF,
            AnimationID.HUMAN_BALL_DEFEND,
            AnimationID.HUMAN_BANNER_BLOCK,
            AnimationID.HUMAN_BANNER_LEAGUE03_BLOCK,
            AnimationID.HUMAN_BLUNT_BLOCK,
            AnimationID.HUMAN_BLUNT_DEF,
            AnimationID.HUMAN_BOXING_BLOCK,
            AnimationID.HUMAN_CARD_DEFEND,
            AnimationID.HUMAN_CHINCHOMPA_DEFEND,
            AnimationID.HUMAN_DDAGGER_BLOCK,
            AnimationID.HUMAN_DDAGGER_DEF,
            AnimationID.HUMAN_DHSWORD_BLOCK,
            AnimationID.HUMAN_DHSWORD_DEF,
            AnimationID.HUMAN_DINHS_BULWARK_BLOCK,
            AnimationID.HUMAN_DSPEAR_BLOCK,
            AnimationID.HUMAN_DSPEAR_DEF,
            AnimationID.HUMAN_ELDER_MAUL_BLOCK,
            AnimationID.HUMAN_FALCONER_DEFEND,
            AnimationID.HUMAN_FARMERSFORK_DEF,
            AnimationID.HUMAN_FUNGICIDE_PUMP_DEFEND,
            AnimationID.HUMAN_HALLOWFELL_BLOCK,
            AnimationID.HUMAN_HALLOWFELL_DEFEND,
            AnimationID.HUMAN_RUBBER_CHICKEN_BLOCK,
            AnimationID.HUMAN_SCYTHE_BLOCK,
            // Spelled DEFENCE rather than BLOCK or DEFEND, so the generated list missed both. Shield blocks
            // alone accounted for 217 of the "early attacks" across debug-logs/ -- each one re-seeding the
            // cooldown off a tick spent defending, which hides real loss.
            AnimationID.HUMAN_SHIELD_DEFENCE,
            AnimationID.HUMAN_SHADE_DEFENCE,
            // A drink, not an attack. Found the same way: it only ever plays on a tick you are being hit.
            AnimationID.PREPOT_DEVICE_DRINK_FULL,
            AnimationID.HUMAN_SCYTHE_DEF,
            AnimationID.HUMAN_SKI_DEFEND,
            AnimationID.HUMAN_SPEAR_BLOCK,
            AnimationID.HUMAN_SPEAR_BLOCK_LEATHER_HIT_3,
            AnimationID.HUMAN_SPEAR_DEF,
            AnimationID.HUMAN_STAFFORB_BLOCK,
            AnimationID.HUMAN_STAFFORB_DEF,
            AnimationID.HUMAN_STAFF_BLOCK,
            AnimationID.HUMAN_STAFF_BLOCK_LEATHER_HIT_3,
            AnimationID.HUMAN_STAFF_DEF,
            AnimationID.HUMAN_SWORD_BLOCK,
            AnimationID.HUMAN_SWORD_DEF,
            AnimationID.HUMAN_SWORD_DEF_LEATHER_HIT_3,
            AnimationID.HUMAN_SWORD_DEF_THEN_DIE,
            AnimationID.HUMAN_SWORD_DEF_THEN_DIE_QUICK,
            AnimationID.HUMAN_SWORD_SLASH_THEN_DEF_THEN_DIE,
            AnimationID.HUMAN_SWORD_SLASH_THEN_DEF_THEN_DIE_QUICK,
            AnimationID.HUMAN_TRANS_AXE_DEF,
            AnimationID.HUMAN_TROPHY_LEAGUE03_BLOCK,
            AnimationID.HUMAN_UNARMEDBLOCK,
            AnimationID.HUMAN_UNARMEDBLOCK_LEATHER_HIT_3,
            AnimationID.HUMAN_UNARMED_DEF,
            AnimationID.HUMAN_UNARMED_DEF_LONG,
            AnimationID.HUMAN_WEAPONS_CRIMSON_KISTEN_DEF,
            AnimationID.HUMAN_WEAPONS_HALLOWED_FLAIL01_DEFEND,
            AnimationID.HUMAN_ZAMORAKSPEAR_BLOCK,
            // Deaths
            AnimationID.HUMAN_BECOME_SKELETON_DEATH,
            AnimationID.HUMAN_DEATH,
            AnimationID.HUMAN_DEATH_BACKWARDS,
            AnimationID.HUMAN_DEATH_FADE,
            AnimationID.HUMAN_DEATH_IDLE,
            AnimationID.HUMAN_DEATH_LOOP,
            AnimationID.HUMAN_DEATH_REVERSE,
            AnimationID.HUMAN_DOOM_SCORPION_01_PLAYER_DEATH_01,
            AnimationID.HUMAN_DOOM_SCORPION_01_PLAYER_DEATH_01_FAST,
            AnimationID.HUMAN_REAL_SWIMMING_DEATH,
            AnimationID.HUMAN_TRANSDEATH,
            // Consumables. Eating replaces the idle animation, so it reads as an attack without this.
            AnimationID.HUMAN_DRINK_BLUE_RUM,
            AnimationID.HUMAN_DRINK_CHARTING_BOTTLE,
            AnimationID.HUMAN_DRINK_FROM_TEACUP_CHINA,
            AnimationID.HUMAN_DRINK_FROM_TEACUP_CLAY,
            AnimationID.HUMAN_DRINK_FROM_TEACUP_GILTCHINA,
            AnimationID.HUMAN_DRINK_FROM_VIAL_CADAVA,
            AnimationID.HUMAN_DRINK_FROM_VIAL_CADAVA_SPOTANIM,
            AnimationID.HUMAN_DRINK_RED_RUM,
            AnimationID.HUMAN_DRINK_RUM,
            AnimationID.HUMAN_EAT,
            AnimationID.HUMAN_EAT_BANANA,
            AnimationID.HUMAN_EAT_WITHSOUND,
            // Summoning a thrall. attacktimer treats this as a non-attack for the same reason, and it is the
            // classic case of an attack animation being masked -- seen twice in the Bork fight in
            // debug-logs/tfa-events-20261112-161404.log
            AnimationID.HUMAN_SPELLCAST_RESURRECT,
            // Imbuing yourself, not an attack on anything. Seen between purging staff casts at Xarth in
            // debug-logs/tfa-events-20261112-170638.log, where it was registering as an attack.
            AnimationID.HUMAN_CAST_SELFIMBUE,
            // Freezing a Xarth corpse costs no tick at all, so it must not re-seed the attack cooldown. Across
            // every log, 309 grasp-then-weapon transitions never went below 5 ticks -- the weapon's own speed
            // running underneath -- while 291 grasp-then-grasp transitions bottom out at 2, i.e. click speed.
            // NOTE: this is the freeze being free on a corpse, not the spell being free in general.
            // A dodge, reused by Ocellus P3
            AnimationID.HUMAN_YAMA_SHORTJUMP01,
            // Raid-specific defence animations
            AnimationID.TFA_HUMAN_OBLIGATOR_DEF,
            AnimationID.TFA_HUMAN_OBLIGATOR_DEF_FEMALE,
            AnimationID.TFA_HUMAN_ZORYA_TOME_DEFEND,
            AnimationID.TFA_HUMAN_ZORYA_TOME_DEFEND_FEMALE,

            // Greater Corruption, which is a buff rather than an attack -- the same family as a thrall or
            // Mark of Darkness, confirmed by the player, who is the only one able to confirm in-game
            // behaviour here. The awkward part is that the id is shared: HUMAN_SPELLCAST_DEMONBANE is also
            // the Demonbane spells, which ARE attacks. One animation, two meanings, and no way to tell them
            // apart from the animation alone. It sits here because this plugin only ever measures inside
            // The Fractured Archives, where the cast being made is the corruption; in a demon-slaying
            // context this entry would be wrong. attacktimer has the same collision and says so in its own
            // comment on the entry.
            AnimationID.HUMAN_SPELLCAST_DEMONBANE,

            // Blocks and defends attacktimer knew about and we did not
            AnimationID.BARROW_GUTHAN_DEFEND,
            AnimationID.BRAIN_PLAYER_ANCHOR_DEFEND,
            AnimationID.DH_SWORD_UPDATE_DEFEND,
            AnimationID.IVANDIS_FLAIL_DEFEND,
            AnimationID.SLAYER_ABYSSAL_WHIP_DEFEND,
            AnimationID.SLAYER_GRANITE_MAUL_DEFEND,
            AnimationID.WARGUILD_PARRY_DEFEND,
            AnimationID.WILD_CAVE_CHAINMACE_DEFEND,
            // Spells that are not attacks: buffs, vengeance, spellbook swaps, utility casts
            AnimationID.ARCEUUS_NECROMANCY_PLAYERANIM,
            AnimationID.DREAM_PLAYER_HUMIDIFY_SPELL,
            AnimationID.DREAM_PLAYER_MONSTEREXAM_STATSPY,
            AnimationID.DREAM_PLAYER_SPELLBOOK_SWAP,
            AnimationID.HUMAN_CAST_OFFERING,
            AnimationID.HUMAN_CHINCHOMPA_ATTACK,
            AnimationID.HUMAN_KILLERWATT_ELECTRICSHOCK,
            AnimationID.HUMAN_SPELLCAST_SHADOWVEIL,
            AnimationID.LUNAR_HUMAN_MAGIC_GEOMANCY,
            AnimationID.LUNAR_HUMAN_MAGIC_SUMMON2,
            AnimationID.QUEST_LUNAR_PUSHING_MAGIC_ANIMATION,
            AnimationID.QUEST_LUNAR_SPELL_CAST_SPELL_ON_GROUP,
            AnimationID.VENGEANCE_SPELL_ANIM_NOSTALLING,
            AnimationID.VENGEANCE_SPELL_ANIM_STALLING,
            // Teleports
            AnimationID.HUMAN_TELEPORT_OTHER_IMPACT,
            AnimationID.TELEPORT_NARDAH_HUMAN,
            // Skilling and other things you can do with a target selected
            AnimationID.FOSSIL_LOC_CLAM_IDLE_SHUT,
            AnimationID.HUMAN_CASTBONESTOBANANAS,
            AnimationID.HUMAN_CASTHIGHLVLALCHEMY,
            AnimationID.HUMAN_CASTLOWLVLALCHEMY,
            AnimationID.HUMAN_CRAFTING_SPIKEDVAMBRACES,
            AnimationID.HUMAN_FLETCHING,
            AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_ADAMANT,
            AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_AMETHYST,
            AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_BRONZE,
            AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_DRAGON,
            AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_IRON,
            AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_MITHRIL,
            AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_RUNE,
            AnimationID.HUMAN_FLETCHING_ADD_DART_FEATHERS_STEEL,
            AnimationID.HUMAN_FLETCHING_HUNTINGBOLTS,
            AnimationID.HUMAN_HUNTING_DISMANTLE_NET,
            AnimationID.HUMAN_LAYTRAP,
            AnimationID.HUMAN_PICKPOCKET,
            AnimationID.HUMAN_SALAMANDER_TAR_GRIND,
            AnimationID.HUNTING_SETTING_TRAP_SMALL,
            AnimationID.SLAYER_SALT_SPRINKLE,
            // Sailing, which cannot happen in a raid
            AnimationID.HUMAN_SAILING_ALPHA_HELM_RAFT01_ACTIVE01,
            AnimationID.HUMAN_SAILING_ALPHA_HELM_RAFT01_ACTIVE01_LOOP,
            AnimationID.HUMAN_SAILING_ALPHA_HELM_RAFT01_TRIM_SAILS01,
            AnimationID.HUMAN_SAILING_ALPHA_HELM_RAFT01_TRIM_SAILS01_LOOP,
            AnimationID.HUMAN_SAILING_ALPHA_HELM_RAFT01_TRIM_SAILS02,
            AnimationID.HUMAN_SAILING_ALPHA_HELM_SMALL01_ACTIVE01,
            AnimationID.HUMAN_SAILING_ALPHA_HELM_SMALL01_ACTIVE01_LOOP,
            AnimationID.HUMAN_SAILING_ALPHA_HELM_SMALL01_TRIM_SAILS01,
            AnimationID.HUMAN_SAILING_ALPHA_HELM_SMALL01_TRIM_SAILS01_LOOP,
            AnimationID.HUMAN_SAILING_ALPHA_HELM_SMALL01_TRIM_SAILS02,
            AnimationID.HUMAN_SAILING_CANNON01_SMALL01_ENGAGE01,
            AnimationID.HUMAN_SAILING_CANNON01_SMALL01_IDLE01,
            AnimationID.HUMAN_SAILING_HELM_3X8_ACTIVE01,
            AnimationID.HUMAN_SAILING_HELM_3X8_ACTIVE01_LOOP,
            AnimationID.HUMAN_SAILING_HELM_3X8_TRIM_SAILS01,
            AnimationID.HUMAN_SAILING_HELM_3X8_TRIM_SAILS01_LOOP,
            AnimationID.HUMAN_SAILING_HELM_3X8_TRIM_SAILS02);

    private NonAttackAnimations() {
    }

    /**
     * Whether this animation means you were doing something OTHER than attacking -- blocking, dying, eating.
     * <p>
     * Combat spells are deliberately NOT in here, freezing a corpse included. Such a cast ignores the cooldown
     * you are on but sets its own, which is exactly what the ordinary attack path does: off cooldown it is a
     * normal attack, on cooldown it is a reset, and either way it commits the cast's own speed. Keeping them out
     * means any freeze spell works without being named.
     */
    static boolean isNonAttack(int animationId) {
        return IDS.contains(animationId);
    }

}

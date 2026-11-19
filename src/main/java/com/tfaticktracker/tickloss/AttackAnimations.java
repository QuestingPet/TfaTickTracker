package com.tfaticktracker.tickloss;

/*
 * The animation ids in ATTACKS below are taken from the Attack Timer Metronome plugin's AnimationData,
 * which is BSD 2-Clause licensed and whose notice is reproduced here as that licence requires:
 *
 * Copyright (c) 2021, Matsyir <https://github.com/matsyir>
 * Copyright (c) 2020, Mazhar <https://twitter.com/maz_rs>
 * Copyright (c) 2024-2026, Lexer747 <https://github.com/Lexer747>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *    following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 *    the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

import com.google.common.collect.ImmutableSet;
import net.runelite.api.gameval.AnimationID;

import java.util.Set;

/**
 * Animations known to BE an attack. A label, not the decision.
 * <p>
 * {@link NonAttackAnimations} decides, and its default is that an animation nobody has listed is an attack.
 * This list exists so that a confident attack can be told from a guess: an animation in neither file is one
 * we are counting on that default alone, and {@link UnknownAnimations} watches those for signs the default
 * was wrong. Nothing here changes a measurement, which is exactly why it is safe to grow or trim.
 * <p>
 * It was briefly the decision, as an allowlist, and that was a mistake worth recording. An allowlist throws
 * away two things a denylist handles for free: a weapon nobody has listed, and an attack whose animation was
 * overridden by something else that tick. Over the 44 audited raids the denylist made no measurement errors
 * at all, while an allowlist would have discarded 226 Crimson Kisten attacks.
 * <p>
 * It also turned out that attacktimer, which this list comes from, does not use its copy as a detector
 * either. It decides an attack happened from the player interacting with a target, vetoed by a BLOCKLIST,
 * and uses these entries for the combat skill and the speed, falling back to the weapon's own item stats
 * when it has never seen the animation. Its list being incomplete costs it nothing, which is why an
 * incomplete list looked safer than it was.
 * <p>
 * <b>Why these ids.</b> Audited across 44 recorded raids from 11-18 November, every animation the local
 * player showed while targeting an NPC in the raid: 22 distinct ones, and not one of them is called a
 * non-attack by us and an attack by attacktimer.
 */
final class AttackAnimations {

    private static final Set<Integer> ATTACKS = ImmutableSet.of(
            // Melee
            AnimationID.ABYSSAL_BLUDGEON_CRUSH,
            AnimationID.ABYSSAL_BLUDGEON_SPECIAL_ATTACK,
            AnimationID.ABYSSAL_DAGGER_LUNGE,
            AnimationID.AGS_SPECIAL_ORNATE_PLAYER,
            AnimationID.AGS_SPECIAL_PLAYER,
            AnimationID.ANCIENT_AXE_CRUSH,
            AnimationID.ANCIENT_AXE_SPECIAL,
            AnimationID.BARROWS_QUARTERSTAFF_ATTACK,
            AnimationID.BARROWS_WAR_SPEAR_CRUSH,
            AnimationID.BARROWS_WAR_SPEAR_SLASH,
            AnimationID.BARROWS_WAR_SPEAR_STAB,
            AnimationID.BARROW_DHAROK_CRUSH,
            AnimationID.BARROW_DHAROK_SLASH,
            AnimationID.BARROW_GUTHAN_CRUSH,
            AnimationID.BARROW_TORAG_CRUSH,
            AnimationID.BATTLEAXE_CRUSH,
            AnimationID.BGS_SPECIAL_ORNATE_PLAYER,
            AnimationID.BGS_SPECIAL_PLAYER,
            AnimationID.BRAIN_PLAYER_ANCHOR_ATTACK,
            AnimationID.BRAIN_PLAYER_ANCHOR_SPECIAL_ATTACK,
            AnimationID.CLEAVE,
            AnimationID.DARK_SPEC_PLAYER,
            AnimationID.DH_SWORD_UPDATE_BLOCK,
            AnimationID.DH_SWORD_UPDATE_SLASH,
            AnimationID.DH_SWORD_UPDATE_SMASH,
            AnimationID.DRAGON_HALBERD_SPECIAL_ATTACK,
            AnimationID.DRAGON_PICKAXE_ANIM,
            AnimationID.DRAGON_TWO_HANDED_SWORD,
            AnimationID.DRAGON_WARHAMMER_SA_PLAYER,
            AnimationID.DTTD_PLAYER_STAB_BONE_DAGGER,
            AnimationID.FORESTRY_2H_AXE_ATTACK,
            AnimationID.GHRAZI_RAPIER_ATTACK,
            AnimationID.GODWARS_GODSWORD_ZAMORAK_PLAYER,
            AnimationID.HUMAN_ATTACK_SALAMANDER,
            AnimationID.HUMAN_AXE_CHOP,
            AnimationID.HUMAN_AXE_HACK,
            AnimationID.HUMAN_BLUNT_POUND,
            AnimationID.HUMAN_BLUNT_SPIKE,
            AnimationID.HUMAN_DDAGGER_LUNGE,
            AnimationID.HUMAN_DHSWORD_CHOP,
            AnimationID.HUMAN_DHSWORD_SLASH,
            AnimationID.HUMAN_DHUNTER_LANCE_ATTACK,
            AnimationID.HUMAN_DHUNTER_LANCE_CRUSH,
            AnimationID.HUMAN_DHUNTER_LANCE_SLASH,
            AnimationID.HUMAN_DINHS_BULWARK_BASH,
            AnimationID.HUMAN_DRAGON_CLAWS_SPEC,
            AnimationID.HUMAN_DRAGON_SWORD_SPEC,
            AnimationID.HUMAN_DSPEAR_STAB,
            AnimationID.HUMAN_ELDER_MAUL_ATTACK,
            AnimationID.HUMAN_ELDER_MAUL_SPEC,
            AnimationID.HUMAN_HALLOWFELL_SLASH,
            AnimationID.HUMAN_INQUISITORS_MACE_CRUSH,
            AnimationID.HUMAN_OSMUMTENS_FANG,
            AnimationID.HUMAN_SCYTHE_SWEEP,
            AnimationID.HUMAN_SPEAR_LUNGE,
            AnimationID.HUMAN_SPEAR_SPIKE,
            AnimationID.HUMAN_SPECIAL02_URSINE,
            AnimationID.HUMAN_STAFFORB_PUMMEL,
            AnimationID.HUMAN_STAFF_PUMMEL,
            AnimationID.HUMAN_SWORD_SLASH,
            AnimationID.HUMAN_SWORD_STAB,
            AnimationID.HUMAN_UNARMEDKICK,
            AnimationID.HUMAN_UNARMEDPUNCH,
            AnimationID.HUMAN_WEAPONS_HALLOWED_FLAIL01_ATTACK01,
            AnimationID.HUMAN_ZAMORAKSPEAR_LUNGE,
            AnimationID.HUMAN_ZAMORAKSPEAR_STAB,
            AnimationID.IMPALE,
            AnimationID.IVANDIS_FLAIL_ATTACK,
            AnimationID.NGS_SPECIAL_PLAYER,
            AnimationID.OLAF2_BRINE_SABRE_SPECIAL,
            AnimationID.PMOON_MACUAHUITL_CRUSH,
            AnimationID.PUNCTURE,
            AnimationID.SARADOMIN_SWORD_SPECIAL_PLAYER,
            AnimationID.SCYTHE_OF_VITUR_ATTACK,
            AnimationID.SGS_SPECIAL_ORNATE_PLAYER,
            AnimationID.SGS_SPECIAL_PLAYER,
            AnimationID.SHATTER,
            AnimationID.SLAYER_ABYSSAL_WHIP_ATTACK,
            AnimationID.SLAYER_GRANITE_MAUL_ATTACK,
            AnimationID.SLAYER_GRANITE_MAUL_SPECIAL_ATTACK,
            AnimationID.SLICE_PLAYER_MACE_SPECIAL_ATTACK,
            AnimationID.SP_ATTACK_DRAGON_SCIMITAR,
            AnimationID.TECPATL_STAB,
            AnimationID.TFA_HUMAN_OBLIGATOR_ATTACK,
            AnimationID.TFA_HUMAN_OBLIGATOR_ATTACK_FEMALE,
            AnimationID.TFA_HUMAN_OBLIGATOR_MULTIHIT,
            AnimationID.TFA_HUMAN_OBLIGATOR_MULTIHIT_FEMALE,
            AnimationID.TOA_KERIS_PARTISAN_SPECIAL01,
            AnimationID.WILD_CAVE_CHAINMACE_CRUSH,
            AnimationID.ZGS_SPECIAL_ORNATE_PLAYER,
            AnimationID.ZGS_SPECIAL_PLAYER,
            // Ranged
            AnimationID.BALLISTA_ATTACK,
            AnimationID.BALLISTA_ATTACK_PVN,
            AnimationID.BALLISTA_SPECIAL_ATTACK_PVN,
            AnimationID.BARROWS_REPEATING_CROSSBOW_FIRE,
            AnimationID.CHAINHIT,
            AnimationID.DTTD_PLAYER_FIRE_BONE_CROSSBOW_PVN,
            AnimationID.HUMAN_ATLATL_ATTACK_RANGED_01,
            AnimationID.HUMAN_BOW,
            AnimationID.HUMAN_CHINCHOMPA_ATTACK_PVN,
            AnimationID.HUMAN_DRAGON_KNIFE,
            AnimationID.HUMAN_DRAGON_KNIFE_P,
            AnimationID.HUMAN_DRAGON_TAXE_SPEC,
            AnimationID.HUMAN_DRAGON_TKNIVES_SPEC,
            AnimationID.HUMAN_DRAGON_TKNIVES_SPEC_POISON,
            AnimationID.HUMAN_GLAIVE_RALOS01_CHARGED_SPECIAL,
            AnimationID.HUMAN_GLAIVE_RALOS01_CHARGED_THROW,
            AnimationID.HUMAN_SPECIAL01_WEBWEAVER,
            AnimationID.HUMAN_SPECIAL_ATLATL_01,
            AnimationID.HUMAN_STAKE2,
            AnimationID.HUMAN_STAKE2_PVN,
            AnimationID.HUMAN_WEAPON_BOW_SCORCHED_01_SPEC,
            AnimationID.HUMAN_WEAPON_BOW_VENATOR01_SHOOT,
            AnimationID.HUMAN_XBOWS_LEAGUE03_ATTACK_PVN,
            AnimationID.II_HUMAN_DART_THROW_PVN,
            AnimationID.SNAKEBOSS_BLOWPIPE_ATTACK,
            AnimationID.SNAKEBOSS_BLOWPIPE_ATTACK_ORNAMENT,
            AnimationID.SNAPSHOT,
            AnimationID.TFA_HUMAN_ASCENSION_CROSSBOW_FIRE_LEFT,
            AnimationID.TFA_HUMAN_ASCENSION_CROSSBOW_FIRE_LEFT_PVN,
            AnimationID.TFA_HUMAN_ASCENSION_CROSSBOW_FIRE_RIGHT,
            AnimationID.TFA_HUMAN_ASCENSION_CROSSBOW_FIRE_RIGHT_PVN,
            AnimationID.XBOWS_HUMAN_FIRE_AND_RELOAD,
            AnimationID.XBOWS_HUMAN_FIRE_AND_RELOAD_PVN,
            AnimationID.ZCB_ATTACK,
            AnimationID.ZCB_ATTACK_PVN,
            // Magic, combat casts only -- alchemy, teleports and vengeance are not here
            AnimationID.HUMAN_CASTCONFUSE,
            AnimationID.HUMAN_CASTCONFUSE_STAFF,
            AnimationID.HUMAN_CASTCONFUSE_STAFF_WALKMERGE,
            AnimationID.HUMAN_CASTCONFUSE_WALKMERGE,
            AnimationID.HUMAN_CASTCRUMBLEUNDEAD,
            AnimationID.HUMAN_CASTCRUMBLEUNDEAD_STAFF,
            AnimationID.HUMAN_CASTCURSE,
            AnimationID.HUMAN_CASTCURSE_STAFF,
            AnimationID.HUMAN_CASTCURSE_STAFF_WALKMERGE,
            AnimationID.HUMAN_CASTCURSE_WALKMERGE,
            AnimationID.HUMAN_CASTENFEEBLE_STAFF,
            AnimationID.HUMAN_CASTENTANGLE,
            AnimationID.HUMAN_CASTENTANGLE_STAFF,
            AnimationID.HUMAN_CASTIBANBLAST,
            AnimationID.HUMAN_CASTING,
            AnimationID.HUMAN_CASTSTRIKE,
            AnimationID.HUMAN_CASTSTRIKE_STAFF,
            AnimationID.HUMAN_CASTSTRIKE_STAFF_WALKMERGE,
            AnimationID.HUMAN_CASTSTRIKE_WALKMERGE,
            AnimationID.HUMAN_CASTSTUN_STAFF,
            AnimationID.HUMAN_CASTWAVE,
            AnimationID.HUMAN_CASTWAVE_STAFF,
            AnimationID.HUMAN_CASTWAVE_STAFF_WALKMERGE,
            AnimationID.HUMAN_CASTWAVE_WALKMERGE,
            AnimationID.HUMAN_CASTWEAKEN,
            AnimationID.HUMAN_CASTWEAKEN_STAFF,
            AnimationID.HUMAN_CASTWEAKEN_STAFF_WALKMERGE,
            AnimationID.HUMAN_CASTWEAKEN_WALKMERGE,
            AnimationID.HUMAN_CAST_SURGE,
            AnimationID.HUMAN_CAST_SURGE_FAST,
            AnimationID.HUMAN_CAST_SURGE_WALKMERGE,
            AnimationID.HUMAN_EYE_OF_AYAK_NORMAL,
            AnimationID.HUMAN_EYE_OF_AYAK_SPECIAL,
            AnimationID.HUMAN_SPECIAL_ACCURSED,
            // One id, two spells. This is the Demonbane casts, which ARE attacks and can be cast in the
            // raid, and it is also Greater Corruption, which is a buff in the same family as a thrall. The
            // animation cannot tell them apart. It is listed as an attack because the attack is the case
            // that can be got wrong: the denylist counts an unlisted animation as an attack anyway, so the
            // only thing this entry decides is whether it is a confident attack or a watched guess, and a
            // Demonbane cast is not a guess. The corruption is rare enough to live with.
            AnimationID.HUMAN_SPELLCAST_DEMONBANE,
            AnimationID.HUMAN_SPELLCAST_GRASP,
            AnimationID.NIGHTMARE_STAFF_SPECIAL,
            AnimationID.POG_WARPED_SCEPTRE_ATTACK,
            AnimationID.SLAYER_MAGICDART_CAST,
            AnimationID.TFA_HUMAN_ZORYA_TOME_ATTACK,
            AnimationID.TFA_HUMAN_ZORYA_TOME_ATTACK_FEMALE,
            AnimationID.TFA_HUMAN_ZORYA_TOME_SPECIAL_ATTACK,
            AnimationID.TFA_HUMAN_ZORYA_TOME_SPECIAL_ATTACK_FEMALE,
            AnimationID.TOA_SOT_CAST_B,
            AnimationID.ZAROS_CASTING,
            AnimationID.ZAROS_CASTING_WALKMERGE,
            AnimationID.ZAROS_VERTICAL_CASTING,
            AnimationID.ZAROS_VERTICAL_CASTING_WALKMERGE,
            // Found by the audit, in neither list. The Crimson Kisten appears in 9 of the 44 raids, and
            // attacktimer's data has its block but neither of its attacks -- which costs attacktimer
            // nothing, because that data is its speed lookup rather than its detector, and it falls back to
            // the weapon's own stats. Here the list IS the detector, so without these two a Kisten user
            // would be told they lost every tick they attacked in.
            AnimationID.HUMAN_WEAPONS_CRIMSON_KISTEN_ATTACK,
            AnimationID.HUMAN_WEAPONS_CRIMSON_KISTEN_SPECIAL);

    private AttackAnimations() {
    }

    /** Whether this animation is a known attack. */
    static boolean isAttack(int animationId) {
        return ATTACKS.contains(animationId);
    }

    /**
     * Whether we have never heard of this animation at all.
     * <p>
     * Neither an attack nor a known non-attack. Treated as not attacking, and reported, because the honest
     * answer to "was that an attack" here is that we do not know, and a measurement that guesses silently
     * is worse than one that says so.
     */
    static boolean isUnrecognised(int animationId) {
        return animationId != -1 && !isAttack(animationId) && !NonAttackAnimations.isNonAttack(animationId);
    }
}

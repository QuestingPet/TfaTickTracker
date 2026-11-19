/*
 * Copyright (c) 2017, honeyhoney <https://github.com/honeyhoney>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.tfaticktracker.tickloss;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * The local player's current attack style.
 * <p>
 * Adapted from RuneLite's AttackStylesPlugin via Customizable XP Drops. RuneLite's own AttackStyle enum and its
 * weapon-type lookup are both package private, so neither can be imported; this follows CXD's approach of reading
 * the style list out of the game's own cache (mirroring script4525) rather than keeping a hardcoded weapon-category
 * table that goes stale when Jagex adds a category.
 * <p>
 * On a ranged weapon BOTH Accurate and Rapid resolve to {@link Style#RANGING} -- they train Ranged only, so
 * neither can map to {@link Style#ACCURATE}, which carries Skill.ATTACK. Only Rapid is actually a tick faster, so
 * the style alone is not enough and {@link #isRapid} also checks the style index. DamageHistory's comment about
 * this is correct; an earlier version of this class got it wrong and treated Accurate as Rapid.
 */
@Slf4j
final class AttackStyles {

    enum Style {
        ACCURATE,
        AGGRESSIVE,
        DEFENSIVE,
        CONTROLLED,
        /** The game calls this one "Rapid" in the UI -- one tick faster. */
        RANGING,
        LONGRANGE,
        CASTING,
        DEFENSIVE_CASTING,
        OTHER,
    }

    private static final int WEAPON_STYLES = 3908; // EnumID.WEAPON_STYLES
    private static final int ATTACK_STYLE_NAME = 1407; // ParamID.ATTACK_STYLE_NAME

    // Weapon categories the WEAPON_STYLES enum has no entry for
    private static final int WEAPON_TYPE_BLUE_MOON_SPEAR = 22;
    private static final int WEAPON_TYPE_POWERED_STAFF = 24;
    private static final int WEAPON_TYPE_PARTISAN = 30;

    // Style index 4 is casting, and defensive casting is index 4 plus the casting mode
    private static final int CASTING_STYLE_INDEX = 4;

    private AttackStyles() {
    }

    // On a ranged weapon the three options are Accurate, Rapid, Longrange, in that order
    private static final int RAPID_STYLE_INDEX = 1;

    /**
     * @return the local player's current attack style, or null if the weapon's styles could not be resolved
     */
    static Style current(Client client) {
        final Style[] styles = forWeaponType(client, client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY));
        final int styleIndex = styleIndex(client);
        if (styleIndex < 0 || styleIndex >= styles.length) return null;

        return styles[styleIndex];
    }

    /**
     * Whether the local player is on Rapid, which is one tick faster. Accurate and Longrange are not.
     * <p>
     * Needs the index as well as the style: a ranged weapon reports {@link Style#RANGING} for both Accurate
     * (index 0) and Rapid (index 1). Requiring both of those slots to be RANGING is what distinguishes a real
     * ranged weapon from a hybrid that merely happens to have a ranged option at index 1.
     */
    static boolean isRapid(Client client) {
        if (styleIndex(client) != RAPID_STYLE_INDEX) return false;

        final Style[] styles = forWeaponType(client, client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY));
        return styles.length > RAPID_STYLE_INDEX
                && styles[0] == Style.RANGING
                && styles[RAPID_STYLE_INDEX] == Style.RANGING;
    }

    static int styleIndex(Client client) {
        int styleIndex = client.getVarpValue(VarPlayerID.COM_MODE);
        if (styleIndex == CASTING_STYLE_INDEX) {
            // Staves have five styles but only 0-4 are used, with an extra casting mode for defensive casting
            styleIndex += client.getVarbitValue(VarbitID.AUTOCAST_DEFMODE);
        }
        return styleIndex;
    }

    /**
     * The styles available for a weapon category, read from the game's cache. Mirrors script4525.
     */
    private static Style[] forWeaponType(Client client, int weaponType) {
        final int weaponStyleEnum = client.getEnum(WEAPON_STYLES).getIntValue(weaponType);
        if (weaponStyleEnum == -1) {
            switch (weaponType) {
                case WEAPON_TYPE_BLUE_MOON_SPEAR:
                    return new Style[]{Style.ACCURATE, Style.AGGRESSIVE, null, Style.DEFENSIVE,
                            Style.CASTING, Style.DEFENSIVE_CASTING};
                case WEAPON_TYPE_PARTISAN:
                    return new Style[]{Style.ACCURATE, Style.AGGRESSIVE, Style.AGGRESSIVE, Style.DEFENSIVE};
                default:
                    return new Style[0];
            }
        }

        final int[] structIds = client.getEnum(weaponStyleEnum).getIntVals();
        final Style[] styles = new Style[structIds.length];
        int i = 0;
        for (int structId : structIds) {
            final StructComposition struct = client.getStructComposition(structId);
            final String name = struct.getStringValue(ATTACK_STYLE_NAME);

            Style style = parse(name);
            if (style == null || style == Style.OTHER) {
                // "Other" means no style in this slot
                i++;
                continue;
            }

            // "Defensive" covers both Defensive and Defensive Casting
            if (i == 5 && style == Style.DEFENSIVE) {
                style = Style.DEFENSIVE_CASTING;
            }
            if (i == 3 && style == Style.DEFENSIVE && weaponType == WEAPON_TYPE_POWERED_STAFF) {
                style = Style.DEFENSIVE_CASTING;
            }

            styles[i++] = style;
        }
        return styles;
    }

    private static Style parse(String name) {
        if (name == null) return null;
        try {
            return Style.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.debug("Tick loss: unknown attack style name '{}'", name);
            return null;
        }
    }
}

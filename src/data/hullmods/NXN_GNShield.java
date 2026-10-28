/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fs.starfarer.api.Global
 *  com.fs.starfarer.api.combat.BaseHullMod
 *  com.fs.starfarer.api.combat.ShipAPI
 */
package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import data.hullmods.NXN_GNShield_Script;
import data.scripts.weapons.render.NXN_GNShieldRendering_Shader;

public class NXN_GNShield
extends BaseHullMod {
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (!ship.hasListenerOfClass(NXN_GNShield_Script.class)) {
            ship.addListener(new NXN_GNShield_Script(ship));
        }
    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!Global.getCombatEngine().getCustomData().containsKey("NXN_GNShieldRendering_Shader")) {
            NXN_GNShieldRendering_Shader renderer = NXN_GNShieldRendering_Shader.getInstance();
            if (renderer.isShaderInitialized()) {

            } else {

            }
        }
        if (ship.isShipWithModules()) {
            for (ShipAPI module : ship.getChildModulesCopy()) {
                if (module.hasListenerOfClass(NXN_GNShield_Script.class)) continue;
                module.addListener(new NXN_GNShield_Script(module));
            }
        }
    }

    public boolean isApplicableToShip(ShipAPI ship) {
        return ship.getShield() != null && true;
    }
}


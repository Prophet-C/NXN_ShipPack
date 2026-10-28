package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

/**
 * 天灾旗舰：
 *   ① 地鸣级在场时，同阵营舰船全属性 +5%
 *   ② 地鸣级自身免疫电子战负面效果
 */
public class NXN_FlagshipOfCalamity extends BaseHullMod {

    private static final String ID = "NXN_flagship_of_calamity";
    private static final float BUFF = 0.05f;

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // 免疫电子战：把电子战惩罚乘数压到 0
        try {
            ship.getMutableStats().getDynamic().getStat(Stats.ELECTRONIC_WARFARE_PENALTY_MULT)
                    .modifyMult(ID + "_ew", 0f);
            ship.getMutableStats().getDynamic().getStat(Stats.ELECTRONIC_WARFARE_PENALTY_MOD)
                    .modifyMult(ID + "_ewmod", 0f);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || ship == null || !ship.isAlive()) return;

        int owner = ship.getOwner();
        for (ShipAPI other : engine.getShips()) {
            if (other == null || !other.isAlive() || other == ship) continue;
            if (other.getOwner() != owner) continue;
            if (other.isFighter() || other.isDrone()) continue;

            String tag = ID + "_" + other.hashCode();
            try {
                other.getMutableStats().getFluxDissipation().modifyMult(tag, 1f + BUFF);
                other.getMutableStats().getFluxCapacity().modifyMult(tag, 1f + BUFF);
                other.getMutableStats().getMaxSpeed().modifyMult(tag, 1f + BUFF);
                other.getMutableStats().getMaxTurnRate().modifyMult(tag, 1f + BUFF);
                other.getMutableStats().getHullDamageTakenMult().modifyMult(tag, 1f - BUFF);
                other.getMutableStats().getArmorDamageTakenMult().modifyMult(tag, 1f - BUFF);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        return null;
    }
}

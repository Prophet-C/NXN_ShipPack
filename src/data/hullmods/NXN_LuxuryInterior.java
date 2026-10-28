package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;

public class NXN_LuxuryInterior extends BaseHullMod {

    private static final String TARGET_HULL_ID = "NXN_rumbling";

    private static final float MAX_CR_BONUS = 0.15f;
    private static final float CR_RECOVERY_BONUS_PERCENT = 20f;
    private static final float SUPPLIES_DEMAND_MULT = 1.3f;

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if (stats == null || stats.getVariant() == null || stats.getVariant().getHullSpec() == null) {
            return;
        }
        if (!isTargetHull(stats.getVariant().getHullSpec().getHullId(), stats.getVariant().getHullSpec().getBaseHullId())) {
            return;
        }

        stats.getMaxCombatReadiness().modifyFlat(id, MAX_CR_BONUS);
        stats.getBaseCRRecoveryRatePercentPerDay().modifyPercent(id, CR_RECOVERY_BONUS_PERCENT);
        stats.getSuppliesPerMonth().modifyMult(id, SUPPLIES_DEMAND_MULT);
        stats.getSuppliesToRecover().modifyMult(id, SUPPLIES_DEMAND_MULT);
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null || ship.getHullSpec() == null) return false;
        return isTargetHull(ship.getHullSpec().getHullId(), ship.getHullSpec().getBaseHullId());
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        return "仅适用于 NXN_rumbling";
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "" + Math.round(MAX_CR_BONUS * 100f) + "%";
        if (index == 1) return "" + Math.round(CR_RECOVERY_BONUS_PERCENT) + "%";
        if (index == 2) return "" + Math.round((SUPPLIES_DEMAND_MULT - 1f) * 100f) + "%";
        return null;
    }

    private static boolean isTargetHull(String hullId, String baseHullId) {
        return TARGET_HULL_ID.equals(hullId) || TARGET_HULL_ID.equals(baseHullId);
    }
}

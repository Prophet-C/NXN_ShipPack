package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;

public class NXN_FlagshipTargetingCore extends BaseHullMod {

    private static final String TARGET_HULL_ID = "NXN_rumbling";

    private static final float BALLISTIC_RANGE_BONUS_PERCENT = 80f;
    private static final float ENERGY_RANGE_BONUS_PERCENT = 80f;
    private static final float RECOIL_MULT = 0.6f;
    private static final float AUTOFIRE_ACCURACY_PENALTY = 0.2f;
    /** 零赋能加速（基础值 50）降低 30 点 */
    private static final float ZERO_FLUX_BOOST_DELTA = -30f;

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if (stats == null || stats.getVariant() == null || stats.getVariant().getHullSpec() == null) {
            return;
        }
        if (!isTargetHull(stats.getVariant().getHullSpec().getHullId(), stats.getVariant().getHullSpec().getBaseHullId())) {
            return;
        }

        stats.getBallisticWeaponRangeBonus().modifyPercent(id, BALLISTIC_RANGE_BONUS_PERCENT);
        stats.getEnergyWeaponRangeBonus().modifyPercent(id, ENERGY_RANGE_BONUS_PERCENT);

        stats.getMaxRecoilMult().modifyMult(id, RECOIL_MULT);
        stats.getRecoilPerShotMult().modifyMult(id, RECOIL_MULT);

        // 该项主要影响自动瞄准精度。
        stats.getAutofireAimAccuracy().modifyFlat(id, -AUTOFIRE_ACCURACY_PENALTY);

        // 零赋能时的速度加成降低（基础射程加成很高，用这个做平衡）
        stats.getZeroFluxSpeedBoost().modifyFlat(id, ZERO_FLUX_BOOST_DELTA);
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
        if (index == 0) return "" + Math.round(BALLISTIC_RANGE_BONUS_PERCENT) + "%";
        if (index == 1) return "" + Math.round((1f - RECOIL_MULT) * 100f) + "%";
        if (index == 2) return "" + Math.round(AUTOFIRE_ACCURACY_PENALTY * 100f) + "%";
        return null;
    }

    private static boolean isTargetHull(String hullId, String baseHullId) {
        return TARGET_HULL_ID.equals(hullId) || TARGET_HULL_ID.equals(baseHullId);
    }
}

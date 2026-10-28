package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;

public class NXN_RumblingWeaponSwapCycle extends BaseHullMod {

    private static final String TARGET_HULL_ID = "NXN_rumbling";

    @Override
    public int getDisplaySortOrder() {
        return 2999;
    }

    @Override
    public int getDisplayCategoryIndex() {
        return 3;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null || ship.getHullSpec() == null) {
            return false;
        }
        String hullId = ship.getHullSpec().getHullId();
        String baseHullId = ship.getHullSpec().getBaseHullId();
        return TARGET_HULL_ID.equals(hullId) || TARGET_HULL_ID.equals(baseHullId);
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        return "仅适用于 NXN_rumbling";
    }
}

package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * 侦查机库：装备截击机时，中槽以下的武器射程 +200。
 * 用 every-frame 状态机实现：有截击机 → 加射程；没有 → 撤销。
 */
public class NXN_ScoutHangar extends BaseHullMod {

    private static final String ID = "NXN_scout_hangar";
    private static final float RANGE_BONUS = 200f;

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) return;

        boolean hasInterceptor = false;
        try {
            for (FighterWingAPI wing : ship.getAllWings()) {
                if (wing != null && wing.getRole() != null
                        && "INTERCEPTOR".equals(wing.getRole().toString())) {
                    hasInterceptor = true;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            if (hasInterceptor) {
                ship.getMutableStats().getBallisticWeaponRangeBonus().modifyFlat(ID, RANGE_BONUS);
                ship.getMutableStats().getEnergyWeaponRangeBonus().modifyFlat(ID, RANGE_BONUS);
                ship.getMutableStats().getNonBeamPDWeaponRangeBonus().modifyFlat(ID, RANGE_BONUS * 0.5f);
            } else {
                ship.getMutableStats().getBallisticWeaponRangeBonus().unmodify(ID);
                ship.getMutableStats().getEnergyWeaponRangeBonus().unmodify(ID);
                ship.getMutableStats().getNonBeamPDWeaponRangeBonus().unmodify(ID);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "200";
        return null;
    }
}

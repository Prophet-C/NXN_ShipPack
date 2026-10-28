package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * 薄弱环节：全舰武器受到的伤害 +30%。
 * （设定上是"第二主炮"存在结构缺陷，引擎无法按单门炮施加，故作用于全舰武器。）
 */
public class NXN_WeakPoint extends BaseHullMod {

    private static final String ID = "NXN_weak_point";
    private static final float WEAPON_DAMAGE_TAKEN_MULT = 1.3f;

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ship.getMutableStats().getWeaponDamageTakenMult().modifyMult(ID, WEAPON_DAMAGE_TAKEN_MULT);
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "30";
        return null;
    }
}

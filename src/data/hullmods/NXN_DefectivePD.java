package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import org.lwjgl.util.vector.Vector2f;

public class NXN_DefectivePD extends BaseHullMod {

    private static final String TARGET_HULL_ID = "NXN_rumbling";
    private static final float PD_DAMAGE_MULT = 0.7f;
    private static final String DAMAGE_MOD_ID = "NXN_defective_pd_damage";

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !isTargetShip(ship)) {
            return;
        }

        ensureListener(ship);
        for (ShipAPI module : ship.getChildModulesCopy()) {
            ensureListener(module);
        }
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return isTargetShip(ship);
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        return "仅适用于 NXN_rumbling";
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "" + (int) ((1f - PD_DAMAGE_MULT) * 100f) + "%";
        return null;
    }

    private static void ensureListener(ShipAPI ship) {
        if (ship == null || !ship.isAlive() || ship.isHulk()) {
            return;
        }
        if (!ship.hasListenerOfClass(DefectivePDDamageListener.class)) {
            ship.addListener(new DefectivePDDamageListener());
        }
    }

    private static boolean isTargetShip(ShipAPI ship) {
        if (ship == null || ship.getHullSpec() == null) {
            return false;
        }
        return TARGET_HULL_ID.equals(ship.getHullSpec().getHullId())
                || TARGET_HULL_ID.equals(ship.getHullSpec().getBaseHullId());
    }

    private static final class DefectivePDDamageListener implements DamageDealtModifier {
        @Override
        public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
            WeaponAPI weapon = getWeaponFromParam(param);
            if (weapon == null || !isPdWeapon(weapon)) {
                return null;
            }
            damage.getModifier().modifyMult(DAMAGE_MOD_ID, PD_DAMAGE_MULT);
            return DAMAGE_MOD_ID;
        }

        private static WeaponAPI getWeaponFromParam(Object param) {
            if (param instanceof DamagingProjectileAPI) {
                return ((DamagingProjectileAPI) param).getWeapon();
            }
            if (param instanceof BeamAPI) {
                return ((BeamAPI) param).getWeapon();
            }
            return null;
        }

        private static boolean isPdWeapon(WeaponAPI weapon) {
            return weapon.hasAIHint(WeaponAPI.AIHints.PD)
                    || weapon.hasAIHint(WeaponAPI.AIHints.PD_ONLY)
                    || weapon.hasAIHint(WeaponAPI.AIHints.PD_ALSO);
        }
    }
}

package data.hullmods;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

public class NXN_CollapseReactorSystem extends BaseHullMod {

    private static final String MOD_ID = "NXN_collapse_reactor_system";
    private static final String TARGET_HULL_ID = "NXN_rumbling";
    private static final String DATA_KEY = "NXN_CollapseReactorSystem_Data";

    private static final String SYNC_MOD_SUFFIX = "_sync";
    private static final String VENT_MOD_SUFFIX = "_vent_penalty";

    private static final float REDUCTION_PER_SHOT_PERCENT = 0.5f;
    private static final float REDUCTION_DURATION = 3f;
    private static final float MAX_REDUCTION_PERCENT = 20f;

    private static final float VENT_RATE_BONUS_PERCENT = 50f;
    private static final float VENTING_MANEUVER_PENALTY = 0.5f;

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, com.fs.starfarer.api.combat.MutableShipStatsAPI stats, String id) {
        if (stats == null || stats.getVariant() == null || stats.getVariant().getHullSpec() == null) {
            return;
        }
        if (!isTargetHull(stats.getVariant().getHullSpec().getHullId(), stats.getVariant().getHullSpec().getBaseHullId())) {
            return;
        }

        stats.getVentRateMult().modifyPercent(id, VENT_RATE_BONUS_PERCENT);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !isTargetShip(ship)) {
            return;
        }

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) {
            return;
        }

        ReactorData data = getData(ship);
        float now = engine.getTotalElapsedTime(false);

        updateParticleSyncStacks(ship, data, now);
        pruneExpiredStacks(data, now);
        applyParticleSyncPenalty(ship, MOD_ID, data);
        applyVentingManeuverPenalty(ship, MOD_ID);

        if (ship == engine.getPlayerShip()) {
            float reductionPercent = Math.min(MAX_REDUCTION_PERCENT, data.stackExpireTimes.size() * REDUCTION_PER_SHOT_PERCENT);
            engine.maintainStatusForPlayerShip(
                    DATA_KEY,
                    "graphics/icons/hullsys/fortress_shield.png",
                    "NxN坍缩炉系统",
                    String.format("粒子同步: 武器幅能 -%.1f%%", reductionPercent),
                    reductionPercent > 0f
            );
        }
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        return "仅适用于 NXN_rumbling";
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return String.format("%.1f%%", REDUCTION_PER_SHOT_PERCENT);
        if (index == 1) return "" + (int) REDUCTION_DURATION;
        if (index == 2) return "" + (int) MAX_REDUCTION_PERCENT + "%";
        if (index == 3) return "" + (int) VENT_RATE_BONUS_PERCENT + "%";
        if (index == 4) return "" + (int) (VENTING_MANEUVER_PENALTY * 100f) + "%";
        return null;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return isTargetShip(ship);
    }

    private static void updateParticleSyncStacks(ShipAPI ship, ReactorData data, float now) {
        Set<WeaponAPI> validWeaponsThisFrame = new HashSet<>();

        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (!isEligibleWeapon(weapon)) {
                continue;
            }

            validWeaponsThisFrame.add(weapon);

            float currCooldown = weapon.getCooldownRemaining();
            Float prevCooldown = data.lastCooldown.get(weapon);

            // 冷却值突增通常意味着该武器刚触发了一次开火周期。
            if (prevCooldown != null && currCooldown > prevCooldown + 0.05f) {
                data.stackExpireTimes.add(now + REDUCTION_DURATION);
            }

            data.lastCooldown.put(weapon, currCooldown);
        }

        data.lastCooldown.keySet().retainAll(validWeaponsThisFrame);
    }

    private static void pruneExpiredStacks(ReactorData data, float now) {
        Iterator<Float> iter = data.stackExpireTimes.iterator();
        while (iter.hasNext()) {
            if (iter.next() <= now) {
                iter.remove();
            }
        }
    }

    private static void applyParticleSyncPenalty(ShipAPI ship, String id, ReactorData data) {
        float reductionPercent = Math.min(MAX_REDUCTION_PERCENT, data.stackExpireTimes.size() * REDUCTION_PER_SHOT_PERCENT);
        float weaponFluxMult = 1f - reductionPercent / 100f;
        ship.getMutableStats().getBallisticWeaponFluxCostMod().modifyMult(id + SYNC_MOD_SUFFIX, weaponFluxMult);
        ship.getMutableStats().getEnergyWeaponFluxCostMod().modifyMult(id + SYNC_MOD_SUFFIX, weaponFluxMult);
        ship.getMutableStats().getMissileWeaponFluxCostMod().modifyMult(id + SYNC_MOD_SUFFIX, weaponFluxMult);
    }

    private static void applyVentingManeuverPenalty(ShipAPI ship, String id) {
        String modId = id + VENT_MOD_SUFFIX;
        if (ship.getFluxTracker() != null && ship.getFluxTracker().isVenting()) {
            float maneuverMult = 1f - VENTING_MANEUVER_PENALTY;
            ship.getMutableStats().getAcceleration().modifyMult(modId, maneuverMult);
            ship.getMutableStats().getDeceleration().modifyMult(modId, maneuverMult);
            ship.getMutableStats().getTurnAcceleration().modifyMult(modId, maneuverMult);
            ship.getMutableStats().getMaxTurnRate().modifyMult(modId, maneuverMult);
        } else {
            ship.getMutableStats().getAcceleration().unmodify(modId);
            ship.getMutableStats().getDeceleration().unmodify(modId);
            ship.getMutableStats().getTurnAcceleration().unmodify(modId);
            ship.getMutableStats().getMaxTurnRate().unmodify(modId);
        }
    }

    private static boolean isEligibleWeapon(WeaponAPI weapon) {
        if (weapon == null || weapon.isDecorative()) {
            return false;
        }
        if (weapon.getSize() != WeaponAPI.WeaponSize.MEDIUM && weapon.getSize() != WeaponAPI.WeaponSize.LARGE) {
            return false;
        }
        WeaponAPI.WeaponType type = weapon.getType();
        return type != WeaponAPI.WeaponType.DECORATIVE
                && type != WeaponAPI.WeaponType.SYSTEM
                && type != WeaponAPI.WeaponType.STATION_MODULE
                && type != WeaponAPI.WeaponType.LAUNCH_BAY;
    }

    private static ReactorData getData(ShipAPI ship) {
        Object existing = ship.getCustomData().get(DATA_KEY);
        if (existing instanceof ReactorData) {
            return (ReactorData) existing;
        }
        ReactorData created = new ReactorData();
        ship.getCustomData().put(DATA_KEY, created);
        return created;
    }

    private static boolean isTargetShip(ShipAPI ship) {
        if (ship == null || ship.getHullSpec() == null) return false;
        return isTargetHull(ship.getHullSpec().getHullId(), ship.getHullSpec().getBaseHullId());
    }

    private static boolean isTargetHull(String hullId, String baseHullId) {
        return TARGET_HULL_ID.equals(hullId) || TARGET_HULL_ID.equals(baseHullId);
    }

    private static final class ReactorData {
        private final Map<WeaponAPI, Float> lastCooldown = new HashMap<>();
        private final List<Float> stackExpireTimes = new ArrayList<>();
    }
}

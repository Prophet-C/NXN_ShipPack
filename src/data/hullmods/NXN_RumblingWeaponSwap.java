package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;

import java.util.HashMap;
import java.util.Map;

public class NXN_RumblingWeaponSwap extends BaseHullMod {

    private static final String TARGET_HULL_ID = "NXN_rumbling";

    private static final String SLOT_1 = "WS0001";
    private static final String SLOT_2 = "WS0002";
    private static final String SLOT_3 = "WS0013";

    private static final String WEAPON_BALLISTIC = "NXN_rumbling_maingun_ballistic";
    private static final String WEAPON_ENERGY = "NXN_rumbling_maingun_energy";

    private static final String MODE_BALLISTIC = "NXN_rumbling_mode_ballistic";
    private static final String MODE_ENERGY = "NXN_rumbling_mode_energy";

    private static final Map<String, String> WEAPON_CYCLE = new HashMap<>();
    private static final Map<String, String> MODE_BY_WEAPON = new HashMap<>();

    static {
        WEAPON_CYCLE.put(WEAPON_BALLISTIC, WEAPON_ENERGY);
        WEAPON_CYCLE.put(WEAPON_ENERGY, WEAPON_BALLISTIC);

        MODE_BY_WEAPON.put(WEAPON_BALLISTIC, MODE_BALLISTIC);
        MODE_BY_WEAPON.put(WEAPON_ENERGY, MODE_ENERGY);
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if (stats == null || stats.getVariant() == null || !isTargetVariant(stats.getVariant())) {
            return;
        }

        ShipVariantAPI variant = stats.getVariant();

        // 仅当玩家移除了当前模式船插时触发切换。
        if (hasAnyModeHullmod(variant)) {
            return;
        }

        String nextWeapon = detectNextWeapon(variant);
        replaceWeapon(variant, SLOT_1, nextWeapon);
        replaceWeapon(variant, SLOT_2, nextWeapon);
        replaceWeapon(variant, SLOT_3, nextWeapon);

        String modeHullmodId = MODE_BY_WEAPON.get(nextWeapon);
        if (modeHullmodId != null && !variant.getHullMods().contains(modeHullmodId)) {
            variant.addMod(modeHullmodId);
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (ship == null || ship.getOriginalOwner() >= 0 || !isTargetShip(ship)) {
            return;
        }

        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null
                || Global.getSector().getPlayerFleet().getCargo() == null) {
            return;
        }

        for (CargoStackAPI stack : Global.getSector().getPlayerFleet().getCargo().getStacksCopy()) {
            if (stack.isWeaponStack()
                    && stack.getWeaponSpecIfWeapon() != null
                    && stack.getWeaponSpecIfWeapon().getWeaponId().startsWith("NXN_rumbling_maingun_")) {
                Global.getSector().getPlayerFleet().getCargo().removeStack(stack);
            }
        }
    }

    @Override
    public int getDisplayCategoryIndex() {
        return 2;
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
        return "你不应该看到这个船插";
    }

    private static boolean hasAnyModeHullmod(ShipVariantAPI variant) {
        return variant.getHullMods().contains(MODE_BALLISTIC) || variant.getHullMods().contains(MODE_ENERGY);
    }

    private static String detectNextWeapon(ShipVariantAPI variant) {
        String current1 = variant.getWeaponId(SLOT_1);
        if (WEAPON_CYCLE.containsKey(current1)) {
            return WEAPON_CYCLE.get(current1);
        }

        String current2 = variant.getWeaponId(SLOT_2);
        if (WEAPON_CYCLE.containsKey(current2)) {
            return WEAPON_CYCLE.get(current2);
        }

        String current3 = variant.getWeaponId(SLOT_3);
        if (WEAPON_CYCLE.containsKey(current3)) {
            return WEAPON_CYCLE.get(current3);
        }

        return WEAPON_BALLISTIC;
    }

    private static void replaceWeapon(ShipVariantAPI variant, String slotId, String weaponId) {
        variant.clearSlot(slotId);
        variant.addWeapon(slotId, weaponId);
    }

    private static boolean isTargetVariant(ShipVariantAPI variant) {
        if (variant.getHullSpec() == null) {
            return false;
        }
        String hullId = variant.getHullSpec().getHullId();
        String baseHullId = variant.getHullSpec().getBaseHullId();
        return TARGET_HULL_ID.equals(hullId) || TARGET_HULL_ID.equals(baseHullId);
    }

    private static boolean isTargetShip(ShipAPI ship) {
        if (ship == null || ship.getHullSpec() == null) {
            return false;
        }
        String hullId = ship.getHullSpec().getHullId();
        String baseHullId = ship.getHullSpec().getBaseHullId();
        return TARGET_HULL_ID.equals(hullId) || TARGET_HULL_ID.equals(baseHullId);
    }
}

package data.hullmods;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;

/**
 * 模块化装甲设计（地鸣级专用）
 *
 * 设计目标：
 * 1. 只要外置装甲模块还存在，就提供较强 EMP 抗性；
 * 2. 模块完整时机动/散幅/航速惩罚最重；
 * 3. 模块被逐步击毁后，惩罚逐步减轻，直到全部失去模块时恢复到无惩罚状态。
 *
 * 机制核心：
 * - 使用 aliveRatio（存活模块比例）作为统一缩放系数；
 * - 所有惩罚按 aliveRatio 线性缩放；
 * - EMP 抗性按模块存活比例线性衰减：
 *   模块完整时抗性最高，模块越少抗性越弱，模块全失时无抗性。
 */
public class NXN_ModularArmorDesign extends BaseHullMod {

    /** 与 hull_mods.csv 中该船插 id 对应，用于战斗内实时修正的唯一 key。 */
    private static final String MOD_ID = "NXN_modular_armor_design";
    /** 仅允许作用于地鸣级。 */
    private static final String TARGET_HULL_ID = "NXN_rumbling";

    /** 地鸣级六个外置模块槽位 id。 */
    private static final Set<String> ARMOR_MODULE_SLOTS = new HashSet<>(Arrays.asList(
            "L_1", "L_2", "L_3", "R_1", "R_2", "R_3"
    ));
    /** 模块总数，用于计算存活比例。 */
    private static final int TOTAL_MODULE_COUNT = 6;

    /** 模块完整时的 EMP 受伤倍率：0.25 = 仅承受 25% EMP。 */
    private static final float EMP_DAMAGE_MULT_AT_FULL_MODULES = 0.25f;
    /** 模块完整时最大散幅惩罚（乘区）：55%。 */
    private static final float MAX_FLUX_DISSIPATION_PENALTY = 0.30f;
    /** 模块完整时最大机动惩罚（乘区）：60%。 */
    private static final float MAX_MANEUVER_PENALTY = 0.30f;
    /** 模块完整时最大航速平值惩罚。 */
    private static final float MAX_SPEED_FLAT_PENALTY = 30f;

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // 常规空指针保护：在舰船/变体尚未完全可用时直接返回。
        if (stats == null || stats.getVariant() == null || stats.getVariant().getHullSpec() == null) {
            return;
        }
        // 非地鸣级不生效，避免意外影响其他舰船。
        if (!isTargetHull(stats.getVariant().getHullSpec().getHullId(), stats.getVariant().getHullSpec().getBaseHullId())) {
            return;
        }

        // 战斗外无法可靠读取模块存活状态，按“模块全部完好”进行预应用。
        applyScaledEffects(stats, id, 1f);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        // 只处理目标舰船。
        if (ship == null || !isTargetShip(ship)) {
            return;
        }

        // 统计当前存活模块数。
        int aliveModules = countAliveArmorModules(ship);
        // 存活比例：1 表示完整，0 表示全部损失。
        float aliveRatio = Math.max(0f, Math.min(1f, aliveModules / (float) TOTAL_MODULE_COUNT));
        // 以统一 MOD_ID 持续覆盖本船插修正，确保状态实时同步。
        applyScaledEffects(ship.getMutableStats(), MOD_ID, aliveRatio);
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
        // 与 hull_mods.csv 中 {%s} 顺序严格对应。
        if (index == 0) return "" + (int) ((1f - EMP_DAMAGE_MULT_AT_FULL_MODULES) * 100f) + "%";
        if (index == 1) return "" + (int) (MAX_FLUX_DISSIPATION_PENALTY * 100f) + "%";
        if (index == 2) return "" + (int) (MAX_MANEUVER_PENALTY * 100f) + "%";
        if (index == 3) return "" + (int) MAX_SPEED_FLAT_PENALTY;
        return null;
    }

    /**
     * 按模块存活比例应用动态惩罚。
     *
     * 缩放规则（线性）：
     * - aliveRatio = 1.0：惩罚最大（模块完整）
     * - aliveRatio = 0.0：惩罚为 0（模块全失）
     */
    private static void applyScaledEffects(MutableShipStatsAPI stats, String id, float aliveRatio) {
        // 散幅与机动采用乘区惩罚。
        float fluxDissMult = 1f - MAX_FLUX_DISSIPATION_PENALTY * aliveRatio;
        float maneuverMult = 1f - MAX_MANEUVER_PENALTY * aliveRatio;
        // 航速采用平值惩罚（便于直观控制“最高航速提高”的体感）。
        float speedPenalty = MAX_SPEED_FLAT_PENALTY * aliveRatio;
        // EMP 受伤倍率线性插值：
        // aliveRatio = 1 -> EMP_DAMAGE_MULT_AT_FULL_MODULES（满模块，抗性最强）
        // aliveRatio = 0 -> 1.0（无模块，无抗性）
        float empDamageMult = 1f - (1f - EMP_DAMAGE_MULT_AT_FULL_MODULES) * aliveRatio;
        stats.getEmpDamageTakenMult().modifyMult(id, empDamageMult);

        // 其余惩罚按比例实时更新。
        stats.getFluxDissipation().modifyMult(id, fluxDissMult);
        stats.getAcceleration().modifyMult(id, maneuverMult);
        stats.getDeceleration().modifyMult(id, maneuverMult);
        stats.getTurnAcceleration().modifyMult(id, maneuverMult);
        stats.getMaxTurnRate().modifyMult(id, maneuverMult);
        stats.getMaxSpeed().modifyFlat(id, -speedPenalty);
    }

    /**
     * 统计地鸣级六个外置模块当前存活数量。
     *
     * 注意：
     * - 只统计 ARMOR_MODULE_SLOTS 中定义的模块；
     * - 模块对象在战斗极早期可能尚未建立，此时回退为“模块完整”。
     */
    private static int countAliveArmorModules(ShipAPI ship) {
        int alive = 0;
        int tracked = 0;

        // 子模块遍历：只关心绑定到目标模块槽位的模块。
        for (ShipAPI module : ship.getChildModulesCopy()) {
            if (module == null) {
                continue;
            }
            WeaponSlotAPI stationSlot = module.getStationSlot();
            if (stationSlot == null || !ARMOR_MODULE_SLOTS.contains(stationSlot.getId())) {
                continue;
            }
            tracked++;
            if (module.isAlive() && !module.isHulk()) {
                alive++;
            }
        }

        // 极早期帧可能尚未拿到模块对象，按完整模块处理，避免开局惩罚抖动。
        if (tracked == 0) {
            return TOTAL_MODULE_COUNT;
        }
        return alive;
    }

    /** 战斗内适配：舰船目标判定。 */
    private static boolean isTargetShip(ShipAPI ship) {
        if (ship == null || ship.getHullSpec() == null) return false;
        return isTargetHull(ship.getHullSpec().getHullId(), ship.getHullSpec().getBaseHullId());
    }

    /** 舰体 id 判定，兼容皮肤/衍生 hull 通过 baseHullId 识别。 */
    private static boolean isTargetHull(String hullId, String baseHullId) {
        return TARGET_HULL_ID.equals(hullId) || TARGET_HULL_ID.equals(baseHullId);
    }
}

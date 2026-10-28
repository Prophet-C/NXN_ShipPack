package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

import org.magiclib.subsystems.MagicSubsystem;

/**
 * 系统三 · 量子爆发-反转（CAPS）
 *
 * 当前阶段：**只做视觉**（领域展开 / 边界粒子 / 向内脉冲）。
 * 机制（结束过载、清空赋能、领域减速、光束射程 -50%）留待后续实现。
 *
 * 注意：正式设计是"仅过载时可用"，先置 REQUIRE_OVERLOAD = false 方便测试。
 */
public class NXN_QuantumBurstSubsystem extends MagicSubsystem {

    public static final String NAME = "量子爆发-反转";

    /** 是否要求过载才能使用 */
    public static final boolean REQUIRE_OVERLOAD = true;   // 仅过载时可用

    private static final float DURATION = 20f;
    private static final float COOLDOWN = 60f;
    private static final float FIELD_RADIUS = 1500f;
    /** 量子消耗 */
    private static final float QUANTUM_COST = 40f;

    public NXN_QuantumBurstSubsystem(ShipAPI ship) {
        super(ship);
    }

    @Override
    public String getDisplayText() {
        return NAME;
    }

    @Override
    public String getKey() {
        return "CAPITAL";   // LWJGL 键名（显示为 CAPS）
    }

    @Override
    public boolean requiresTarget() {
        return false;
    }

    @Override
    public float getBaseActiveDuration() {
        return DURATION;
    }

    @Override
    public float getBaseInDuration() {
        return 0.3f;
    }

    @Override
    public float getOutDuration() {
        return 0.3f;
    }

    @Override
    public float getBaseCooldownDuration() {
        return COOLDOWN;
    }

    @Override
    public boolean shouldActivateAI(float amount) {
        return false;
    }

    /** MagicLib 默认禁止过载时使用子系统，这里必须放开 */
    @Override
    public boolean canUseWhileOverloaded() {
        return true;
    }

    @Override
    public boolean canActivate() {
        if (REQUIRE_OVERLOAD && !ship.getFluxTracker().isOverloaded()) {
            return false;
        }
        return NXN_QuantumGauge.getValue(ship) >= QUANTUM_COST;
    }

    @Override
    public void onActivate() {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || ship == null) return;

        // 领域展开：摩擦音
        try {
            Global.getSoundPlayer().playSound("NXN_field_hum", 1f, 1f,
                    ship.getLocation(), ship.getVelocity());
        } catch (Throwable ignored) {
        }

        // 扣量子
        if (!NXN_QuantumGauge.spend(ship, QUANTUM_COST)) {
            return;
        }

        // 结束过载并清空赋能（硬幅能也清）
        try {
            com.fs.starfarer.api.combat.FluxTrackerAPI ft = ship.getFluxTracker();
            ft.stopOverload();
            ft.setCurrFlux(0f);
            ft.setHardFlux(0f);
        } catch (Throwable ignored) {
        }



        // 领域视觉
        engine.addLayeredRenderingPlugin(new data.scripts.weapons.NXN_QuantumFieldVisual(ship));

        // 展开瞬间的闪光
        engine.spawnExplosion(ship.getLocation(), ship.getVelocity(),
                new java.awt.Color(210, 170, 255, 220), 320f, 0.45f);
    }
}

package data.shipsystems.scripts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import data.scripts.weapons.NXN_AfterimageRenderer;

public class NXN_OutputUnlockStats extends BaseShipSystemScript {

    private static final float SHIELD_EFFICIENCY_BONUS = 0.50f; // 护盾效率提升比例（最终表现为受盾伤害幅能降低 50%）
    private static final float HARD_FLUX_DISSIPATION_BONUS = 0.30f; // 开盾时硬幅能额外排散比例（+30%）
    private static final float FLUX_DISSIPATION_BONUS = 0.30f; // 总幅能排散倍率加成（+30%）
    private static final float WEAPON_ROF_PENALTY = 0.30f; // 武器射速惩罚比例（-30%）
    private static final float MANEUVER_BONUS = 0.50f; // 机动性加成（+50%）
    private static final float MAX_SPEED_FLAT_BONUS = 30f; // 最大航速固定值加成（+30）

    // ===== NXN：最小开启时长（防 AI 在自动模式下立刻关闭系统）=====
    private static final long MIN_ACTIVE_MS = 10000L;
    private long activeSinceMs = 0L;
    private boolean fStartPlayed = false;

    /** 粒子槽位缓存：按 hullId 收集所有 RR* / LL* 槽位（不再写死） */
    private static final Map<String, List<String>> PARTICLE_SLOT_CACHE = new HashMap<String, List<String>>();

    private static List<String> getParticleSlots(ShipAPI ship) {
        if (ship == null || ship.getHullSpec() == null) return new ArrayList<String>();
        String hullId = ship.getHullSpec().getHullId();
        List<String> cached = PARTICLE_SLOT_CACHE.get(hullId);
        if (cached != null) return cached;
        List<String> slots = new ArrayList<String>();
        try {
            for (WeaponSlotAPI slot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
                String sid = slot.getId();
                if (sid != null && (sid.startsWith("RR") || sid.startsWith("LL"))) {
                    slots.add(sid);
                }
            }
        } catch (Throwable ignored) {
        }
        PARTICLE_SLOT_CACHE.put(hullId, slots);
        return slots;
    }
    private static final int PARTICLES_PER_SLOT = 1; // 每个喷口每帧生成的粒子数量（所有喷口同时喷）
    private static final float PARTICLE_SPAWN_CHANCE = 1f; // 每次尝试实际生成的概率（0~1）
    private static final Color PARTICLE_COLOR = new Color(70, 255, 120, 255); // 粒子颜色（RGBA）
    private static final float PARTICLE_SPEED_MIN = 250f; // 粒子初速度下限
    private static final float PARTICLE_SPEED_MAX = 300f; // 粒子初速度上限
    private static final float PARTICLE_SIZE_MIN = 1f; // 粒子尺寸下限
    private static final float PARTICLE_SIZE_MAX = 5f; // 粒子尺寸上限
    private static final float PARTICLE_DURATION_MIN = 1f; // 粒子持续时间下限（秒）
    private static final float PARTICLE_DURATION_MAX = 1.5f; // 粒子持续时间上限（秒）
    private static final float PARTICLE_BRIGHTNESS = 1.1f; // 粒子亮度倍率
    private static final float PARTICLE_SCATTER_HALF_ARC = 15f; // 粒子散射半角（总散射角 30 度）

    private static final float MODULE_EXPAND_SPEED = 3f; // 子模块展开/收回的插值速度（数值越大动作越快）
    // 模块位移开关（按三组）
    private static final boolean MOVE_GROUP_L1_R1 = false; // 是否启用 L_1 / R_1 这组模块位移
    private static final boolean MOVE_GROUP_L2_R2 = true; // 是否启用 L_2 / R_2 这组模块位移
    private static final boolean MOVE_GROUP_L3_R3 = false; // 是否启用 L_3 / R_3 这组模块位移

    private static final Map<String, Vector2f> MODULE_OFFSETS = new HashMap<>(); // 各模块展开时的目标局部偏移
    static {
        // 展开装甲位移（舰体局部坐标）：
        // x = 侧向（+左 / -右），y = 前向（+前 / -后）
        MODULE_OFFSETS.put("L_1", new Vector2f(18f, 24f));
        MODULE_OFFSETS.put("R_1", new Vector2f(-18f, 24f));
        MODULE_OFFSETS.put("L_2", new Vector2f(0f, 20f));
        MODULE_OFFSETS.put("R_2", new Vector2f(0f, 20f));
        MODULE_OFFSETS.put("L_3", new Vector2f(18f, -24f));
        MODULE_OFFSETS.put("R_3", new Vector2f(-18f, -24f));
    }

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
        }

        // 开启瞬间播放轮机启动声
        if (ship != null) {
            if (state == State.IN && !fStartPlayed) {
                fStartPlayed = true;
                try {
                    Global.getSoundPlayer().playSound("NXN_f_start", 1f, 1f,
                            ship.getLocation(), ship.getVelocity());
                } catch (Throwable ignored) {
                }
            } else if (state != State.IN && state != State.ACTIVE) {
                fStartPlayed = false;
            }
        }

        float shieldMult = 1f - SHIELD_EFFICIENCY_BONUS * effectLevel;
        float hardFluxBonus = HARD_FLUX_DISSIPATION_BONUS * effectLevel;
        float fluxDissipationMult = 1f + FLUX_DISSIPATION_BONUS * effectLevel;
        float weaponRofMult = 1f - WEAPON_ROF_PENALTY * effectLevel;
        float maneuverMult = 1f + MANEUVER_BONUS * effectLevel;
        float maxSpeedBonus = MAX_SPEED_FLAT_BONUS * effectLevel;

        // 盾效 +50%（受盾伤害幅能降低）
        stats.getShieldDamageTakenMult().modifyMult(id, shieldMult);

        // 开盾硬幅能排散 +30%
        stats.getHardFluxDissipationFraction().modifyFlat(id, hardFluxBonus);

        // 赋能排散 +30%
        stats.getFluxDissipation().modifyMult(id, fluxDissipationMult);

        // 武器射速 -30%
        stats.getBallisticRoFMult().modifyMult(id, weaponRofMult);
        stats.getEnergyRoFMult().modifyMult(id, weaponRofMult);
        stats.getMissileRoFMult().modifyMult(id, weaponRofMult);

        // 机动性 -50%
        stats.getAcceleration().modifyMult(id, maneuverMult);
        stats.getDeceleration().modifyMult(id, maneuverMult);
        stats.getTurnAcceleration().modifyMult(id, maneuverMult);
        stats.getMaxTurnRate().modifyMult(id, maneuverMult);

        // 最大航速 -20
        stats.getMaxSpeed().modifyFlat(id, maxSpeedBonus);

        if (ship != null) {
            ensureVisualController(ship);
        }

        // ===== NXN：AI 自动模式下，刚开启就被关闭时强制维持 MIN_ACTIVE_MS =====
        if (ship != null && ship.getSystem() != null) {
            long now = System.currentTimeMillis();
            if (state == State.IDLE || state == State.COOLDOWN) {
                activeSinceMs = 0L;
            } else if (state == State.IN || state == State.ACTIVE) {
                if (activeSinceMs <= 0L) {
                    activeSinceMs = now;
                }
            } else if (state == State.OUT) {
                // 玩家手动关闭时立刻生效，不强制维持（最小开启时间只约束 AI）
                boolean playerControlled = com.fs.starfarer.api.Global.getCombatEngine() != null
                        && com.fs.starfarer.api.Global.getCombatEngine().getPlayerShip() == ship;
                if (playerControlled) {
                    activeSinceMs = 0L;
                }
                // 量子耗尽时不强行维持（让系统正常退出）
                if (!playerControlled
                        && activeSinceMs > 0L
                        && data.hullmods.NXN_QuantumGauge.getValue(ship) > 0.001f) {
                    long onMs = now - activeSinceMs;
                    if (onMs < MIN_ACTIVE_MS) {
                        ship.getSystem().forceState(ShipSystemAPI.SystemState.ACTIVE,
                                (MIN_ACTIVE_MS - onMs) / 1000f);
                        return;
                    }
                    activeSinceMs = 0L;
                }
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        activeSinceMs = 0L;
        stats.getShieldDamageTakenMult().unmodify(id);
        stats.getHardFluxDissipationFraction().unmodify(id);
        stats.getFluxDissipation().unmodify(id);

        stats.getBallisticRoFMult().unmodify(id);
        stats.getEnergyRoFMult().unmodify(id);
        stats.getMissileRoFMult().unmodify(id);

        stats.getAcceleration().unmodify(id);
        stats.getDeceleration().unmodify(id);
        stats.getTurnAcceleration().unmodify(id);
        stats.getMaxTurnRate().unmodify(id);
        stats.getMaxSpeed().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) return new StatusData("盾效 +50%", false);
        if (index == 1) return new StatusData("开盾硬幅能排散 +30%", false);
        if (index == 2) return new StatusData("幅能排散 +30%", false);
        if (index == 3) return new StatusData("武器射速 -30%，机动性 +50%，航速 +30", false);
        return null;
    }

    private static void spawnDirectionalParticles(ShipAPI ship) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) {
            return;
        }

        List<String> slotIds = getParticleSlots(ship);
        if (slotIds.isEmpty()) {
            return;
        }

        // 所有喷口同时喷射
        for (String slotId : slotIds) {
            WeaponSlotAPI slot = ship.getHullSpec().getWeaponSlotAPI(slotId);
            if (slot == null) continue;

            for (int i = 0; i < PARTICLES_PER_SLOT; i++) {
                if (randomRange(0f, 1f) > PARTICLE_SPAWN_CHANCE) continue;

                Vector2f point = slot.computePosition(ship);
                float angle = slot.computeMidArcAngle(ship)
                        + randomRange(-PARTICLE_SCATTER_HALF_ARC, PARTICLE_SCATTER_HALF_ARC);
                float radians = (float) Math.toRadians(angle);
                float speed = randomRange(PARTICLE_SPEED_MIN, PARTICLE_SPEED_MAX);

                Vector2f velocity = new Vector2f(
                        (float) Math.cos(radians) * speed,
                        (float) Math.sin(radians) * speed
                );
                Vector2f.add(velocity, ship.getVelocity(), velocity);
                float size = randomRange(PARTICLE_SIZE_MIN, PARTICLE_SIZE_MAX);
                float duration = randomRange(PARTICLE_DURATION_MIN, PARTICLE_DURATION_MAX);
                engine.addHitParticle(point, velocity, size, PARTICLE_BRIGHTNESS, duration, PARTICLE_COLOR);
            }
        }
    }

    private static void applyModuleExpansion(ShipAPI parent, float effectLevel) {
        float level = Math.max(0f, Math.min(1f, effectLevel));
        float facingRad = (float) Math.toRadians(parent.getFacing());
        // 舰体前向单位向量（受朝向影响）
        float fwdX = (float) Math.cos(facingRad);
        float fwdY = (float) Math.sin(facingRad);
        // 舰体左向单位向量（前向逆时针 90 度）
        float leftX = -fwdY;
        float leftY = fwdX;

        for (ShipAPI module : parent.getChildModulesCopy()) {
            if (module == null || module.getStationSlot() == null) continue;

            String slotId = module.getStationSlot().getId();
            Vector2f localOffset = MODULE_OFFSETS.get(slotId);
            if (localOffset == null) continue;

            Vector2f basePos = module.getStationSlot().computePosition(parent);
            if (!isMovementEnabledForSlot(slotId)) {
                module.getLocation().set(basePos);
                module.getVelocity().set(parent.getVelocity());
                continue;
            }
            // 局部坐标 -> 世界坐标：
            // lateral * left + forward * forward
            float lateral = localOffset.x * level;
            float forward = localOffset.y * level;
            float worldX = leftX * lateral + fwdX * forward;
            float worldY = leftY * lateral + fwdY * forward;
            module.getLocation().set(basePos.x + worldX, basePos.y + worldY);
            module.getVelocity().set(parent.getVelocity());
        }
    }

    private static void resetModulePositions(ShipAPI parent) {
        for (ShipAPI module : parent.getChildModulesCopy()) {
            if (module == null || module.getStationSlot() == null) continue;

            String slotId = module.getStationSlot().getId();
            if (!MODULE_OFFSETS.containsKey(slotId)) continue;

            Vector2f basePos = module.getStationSlot().computePosition(parent);
            module.getLocation().set(basePos);
            module.getVelocity().set(parent.getVelocity());
        }
    }

    private static float randomRange(float min, float max) {
        return (float) ThreadLocalRandom.current().nextDouble(min, max);
    }

    private static boolean isMovementEnabledForSlot(String slotId) {
        if ("L_1".equals(slotId) || "R_1".equals(slotId)) {
            return MOVE_GROUP_L1_R1;
        }
        if ("L_2".equals(slotId) || "R_2".equals(slotId)) {
            return MOVE_GROUP_L2_R2;
        }
        if ("L_3".equals(slotId) || "R_3".equals(slotId)) {
            return MOVE_GROUP_L3_R3;
        }
        return true;
    }

    /** 残影渲染器（每艘船一个，常驻） */
    private static final java.util.Map<ShipAPI, NXN_AfterimageRenderer> AFTERIMAGES =
            new java.util.WeakHashMap<ShipAPI, NXN_AfterimageRenderer>();

    private static void ensureAfterimage(ShipAPI ship) {
        if (ship == null) return;
        if (AFTERIMAGES.containsKey(ship)) return;
        try {
            NXN_AfterimageRenderer r = new NXN_AfterimageRenderer(ship);
            Global.getCombatEngine().addLayeredRenderingPlugin(r);
            AFTERIMAGES.put(ship, r);
        } catch (Throwable ignored) {
        }
    }

    private static void ensureVisualController(ShipAPI ship) {
        ensureAfterimage(ship);
        if (!ship.hasListenerOfClass(OutputUnlockVisualController.class)) {
            ship.addListener(new OutputUnlockVisualController(ship));
        }
    }

    private static float approach(float current, float target, float maxStep) {
        if (current < target) return Math.min(target, current + maxStep);
        if (current > target) return Math.max(target, current - maxStep);
        return current;
    }

    private static final class OutputUnlockVisualController implements AdvanceableListener {
        private final ShipAPI ship;
        private float moduleLevel = 0f;

        private OutputUnlockVisualController(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public void advance(float amount) {
            if (ship == null || !ship.isAlive() || ship.isHulk()) {
                return;
            }
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) {
                return;
            }

            ShipSystemAPI system = ship.getSystem();
            boolean isTargetSystem = system != null && "NXN_output_unlock".equals(system.getId());
            float systemLevel = isTargetSystem ? system.getEffectLevel() : 0f;
            boolean systemActive = isTargetSystem && system.isActive();

            boolean venting = ship.getFluxTracker() != null && ship.getFluxTracker().isVenting();
            float targetLevel = Math.max(systemLevel, venting ? 1f : 0f);

            moduleLevel = approach(moduleLevel, targetLevel, amount * MODULE_EXPAND_SPEED);
            applyModuleExpansion(ship, moduleLevel);

            if (systemActive && moduleLevel > 0f) {
                spawnDirectionalParticles(ship);
            }

            if (moduleLevel <= 0.001f && !systemActive && !venting) {
                resetModulePositions(ship);
            }
        }
    }
}

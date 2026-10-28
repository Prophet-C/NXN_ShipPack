package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * 测距仪（装饰武器）。
 *
 *  - 默认朝正前方（90°）静止
 *  - 2000 su 内有敌舰 → 缓缓转向最近的一艘
 *  - 有手动锁定目标 → 优先朝向锁定目标（无距离限制）
 *  - 转向速度 20°/秒
 *
 * 角度计算全程手动处理最短弧，避免 normalizeAngle 约定不一致导致的原地打转。
 */
public class NXN_RangefinderRotator implements EveryFrameWeaponEffectPlugin {

    /** 转向速度（度/秒） */
    private static final float TURN_SPEED = 20f;
    /** 自动索敌距离（su） */
    private static final float DETECT_RANGE = 2000f;
    /** 对准容差（度）：小于这个角度才算"锁定" */
    private static final float ALIGN_TOLERANCE = 3f;
    /** 需要持续对准多久才生效（秒） */
    private static final float LOCK_CHARGE = 3f;
    /** 主炮加成：伤害倍数 */
    private static final float DAMAGE_BONUS = 1.20f;
    /** stat id */
    private static final String BONUS_ID = "NXN_rangefinder_lock";

    /** 空闲时与船体垂直（贴图“上”= 船首方向），即 currAngle = 船体朝向 */
    private static final float IDLE_OFFSET = 0f;

    /** 当前角度（不做 wrap，保持连续） */
    private float current = 90f;
    /** 持续对准计时 */
    private float lockTimer = 0f;
    /** 当前是否已生效（避免反复 modify） */
    private boolean bonusActive = false;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || weapon == null) return;
        if (engine.isPaused()) return;

        ShipAPI ship = weapon.getShip();
        if (ship == null || !ship.isAlive()) return;

        ShipAPI target = null;

        // 1) 手动锁定优先
        ShipAPI locked = ship.getShipTarget();
        if (locked != null && locked.isAlive() && !locked.isHulk()
                && locked.getOwner() != ship.getOwner()) {
            target = locked;
        } else {
            // 2) 最近敌舰（2000 su 内）
            float best = DETECT_RANGE;
            for (ShipAPI s : engine.getShips()) {
                if (s == null || !s.isAlive() || s.isHulk()) continue;
                if (s.getOwner() == ship.getOwner()) continue;
                float d = Misc.getDistance(s.getLocation(), ship.getLocation());
                if (d < best) {
                    best = d;
                    target = s;
                }
            }
        }

        // 目标角度（世界坐标绝对角度）
        // 无目标时：跟随船体朝向（而不是固定世界正上方）
        float desired = ship.getFacing() + IDLE_OFFSET;
        if (target != null) {
            desired = (float) Math.toDegrees(Math.atan2(
                    target.getLocation().y - ship.getLocation().y,
                    target.getLocation().x - ship.getLocation().x));
        }

        // 最短弧差值（-180 ~ 180）
        float diff = desired - current;
        diff = diff - 360f * (float) Math.floor((diff + 180f) / 360f);

        // 限速旋转
        float step = TURN_SPEED * amount;
        if (diff > step) diff = step;
        else if (diff < -step) diff = -step;

        current += diff;

        // 保持 current 在合理范围（不影响连续性）
        if (current > 100000f || current < -100000f) {
            current = desired;
        }

        weapon.setCurrAngle(current);

        // ===== 复古弹道学：对准目标并持续数秒 -> 主炮伤害加成 =====
        boolean aligned = target != null && Math.abs(diff) <= ALIGN_TOLERANCE;
        if (aligned) {
            lockTimer += amount;
        } else {
            lockTimer = 0f;
        }

        boolean shouldBonus = lockTimer >= LOCK_CHARGE;
        if (shouldBonus && !bonusActive) {
            ship.getMutableStats().getBallisticWeaponDamageMult().modifyMult(BONUS_ID, DAMAGE_BONUS);
            bonusActive = true;
        } else if (!shouldBonus && bonusActive) {
            ship.getMutableStats().getBallisticWeaponDamageMult().unmodify(BONUS_ID);
            bonusActive = false;
        }
    }
}

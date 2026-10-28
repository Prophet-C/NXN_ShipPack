package data.scripts.weapons;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.AutofireAIPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponGroupAPI;

/**
 * 「断层」的高平两用引信切换。
 *
 * 判定依据：**这门炮当前自动瞄准的目标**
 *   - 从武器组的 AutofireAIPlugin 读 getTargetMissile / getTargetShip
 *   - 目标是导弹 / 舰载机 / 无人机 -> 换成近炸引信弹
 *   - 目标是舰船 -> 保持穿甲弹
 *   - 读不到目标 -> 保持穿甲弹
 */
public class NXN_DuancengFuse implements OnFireEffectPlugin {

    private static final String FLAK_WEAPON_ID = "NXN_duanceng_flak";

    /** 近炸弹散布角（度，单侧）—— 让弹片云散开成一片 */
    private static final float FLAK_ANGLE_SPREAD = 9f;
    /** 速度随机浮动比例 */
    private static final float FLAK_SPEED_VARIANCE = 0.15f;
    /** 出膛位置横向抖动（像素） */
    private static final float FLAK_POS_JITTER = 10f;

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        if (engine == null || projectile == null || weapon == null) return;

        ShipAPI ship = weapon.getShip();
        if (ship == null) return;

        CombatEntityAPI target = resolveTarget(weapon, ship);
        boolean aaTarget = isAirTarget(target);


        if (!aaTarget) return;

        Vector2f loc = new Vector2f(projectile.getLocation());
        float angle = projectile.getFacing();
        Vector2f vel = new Vector2f(projectile.getVelocity());

        // 散布：角度、位置、速度都随机化，让近炸散成一片
        angle += (float) (Math.random() * 2 - 1) * FLAK_ANGLE_SPREAD;
        loc.x += (float) (Math.random() * 2 - 1) * FLAK_POS_JITTER;
        loc.y += (float) (Math.random() * 2 - 1) * FLAK_POS_JITTER;
        // 不叠加原弹速度：引擎会按弹种自身速度生成，保证与穿甲弹一致
        float speedMul = 1f + (float) (Math.random() * 2 - 1) * FLAK_SPEED_VARIANCE;
        float rad2 = (float) Math.toRadians(angle);
        vel = new Vector2f(
                (float) Math.cos(rad2) * 50f * speedMul,
                (float) Math.sin(rad2) * 50f * speedMul);

        try {
            engine.spawnProjectile(ship, weapon, FLAK_WEAPON_ID, loc, angle, vel);
            engine.removeEntity(projectile);
        } catch (Throwable e) {
        }
    }

    /** 从自动瞄准 AI 里读这门炮当前锁定的目标 */
    private CombatEntityAPI resolveTarget(WeaponAPI weapon, ShipAPI ship) {
        try {
            WeaponGroupAPI group = ship.getWeaponGroupFor(weapon);
            if (group != null) {
                AutofireAIPlugin ai = group.getAutofirePlugin(weapon);
                if (ai != null) {
                    MissileAPI m = ai.getTargetMissile();
                    if (m != null && m.getHitpoints() > 0f && !m.didDamage()) {
                        return m;
                    }
                    ShipAPI s = ai.getTargetShip();
                    if (s != null && s.isAlive()) {
                        return s;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            return ship.getShipTarget();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isAirTarget(CombatEntityAPI target) {
        if (target == null) return false;
        if (target instanceof MissileAPI) return true;
        if (target instanceof ShipAPI) {
            ShipAPI s = (ShipAPI) target;
            return s.isFighter() || s.isDrone();
        }
        return false;
    }

    private String describe(CombatEntityAPI target) {
        if (target == null) return "null";
        if (target instanceof MissileAPI) return "MISSILE";
        if (target instanceof ShipAPI) {
            ShipAPI s = (ShipAPI) target;
            return s.getHullSpec() != null ? s.getHullSpec().getHullId() : "ship";
        }
        return target.getClass().getSimpleName();
    }
}

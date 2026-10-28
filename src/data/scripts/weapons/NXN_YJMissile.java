package data.scripts.weapons;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * 鹰击两级反舰导弹。
 *
 * ① 冷抛射：垂直于舰体方向抛出 200su（纯弹道，不点火、无发光）
 * ② 加速段：锁定敌方目标，直线加速到 2500
 * ③ 抛体：距目标 < 800 或射程耗尽 → 抛掉弹体，射出战斗部
 * ④ 战斗部：锁定 1200 内敌舰，S 形机动，速度 1600（精度最高）
 */
public class NXN_YJMissile implements OnFireEffectPlugin {

    static final String WARHEAD_ID = "NXN_yj_warhead_launcher";

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        if (!(projectile instanceof MissileAPI)) return;
        MissileAPI m = (MissileAPI) projectile;
        try {
            m.setMissileAI(new BoosterAI(m));
        } catch (Throwable t) {
        }
    }

    /** 由发射方直接挂载 AI（onFireEffect 对导弹不一定触发，必须主动挂） */
    public static void attach(MissileAPI m) {
        if (m == null) return;
        try {
            m.setMissileAI(new BoosterAI(m));
        } catch (Throwable t) {
        }
    }

    // ================= 第一级 =================
    public static class BoosterAI implements MissileAIPlugin {

        /** 冷抛射距离 */
        private static final float EJECT_DIST = 200f;
        /** 抛射初速 */
        private static final float EJECT_SPEED = 320f;
        /** 战斗部弹头速度 */
        private static final float WARHEAD_SPEED = 730f;
        /** 加速段目标速度 */
        private static final float BOOST_SPEED = 600f;
        /** 加速率 */
        private static final float BOOST_ACCEL = 3600f;
        /** 锁定距离（敌方舰船） */
        private static final float LOCK_RANGE = 3000f;
        /** 抛体距离 */
        private static final float SPLIT_DIST = 800f;
        /** 最长飞行时间（保护） */
        private static final float MAX_TIME = 6f;

        /** 抛射减速率 */
        private static final float EJECT_DECEL = 256f;
        /** 转向超时（超时强制锁定，避免一直原地转） */
        private static final float TURN_TIMEOUT = 0.7f;
        /** 推进段最长 1 秒（飞得很快，够了） */
        private static final float BOOST_MAX_TIME = 2.0f;
        /** 点火但不移动的时间 */
        private static final float IGNITE_TIME = 0.35f;
        /** 停住后转向速度（度/秒） */
        private static final float TURN_DEG = 95f;

        private final MissileAPI missile;
        private final Vector2f start = new Vector2f();
        private float t = 0f;
        private int phase = 0;                 // 0 抛射 1 转向 2 加速
        private float lockedFacing = 0f;
        private float igniteTimer = -1f;
        private float turnTimer = 0f;
        private float ejectSpeed = -1f;
        private float boostTimer = 0f;
        private ShipAPI target;

        BoosterAI(MissileAPI missile) {
            this.missile = missile;
            this.start.set(missile.getLocation());
        }

        @Override
        public void advance(float amount) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || missile.isExpired() || engine.isPaused()) return;

            t += amount;
            if (t > MAX_TIME) {
                split(engine);
                return;
            }
            float myOwner = missile.getOwner();

            // ① 抛射：从初速减速到 0（滑出约 200su）
            if (phase == 0) {
                // 自己维护抛射速度（getVelocity 可能是副本，不能依赖它）
                if (ejectSpeed < 0f) ejectSpeed = EJECT_SPEED;
                ejectSpeed -= EJECT_DECEL * amount;
                if (ejectSpeed <= 0f) {
                    ejectSpeed = 0f;
                    setVel(0f, 0f);
                    phase = 1;
                    return;
                }
                float er = (float) Math.toRadians(missile.getFacing());
                setVel((float) Math.cos(er) * ejectSpeed, (float) Math.sin(er) * ejectSpeed);
                return;
            }

            // ② 停住后锁定并转向
            if (phase == 1) {
                if (target == null || !target.isAlive()) {
                    target = findTarget(engine, missile.getLocation(), LOCK_RANGE, myOwner, null);
                }
                if (target == null) {
                    phase = 2;   // 没目标就直接飞
                } else {
                    float desired = Misc.getAngleInDegrees(missile.getLocation(), target.getLocation());
                    float cur = missile.getFacing();
                    float diff = desired - cur;
                    diff = diff - 360f * (float) Math.floor((diff + 180f) / 360f);
                    float maxTurn = TURN_DEG * amount;
                    if (diff > maxTurn) diff = maxTurn;
                    if (diff < -maxTurn) diff = -maxTurn;
                    missile.setFacing(cur + diff);
                    turnTimer += amount;
                    if (Math.abs(diff) < 3f || turnTimer > TURN_TIMEOUT) {
                        phase = 2;
                        lockedFacing = missile.getFacing()
                                + (float) (Math.random() * 5.0 - 2.5);
                    }
                }
                return;
            }

            // ③ 点火但不移动（冷发射的"点火"瞬间）
            if (igniteTimer < 0f) {
                igniteTimer = IGNITE_TIME;
            }
            if (igniteTimer > 0f) {
                igniteTimer -= amount;
                missile.setFacing(lockedFacing);
                missile.getVelocity().set(0f, 0f);
                return;
            }

            // ④ 爆发式直线加速（不再机动）
            float sp2 = Math.min(BOOST_SPEED, missile.getVelocity().length() + BOOST_ACCEL * amount);
            float rad = (float) Math.toRadians(lockedFacing);
            missile.setFacing(lockedFacing);
            missile.getVelocity().set((float) Math.cos(rad) * sp2, (float) Math.sin(rad) * sp2);

            // ④ 抛体判定：进入 800 或推进段超过 1 秒
            boostTimer += amount;
            boolean close = target != null && target.isAlive()
                    && Misc.getDistance(missile.getLocation(), target.getLocation()) <= SPLIT_DIST;
            if (close || boostTimer > BOOST_MAX_TIME) split(engine);
        }

        private void setVel(float vx, float vy) {
            try {
                missile.getVelocity().set(vx, vy);
            } catch (Throwable ignored) {
            }
        }

        private void split(CombatEngineAPI engine) {
            try {
                Vector2f loc = new Vector2f(missile.getLocation());
                float facing = missile.getFacing();
                float rad = (float) Math.toRadians(facing);
                Vector2f vel = new Vector2f(
                        (float) Math.cos(rad) * WARHEAD_SPEED,
                        (float) Math.sin(rad) * WARHEAD_SPEED);

                ShipAPI source = missile.getSource();
                engine.spawnProjectile(source, null, WARHEAD_ID, loc, facing, vel);
                engine.spawnExplosion(loc, new Vector2f(), new Color(255, 220, 170, 255), 10f, 0.14f);
            } catch (Throwable ignored) {
            }
            engine.removeEntity(missile);
        }
    }

    // ================= 第二级 =================
    static class WarheadAI implements MissileAIPlugin {

        private static final float LOCK_RANGE = 1200f;
        private static final float SPEED = 730f;
        private static final float TURN_RATE = 260f;
        private static final float WEAVE_AMP = 12f;
        private static final float WEAVE_HZ = 2.4f;
        private static final float LIFE = 9f;

        private final MissileAPI missile;
        private float t = 0f;
        private ShipAPI target;

        WarheadAI(MissileAPI missile) {
            this.missile = missile;
        }

        @Override
        public void advance(float amount) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || missile.isExpired() || engine.isPaused()) return;

            t += amount;
            if (t > LIFE) {
                engine.removeEntity(missile);
                return;
            }

            if (target == null || !target.isAlive()) {
                target = findTarget(engine, missile.getLocation(), LOCK_RANGE,
                        missile.getOwner(), null);
            }

            float desired;
            if (target != null) {
                desired = Misc.getAngleInDegrees(missile.getLocation(), target.getLocation());
                desired += (float) Math.sin(t * WEAVE_HZ * 2f * Math.PI) * WEAVE_AMP;
            } else {
                desired = missile.getFacing();
            }

            float cur = missile.getFacing();
            float diff = desired - cur;
            diff = diff - 360f * (float) Math.floor((diff + 180f) / 360f);
            float maxTurn = TURN_RATE * amount;
            if (diff > maxTurn) diff = maxTurn;
            if (diff < -maxTurn) diff = -maxTurn;
            float facing = cur + diff;
            missile.setFacing(facing);

            float rad = (float) Math.toRadians(facing);
            missile.getVelocity().set((float) Math.cos(rad) * SPEED, (float) Math.sin(rad) * SPEED);
        }
    }

    // ================= 找目标（排除己方） =================
    static ShipAPI findTarget(CombatEngineAPI engine, Vector2f from, float range,
                              float myOwner, ShipAPI ignore) {
        ShipAPI best = null;
        float bestD = range;
        for (ShipAPI s : engine.getShips()) {
            if (s == null || !s.isAlive() || s == ignore) continue;
            if (s.isFighter() || s.isDrone()) continue;
            if (s.getOwner() == myOwner) continue;          // ★ 排除己方
            float d = Misc.getDistance(from, s.getLocation());
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }
}

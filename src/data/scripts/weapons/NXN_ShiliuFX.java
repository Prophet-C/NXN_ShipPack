package data.scripts.weapons;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.dark.shaders.distortion.WaveDistortion;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * 石流 3800mm 四联装（2+2）开火特效 + 齐射逻辑。
 *
 * 开火模式：左 2 管齐射(2 发) → 右 2 管齐射(2 发) → 冷却 5 秒
 * 特效：全部照搬主炮（炮口火光 + 爆发浓烟 + 细烟），参数一律减半。
 */
public class NXN_ShiliuFX implements EveryFrameWeaponEffectPlugin {

    // ===== 炮管几何 =====
    private static final float MUZZLE_DIST = 41f;
    private static final float GROUP_LATERAL = 9f;
    private static final float INNER_OFFSET = 3f;
    private static final float SHOT_SPEED = 760f;
    /** 炮管贴图内容高度（UAF 用 getBarrelSpriteAPI().getHeight()）*/
    private static final float BARREL_SPRITE_HEIGHT = 31f;
    /** UAF 尾迹配色 */
    private static final Color TRAIL_BLUE = new Color(108, 174, 255, 200);
    private static final Color TRAIL_GREY = new Color(40, 40, 40, 40);
    private static final Color TRAIL_SMOKE = new Color(40, 40, 40, 50);

    // ===== 炮口火光（主炮的一半）=====
    private static final int CORE_COUNT = 5;          // 主炮 9
    private static final int FLAME_COUNT = 39;        // 主炮 78
    private static final float CONE_ANGLE = 3.3f;     // 主炮 6.67
    private static final float CONE_RANGE = 85f;      // 主炮 170

    // ===== 烟雾（主炮的一半）=====
    private static final float SMOKE_DELAY = 0.05f;
    private static final float BURST_TIME = 0.20f;    // 爆发烟时长
    private static final float SMOKE_TIME = 1.25f;    // 总时长
    private static final float BURST_RANGE = 123f;    // 主炮 246
    private static final float BURST_ANGLE = 5f;      // 主炮 10
    private static final float BURST_RATE = 1.4f;     // 主炮 2.8
    private static final float BURST_SIZE_MIN = 22f;  // 主炮 45
    private static final float BURST_SIZE_RANGE = 45f;// 主炮 90
    private static final float FINE_RANGE = 27f;      // 主炮 54.67
    private static final float FINE_ANGLE = 0.85f;    // 主炮 1.67
    private static final float FINE_RATE = 2.1f;      // 主炮 4.2
    private static final float FINE_SIZE_MIN = 1f;    // 主炮 2
    private static final float FINE_SIZE_RANGE = 2f;  // 主炮 4

    private static final Color CORE_WHITE = new Color(255, 250, 225);
    private static final Color FIRE_ORANGE = new Color(255, 170, 60);
    private static final Color FIRE_DEEP = new Color(240, 100, 20);
    private static final Color BURST_COLOR = new Color(70, 70, 75, 160);
    private static final Color BURST_COLOR_2 = new Color(95, 95, 100, 140);
    private static final Color FINE_COLOR = new Color(150, 150, 156, 160);
    private static final Color FINE_COLOR_2 = new Color(180, 180, 186, 140);

    /** 单发烟雾发射器（每根炮管一个）*/
    /** 延迟抛壳队列 */
    private static class Pending {
        final float lx, ly, angle, spin, spd, delay;
        float t = 0f;

        Pending(float lx, float ly, float angle, float spin, float spd, float delay) {
            this.lx = lx;
            this.ly = ly;
            this.angle = angle;
            this.spin = spin;
            this.spd = spd;
            this.delay = delay;
        }
    }

    private final List<Pending> PENDING = new ArrayList<Pending>();
    /** 弹丸 → 跟随的重力扭曲 */
    private static final java.util.Map<DamagingProjectileAPI, WaveDistortion> SHELL_WAVES =
            new java.util.HashMap<DamagingProjectileAPI, WaveDistortion>();

    private static class Emitter {
        final Vector2f localPos;     // 相对舰船的局部坐标（跟随舰体）
        final float localAngle;
        float t = 0f;
        float acc = 0f;

        Emitter(Vector2f localPos, float localAngle) {
            this.localPos = localPos;
            this.localAngle = localAngle;
        }
    }

    private final List<Emitter> emitters = new ArrayList<Emitter>();
    private float prevCooldown = 0f;
    /** 弹壳存活时间（秒）*/    private static final float CASING_LIFE = 2.0f;
    /** 弹壳：{自旋速度, 已存活时间} */
    private static final java.util.Map<DamagingProjectileAPI, float[]> CASING_SPIN =
            new java.util.HashMap<DamagingProjectileAPI, float[]>();
    private int side = 1;            // 初始 +1 → 第一次翻转成 -1（左组先开火）

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || weapon == null || engine.isPaused()) return;
        ShipAPI ship = weapon.getShip();
        if (ship == null || !ship.isAlive()) return;

        float cd = weapon.getCooldownRemaining();
        boolean fired = cd > prevCooldown + 0.01f;
        prevCooldown = cd;

        if (fired) {
            side = -side;
            fireOnce(engine, ship, weapon);
        }

        advanceSmoke(engine, ship, amount);
        advanceCasingSpin(engine, amount);
        advanceCasingQueue(engine, ship, weapon, amount);
        NXN_ShiliuSuppress.advance(engine, amount);
        advanceShellDistortion(engine);
        advanceTrail(engine, amount);
    }

    // ================= 开火 =================
    private void fireOnce(CombatEngineAPI engine, ShipAPI ship, WeaponAPI weapon) {
        float rad = (float) Math.toRadians(weapon.getCurrAngle());
        float fx = (float) Math.cos(rad);
        float fy = (float) Math.sin(rad);
        float latx = -fy;
        float laty = fx;
        float groupLat = GROUP_LATERAL * side;

        try {
            Vector2f groupMuzzle = new Vector2f(
                    weapon.getLocation().x + fx * MUZZLE_DIST + latx * groupLat,
                    weapon.getLocation().y + fy * MUZZLE_DIST + laty * groupLat);
            spawnPartner(engine, ship, weapon, rad, latx, laty, groupLat);

            for (int b = -1; b <= 1; b += 2) {
                float lat = groupLat + INNER_OFFSET * b;
                Vector2f muzzle = new Vector2f(
                        weapon.getLocation().x + fx * MUZZLE_DIST + latx * lat,
                        weapon.getLocation().y + fy * MUZZLE_DIST + laty * lat);
                flashAt(engine, muzzle, rad);
                // 每根管登记一个烟雾发射器
                emitters.add(new Emitter(worldToLocal(ship, muzzle), rad - ship.getFacing()));
                // 抛壳：从炮塔后方甩出，向侧面飞散
                ejectCasing(ship, weapon, rad, fx, fy, latx, laty, lat);
            }

            // 弹丸尾迹 + 冲击波（UAF 逻辑，开火瞬间一次性生成）
            shellTrailOnce(engine, ship, weapon, groupMuzzle);

            // 重力扭曲：炮口一圈涟漪
            spawnRipple(groupMuzzle, ship.getVelocity(), 24f, 150f, 4f, 0.4f);
        } catch (Throwable t) {
        }
    }

    /** 抛壳：从炮塔后方甩出，带随机侧向速度和自旋 */
    /** 登记一枚延迟抛出的弹壳（开火后 0.3 秒）*/
    private void ejectCasing(ShipAPI ship, WeaponAPI weapon,
                             float rad, float fx, float fy, float latx, float laty,
                             float lateral) {
        // 位置：炮塔后方 26~36 像素（存成舰体局部坐标，舰船动了也跟着走）
        float back = 26f + (float) Math.random() * 10f;
        Vector2f world = new Vector2f(
                weapon.getLocation().x - fx * back + latx * lateral * 0.6f,
                weapon.getLocation().y - fy * back + laty * lateral * 0.6f);
        Vector2f local = worldToLocal(ship, world);

        // 朝向：炮塔正后方 ±20°（存相对舰体的角度）
        float worldAngle = weapon.getCurrAngle() + 180f
                + (float) (Math.random() * 40.0 - 20.0);
        float relAngle = worldAngle - ship.getFacing();

        float spin = (float) (Math.random() * 2.0 - 1.0) * (200f + (float) Math.random() * 360f);
        float spd = 40f + (float) Math.random() * 30f;   // 原 80~140 的一半

        PENDING.add(new Pending(local.x, local.y, relAngle, spin, spd, 0.3f));
    }

    /** 延迟队列：到点后真正生成弹壳 + 烟雾 */
    private void advanceCasingQueue(CombatEngineAPI engine, ShipAPI ship, WeaponAPI weapon,
                                    float amount) {
        if (PENDING.isEmpty()) return;
        Vector2f shipLoc = ship.getLocation();
        float facing = ship.getFacing();

        java.util.Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            p.t += amount;
            if (p.t < p.delay) continue;
            it.remove();

            Vector2f world = localToWorld(shipLoc, facing, new Vector2f(p.lx, p.ly));
            float ang = facing + p.angle;
            float ar = (float) Math.toRadians(ang);
            Vector2f vel = new Vector2f(
                    (float) Math.cos(ar) * p.spd + ship.getVelocity().x * 0.75f,
                    (float) Math.sin(ar) * p.spd + ship.getVelocity().y * 0.75f);

            try {
                DamagingProjectileAPI c = (DamagingProjectileAPI) engine.spawnProjectile(
                        ship, weapon, "NXN_shiliu_casing_launcher", world, ang, new Vector2f());
                if (c != null) {
                    c.getVelocity().set(vel.x, vel.y);
                    c.setFacing(ang);
                    CASING_SPIN.put(c, new float[]{p.spin, 0f});
                }
            } catch (Throwable t) {
                // 抛壳失败静默（不影响游戏）
            }

            // 抛壳时的烟雾
            for (int i = 0; i < 4; i++) {
                float a = (float) (Math.random() * Math.PI * 2);
                float dist = (float) Math.random() * 6f;
                Vector2f sp = new Vector2f(world.x + (float) Math.cos(a) * dist,
                        world.y + (float) Math.sin(a) * dist);
                Vector2f sv = new Vector2f(vel.x * 0.25f + (float) (Math.random() * 20 - 10),
                        vel.y * 0.25f + (float) (Math.random() * 20 - 10));
                engine.addSmokeParticle(sp, sv,
                        6f + (float) Math.random() * 8f,
                        0.35f,
                        0.45f + (float) Math.random() * 0.3f,
                        new Color(140, 140, 145, 90));
            }
        }
    }



    /** 补射：让本组变 2 发齐射 */
    /** 弹壳自旋（引擎按速度定朝向，这里叠加旋转）*/
    /** 弹壳：叠加自旋 + 自己计时销毁（不依赖引擎的 maxFlightTime）*/
    private void advanceCasingSpin(CombatEngineAPI engine, float amount) {
        if (CASING_SPIN.isEmpty()) return;
        java.util.Iterator<java.util.Map.Entry<DamagingProjectileAPI, float[]>> it =
                CASING_SPIN.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<DamagingProjectileAPI, float[]> e = it.next();
            DamagingProjectileAPI c = e.getKey();
            float[] v = e.getValue();
            if (c == null || v == null || !engine.isEntityInPlay(c)) {
                it.remove();
                continue;
            }
            // 自旋
            c.setFacing(c.getFacing() + v[0] * amount);

            // 红热发光（每帧一颗加色粒子，越接近消失越暗）
            float heat = 1f - Math.min(1f, v[1] / CASING_LIFE);
            if (Math.random() < 0.85f) {
                Vector2f cp = c.getLocation();
                engine.addHitParticle(new Vector2f(
                                cp.x + (float) (Math.random() * 4 - 2),
                                cp.y + (float) (Math.random() * 4 - 2)),
                        new Vector2f(c.getVelocity().x * 0.2f, c.getVelocity().y * 0.2f),
                        5f + (float) Math.random() * 5f,
                        0.55f * heat + 0.15f,
                        0.10f + (float) Math.random() * 0.08f,
                        new Color(255, (int) (70 + 90 * heat), 30, (int) (140 + 90 * heat)));
            }
            // 计时销毁（0.3 秒）
            v[1] += amount;
            float fadeStart = 1.20f;
            if (v[1] >= CASING_LIFE) {
                try {
                    engine.removeEntity(c);
                } catch (Throwable ignored) {
                }
                it.remove();
            } else if (v[1] > fadeStart) {
                // 尾部淡出：靠缩小视觉，简单可靠
                float k = 1f - (v[1] - fadeStart) / (CASING_LIFE - fadeStart);
                c.setCollisionRadius(Math.max(0.5f, 4f * k));
            }
        }
    }



    private void spawnPartner(CombatEngineAPI engine, ShipAPI ship, WeaponAPI weapon,
                              float rad, float latx, float laty, float groupLat) {
        float lat = groupLat - INNER_OFFSET * side;
        Vector2f pos = new Vector2f(
                weapon.getLocation().x + (float) Math.cos(rad) * MUZZLE_DIST + latx * lat,
                weapon.getLocation().y + (float) Math.sin(rad) * MUZZLE_DIST + laty * lat);

        float ang = weapon.getCurrAngle() + (float) (Math.random() * 2.0 - 1.0) * 2f;
        float ar = (float) Math.toRadians(ang);
        Vector2f vel = new Vector2f(
                (float) Math.cos(ar) * SHOT_SPEED + ship.getVelocity().x,
                (float) Math.sin(ar) * SHOT_SPEED + ship.getVelocity().y);

        DamagingProjectileAPI p = (DamagingProjectileAPI)
                engine.spawnProjectile(ship, weapon, "NXN_shiliu_quad", pos, ang, vel);
        if (p != null) p.getVelocity().set(vel.x, vel.y);
    }

    // ================= 炮口火光（主炮减半）=================
    private void flashAt(CombatEngineAPI engine, Vector2f muzzle, float rad) {
        for (int i = 0; i < CORE_COUNT; i++) {
            Vector2f p = cone(muzzle, 9f + (float) Math.random() * 15f, rad, CONE_ANGLE);
            engine.addHitParticle(p, new Vector2f(),
                    74f + (float) Math.random() * 60f,
                    1.2f + (float) Math.random() * 0.4f,
                    0.07f + (float) Math.random() * 0.05f, CORE_WHITE);
        }
        for (int i = 0; i < FLAME_COUNT; i++) {
            float dist = CONE_RANGE * (0.2f + (float) Math.random() * 0.8f);
            Vector2f p = cone(muzzle, dist, rad, CONE_ANGLE);
            Vector2f vel = new Vector2f(p.x - muzzle.x, p.y - muzzle.y);
            float len = vel.length();
            if (len > 0.01f) vel.scale((250f + (float) Math.random() * 250f) / len);
            engine.addHitParticle(p, vel,
                    30f + (float) Math.random() * 48f,
                    0.8f + (float) Math.random() * 0.4f,
                    0.10f + (float) Math.random() * 0.08f,
                    Math.random() < 0.5f ? FIRE_ORANGE : FIRE_DEEP);
        }
    }

    // ================= 烟雾（主炮减半：爆发浓烟 → 细烟）=================
    private void advanceSmoke(CombatEngineAPI engine, ShipAPI ship, float amount) {
        if (emitters.isEmpty()) return;
        Vector2f shipLoc = ship.getLocation();
        float shipFacing = ship.getFacing();

        Iterator<Emitter> it = emitters.iterator();
        while (it.hasNext()) {
            Emitter e = it.next();
            Vector2f now = localToWorld(shipLoc, shipFacing, e.localPos);
            float angleNow = shipFacing + e.localAngle;

            if (e.t >= SMOKE_DELAY && e.t <= SMOKE_TIME) {
                if (e.t <= BURST_TIME) {
                    e.acc += BURST_RATE;
                    while (e.acc >= 1f) {
                        e.acc -= 1f;
                        spawnSmoke(engine, now, angleNow, BURST_RANGE,
                                BURST_SIZE_MIN, BURST_SIZE_RANGE, BURST_ANGLE,
                                Math.random() < 0.8f ? BURST_COLOR : BURST_COLOR_2);
                    }
                } else {
                    e.acc += FINE_RATE;
                    while (e.acc >= 1f) {
                        e.acc -= 1f;
                        spawnSmoke(engine, now, angleNow, FINE_RANGE,
                                FINE_SIZE_MIN, FINE_SIZE_RANGE, FINE_ANGLE,
                                Math.random() < 0.8f ? FINE_COLOR : FINE_COLOR_2);
                    }
                }
            }

            e.t += amount;
            if (e.t > SMOKE_TIME) it.remove();
        }
    }

    private static void spawnSmoke(CombatEngineAPI engine, Vector2f origin, float angle,
                                   float range, float sizeMin, float sizeRange,
                                   float halfAngle, Color color) {
        float dist = range * (0.1f + (float) Math.random() * 0.9f);
        Vector2f p = cone(origin, dist, angle, halfAngle);
        Vector2f vel = new Vector2f(p.x - origin.x, p.y - origin.y);
        float len = vel.length();
        if (len > 0.01f) vel.scale((28f + (float) Math.random() * 44f) / len);
        engine.addSmokeParticle(p, vel,
                sizeMin + (float) Math.random() * sizeRange,
                0.327f + (float) Math.random() * 0.14f,
                0.76f + (float) Math.random() * 0.21f,
                color);
    }

    /** 跟随弹丸的重力扭曲（复刻 GraphicsLib 对 mjolnir_shot 的处理）*/
    private void advanceShellDistortion(CombatEngineAPI engine) {
        try {
            for (DamagingProjectileAPI p : engine.getProjectiles()) {
                if (p == null) continue;
                if (!"NXN_shiliu_shot".equals(p.getProjectileSpecId())) continue;

                WaveDistortion w = SHELL_WAVES.get(p);
                if (w == null) {
                    if (p.getElapsed() > 0.15f) continue;      // 只给刚出膛的弹丸建
                    w = new WaveDistortion(new Vector2f(p.getLocation()), new Vector2f());
                    w.setIntensity(5f);
                    w.setSize(50f);
                    w.flip(true);
                    w.setLifetime(6f);                          // 长寿命，手动移除
                    DistortionShader.addDistortion(w);
                    SHELL_WAVES.put(p, w);
                } else {
                    w.setLocation(new Vector2f(p.getLocation()));
                }
            }

            if (!SHELL_WAVES.isEmpty()) {
                java.util.Iterator<java.util.Map.Entry<DamagingProjectileAPI, WaveDistortion>> it =
                        SHELL_WAVES.entrySet().iterator();
                while (it.hasNext()) {
                    java.util.Map.Entry<DamagingProjectileAPI, WaveDistortion> e = it.next();
                    DamagingProjectileAPI p = e.getKey();
                    if (p == null || !engine.isEntityInPlay(p)) {
                        try {
                            DistortionShader.removeDistortion(e.getValue());
                        } catch (Throwable ignored) {
                        }
                        it.remove();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 重力扭曲涟漪（雷神炮同款：GraphicsLib RippleDistortion）*/
    private void spawnRipple(Vector2f loc, Vector2f vel, float startSize, float maxSize,
                             float intensity, float life) {
        try {
            RippleDistortion ripple = new RippleDistortion(new Vector2f(loc), new Vector2f(vel));
            ripple.setSize(startSize);
            ripple.setMaxSize(maxSize);
            ripple.setIntensity(intensity);
            ripple.setMaxIntensity(intensity);
            ripple.setLifetime(life);
            ripple.setArc(360f, 360f);
            ripple.setFrameRate(2f);
            ripple.setAutoFadeIntensityTime(life * 0.7f);
            ripple.setAutoFadeSizeTime(life);
            DistortionShader.addDistortion(ripple);
        } catch (Throwable ignored) {
        }
    }

    // ================= 弹丸尾迹：逐行复刻 UAF uaf_chemicalWeaponEffect =================
    /** 弹体拖尾：密集淡色烟柱（对齐嘶嚎碎甲炮的观感）*/
    private void advanceTrail(CombatEngineAPI engine, float amount) {
        // 弹道渲染交给 BALLISTIC_AS_BEAM（与嘶嚎碎甲炮一致），不额外加粒子
    }



    /** 开火瞬间生成弹丸尾迹（UAF 原始逻辑）*/
    private void shellTrailOnce(CombatEngineAPI engine, ShipAPI ship, WeaponAPI weapon,
                                Vector2f projLoc) {
        float L = 80f;
        try {
            if (weapon.getMuzzleFlashSpec() != null) {
                L = weapon.getMuzzleFlashSpec().getLength();
            }
        } catch (Throwable ignored) {
        }
        float shipVx = ship.getVelocity().x;
        float shipVy = ship.getVelocity().y;
        float ang = weapon.getCurrAngle();
        float cx = (float) Math.cos(Math.toRadians(ang));
        float cy = (float) Math.sin(Math.toRadians(ang));

        Color pc = FIRE_ORANGE;
        try {
            if (weapon.getMuzzleFlashSpec() != null
                    && weapon.getMuzzleFlashSpec().getParticleColor() != null) {
                pc = weapon.getMuzzleFlashSpec().getParticleColor();
            }
        } catch (Throwable ignored) {
        }

        // ① 出膛闪光：尺寸 L/2，寿命 0.1
        engine.addHitParticle(projLoc, ship.getVelocity(), L / 2f, 1f, 0.1f,
                new Color(255, 255, 200, 255));

        // ② 6 颗星云：位置 0~L/3，速度 0~30，尺寸 L*0.3~0.5，寿命 0.5~1.5，灰→炮口色 40%
        for (int i = 0; i < 6; i++) {
            Vector2f point = new Vector2f(projLoc.x + cx * rnd(0f, L / 3f),
                    projLoc.y + cy * rnd(0f, L / 3f));
            Vector2f vel = new Vector2f(cx * rnd(0f, 30f) + shipVx,
                    cy * rnd(0f, 30f) + shipVy);
            engine.addNebulaParticle(point, vel, rnd(L * 0.3f, L * 0.5f),
                    1.5f, 0.1f, 0.3f, rnd(0.5f, 1.5f), lerp(TRAIL_GREY, pc, 0.4f));
        }

        // ③ LARGE 专属：14 颗大星云，尺寸 L*0.5~0.7，寿命 3~5，纯灰
        for (int i = 0; i < 14; i++) {
            Vector2f point = new Vector2f(projLoc.x + cx * rnd(0f, L / 3f),
                    projLoc.y + cy * rnd(0f, L / 3f));
            Vector2f vel = new Vector2f(cx * rnd(0f, L / 2f) + shipVx,
                    cy * rnd(0f, L / 2f) + shipVy);
            engine.addNebulaParticle(point, vel, rnd(L * 0.5f, L * 0.7f),
                    2.0f, 0.1f, 0.3f, rnd(3.0f, 5.0f), TRAIL_GREY);
        }

        // ⑤ 炮口冲击波（UAF：L*0.4 → L*0.25，持续 particleDuration，加色）
        float shockDur = 0.1f;
        try {
            if (weapon.getMuzzleFlashSpec() != null) {
                shockDur = weapon.getMuzzleFlashSpec().getParticleDuration();
            }
        } catch (Throwable ignored) {
        }
        NXN_ShockwaveFX.spawn(engine, projLoc.x, projLoc.y,
                L * 0.4f, L * 0.25f, shockDur, ang + 90f, pc);

        // ④ 3 组后喷：黑烟 + 淡蓝紫拖尾
        float rearDist = BARREL_SPRITE_HEIGHT * 0.5f;
        for (int i = 0; i < 3; i++) {
            Vector2f rearPos = new Vector2f(projLoc.x - cx * rearDist, projLoc.y - cy * rearDist);
            Vector2f vel = new Vector2f(-cx * rnd(0f, 10f) + shipVx, -cy * rnd(0f, 10f) + shipVy);

            engine.addSmokeParticle(rearPos, vel, rnd(L * 0.3f, L * 0.5f),
                    1.0f, rnd(1.0f, 2.0f), TRAIL_SMOKE);
            engine.addHitParticle(rearPos, ship.getVelocity(), rnd(L * 0.2f, L * 0.4f),
                    1.0f, rnd(0.1f, 0.3f), TRAIL_BLUE);
        }
    }

    /** UAF 用的随机区间 */
    private static float rnd(float a, float b) {
        return a + (float) Math.random() * (b - a);
    }

    private static Color lerp(Color a, Color b, float t) {
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    // ================= 工具 =================
    private static Vector2f cone(Vector2f origin, float dist, float rad, float halfAngleDeg) {
        float a = rad + (float) Math.toRadians((Math.random() * 2.0 - 1.0) * halfAngleDeg);
        return new Vector2f(origin.x + (float) Math.cos(a) * dist,
                origin.y + (float) Math.sin(a) * dist);
    }

    private static Vector2f worldToLocal(ShipAPI ship, Vector2f world) {
        double rad = Math.toRadians(-ship.getFacing());
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float dx = world.x - ship.getLocation().x;
        float dy = world.y - ship.getLocation().y;
        return new Vector2f(dx * cos - dy * sin, dx * sin + dy * cos);
    }

    private static Vector2f localToWorld(Vector2f shipLoc, float facing, Vector2f local) {
        double rad = Math.toRadians(facing);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        return new Vector2f(shipLoc.x + local.x * cos - local.y * sin,
                shipLoc.y + local.x * sin + local.y * cos);
    }
}

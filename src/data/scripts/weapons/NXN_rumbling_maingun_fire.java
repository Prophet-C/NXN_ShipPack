package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * 地鸣级主炮开火特效：
 *   1) 每一发：该炮管炮口火光（小核心 + 大锥形火舌）
 *   2) 每一发：该炮管自己的爆发烟（0.2s）→ 细烟（1.05s）—— 三发各自独立播放
 *   3) 弹体飞行期间：弹体后方拖出火星拖尾
 *
 * 炮管位置由 .wpn 的三个 turretOffsets 决定，开火时按"交替顺序"取对应炮管，
 * 保证三连发时三根炮管各自出烟，而不是都挤在最后一根上。
 */
public class NXN_rumbling_maingun_fire implements EveryFrameWeaponEffectPlugin {

    // ==== 位置（像素）====
    private static final float BARREL_BACK = 30f;       // 火光位置：炮口后 30px
    private static final float SMOKE_BACK = 20f;        // 烟雾比火光再向后 20px

    // ==== 炮管（.wpn turretOffsets + 管长）====
    private static final float[] BARREL_X = {78f, 78.5f, 78.5f};
    private static final float[] BARREL_Y = {11f, 0f, -11f};
    private static final float BARREL_LENGTH = 180f;
    private static final String MAIN_PROJ_ID = "NXN_rumbling_maingun_ballistic_shot";

    // ==== 火光 ====
    private static final float CONE_ANGLE = 6.67f;      // 火舌锥半角
    private static final float CONE_RANGE = 170f;       // 火舌长度
    private static final int CORE_COUNT = 9;            // 白热核心粒子数
    private static final int CONE_COUNT = 78;           // 火舌粒子数

    // ==== 烟雾时间 ====
    private static final float SMOKE_DELAY = 0.05f;
    private static final float BURST_TIME = 0.20f;      // 爆发烟持续 0.2 秒
    private static final float SMOKE_TIME = 1.25f;      // 总时长（0.2s 爆发 + 1.05s 细烟）

    // ==== 阶段A：爆发浓烟 ====
    private static final float BURST_RANGE = 246f;
    private static final float BURST_ANGLE = 10f;
    private static final float BURST_RATE = 2.8f;
    private static final float BURST_SIZE_MIN = 45f;    // 浓烟尺寸 ×2（原 22.5）
    private static final float BURST_SIZE_RANGE = 90f;

    // ==== 阶段B：细烟 ====
    private static final float FINE_RANGE = 54.67f;
    private static final float FINE_ANGLE = 1.67f;
    private static final float FINE_RATE = 4.2f;
    private static final float FINE_SIZE_MIN = 2f;      // 缩小到 1/5（原 10）
    private static final float FINE_SIZE_RANGE = 4f;

    // ==== 弹体拖尾 ====
    private static final float TRAIL_TIME = 2.50f;
    private static final int TRAIL_PER_FRAME = 1;

    // ==== 颜色 ====
    private static final Color CORE_WHITE = new Color(255, 250, 225);
    private static final Color FIRE_ORANGE = new Color(255, 170, 60);
    private static final Color FIRE_DEEP = new Color(240, 100, 20);
    private static final Color BURST_COLOR = new Color(70, 70, 75, 160);
    private static final Color BURST_COLOR_2 = new Color(95, 95, 100, 140);
    private static final Color FINE_COLOR = new Color(150, 150, 156, 160);   // 细烟偏白
    private static final Color FINE_COLOR_2 = new Color(180, 180, 186, 140);

    /** 单发对应的烟雾发射器（每发独立，保证三根炮管各自出烟） */
    private static class SmokeEmitter {
        /** 相对舰船的局部坐标（跟着船走，避免行进中烟雾和炮口脱节） */
        final Vector2f localFlash;
        final Vector2f localSmoke;
        /** 相对船体朝向的角度 */
        final float localAngle;
        float t = 0f;
        float acc = 0f;
        boolean flashDone = false;

        SmokeEmitter(Vector2f localFlash, Vector2f localSmoke, float localAngle) {
            this.localFlash = localFlash;
            this.localSmoke = localSmoke;
            this.localAngle = localAngle;
        }
    }

    private final List<SmokeEmitter> emitters = new ArrayList<>();
    private final java.util.Set<DamagingProjectileAPI> usedProjectiles = new java.util.HashSet<>();
    private int shotIndex = -1;
    private float sinceFire = 99f;
    private float prevCooldown = 0f;
    private float prevReloadCd = 0f;
    private boolean inReload = false;
    /** 装弹音最小间隔（毫秒），防止连发时刷屏 */
    private static final long RELOAD_SOUND_GAP_MS = 2800L;
    /** 最后一发之后多久播放装弹音（秒） */
    private static final float RELOAD_SOUND_DELAY = 2.0f;
    private static long lastReloadSoundMs = 0L;
    private static int gunDiagLog = 0;
    /** 长冷却是否已开始（用于装弹音判定） */
    private boolean reloadArmed = false;
    /** 是否已经倒数进入最后 5 秒（排除连发内部瞬间归零） */
    private boolean sawLowCd = false;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || weapon == null || weapon.getShip() == null) return;

        // 装弹音：音效全长 2.46s（上膛 1.48 + 静音 0.1 + 弹壳 0.85）
        //   → 在冷却剩 2.5~2.0 秒时开播，音效正好在开火那一刻播完
        try {
            float cdNow = weapon.getCooldownRemaining();
            if (cdNow > 20f) {
                reloadArmed = true;                 // 长冷却开始（chargedown 28）
                sawLowCd = false;
            } else if (reloadArmed) {
                if (cdNow <= 2.5f && cdNow > 2.0f) {
                    sawLowCd = true;
                }
                if (sawLowCd && cdNow <= 2.0f) {
                    reloadArmed = false;
                    sawLowCd = false;
                    long now = System.currentTimeMillis();
                    if (now - lastReloadSoundMs >= RELOAD_SOUND_GAP_MS) {
                        lastReloadSoundMs = now;
                        if (gunDiagLog++ < 20) {
                        }
                        Global.getSoundPlayer().playSound("NXN_gun_reload", 1f, 0.9f,
                                weapon.getShip().getLocation(), weapon.getShip().getVelocity());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (engine.isPaused()) return;


        // 开火检测：每发新建一个发射器，按交替顺序取炮管
        float cd = weapon.getCooldownRemaining();
        if (cd > prevCooldown + 0.05f) {
            shotIndex = (shotIndex + 1) % BARREL_X.length;
            emitters.add(makeEmitter(engine, weapon));
            sinceFire = 0f;
            inReload = true;
            if (gunDiagLog++ < 20) {
            }
        }
        prevCooldown = cd;
        sinceFire += amount;

        // 弹体拖尾火光
        if (sinceFire <= TRAIL_TIME) {
            spawnTrail(engine, weapon);
        }

        // 逐发推进各自的火光与烟雾
        Iterator<SmokeEmitter> it = emitters.iterator();
        while (it.hasNext()) {
            SmokeEmitter e = it.next();

            // 每帧把局部坐标换算成当前世界坐标（跟随舰船）
            Vector2f shipLoc = weapon.getShip().getLocation();
            float shipFacing = weapon.getShip().getFacing();
            Vector2f flashNow = localToWorld(shipLoc, shipFacing, e.localFlash);
            Vector2f smokeNow = localToWorld(shipLoc, shipFacing, e.localSmoke);
            float angleNow = shipFacing + e.localAngle;

            if (!e.flashDone) {
                e.flashDone = true;
                spawnFlash(engine, flashNow, angleNow);
            }

            if (e.t >= SMOKE_DELAY && e.t <= SMOKE_TIME) {
                if (e.t <= BURST_TIME) {
                    e.acc += BURST_RATE;
                    while (e.acc >= 1f) {
                        e.acc -= 1f;
                        spawnSmoke(engine, smokeNow, angleNow, BURST_RANGE,
                                BURST_SIZE_MIN, BURST_SIZE_RANGE, BURST_ANGLE, burstColor());
                    }
                } else {
                    e.acc += FINE_RATE;
                    while (e.acc >= 1f) {
                        e.acc -= 1f;
                        spawnSmoke(engine, smokeNow, angleNow, FINE_RANGE,
                                FINE_SIZE_MIN, FINE_SIZE_RANGE, FINE_ANGLE, fineColor());
                    }
                }
            }

            e.t += amount;
            if (e.t > SMOKE_TIME) it.remove();
        }
    }

    /** 以本次开火生成的弹体位置为炮口，计算火光/烟雾锚点（每发独立） */
    private SmokeEmitter makeEmitter(CombatEngineAPI engine, WeaponAPI weapon) {
        Vector2f loc = weapon.getLocation();
        DamagingProjectileAPI best = null;
        float bestD = Float.MAX_VALUE;
        for (DamagingProjectileAPI p : engine.getProjectiles()) {
            if (p.getWeapon() != weapon) continue;
            if (!MAIN_PROJ_ID.equals(p.getProjectileSpecId())) continue;
            if (usedProjectiles.contains(p)) continue;
            float dx = p.getLocation().x - loc.x;
            float dy = p.getLocation().y - loc.y;
            float d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }

        Vector2f tip;
        float facing;
        if (best != null) {
            usedProjectiles.add(best);
            tip = new Vector2f(best.getLocation());
            facing = best.getFacing();
        } else {
            // 兜底：按炮管几何估算
            float bx = BARREL_X[shotIndex] + BARREL_LENGTH;
            float by = BARREL_Y[shotIndex];
            facing = weapon.getCurrAngle();
            double rad0 = Math.toRadians(facing);
            tip = new Vector2f(loc.x + (float) (Math.cos(rad0) * bx - Math.sin(rad0) * by),
                               loc.y + (float) (Math.sin(rad0) * bx + Math.cos(rad0) * by));
        }

        double rad = Math.toRadians(facing);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        Vector2f flashAnchor = new Vector2f(tip.x - cos * BARREL_BACK, tip.y - sin * BARREL_BACK);
        Vector2f smokeAnchor = new Vector2f(tip.x - cos * (BARREL_BACK + SMOKE_BACK),
                                            tip.y - sin * (BARREL_BACK + SMOKE_BACK));

        // 转成"相对舰船"的局部坐标
        Vector2f shipLoc = weapon.getShip().getLocation();
        float shipFacing = weapon.getShip().getFacing();
        return new SmokeEmitter(
                worldToLocal(shipLoc, shipFacing, flashAnchor),
                worldToLocal(shipLoc, shipFacing, smokeAnchor),
                facing - shipFacing);
    }

    private static void spawnSmoke(CombatEngineAPI engine, Vector2f origin, float angle, float range,
                                   float sizeMin, float sizeRange, float halfAngle, Color color) {
        float dist = range * (0.1f + (float) Math.random() * 0.9f);
        Vector2f p = pointInCone(origin, dist, angle, halfAngle);
        Vector2f vel = directionFrom(p, origin);
        vel.scale(28f + (float) Math.random() * 44f);
        engine.addSmokeParticle(p, vel,
                sizeMin + (float) Math.random() * sizeRange,
                0.327f + (float) Math.random() * 0.14f,
                0.56f + 0.2f + (float) Math.random() * 0.21f,
                color);
    }

    private static Color burstColor() {
        return Math.random() < 0.8f ? BURST_COLOR : BURST_COLOR_2;
    }

    /** 细烟：偏白 */
    private static Color fineColor() {
        return Math.random() < 0.8f ? FINE_COLOR : FINE_COLOR_2;
    }

    /** 给本武器的弹体加拖尾火光 */
    private static void spawnTrail(CombatEngineAPI engine, WeaponAPI weapon) {
        for (DamagingProjectileAPI p : engine.getProjectiles()) {
            if (p.getWeapon() != weapon) continue;
            for (int i = 0; i < TRAIL_PER_FRAME; i++) {
                float ang = p.getFacing() + 180f + (float) (Math.random() * 2.0 - 1.0) * 25f;
                double rad = Math.toRadians(ang);
                float back = 4f + (float) Math.random() * 10f;
                Vector2f pos = new Vector2f(p.getLocation().x + (float) Math.cos(rad) * back,
                                            p.getLocation().y + (float) Math.sin(rad) * back);
                Vector2f vel = new Vector2f(-(float) Math.cos(rad), -(float) Math.sin(rad));
                vel.scale(60f + (float) Math.random() * 120f);
                engine.addHitParticle(pos, vel,
                        6f + (float) Math.random() * 10f,
                        1.5f + (float) Math.random() * 0.9f,
                        0.12f + (float) Math.random() * 0.18f,
                        Math.random() < 0.5f ? FIRE_ORANGE : FIRE_DEEP);
            }
        }
    }

    private static void spawnFlash(CombatEngineAPI engine, Vector2f anchor, float angle) {
        // 白热核心
        for (int i = 0; i < CORE_COUNT; i++) {
            Vector2f p = pointInCone(anchor, 18f + (float) Math.random() * 30f, angle, CONE_ANGLE);
            engine.addHitParticle(p, new Vector2f(0f, 0f),
                    147f + (float) Math.random() * 120f,
                    2.4f + (float) Math.random() * 0.8f,
                    0.14f + (float) Math.random() * 0.10f,
                    CORE_WHITE);
        }
        // 锥形橙色火舌
        for (int i = 0; i < CONE_COUNT; i++) {
            float dist = CONE_RANGE * (0.2f + (float) Math.random() * 0.8f);
            Vector2f p = pointInCone(anchor, dist, angle, CONE_ANGLE);
            Vector2f vel = directionFrom(p, anchor);
            vel.scale(500f + (float) Math.random() * 500f);
            engine.addHitParticle(p, vel,
                    60f + (float) Math.random() * 96f,
                    1.6f + (float) Math.random() * 0.8f,
                    0.20f + (float) Math.random() * 0.16f,
                    Math.random() < 0.5f ? FIRE_ORANGE : FIRE_DEEP);
        }
    }

    private static Vector2f pointInCone(Vector2f origin, float dist, float angle, float halfAngleDeg) {
        float a = angle + (float) (Math.random() * 2.0 - 1.0) * halfAngleDeg;
        double rad = Math.toRadians(a);
        return new Vector2f(origin.x + (float) Math.cos(rad) * dist,
                            origin.y + (float) Math.sin(rad) * dist);
    }

    private static Vector2f directionFrom(Vector2f from, Vector2f to) {
        Vector2f v = new Vector2f(from.x - to.x, from.y - to.y);
        float len = v.length();
        if (len < 0.001f) return new Vector2f(0f, 0f);
        v.scale(1f / len);
        return v;
    }

    /** 局部坐标 -> 世界坐标 */
    private static Vector2f localToWorld(Vector2f shipLoc, float shipFacing, Vector2f local) {
        double rad = Math.toRadians(shipFacing);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        return new Vector2f(
                shipLoc.x + local.x * cos - local.y * sin,
                shipLoc.y + local.x * sin + local.y * cos);
    }

    /** 世界坐标 -> 局部坐标 */
    private static Vector2f worldToLocal(Vector2f shipLoc, float shipFacing, Vector2f world) {
        double rad = Math.toRadians(-shipFacing);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float dx = world.x - shipLoc.x;
        float dy = world.y - shipLoc.y;
        return new Vector2f(dx * cos - dy * sin, dx * sin + dy * cos);
    }
}

package data.scripts.weapons;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * 烟囱排烟（装饰武器）。
 *
 * 物理：
 *   - 烟没有自带推力，只继承 88% 船速 —— 船超过烟，自然形成拖尾
 *   - 拖尾长度靠**长持续时间**（约 7~11 秒）
 *
 * 多点喷发：整个烟囱同时冒烟（1 个中心点 + 6 个周边点，共 7 点）
 * 颜色随赋能平滑过渡：0~18% 白 → 18~58% 渐黑 → 58% 以上浓黑 + 火星
 */
public class NXN_FunnelSmoke implements EveryFrameWeaponEffectPlugin {

    // ===== 多点喷发（相对烟囱中心的偏移：横向, 前向）=====
    private static final float[][] EMIT_POINTS = {
            {0f, 0f},
            {-6f, 0f}, {6f, 0f},
            {-3f, 4f}, {3f, 4f},
            {-3f, -4f}, {3f, -4f},
    };

    // ===== 密度 =====
    private static final float PUFF_INTERVAL = 0.07f;
    private static final int PUFF_MIN = 1;
    private static final int PUFF_MAX = 2;

    // ===== 尺寸（像素）=====
    private static final float SIZE_MIN = 6f;
    private static final float SIZE_RANGE = 6f;

    // ===== 寿命：×3（原来 2.4~3.6，现在 7.2~10.8）=====
    /** 烟柱最大长度（像素）：超过后不再往后延伸 */
    private static final float MAX_TRAIL_LEN = 150f;
    /** 烟柱最小长度（像素）：低速时也要有这么多烟 */
    private static final float MIN_TRAIL_LEN = 70f;
    /** 寿命上下限（低速时需要很长寿命才能凑够最小长度） */
    private static final float DURATION_MAX = 9.0f;
    private static final float DURATION_FLOOR = 0.6f;

    private static final float DURATION_MIN = 7.2f;
    private static final float DURATION_RANGE = 3.6f;

    // ===== 运动 =====
    private static final float VEL_INHERIT = 0.50f;
    /** 位置抖动 */
    private static final float POS_JITTER = 3f;
    /** 速度抖动：原来 9，扩散角度要求减半 -> 4.5 */
    private static final float VEL_JITTER = 6.75f;

    // ===== 颜色 =====
    private static final Color C_WHITE = new Color(150, 150, 152, 78);
    private static final Color C_BLACK = new Color(38, 38, 42, 175);
    private static final Color C_STEAM = new Color(240, 244, 250, 150);
    private static final Color C_EMBER = new Color(255, 145, 55, 225);

    private float acc = 0f;
    /** 平滑后的赋能，避免颜色突变 */
    private float smoothedFlux = 0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || weapon == null) return;
        if (engine.isPaused()) return;

        ShipAPI ship = weapon.getShip();
        if (ship == null || !ship.isAlive()) return;

        // 赋能做时间平滑（约 2 秒时间常数），让烟色渐变而不是跳变
        float rawFlux = Math.min(1f, ship.getFluxTracker().getFluxLevel());
        smoothedFlux += (rawFlux - smoothedFlux) * Math.min(1f, amount * 0.5f);

        acc += amount;
        if (acc < PUFF_INTERVAL) return;
        acc = 0f;

        // ---- 状态 ----
        float maxSpeed = Math.max(1f, ship.getMutableStats().getMaxSpeed().getModifiedValue());
        float speedFrac = Math.min(1f, ship.getVelocity().length() / maxSpeed);
        float flux = smoothedFlux;
        boolean overload = ship.getFluxTracker().isOverloaded();

        float blackness = smoothstep(0.08f, 0.80f, flux);
        // 过载时直接浓黑烟（不再是白蒸汽）
        Color base = overload ? C_BLACK : lerp(C_WHITE, C_BLACK, blackness);

        float density = 0.35f + speedFrac * 0.35f + flux * 0.45f;
        density = Math.min(1f, density);
        int perPoint = PUFF_MIN + (int) Math.round(density * (PUFF_MAX - PUFF_MIN));

        // 火花概率：赋能 55% 起出现；过载时翻倍
        float emberChance = smoothstep(0.55f, 0.72f, flux) * 0.35f;
        if (overload) emberChance = Math.min(0.95f, emberChance * 2f + 0.20f);

        // 火花寿命受"存在距离 ≤ 100px"约束
        float sparkRel = Math.max(8f, ship.getVelocity().length() * (1f - VEL_INHERIT));
        float sparkDur = Math.min(0.85f, 100f / sparkRel);
        if (sparkDur < 0.15f) sparkDur = 0.15f;
        sparkDur *= 1.5f;                      // 火星存在时间 +50%

        Vector2f loc = weapon.getLocation();
        float facingRad = (float) Math.toRadians(ship.getFacing());
        float fwdX = (float) Math.cos(facingRad);
        float fwdY = (float) Math.sin(facingRad);
        // 横向（局部 +y 方向）
        float latX = -fwdY;
        float latY = fwdX;

        for (float[] pt : EMIT_POINTS) {
            float lateral = pt[0];
            float forward = pt[1];
            float bx = loc.x + latX * lateral + fwdX * forward;
            float by = loc.y + latY * lateral + fwdY * forward;

            for (int i = 0; i < perPoint; i++) {
                Vector2f p = new Vector2f(bx + rand() * POS_JITTER, by + rand() * POS_JITTER);

                Vector2f vel = new Vector2f(
                        ship.getVelocity().x * VEL_INHERIT + rand() * VEL_JITTER,
                        ship.getVelocity().y * VEL_INHERIT + rand() * VEL_JITTER);

                float size = SIZE_MIN + (float) Math.random() * SIZE_RANGE;
                size *= (0.85f + density * 0.35f);
                // 寿命按"烟柱长度 / 相对漂移速度"计算：
            //   长度在 [MIN_TRAIL_LEN, MAX_TRAIL_LEN] 之间随机，
            //   低速时自动拉长寿命，保证烟柱不会缩成一团
            float relSpeed = Math.max(8f, ship.getVelocity().length() * (1f - VEL_INHERIT));
            float durLo = MIN_TRAIL_LEN / relSpeed;
            float durHi = MAX_TRAIL_LEN / relSpeed;
            float dur = durLo + (float) Math.random() * (durHi - durLo);
            if (dur > DURATION_MAX) dur = DURATION_MAX;
            if (dur < DURATION_FLOOR) dur = DURATION_FLOOR;

                float brightness = 0.35f + density * 0.30f;
                engine.addSmokeParticle(p, vel, size, brightness, dur, base);

                // 火花：寿命受"存在距离 ≤ 100px"约束
                if (emberChance > 0f && Math.random() < emberChance) {
                    engine.addHitParticle(p, new Vector2f(vel.x, vel.y),
                            3f + (float) Math.random() * 4f, 1.3f,
                            sparkDur * (0.8f + (float) Math.random() * 0.4f), C_EMBER);
                }
            }
        }
    }

    private static float rand() {
        return (float) Math.random() * 2f - 1f;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        if (edge1 <= edge0) return x >= edge1 ? 1f : 0f;
        float t = (x - edge0) / (edge1 - edge0);
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        return t * t * (3f - 2f * t);
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
}

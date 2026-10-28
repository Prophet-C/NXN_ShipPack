package data.scripts.weapons;

import java.awt.Color;

import org.dark.shaders.light.LightShader;
import org.dark.shaders.light.StandardLight;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * 「断层」视觉：
 *   1. 弹丸飞行中的红色火星
 *   2. 开火：闪光 + 锥形火焰 + 收尾黑烟
 *   3. 单光源：全场只用一个光源，跟着一颗弹丸走（避免叠加过曝）
 */
public class NXN_DuancengSparks implements EveryFrameWeaponEffectPlugin {

    private static final String SHOT_ID = "NXN_duanceng_twin_shot";
    private static final String FLAK_ID = "NXN_duanceng_flak_shot";

    private static final float SPARK_CHANCE = 0.25f;
    private static final float SPARK_SIZE_MIN = 2.2f;
    private static final float SPARK_SIZE_RANGE = 2.2f;
    private static final float SPARK_DUR_MIN = 0.14f;
    private static final float SPARK_DUR_RANGE = 0.28f;
    private static final float SPARK_BACK_MIN = 2f;
    private static final float SPARK_BACK_MAX = 38f;
    private static final float SPARK_SPEED_MIN = 30f;
    private static final float SPARK_SPEED_MAX = 190f;
    private static final float SPARK_ANGLE_SPREAD = 55f;
    private static final Color SPARK = new Color(255, 90, 55, 225);
    private static final Color SPARK_HOT = new Color(255, 190, 140, 215);

    private static final float MUZZLE_DIST = 27f;
    /** 双管左右偏移（交替） */
    private static final float BARREL_LATERAL = 5f;
    private static final float FLASH_SIZE = 11f;
    private static final float FLASH_TIME = 0.07f;
    private static final int FLAME_COUNT = 6;
    private static final float FLAME_CONE = 14f;
    private static final int SMOKE_COUNT = 3;

    private static final float LIGHT_SIZE = 150f;
    private static final float LIGHT_INTENSITY = 0.5f;
    /** 闪光衰减时间 */
    private static final float LIGHT_LIFE = 0.12f;
    private static final Color LIGHT_COLOR = new Color(255, 200, 120);

    private float prevCooldown = 0f;
    private StandardLight light = null;
    private float flashT = 0f;
    private final Vector2f lightPos = new Vector2f();
    private boolean lightLogged = false;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused() || weapon == null) return;

        sparks(engine, weapon);
        muzzle(engine, weapon);
        updateLight(amount, weapon.getShip());

    }

    // ---------------- 火星 + 单光源 ----------------

    private void sparks(CombatEngineAPI engine, WeaponAPI weapon) {

        for (DamagingProjectileAPI p : engine.getProjectiles()) {
            if (p == null || p.isExpired()) continue;
            String id = p.getProjectileSpecId();
            if (id == null || (!SHOT_ID.equals(id) && !FLAK_ID.equals(id))) continue;

            if (Math.random() > SPARK_CHANCE) continue;

            float back = SPARK_BACK_MIN + (float) Math.random() * (SPARK_BACK_MAX - SPARK_BACK_MIN);
            float lat = (float) (Math.random() * 8 - 4);
            float rad = (float) Math.toRadians(p.getFacing());
            float bx = (float) Math.cos(rad);
            float by = (float) Math.sin(rad);

            Vector2f pos = new Vector2f(
                    p.getLocation().x - bx * back - by * lat,
                    p.getLocation().y - by * back + bx * lat);

            float ang = p.getFacing() + 180f + (float) (Math.random() * 2 - 1) * SPARK_ANGLE_SPREAD;
            float speed = SPARK_SPEED_MIN + (float) Math.random() * (SPARK_SPEED_MAX - SPARK_SPEED_MIN);
            float ar = (float) Math.toRadians(ang);
            Vector2f vel = new Vector2f(
                    (float) Math.cos(ar) * speed + p.getVelocity().x * 0.1f,
                    (float) Math.sin(ar) * speed + p.getVelocity().y * 0.1f);

            float size = SPARK_SIZE_MIN + (float) Math.random() * SPARK_SIZE_RANGE;
            float dur = SPARK_DUR_MIN + (float) Math.random() * SPARK_DUR_RANGE;

            engine.addHitParticle(pos, vel, size, 1.05f, dur,
                    Math.random() < 0.3f ? SPARK_HOT : SPARK);
        }


    }

    /** 炮口闪光光源：只闪一下然后衰减 */
    private void flash(CombatEngineAPI engine, ShipAPI ship, Vector2f pos) {
        try {
            lightPos.set(pos);
            flashT = LIGHT_LIFE;

            if (light == null) {
                if (ship == null) return;
                light = new StandardLight(new Vector2f(pos), new Vector2f(),
                        new Vector2f(), ship);
                light.makePermanent();
                light.setColor(LIGHT_COLOR.getRed() / 255f, LIGHT_COLOR.getGreen() / 255f,
                        LIGHT_COLOR.getBlue() / 255f);
                light.setSize(LIGHT_SIZE);
                light.setIntensity(LIGHT_INTENSITY);
                LightShader.addLight(light);
                if (!lightLogged) {
                    lightLogged = true;
                }
                return;
            }
            light.setLocation(pos);
            // 重新注册：GraphicsLib 会在强度归零后回收光源
            LightShader.removeLight(light);
            LightShader.addLight(light);
        } catch (Throwable t) {
            if (!lightLogged) {
                lightLogged = true;
            }
        }
    }

    /** 每帧衰减闪光强度 */
    private void updateLight(float amount, ShipAPI ship) {
        if (light == null) return;
        try {
            if (flashT > 0f) flashT -= amount;
            float k = Math.max(0f, flashT / LIGHT_LIFE);
            light.setLocation(lightPos);
            float intensity = LIGHT_INTENSITY * k * k;
            if (intensity < 0.001f) intensity = 0.001f;
            light.setIntensity(intensity);
        } catch (Throwable ignored) {
        }
        if (ship != null && !ship.isAlive()) {
            try {
                LightShader.removeLight(light);
            } catch (Throwable ignored) {
            }
            light = null;
        }
    }

    // ---------------- 开火 ----------------

    private int barrelSide = -1;

    private void muzzle(CombatEngineAPI engine, WeaponAPI weapon) {
        float cd = weapon.getCooldownRemaining();
        boolean fired = cd > prevCooldown + 0.01f;
        prevCooldown = cd;
        if (!fired) return;

        ShipAPI ship = weapon.getShip();
        if (ship == null) return;

        float rad = (float) Math.toRadians(weapon.getCurrAngle());
        float fx = (float) Math.cos(rad);
        float fy = (float) Math.sin(rad);
        // 左右炮管交替
        barrelSide = -barrelSide;
        float latx = -fy;
        float laty = fx;
        float lateral = BARREL_LATERAL * barrelSide;
        Vector2f muzzle = new Vector2f(
                weapon.getLocation().x + fx * MUZZLE_DIST + latx * lateral,
                weapon.getLocation().y + fy * MUZZLE_DIST + laty * lateral);

        try {
            engine.spawnExplosion(muzzle, ship.getVelocity(),
                    new Color(255, 245, 215, 255), FLASH_SIZE, FLASH_TIME);

            flash(engine, ship, muzzle);

            for (int i = 0; i < FLAME_COUNT; i++) {
                float spread = (float) (Math.random() * 2 - 1) * FLAME_CONE;
                float sr = (float) Math.toRadians(weapon.getCurrAngle() + spread);
                float speed = 90f + (float) Math.random() * 90f;
                Vector2f vel = new Vector2f(
                        ship.getVelocity().x + (float) Math.cos(sr) * speed,
                        ship.getVelocity().y + (float) Math.sin(sr) * speed);
                Vector2f pos = new Vector2f(
                        muzzle.x + (float) Math.cos(sr) * (float) Math.random() * 10f,
                        muzzle.y + (float) Math.sin(sr) * (float) Math.random() * 10f);
                engine.addNebulaParticle(pos, vel, 5f, 13f, 0.16f, 0.04f, 0.08f,
                        new Color(255, 195, 105, 220));
            }

            for (int i = 0; i < SMOKE_COUNT; i++) {
                float spread = (float) (Math.random() * 2 - 1) * 22f;
                float sr = (float) Math.toRadians(weapon.getCurrAngle() + spread);
                float speed = 40f + (float) Math.random() * 50f;
                Vector2f vel = new Vector2f(
                        ship.getVelocity().x + (float) Math.cos(sr) * speed,
                        ship.getVelocity().y + (float) Math.sin(sr) * speed);
                Vector2f pos = new Vector2f(
                        muzzle.x + (float) (Math.random() * 8 - 4),
                        muzzle.y + (float) (Math.random() * 8 - 4));
                float size = 9f + (float) Math.random() * 7f;
                engine.addSmokeParticle(pos, vel, size, 0.45f,
                        0.55f + (float) Math.random() * 0.35f,
                        new Color(70, 70, 76, 165));
            }
        } catch (Throwable t) {
        }
    }
}

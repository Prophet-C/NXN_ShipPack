package data.scripts.weapons;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * 武器视觉特效工具箱（参考穿越者协会「云影质子撞击炮」的手法）。
 *
 *  - 炮口两侧制退气流（涡流粒子）
 *  - 炮口蓄能涡流
 *  - 命中闪光（白闪 + 环光 + MagicRender 贴图绽放）
 */
public class NXN_WeaponFX {

    private static SpriteAPI flashSprite;
    private static boolean flashTried = false;

    /** 命中闪光：白闪 + 彩色光环 + 贴图绽放 */
    public static void impact(CombatEngineAPI engine, Vector2f point, Color core, Color ring,
                              float size, Vector2f vel) {
        if (engine == null || point == null) return;
        Vector2f v = vel != null ? new Vector2f(vel) : new Vector2f();

        try {
            engine.spawnExplosion(point, v, core, size * 0.16f, 0.30f);
            engine.addSmoothParticle(point, v, size, 2.0f, 0.16f, ring);

            SpriteAPI spr = getFlashSprite();
            if (spr != null) {
                // 核心白花
                MagicRenderBattlespace(spr, point, new Vector2f(size, size), new Vector2f(size, size),
                        360f * (float) Math.random(), core, 0.06f, 0.45f);
                // 外圈彩环，稍大
                MagicRenderBattlespace(spr, point, new Vector2f(size * 0.62f, size * 0.62f), new Vector2f(size * 0.62f, size * 0.62f),
                        360f * (float) Math.random(), ring, 0.14f, 0.32f);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 炮口两侧制退气流：沿 ±130° 斜向喷出 */
    public static void muzzleVents(CombatEngineAPI engine, WeaponAPI weapon, Color color, int perSide) {
        if (engine == null || weapon == null) return;
        ShipAPI ship = weapon.getShip();
        if (ship == null) return;

        float[] angles = {-130f, 130f};
        try {
            for (float a : angles) {
                for (int i = 0; i < perSide; i++) {
                    float spawnAngle = weapon.getCurrAngle() + a
                            + (float) (Math.random() * 20f - 10f);
                    float rad = (float) Math.toRadians(spawnAngle);
                    float speed = 5f + (float) Math.random() * 45f;
                    Vector2f vel = new Vector2f(
                            ship.getVelocity().x + (float) Math.cos(rad) * speed,
                            ship.getVelocity().y + (float) Math.sin(rad) * speed);
                    float dist = 16f + (float) Math.random() * 4f;
                    Vector2f loc = new Vector2f(
                            weapon.getLocation().x + (float) Math.cos(rad) * dist,
                            weapon.getLocation().y + (float) Math.sin(rad) * dist);
                    float sz = 10f + (float) Math.random() * 5f;
                    engine.addSwirlyNebulaParticle(loc, vel, sz,
                            0.1f + (float) Math.random() * 0.4f, 0f, 0f, 1f, color, true);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 炮口蓄能涡流（开火时喷一点在炮口前方） */
    public static void muzzleSwirl(CombatEngineAPI engine, WeaponAPI weapon, Color color, int count) {
        if (engine == null || weapon == null) return;
        ShipAPI ship = weapon.getShip();
        if (ship == null) return;
        try {
            float rad = (float) Math.toRadians(weapon.getCurrAngle());
            for (int i = 0; i < count; i++) {
                float jitter = (float) (Math.random() * 30f - 15f);
                float speed = 20f + (float) Math.random() * 60f;
                Vector2f vel = new Vector2f(
                        ship.getVelocity().x + (float) Math.cos(rad) * speed,
                        ship.getVelocity().y + (float) Math.sin(rad) * speed);
                vel.x += (float) (Math.random() * 20f - 10f);
                vel.y += (float) (Math.random() * 20f - 10f);
                float dist = 18f + (float) Math.random() * 12f;
                Vector2f loc = new Vector2f(
                        weapon.getLocation().x + (float) Math.cos(rad) * dist + jitter,
                        weapon.getLocation().y + (float) Math.sin(rad) * dist + jitter);
                float sz = 8f + (float) Math.random() * 7f;
                engine.addSwirlyNebulaParticle(loc, vel, sz,
                        0.12f + (float) Math.random() * 0.3f, 0f, 0f, 1f, color, true);
            }
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 内部 ----------------

    /** 反射调用 MagicLib 的 MagicRender.battlespace（避免硬依赖） */
    private static void MagicRenderBattlespace(SpriteAPI spr, Vector2f loc, Vector2f size,
                                               Vector2f growth, float angle, Color color,
                                               float inTime, float fullTime) {
        try {
            Class<?> cls = Class.forName("org.magiclib.util.MagicRender");
            Class<?> cSprite = Class.forName("com.fs.starfarer.api.graphics.SpriteAPI");
            Class<?> cVec = Vector2f.class;
            java.lang.reflect.Method m = cls.getMethod("battlespace", cSprite, cVec, cVec, cVec, cVec,
                    float.class, float.class, Color.class, boolean.class,
                    float.class, float.class, float.class);
            m.invoke(null, spr, loc, new Vector2f(), size, growth, angle, 0f,
                    color, true, 0f, inTime, fullTime);
        } catch (Throwable ignored) {
        }
    }

    private static SpriteAPI getFlashSprite() {
        if (flashTried) return flashSprite;
        flashTried = true;
        String[] cats = {"graphics", "fx"};
        String[] keys = {"fx_radial", "radial_fx"};
        for (String c : cats) {
            for (String k : keys) {
                try {
                    SpriteAPI s = Global.getSettings().getSprite(c, k);
                    if (s != null) {
                        s.getWidth();
                        flashSprite = s;
                        return flashSprite;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }
}

package data.scripts.weapons;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * L3 / R3 小导弹集群：纯气氛用。
 *
 * 逻辑：范围内有敌人 → 每 COOLDOWN 秒打一轮 COUNT 发小导弹（扇形散开），
 *       每发间隔 INTERVAL 秒。不做追踪、不管命中，纯视觉。
 */
public class NXN_MiniMissileCluster implements EveryFrameWeaponEffectPlugin {

    private static final String MISSILE_ID = "NXN_mini_launcher";

    /** 每轮发数 */
    private static final int COUNT = 6;
    /** 单发间隔（秒） */
    private static final float INTERVAL = 0.12f;
    /** 一轮打完的冷却（秒） */
    private static final float COOLDOWN = 6f;
    /** 交战距离 */
    private static final float RANGE = 2500f;
    /** 扇形散开半角（度） */
    private static final float SPREAD = 14f;

    private float cd = 1.5f;
    private float interval = 0f;
    private int fired = 0;
    private boolean salvo = false;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused() || weapon == null) return;
        ShipAPI ship = weapon.getShip();
        if (ship == null || !ship.isAlive()) return;

        if (!salvo) {
            cd -= amount;
            if (cd <= 0f && !ship.getFluxTracker().isOverloaded()
                    && hasEnemy(engine, weapon.getLocation(), ship.getOwner())) {
                salvo = true;
                fired = 0;
                interval = 0f;
            }
            return;
        }

        interval -= amount;
        if (interval <= 0f) {
            interval = INTERVAL;
            launch(engine, weapon, ship);
            fired++;
            if (fired >= COUNT) {
                salvo = false;
                cd = COOLDOWN;
            }
        }
    }

    private void launch(CombatEngineAPI engine, WeaponAPI weapon, ShipAPI ship) {
        try {
            boolean left = weapon.getId() != null && weapon.getId().endsWith("_L");
            float angle = ship.getFacing() + (left ? 90f : -90f)
                    + (float) (Math.random() * 2.0 - 1.0) * SPREAD;

            Vector2f loc = new Vector2f(weapon.getLocation());
            float er = (float) Math.toRadians(angle);
            Vector2f vel = new Vector2f((float) Math.cos(er) * 160f,
                    (float) Math.sin(er) * 160f);
            Vector2f.add(vel, ship.getVelocity(), vel);

            engine.spawnProjectile(ship, weapon, MISSILE_ID, loc, angle, vel);

            // 口部小烟
            for (int k = 0; k < 2; k++) {
                engine.addSmokeParticle(loc, new Vector2f(
                                (float) Math.cos(er) * (20f + (float) Math.random() * 25f),
                                (float) Math.sin(er) * (20f + (float) Math.random() * 25f)),
                        5f + (float) Math.random() * 4f,
                        0.35f, 0.55f, new java.awt.Color(205, 205, 210, 120));
            }
        } catch (Throwable t) {
        }
    }

    private boolean hasEnemy(CombatEngineAPI engine, Vector2f from, int owner) {
        for (ShipAPI s : engine.getShips()) {
            if (s == null || !s.isAlive() || s.getOwner() == owner) continue;
            if (s.isFighter() || s.isDrone()) continue;
            if (Misc.getDistance(from, s.getLocation()) <= RANGE) return true;
        }
        return false;
    }
}

package data.scripts.weapons;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * AA90 防空炮的近炸引信：
 *
 * 自己发射的弹丸在靠近敌方舰载机 / 导弹时提前引爆，
 * 造成一定范围内的破片伤害（弹丸本体伤害置 0，避免二次结算）。
 */
public class NXN_FlakProximity implements EveryFrameWeaponEffectPlugin {

    private static final String PROJ_ID = "NXN_aa90_shot";
    /** 引信触发半径 */
    private static final float FUSE_RADIUS = 85f;
    /** 破片爆炸半径 / 伤害 */
    private static final float BLAST_RADIUS = 110f;
    private static final float BLAST_DAMAGE = 60f;

    private final Set<DamagingProjectileAPI> detonated = new HashSet<DamagingProjectileAPI>();

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || weapon == null) return;
        if (engine.isPaused()) return;

        for (DamagingProjectileAPI p : engine.getProjectiles()) {
            if (p == null || p.isExpired()) continue;
            if (p.getWeapon() != weapon) continue;
            if (!PROJ_ID.equals(p.getProjectileSpecId())) continue;
            if (detonated.contains(p)) continue;

            Vector2f loc = p.getLocation();
            float owner = p.getOwner();

            // 找最近的敌方舰载机 / 导弹
            float best = FUSE_RADIUS;
            boolean found = false;

            for (ShipAPI s : engine.getShips()) {
                if (s == null || !s.isAlive() || s.getOwner() == owner) continue;
                if (!s.isFighter()) continue;
                float d = Misc.getDistance(s.getLocation(), loc);
                if (d <= best) {
                    best = d;
                    found = true;
                }
            }
            for (MissileAPI m : engine.getMissiles()) {
                if (m == null || m.isExpired() || m.getOwner() == owner) continue;
                float d = Misc.getDistance(m.getLocation(), loc);
                if (d <= best) {
                    best = d;
                    found = true;
                }
            }

            if (!found) continue;

            detonate(engine, p, loc, owner);
            detonated.add(p);
        }
    }

    private void detonate(CombatEngineAPI engine, DamagingProjectileAPI p, Vector2f loc, float owner) {
        // 弹丸本体后续命中不再造成伤害
        p.setDamageAmount(0f);

        // 破片爆炸
        for (ShipAPI s : engine.getShips()) {
            if (s == null || !s.isAlive() || s.getOwner() == owner) continue;
            if (!s.isFighter()) continue;
            if (Misc.getDistance(s.getLocation(), loc) <= BLAST_RADIUS) {
                engine.applyDamage(s, s.getLocation(), BLAST_DAMAGE, DamageType.FRAGMENTATION,
                        0f, false, false, p, false);
            }
        }
        for (MissileAPI m : engine.getMissiles()) {
            if (m == null || m.isExpired() || m.getOwner() == owner) continue;
            if (Misc.getDistance(m.getLocation(), loc) <= BLAST_RADIUS) {
                engine.applyDamage(m, m.getLocation(), BLAST_DAMAGE, DamageType.FRAGMENTATION,
                        0f, false, false, p, false);
            }
        }

        // 视觉
        engine.addHitParticle(loc, new Vector2f(), 110f, 1.4f, 0.18f,
                new Color(255, 245, 200, 230));
        for (int i = 0; i < 6; i++) {
            float ang = (float) (Math.random() * Math.PI * 2.0);
            float spd = 60f + (float) Math.random() * 120f;
            engine.addHitParticle(loc,
                    new Vector2f((float) Math.cos(ang) * spd, (float) Math.sin(ang) * spd),
                    10f + (float) Math.random() * 12f, 1.1f, 0.35f,
                    new Color(255, 220, 150, 200));
        }
    }
}

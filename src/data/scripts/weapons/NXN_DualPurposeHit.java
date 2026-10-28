package data.scripts.weapons;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * 双联机炮的高平两用命中效果：
 *  - 命中舰载机 / 导弹：追加破片近炸（小范围破片伤害 + 小型爆炸）
 *  - 命中舰船 / 护盾：追加高爆扩散
 */
public class NXN_DualPurposeHit implements OnHitEffectPlugin {

    /** 对空（破片） */
    private static final float AA_RADIUS = 70f;
    private static final float AA_DAMAGE = 130f;
    /** 对舰（高爆） */
    private static final float SHIP_RADIUS = 95f;
    private static final float SHIP_DAMAGE = 80f;

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
                      boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        if (engine == null || projectile == null || point == null) return;

        Object src = projectile.getWeapon() != null ? projectile.getWeapon() : projectile;
        float owner = projectile.getOwner();

        boolean antiAir = (target instanceof MissileAPI)
                || (target instanceof ShipAPI && ((ShipAPI) target).isFighter());

        if (antiAir) {
            // 破片近炸
            for (CombatEntityAPI e : engine.getShips()) {
                if (e == null || e.getOwner() == owner) continue;
                ShipAPI s = (ShipAPI) e;
                if (!s.isFighter() || !s.isAlive()) continue;
                float d = Misc.getDistance(s.getLocation(), point);
                if (d <= AA_RADIUS) {
                    engine.applyDamage(s, s.getLocation(), AA_DAMAGE, DamageType.FRAGMENTATION,
                            0f, false, false, src, false);
                }
            }
            for (MissileAPI m : engine.getMissiles()) {
                if (m == null || m.getOwner() == owner) continue;
                if (Misc.getDistance(m.getLocation(), point) <= AA_RADIUS) {
                    engine.applyDamage(m, m.getLocation(), AA_DAMAGE, DamageType.FRAGMENTATION,
                            0f, false, false, src, false);
                }
            }
            // 小型破片闪光
            engine.addHitParticle(point, new Vector2f(), 90f, 1.3f, 0.16f,
                    new Color(255, 240, 190, 220));
        } else {
            // 高爆扩散
            for (CombatEntityAPI e : engine.getShips()) {
                if (e == null || e.getOwner() == owner) continue;
                ShipAPI s = (ShipAPI) e;
                if (!s.isAlive() || s.isHulk()) continue;
                float d = Misc.getDistance(s.getLocation(), point);
                if (d <= SHIP_RADIUS) {
                    engine.applyDamage(s, s.getLocation(), SHIP_DAMAGE, DamageType.HIGH_EXPLOSIVE,
                            0f, false, false, src, false);
                }
            }
            engine.addHitParticle(point, new Vector2f(), 140f, 1.2f, 0.20f,
                    new Color(255, 170, 90, 210));
        }
    }
}

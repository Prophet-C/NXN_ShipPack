package data.scripts.weapons;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

/**
 * 鹰击战斗部命中：
 *   1) 视觉：一个**圆形光球**（白芯 + 暖色外晕）
 *   2) 命中爆发：动能伤害（由 weapon_data.csv 的 type / damage 决定）
 *   3) 持续伤害：命中后在命中点灼烧 2.5 秒的高爆伤害，每 0.25 秒结算一次
 */
public class NXN_YJWarheadHit implements OnHitEffectPlugin {

    /** 持续高爆：每次结算伤害 */
    private static final float BURN_DAMAGE_PER_TICK = 120f;
    /** 结算间隔（秒） */
    private static final float BURN_TICK = 0.25f;
    /** 总时长（秒） */
    private static final float BURN_DURATION = 2.5f;

    // 光球配色
    private static final Color BALL_CORE = new Color(255, 252, 240, 255);
    private static final Color BALL_HALO = new Color(255, 236, 200, 215);
    private static final Color BALL_RIM = new Color(255, 170, 90, 110);

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
                      boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        if (engine == null || point == null) return;

        try {
            // 原版鱼雷爆炸音
            try {
                Global.getSoundPlayer().playSound("rifttorpedo_explosion", 1f, 0.85f,
                        point, new Vector2f());
            } catch (Throwable ignored) {
            }

            // ---- 只用一个大光球表现命中（持续伤害也共用这个球）----
            engine.addHitParticle(point, new Vector2f(), 300f, 1.5f, BURN_DURATION, BALL_HALO);

            // ---- 持续高爆（打盾时不点火）----
            if (!shieldHit && target != null) {
                engine.addPlugin(new BurnPlugin(target, new Vector2f(point), projectile.getSource()));
            }
        } catch (Throwable t) {
        }
    }

    /** 命中后的持续高爆灼烧 */
    public static class BurnPlugin extends BaseEveryFrameCombatPlugin {
        private final CombatEntityAPI target;
        private final Vector2f point;
        private final Object source;
        private float elapsed = 0f;
        private float acc = 0f;
        private CombatEngineAPI engine;

        public BurnPlugin(CombatEntityAPI target, Vector2f point, Object source) {
            this.target = target;
            this.point = point;
            this.source = source;
        }

        @Override
        public void init(CombatEngineAPI engine) {
            this.engine = engine;
        }

        @Override
        public void advance(float amount, java.util.List<com.fs.starfarer.api.input.InputEventAPI> events) {
            if (engine == null) return;
            if (engine.isPaused()) return;
            if (elapsed > BURN_DURATION) {
                engine.removePlugin(this);
                return;
            }
            if (target == null || !engine.isEntityInPlay(target)) {
                engine.removePlugin(this);
                return;
            }

            elapsed += amount;
            acc += amount;
            while (acc >= BURN_TICK) {
                acc -= BURN_TICK;
                try {
                    engine.applyDamage(target, point, BURN_DAMAGE_PER_TICK,
                            DamageType.HIGH_EXPLOSIVE, 0f, false, false, source, false);
                } catch (Throwable ignored) {
                    engine.removePlugin(this);
                    return;
                }
                // 不再额外生成粒子 —— 命中时的大光球本身就是持续伤害的视觉
            }
        }
    }
}

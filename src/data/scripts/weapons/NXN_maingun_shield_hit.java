package data.scripts.weapons;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.util.Misc;

/**
 * 地鸣级主炮 · 命中护盾时的处理：
 *
 * - **取消链式反应**（不再生成穿透弹）
 * - 改为**一次固定动能伤害的 AOE**
 * - 视觉使用「半音符」导弹（UAF）的命中特效，配色改为**外橙红、内白**
 */
public class NXN_maingun_shield_hit {

    /** AOE 伤害（固定动能） */
    private static final float DAMAGE = 1500f;
    private static final float EMP = 0f;
    /** 外半径 / 核心半径（像素）—— 半音符尺度，比全音符小 */
    private static final float RADIUS = 220f;
    private static final float CORE_RADIUS = 140f;
    /** 伤害判定时长 */
    private static final float DURATION = 0.12f;

    /** 爆炸判定颜色：橙红 */
    private static final Color BLAST_COLOR = new Color(255, 120, 45, 170);

    private NXN_maingun_shield_hit() {
    }

    public static void play(CombatEngineAPI engine, ShipAPI source, Vector2f point) {
        if (engine == null || point == null) return;

        // ===== 伤害：一次固定动能 AOE =====
        DamagingExplosionSpec spec = new DamagingExplosionSpec(
                DURATION,
                RADIUS,
                CORE_RADIUS,
                DAMAGE,
                EMP,
                CollisionClass.PROJECTILE_FF,
                CollisionClass.PROJECTILE_FIGHTER,
                10f, 20f, 0.2f, 8,
                BLAST_COLOR, null);
        spec.setDamageType(DamageType.KINETIC);
        spec.setShowGraphic(false);
        engine.spawnDamagingExplosion(spec, source, new Vector2f(point), false);

        // ===== 视觉：半音符式爆炸（外橙红 / 内白）=====
        engine.addLayeredRenderingPlugin(new NXN_nuke_visual(point));

        // ===== 音效 =====
        try {
            Global.getSoundPlayer().playSound("NXN_nuke_ex", 1f, 0.75f, point, Misc.ZERO);
        } catch (Throwable ignored) {
        }
    }
}

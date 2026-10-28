package data.scripts.weapons;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.util.Misc;

/**
 * NXN 弹丸命中后触发的“二次范围爆炸”效果。
 *
 * 设计目标：
 * 1. 弹丸命中任意目标（舰体/护盾/导弹等）后，在命中点立即补一次范围伤害。
 * 2. 视觉上提供明显爆炸反馈，且不额外调用游戏默认爆炸贴图（避免和自定义特效重叠）。
 * 3. 参数集中在类开头，便于后续快速调手感。
 */
public class NXN_ExplosiveOnHitEffect implements OnHitEffectPlugin {

    // ==================== 伤害参数 ====================
    /** 范围爆炸伤害倍率：二次爆炸伤害 = 原弹丸伤害 * AOE_DAMAGE_MULT。 */
    private static final float AOE_DAMAGE_MULT = 0.5f;
    /** EMP 倍率：二次爆炸 EMP = 二次爆炸常规伤害 * AOE_EMP_MULT。 */
    private static final float AOE_EMP_MULT = 0.5f;

    // ==================== 爆炸范围参数 ====================
    /** 二次爆炸外半径（单位：游戏距离单位）。半径越大，波及范围越广。 */
    private static final float AOE_RADIUS = 180f;
    /** 二次爆炸核心半径（单位：游戏距离单位）。核心内伤害按更高权重结算。 */
    private static final float AOE_CORE_RADIUS = 90f;
    /** 二次爆炸持续时间（秒）。数值越大，伤害判定存在时间越长。 */
    private static final float AOE_DURATION = 0.1f;

    // ==================== 视觉参数（只影响观感，不改伤害） ====================
    /** 外层爆炸视觉尺寸。 */
    private static final float VFX_OUTER_SIZE = 260f;
    /** 内核爆炸视觉尺寸。 */
    private static final float VFX_CORE_SIZE = 140f;

    // ==================== 颜色参数 ====================
    /** 外层爆炸颜色（RGBA）。 */
    private static final Color VFX_OUTER_COLOR = new Color(255, 110, 70, 180);
    /** 内核闪光颜色（RGBA）。 */
    private static final Color VFX_CORE_COLOR = new Color(255, 240, 205, 220);
    /** DamagingExplosionSpec 自带粒子颜色（RGBA）。 */
    private static final Color BLAST_COLOR = new Color(255, 140, 120, 100);

    /**
     * 弹丸命中回调。
     *
     * @param projectile 触发命中的弹丸本体，用于读取原始伤害与发射者信息
     * @param target 被命中的实体（舰船/导弹/小行星等）
     * @param point 命中点世界坐标，爆炸会在该坐标生成
     * @param shieldHit 是否命中护盾（当前实现不区分护盾/船体，保留给后续扩展）
     * @param damageResult 原始命中伤害结算结果（当前实现未直接使用）
     * @param engine 战斗引擎对象，用于生成特效、伤害实体与音效
     */
    @Override
    public void onHit(DamagingProjectileAPI projectile,
                      CombatEntityAPI target,
                      Vector2f point,
                      boolean shieldHit,
                      ApplyDamageResultAPI damageResult,
                      CombatEngineAPI engine) {
        // 基础安全检查：核心对象为空时直接退出，避免空指针。
        if (engine == null || point == null || projectile == null) {
            return;
        }

        // 根据弹丸原始伤害动态计算二次爆炸伤害与 EMP。
        float aoeDamage = projectile.getDamageAmount() * AOE_DAMAGE_MULT;
        float aoeEmp = aoeDamage * AOE_EMP_MULT;

        // 自定义视觉反馈（两层爆炸 + 一层命中闪光）。
        engine.spawnExplosion(point, Misc.ZERO, VFX_OUTER_COLOR, VFX_OUTER_SIZE, 0.6f);
        engine.spawnExplosion(point, Misc.ZERO, VFX_CORE_COLOR, VFX_CORE_SIZE, 0.35f);
        engine.addHitParticle(point, Misc.ZERO, VFX_OUTER_SIZE * 0.7f, 1f, 0.08f, VFX_CORE_COLOR);

        // 范围爆炸伤害规格：
        // 这里使用 DamagingExplosionSpec 让命中点产生一次“真正的范围伤害实体”。
        DamagingExplosionSpec spec = new DamagingExplosionSpec(
                AOE_DURATION,                  // 持续时间
                AOE_RADIUS,                    // 外半径
                AOE_CORE_RADIUS,               // 核心半径
                aoeDamage,                     // 常规伤害
                aoeEmp,                        // EMP 伤害
                CollisionClass.PROJECTILE_FF,  // 碰撞类别（对主目标/舰船等）
                CollisionClass.PROJECTILE_FIGHTER, // 碰撞类别（对战机等）
                10f,                           // 内置粒子最小尺寸
                20f,                           // 内置粒子最大尺寸
                0.2f,                          // 内置粒子持续时间
                8,                             // 内置粒子数量
                BLAST_COLOR,                   // 内置粒子颜色
                null                           // 核心颜色（null 表示不额外设置）
        );

        // 伤害类型设为高爆，突出对装甲/船体的爆炸打击特征。
        spec.setDamageType(DamageType.HIGH_EXPLOSIVE);
        // 关闭默认爆炸贴图，避免与上面自定义视觉重复叠加。
        spec.setShowGraphic(false);

        // 在命中点生成范围伤害实体。
        engine.spawnDamagingExplosion(spec, projectile.getSource(), point, false);

        // 命中音效：使用通用地雷爆炸声，增强爆炸反馈。
        Global.getSoundPlayer().playSound("mine_explosion", 1f, 1f, point, Misc.ZERO);
    }
}

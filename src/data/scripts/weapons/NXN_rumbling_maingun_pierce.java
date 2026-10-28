package data.scripts.weapons;

import java.awt.Color;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.util.Misc;

/**
 * 地鸣级主炮「崩断」的穿透连击效果（掘墓者式链 + 减速收口）：
 *
 * - 每 PIERCE_DISTANCE（5 su）生成一发穿透弹，命中后继续
 * - 每推进一段，链内速度 × SPEED_DECAY（弹头越钻越慢，新弹初速同步降低）
 * - 速度降到 SPEED_STOP 以下 → 弹头消失、链终止
 * - 单发最多 MAX_STEPS（20）段伤害
 * - 链的推进点用"上一段生成点 + 5 su"确定性向内（不依赖引擎上报的命中点）
 * - 首段命中舰船/空间站装甲：额外一次 3000 动能爆炸（每发弹丸各自触发一次）
 * - 链上伤害 300 高爆 / 300 EMP，0.9ⁿ 衰减、下限 1/3；命中护盾 ×2
 */
public class NXN_rumbling_maingun_pierce implements OnFireEffectPlugin, OnHitEffectPlugin {

    // ==================== 穿透参数 ====================
    private static final float PIERCE_DISTANCE = 5f;       // 每 5 su 一段
    private static final float PIERCE_DELAY = 0.025f;      // 穿透弹生成延迟（秒）
    private static final float RANGE_MULT = 1.5f;          // 穿透总距离 = 武器射程 × 1.5
    private static final float DAMAGE_MULT = 0.9f;         // 每段伤害倍率
    private static final float MIN_DAMAGE_MULT = 1f / 3f;  // 伤害下限
    private static final float SHIELD_PIERCE_MULT = 2f;    // 命中护盾时穿透伤害翻倍

    // ==================== 减速收口 ====================
    private static final int MAX_STEPS = 20;               // 最多 20 段伤害
    private static final float SPEED_DECAY = 0.85f;        // 每段速度倍率
    private static final float SPEED_STOP = 0.05f;         // 速度低于该值 → 弹头消失
    private static final float SHELL_LAUNCH_SPEED = 1200f; // 弹头基础初速（用于减速可视化）

    // ==================== 首次命中额外爆炸 ====================
    private static final float EXTRA_EXPLOSION_DAMAGE = 3000f;
    private static final float EXTRA_EXPLOSION_EMP = 1500f;
    private static final float EXTRA_EXPLOSION_RADIUS = 210f;        // 外半径 70 su
    private static final float EXTRA_EXPLOSION_CORE_RADIUS = 105f;   // 核心半径 35 su
    private static final float EXTRA_EXPLOSION_DURATION = 0.1f;

    // ==================== 弹体 / 武器 id ====================
    private static final String MAIN_PROJ_ID = "NXN_rumbling_maingun_ballistic_shot";
    private static final String PIERCE_PROJ_ID = "NXN_rumbling_maingun_pierce_shot";
    private static final String PIERCE_WEAPON_ID = "NXN_rumbling_maingun_pierce";

    // ==================== 颜色 ====================
    private static final Color VFX_OUTER_COLOR = new Color(255, 110, 70, 180);
    private static final Color VFX_CORE_COLOR = new Color(255, 240, 205, 220);
    private static final Color BLAST_COLOR = new Color(255, 140, 120, 100);
    private static final Color CHAIN_FLASH_COLOR = new Color(255, 190, 90, 150);

    // ==================== 全局状态 ====================
    private static final Set<PierceData> DATA = new HashSet<>();
    private static CombatEngineAPI managerEngine = null;

    /** 穿透弹运行数据 */
    public static class PierceData {
        private final DamagingProjectileAPI projectile;
        private final Vector2f origin;
        private float remainingTime;
        private final float remainingDist;
        private final float facing;
        private final ShipAPI ship;
        private final WeaponAPI weapon;
        private final boolean fromHit;
        private final int counts;
        private final boolean shieldPierce;
        private final float chainSpeed;    // 链内速度（1.0 = 满速）
        private boolean clear = false;

        public PierceData(DamagingProjectileAPI projectile, Vector2f origin, float totalDist,
                          boolean fromHit, int counts, boolean shieldPierce, float chainSpeed) {
            this.projectile = projectile;
            this.origin = new Vector2f(origin);
            this.remainingDist = totalDist - PIERCE_DISTANCE;
            this.remainingTime = PIERCE_DELAY;
            this.facing = projectile != null ? projectile.getFacing() : 0f;
            this.ship = projectile != null ? projectile.getSource() : null;
            this.weapon = projectile != null ? projectile.getWeapon() : null;
            this.fromHit = fromHit;
            this.counts = counts;
            this.shieldPierce = shieldPierce;
            this.chainSpeed = chainSpeed;
        }

        public static PierceData find(DamagingProjectileAPI p) {
            if (p == null) return null;
            for (PierceData d : DATA) {
                if (d != null && d.projectile == p) return d;
            }
            return null;
        }

        private PierceData advance(float amount, CombatEngineAPI engine) {
            remainingTime -= amount;
            if (clear || remainingTime > 0f) return null;
            if (ship == null || weapon == null) {
                clear = true;
                return null;
            }
            // 速度归零 → 弹头消失（链终止）
            if (chainSpeed <= SPEED_STOP) {
                clear = true;
                return null;
            }
            if ((!fromHit || remainingDist < PIERCE_DISTANCE) && remainingTime <= -5f) {
                clear = true;
                return null;
            }
            // 段数上限 + 剩余距离 + 速度三重收口
            if (fromHit && remainingDist > PIERCE_DISTANCE
                    && counts < MAX_STEPS && chainSpeed > SPEED_STOP) {
                double rad = Math.toRadians(facing);
                Vector2f spawn = new Vector2f(origin.x + (float) Math.cos(rad) * PIERCE_DISTANCE,
                                              origin.y + (float) Math.sin(rad) * PIERCE_DISTANCE);

                // 临时清空弹速增益：弹速过高时，穿透弹命中无护盾覆盖的船体会卡在原地
                MutableShipStatsAPI stats = ship.getMutableStats();
                MutableStat ps = stats.getProjectileSpeedMult().createCopy();
                MutableStat bps = stats.getBallisticProjectileSpeedMult().createCopy();
                stats.getProjectileSpeedMult().unmodify();
                stats.getBallisticProjectileSpeedMult().unmodify();

                // 减速可视化：按当前链内速度给一个反向速度偏移（速度越低，新弹飞得越慢）
                float back = SHELL_LAUNCH_SPEED * (chainSpeed - 1f);
                Vector2f velocity = new Vector2f((float) Math.cos(rad) * back,
                                                 (float) Math.sin(rad) * back);

                DamagingProjectileAPI newProj = (DamagingProjectileAPI) engine.spawnProjectile(
                        ship, weapon, PIERCE_WEAPON_ID, spawn, facing, velocity);

                // 单光球跟随链式位置前进（全场唯一实例）
                // 已改用命中位置放置静止双螺旋，此渲染插件暂不使用

                stats.getProjectileSpeedMult().applyMods(ps);
                stats.getBallisticProjectileSpeedMult().applyMods(bps);

                clear = true;
                return new PierceData(newProj, spawn, remainingDist, false, counts, shieldPierce,
                        chainSpeed * SPEED_DECAY);
            }
            return null;
        }
    }

    // ==================== 接口实现 ====================

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        if (projectile == null || weapon == null || engine == null) return;
        ensureManager(engine);
        ShipAPI ship = weapon.getShip();
        if (ship != null && !ship.hasListenerOfClass(PierceListener.class)) {
            ship.addListener(new PierceListener());
        }
        float totalDist = weapon.getRange() * RANGE_MULT;
        DATA.add(new PierceData(projectile, projectile.getLocation(), totalDist, false, 0, false, 1f));
    }

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
                      boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        if (point == null || projectile == null || engine == null) return;
        ensureManager(engine);

        PierceData matched = PierceData.find(projectile);
        boolean firstHit = (matched == null || !matched.fromHit);
        String specId = projectile.getProjectileSpecId();

        // 主炮本体首次命中舰船/空间站装甲：每发弹丸各自触发一次 3000 动能爆炸
        if (MAIN_PROJ_ID.equals(specId) && firstHit && !shieldHit
                && target instanceof ShipAPI && !((ShipAPI) target).isFighter()) {
            spawnExtraExplosion(engine, projectile.getSource(), point);
        }

        // 命中护盾：取消链式反应，改为一次固定动能 AOE + 全音符式特效
        if (shieldHit) {
            NXN_maingun_shield_hit.play(engine, projectile.getSource(), point);
            return;
        }

        if (matched == null && projectile.getWeapon() != null) {
            float d = Misc.getDistance(projectile.getWeapon().getLocation(), point);
            matched = new PierceData(projectile, point, d, true, 0, shieldHit, 1f);
        }
        if (matched == null) return;

        int hitCounts = matched.counts + 1;

        // 链上命中效果（150 不透明）
        engine.spawnExplosion(point, Misc.ZERO, CHAIN_FLASH_COLOR, 60f, 0.3f);
        // 刷新钻入螺旋的寿命（不再逐段生成新特效）

        // 推进点确定性向内：上一段生成点 + 5 su（不用引擎上报的命中点）
        double rad = Math.toRadians(projectile.getFacing());
        Vector2f nextOrigin = new Vector2f(matched.origin.x + (float) Math.cos(rad) * PIERCE_DISTANCE,
                                           matched.origin.y + (float) Math.sin(rad) * PIERCE_DISTANCE);

        DATA.add(new PierceData(projectile, nextOrigin, matched.remainingDist, true, hitCounts,
                matched.shieldPierce || shieldHit, matched.chainSpeed));
    }

    private static void spawnExtraExplosion(CombatEngineAPI engine, ShipAPI source, Vector2f point) {
        engine.spawnExplosion(point, Misc.ZERO, VFX_OUTER_COLOR, 210f, 0.6f);
        engine.spawnExplosion(point, Misc.ZERO, VFX_CORE_COLOR, 105f, 0.35f);
        engine.addHitParticle(point, Misc.ZERO, 135f, 1f, 0.08f, VFX_CORE_COLOR);

        DamagingExplosionSpec spec = new DamagingExplosionSpec(
                EXTRA_EXPLOSION_DURATION,
                EXTRA_EXPLOSION_RADIUS,
                EXTRA_EXPLOSION_CORE_RADIUS,
                EXTRA_EXPLOSION_DAMAGE,
                EXTRA_EXPLOSION_EMP,
                CollisionClass.PROJECTILE_FF,
                CollisionClass.PROJECTILE_FIGHTER,
                10f, 20f, 0.2f, 8,
                BLAST_COLOR, null);
        spec.setDamageType(DamageType.KINETIC);
        spec.setShowGraphic(false);
        engine.spawnDamagingExplosion(spec, source, point, false);
        Global.getSoundPlayer().playSound("mine_explosion", 1f, 1f, point, Misc.ZERO);
    }

    // ==================== 每帧推进 ====================

    private static void ensureManager(CombatEngineAPI engine) {
        if (engine == managerEngine) return;
        managerEngine = engine;
        DATA.clear();
        engine.addPlugin(new BaseEveryFrameCombatPlugin() {
            @Override
            public void advance(float amount, List<InputEventAPI> events) {
                if (engine.isPaused()) return;
                tick(engine, amount);
            }
        });
    }

    private static void tick(CombatEngineAPI engine, float amount) {
        Set<PierceData> next = new HashSet<>();
        Iterator<PierceData> it = DATA.iterator();
        while (it.hasNext()) {
            PierceData d = it.next();
            if (d == null || d.clear) {
                it.remove();
                continue;
            }
            PierceData n = d.advance(amount, engine);
            if (n != null) {
                next.add(n);
                it.remove();
            }
        }
        if (!next.isEmpty()) DATA.addAll(next);
    }

    // ==================== 伤害衰减 / 护盾翻倍监听器 ====================

    public static class PierceListener implements DamageDealtModifier {
        @Override
        public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage,
                                        Vector2f point, boolean shieldHit) {
            if (point == null || !(param instanceof DamagingProjectileAPI)) return null;
            DamagingProjectileAPI projectile = (DamagingProjectileAPI) param;
            String specId = projectile.getProjectileSpecId();
            boolean isMainShell = MAIN_PROJ_ID.equals(specId);
            boolean isPierceShell = PIERCE_PROJ_ID.equals(specId);
            if (!isMainShell && !isPierceShell) return null;

            PierceData matched = PierceData.find(projectile);
            if (matched == null) return null;

            int hitCounts = matched.counts - 1;
            float mult = 1f;
            if (hitCounts > 0) {
                mult *= Math.max(MIN_DAMAGE_MULT, (float) Math.pow(DAMAGE_MULT, hitCounts));
            }
            if (isPierceShell && matched.shieldPierce) {
                mult *= SHIELD_PIERCE_MULT;
            }
            if (mult != 1f) {
                String modId = "NXN_rumbling_maingun_pierce";
                damage.getModifier().modifyMult(modId, mult);
                return modId;
            }
            return null;
        }
    }
}



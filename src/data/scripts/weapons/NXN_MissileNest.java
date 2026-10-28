package data.scripts.weapons;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * 导弹巢：21 帧开盖动画 + 停顿 0.5 秒 + 连射 18 发鱼叉 + 收盖。
 *
 * 帧序列：1-12 开盖 → 12 帧停 0.5s → 发射（每 0.06s 一发，共 18 发）→ 13-21 收盖 → 冷却。
 */
public class NXN_MissileNest implements EveryFrameWeaponEffectPlugin {

    /** 帧速率（帧/秒） */
    private static final float FPS = 14f;
    /** 开盖结束帧（第 12 帧，0 基为 11） */
    private static final int OPEN_END = 11;
    /** 停顿时间 */
    private static final float HOLD = 0.5f;
    /** 收盖起始帧 */
    private static final int CLOSE_START = 12;
    /** 总帧数 */
    private static final int FRAMES = 21;
    /** 导弹总数 */
    private static final int MISSILES = 9;
    /** 每发间隔 */
    private static final float FIRE_INTERVAL = 0.3f;
    /** 一轮打完后的冷却 */
    private static final float COOLDOWN = 9f;
    /** 导弹武器 id（原版鱼叉） */
    private static final String MISSILE_ID = "NXN_yj_launcher";
    /** 交战距离：范围内有敌人才会开火 */
    private static final float ENGAGE_RANGE = 3000f;

    /** 发射口（相对巢画布中心的局部坐标，从第 12 帧橙色点提取） */
    private static final float[][] PORTS = {
            {-10.0f, 26.5f}, {-10.0f, 20.5f}, {-9.0f, 15.0f}, {-8.5f, 9.5f},
            {-7.5f, 3.5f}, {-6.5f, -2.0f}, {-5.5f, -7.5f}, {-4.5f, -13.0f},
            {-3.5f, -18.5f}, {-2.5f, -24.0f},
            {-4.0f, 21.0f}, {-4.0f, 15.5f}, {-3.5f, 10.0f}, {-2.5f, 4.5f},
            {-1.5f, -1.0f}, {-0.5f, -6.5f}, {0.5f, -12.0f}, {1.5f, -17.5f}
    };

    private enum State { IDLE, OPENING, HOLDING, FIRING, CLOSING }

    private State state = State.IDLE;
    private float timer = 0f;
    private float frameT = 0f;
    private int frame = 0;
    private int firedCount = 0;
    private float fireT = 0f;
    private float cooldown = 2f;

    private int openStartFrame = 0;
    private int closeStartFrame = 0;

    private WeaponAPI weapon;
    private ShipAPI module;
    private boolean registered = false;
    private boolean rendererAdded = false;
    /** 鹰击弹体计时（巨戟式：无 AI，纯计时分级） */
    private static final java.util.Map<com.fs.starfarer.api.combat.MissileAPI, Float> YJ_AGES =
            new java.util.HashMap<com.fs.starfarer.api.combat.MissileAPI, Float>();
    private static final java.util.Map<com.fs.starfarer.api.combat.MissileAPI, Integer> YJ_PHASE =
            new java.util.HashMap<com.fs.starfarer.api.combat.MissileAPI, Integer>();
    /** 推进段锁定朝向 */
    private static final java.util.Map<com.fs.starfarer.api.combat.MissileAPI, Float> YJ_FACE =
            new java.util.HashMap<com.fs.starfarer.api.combat.MissileAPI, Float>();
    /** 进入推进段的时间戳 */
    private static final java.util.Map<com.fs.starfarer.api.combat.MissileAPI, Float> YJ_T0 =
            new java.util.HashMap<com.fs.starfarer.api.combat.MissileAPI, Float>();
    /** 残骸自转速率（度/秒） */
    private static final java.util.Map<com.fs.starfarer.api.combat.MissileAPI, Float> DEAD_SPIN =
            new java.util.HashMap<com.fs.starfarer.api.combat.MissileAPI, Float>();
    /** 每颗导弹锁定的**唯一目标**（第一次锁定后不再换） */
    private static final java.util.Map<com.fs.starfarer.api.combat.MissileAPI, ShipAPI> YJ_TGT =
            new java.util.HashMap<com.fs.starfarer.api.combat.MissileAPI, ShipAPI>();
    /** 上一帧相对目标的沿飞行方向投影（用于检测越过敌舰） */
    private static final java.util.Map<com.fs.starfarer.api.combat.MissileAPI, Float> YJ_ALONG =
            new java.util.HashMap<com.fs.starfarer.api.combat.MissileAPI, Float>();
    /** 每颗导弹的瞄准点（目标周围随机点） */
    private static final java.util.Map<com.fs.starfarer.api.combat.MissileAPI, Vector2f> YJ_AIM =
            new java.util.HashMap<com.fs.starfarer.api.combat.MissileAPI, Vector2f>();
    /** 战斗部火星拖尾开关（用户要求关掉） */
    private static final boolean WARHEAD_TRAIL = false;
    /** 瞄准点在目标周围多远（su） */
    private static final float AIM_RADIUS = 600f;
    /** 离瞄准点多近算「到达」（su）*/
    private static final float AIM_REACH = 90f;

    // ===== 冷发射烟雾（照烟囱那套物理）=====
    /** 继承多少船速 */
    private static final float SMOKE_VEL_INHERIT = 0.95f;
    /** 速度抖动 */
    private static final float SMOKE_VEL_JITTER = 14f;
    /** 烟柱目标长度（像素） */
    private static final float SMOKE_TRAIL_MIN = 60f;
    private static final float SMOKE_TRAIL_MAX = 150f;
    /** 寿命上限 */
    private static final float SMOKE_DURATION_MAX = 6.0f;
    private static final java.awt.Color SMOKE_LIGHT = new java.awt.Color(178, 180, 188, 105);
    private static final java.awt.Color SMOKE_DARK = new java.awt.Color(120, 122, 130, 120);    /**
     * 取点扇区（相对**目标舰朝向**的角度范围）：
     * 排除目标正前 / 正后各 90°（即 bow ±45°、stern ±45°），只在两侧取点。
     */
    private static final float AIM_SIDE_MIN = 45f;
    private static final float AIM_SIDE_MAX = 135f;

    public WeaponAPI getWeapon() { return weapon; }
    public ShipAPI getModule() { return module; }
    public int getCurrentFrame() { return frame; }
    public boolean isLeftSide() {
        try {
            return weapon != null && weapon.getId() != null && weapon.getId().endsWith("_L");
        } catch (Throwable t) { return false; }
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused() || weapon == null) return;
        ShipAPI ship = weapon.getShip();
        if (ship == null || !ship.isAlive()) return;

        this.weapon = weapon;
        this.module = ship;
        if (!registered) {
            registered = true;
            NXN_NestRenderer.register(this);
        }
        if (!rendererAdded) {
            rendererAdded = true;
            try {
                engine.addLayeredRenderingPlugin(new NXN_NestRenderer());
            } catch (Throwable ignored) {
            }
        }
        // 本体贴图隐藏（改由 NXN_NestRenderer 手绘到更低的图层）
        try {
            weapon.getSprite().setColor(new java.awt.Color(255, 255, 255, 0));
        } catch (Throwable ignored) {
        }

        manageYJ(engine, amount);
        manageDebris(engine, amount);
        // 战斗部拖尾只让一侧巢处理，避免双倍粒子
        if (WARHEAD_TRAIL && !isLeftSide()) manageWarheadTrail(engine, amount);

        switch (state) {
            case IDLE:
                cooldown -= amount;
                frame = FRAMES - 1;
                setFrame(weapon, FRAMES - 1);
                if (cooldown <= 0f && !ship.getFluxTracker().isOverloaded()
                        && hasTarget(engine, ship, weapon)) {
                    state = State.OPENING;
                    frameT = 0f;
                    setFrame(weapon, 0);
                }
                break;

            case OPENING:
                frameT += amount;
                frame = (int) (frameT * FPS);
                if (frame >= OPEN_END) {
                    frame = OPEN_END;
                    setFrame(weapon, frame);
                    state = State.HOLDING;
                    timer = HOLD;
                } else {
                    setFrame(weapon, frame);
                }
                break;

            case HOLDING:
                frame = OPEN_END;
                setFrame(weapon, OPEN_END);
                timer -= amount;
                if (timer <= 0f) {
                    state = State.FIRING;
                    firedCount = 0;
                    fireT = 0f;
                    try {
                        Global.getSoundPlayer().playSound("NXN_nest_salvo", 1f, 1f,
                                weapon.getLocation(), ship.getVelocity());
                    } catch (Throwable ignored) {
                    }
                }
                break;

            case FIRING:
                frame = OPEN_END;
                fireT -= amount;
                if (fireT <= 0f) {
                    fireT = FIRE_INTERVAL;
                    launch(engine, ship, weapon, firedCount);
                    firedCount++;
                    if (firedCount >= MISSILES) {
                        state = State.CLOSING;
                        frameT = 0f;
                        setFrame(weapon, OPEN_END);
                    }
                }
                break;

            case CLOSING:
                // 收盖 = 开盖动画倒放：12 → 1
                frameT += amount;
                frame = OPEN_END - (int) (frameT * FPS);
                if (frame <= 0) {
                    frame = 0;
                    setFrame(weapon, 0);
                    state = State.IDLE;
                    cooldown = COOLDOWN;
                } else {
                    setFrame(weapon, frame);
                }
                break;
        }
    }

    /** 正常武器逻辑：范围内有敌舰/导弹才开火 */
    private boolean hasTarget(CombatEngineAPI engine, ShipAPI ship, WeaponAPI weapon) {
        float range = ENGAGE_RANGE;
        try {
            if (weapon.getSpec() != null && weapon.getSpec().getMaxRange() > 0) {
                range = Math.max(range, weapon.getSpec().getMaxRange());
            }
        } catch (Throwable ignored) {
        }
        org.lwjgl.util.vector.Vector2f here = weapon.getLocation();
        for (ShipAPI s : engine.getShips()) {
            if (s == null || !s.isAlive() || s.getOwner() == ship.getOwner()) continue;
            if (s.isFighter() || s.isDrone()) continue;
            if (com.fs.starfarer.api.util.Misc.getDistance(here, s.getLocation()) <= range) return true;
        }
        for (com.fs.starfarer.api.combat.MissileAPI m : engine.getMissiles()) {
            if (m == null || m.isExpired() || m.getOwner() == ship.getOwner()) continue;
            if (com.fs.starfarer.api.util.Misc.getDistance(here, m.getLocation()) <= range) return true;
        }
        return false;
    }

    /**
     * 鹰击分级（巨戟做法：不加 AI，只按时间控制）
     *   0 ~ 1.2s  冷抛射：无尾焰，速度缓慢衰减
     *   1.2 ~ 1.55s 点火：闪光，速度归零（原地悬停）
     *   >= 1.55s  抛体：生成弹头，移除弹体
     */
    private static final float EJECT_TIME = 1.2f;
    private static final float IGNITE_END = 1.75f;
    /** 战斗部拖尾：每帧沿路径补多少颗粒子 / 粒子寿命 / 颜色（鹰击本体灰白） */
    private static final int TRAIL_PER_FRAME = 8;
    private static final float TRAIL_LIFE = 0.95f;
    private static final java.awt.Color TRAIL_COLOR = new java.awt.Color(198, 205, 218, 210);
    private static final java.awt.Color TRAIL_CORE = new java.awt.Color(240, 245, 252, 245);
    /** 推进段：最高速 / 加速度 / 最长时长 / 抛体距离 */
    private static final float BOOST_SPEED = 400f;
    private static final float BOOST_ACCEL = 220f;
    private static final float BOOST_TIME = 9.0f;
    private static final float SPLIT_DIST = 500f;
    /** 战斗部出膛速度 */
    private static final float WARHEAD_SPEED = 730f;

    private void manageYJ(CombatEngineAPI engine, float amount) {
        if (engine == null) return;
        try {
            for (com.fs.starfarer.api.combat.MissileAPI m : engine.getMissiles()) {
                if (m == null) continue;
                if (!"NXN_yj_booster".equals(m.getProjectileSpecId())) continue;
                if (!YJ_AGES.containsKey(m)) {
                    YJ_AGES.put(m, 0f);
                    YJ_PHASE.put(m, 0);
                    if (yjDiagLog++ < 30) {
                    }
                }
            }
            java.util.Iterator<java.util.Map.Entry<com.fs.starfarer.api.combat.MissileAPI, Float>> it =
                    YJ_AGES.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<com.fs.starfarer.api.combat.MissileAPI, Float> e = it.next();
                com.fs.starfarer.api.combat.MissileAPI m = e.getKey();
                if (m == null || m.isExpired()) {
                    YJ_PHASE.remove(m);
                    YJ_FACE.remove(m);
                    YJ_T0.remove(m);
                    YJ_AIM.remove(m);
                    YJ_ALONG.remove(m);
                    YJ_TGT.remove(m);
                    it.remove();
                    continue;
                }
                float t = e.getValue() + amount;
                e.setValue(t);
                int phase = YJ_PHASE.containsKey(m) ? YJ_PHASE.get(m) : 0;
                if (phase >= 3) {
                    // 已抛体：弹体失去动力，先漂一会儿，再移除（避免残留尾焰）
                    try {
                        m.resetEngineGlowBrightness();
                    } catch (Throwable ignored) {
                    }
                    Float splitAt = YJ_T0.get(m);
                    if (splitAt != null && t - splitAt > 1.5f) {
                        engine.removeEntity(m);
                        YJ_PHASE.remove(m);
                        YJ_FACE.remove(m);
                        YJ_T0.remove(m);
                        YJ_AIM.remove(m);
                        YJ_ALONG.remove(m);
                        YJ_TGT.remove(m);
                        it.remove();
                    }
                    continue;
                }   // 3 = 已抛体完成，绝不重复生成（2 = 推进段，必须处理）

                if (phase == 0) {
                    m.resetEngineGlowBrightness();
                    // 抛射：沿发射方向缓慢减速（同时手动位移，引擎物理不可靠）
                    float sp = Math.max(0f, 160f - 128f * t);
                    float er = (float) Math.toRadians(m.getFacing());
                    float vx = (float) Math.cos(er) * sp;
                    float vy = (float) Math.sin(er) * sp;
                    m.getVelocity().set(vx, vy);
                    move(m, vx, vy, amount);
                    if (t >= EJECT_TIME) {
                        YJ_PHASE.put(m, 1);
                    }
                    continue;
                }

                if (phase == 1) {
                    m.setShineBrightness(1f);
                    m.getVelocity().set(0f, 0f);          // 点火但不动
                    // 缓慢转向「目标周围 400su 圆周上的随机点」
                    ShipAPI tgt = getLockedTarget(engine, m);
                    if (tgt != null) {
                        Vector2f aim = YJ_AIM.get(m);
                        if (aim == null) {
                            // 只在两侧取点：排除目标正前 / 正后各 90° 扇区（避免撞进敌方弹幕）
                            float tFacing = tgt.getFacing();
                            float drift = AIM_SIDE_MIN
                                    + (float) (Math.random() * (AIM_SIDE_MAX - AIM_SIDE_MIN));
                            if (Math.random() < 0.5f) drift = -drift;
                            float a = tFacing + drift;
                            float ar = (float) Math.toRadians(a);
                            aim = new Vector2f(
                                    tgt.getLocation().x + (float) Math.cos(ar) * AIM_RADIUS,
                                    tgt.getLocation().y + (float) Math.sin(ar) * AIM_RADIUS);
                            YJ_AIM.put(m, aim);
                        }
                        float want = com.fs.starfarer.api.util.Misc.getAngleInDegrees(
                                m.getLocation(), aim);
                        float cur = m.getFacing();
                        float d = want - cur;
                        d = d - 360f * (float) Math.floor((d + 180f) / 360f);
                        float maxT = 95f * amount;
                        if (d > maxT) d = maxT;
                        if (d < -maxT) d = -maxT;
                        m.setFacing(cur + d);
                    }
                    float diffNow = 999f;
                    if (tgt != null) {
                        Vector2f aim2 = YJ_AIM.get(m);
                        if (aim2 != null) {
                            float want2 = com.fs.starfarer.api.util.Misc.getAngleInDegrees(
                                    m.getLocation(), aim2);
                            diffNow = want2 - m.getFacing();
                            diffNow = diffNow - 360f * (float) Math.floor((diffNow + 180f) / 360f);
                            diffNow = Math.abs(diffNow);
                        }
                    }
                    if (diffNow < 4f || t >= IGNITE_END) {
                        // 转向完成 → 进入推进段（先记下锁定朝向和起始时间）
                        YJ_PHASE.put(m, 2);
                        YJ_FACE.put(m, m.getFacing());
                        YJ_T0.put(m, t);
                    }
                    continue;
                }

                // ===== 推进段：点火后直线加速，飞到目标附近或时到即抛体 =====
                if (phase == 2) {
                    m.setShineBrightness(1f);
                    Float f0 = YJ_T0.get(m);
                    float t0 = (f0 == null) ? t : f0;
                    Float lf = YJ_FACE.get(m);
                    float locked = (lf == null) ? m.getFacing() : lf;
                    float elapsed = t - t0;

                    float sp = Math.min(BOOST_SPEED, BOOST_ACCEL * elapsed);
                    float er = (float) Math.toRadians(locked);
                    m.setFacing(locked);
                    float bvx = (float) Math.cos(er) * sp;
                    float bvy = (float) Math.sin(er) * sp;
                    m.getVelocity().set(bvx, bvy);
                    move(m, bvx, bvy, amount);

                    // 白色火星：稀疏、沿长尾散布（密度 -50%，长度 ×3）
                    if (sp > 80f && Math.random() < 0.5f) {
                        {
                            float back = 20f + (float) Math.random() * 70f;
                            float jitter = (float) (Math.random() * 2.0 - 1.0) * 7f;
                            float px = m.getLocation().x - (float) Math.cos(er) * back
                                    + (float) Math.sin(er) * jitter;
                            float py = m.getLocation().y - (float) Math.sin(er) * back
                                    - (float) Math.cos(er) * jitter;
                            Vector2f sparkVel = new Vector2f(
                                    (float) Math.cos(er) * (sp * 0.18f) * (float) Math.random(),
                                    (float) Math.sin(er) * (sp * 0.18f) * (float) Math.random());
                            float bright = 0.80f + (float) Math.random() * 0.20f;
                            engine.addHitParticle(new Vector2f(px, py), sparkVel,
                                    0.6f + (float) Math.random() * 1.2f,
                                    0.45f,
                                    0.30f + (float) Math.random() * 0.36f,
                                    new java.awt.Color(230, 230, 240, 170));
                        }
                    }

                    // 喷口火焰：明亮核心 + 外围细碎（定位在尾部外 23~33 像素）
                    if (sp > 60f) {
                        float bx = m.getLocation().x - (float) Math.cos(er) * 24f;
                        float by = m.getLocation().y - (float) Math.sin(er) * 24f;
                        // 1) 明亮核心
                        engine.addHitParticle(new Vector2f(bx, by),
                                new Vector2f(-(float) Math.cos(er) * 40f,
                                        -(float) Math.sin(er) * 40f),
                                12f + (float) Math.random() * 7f,
                                1.35f,
                                0.05f,
                                new java.awt.Color(245, 245, 255, 255));
                        // 2) 外围蓝色小火星
                        for (int k = 0; k < 3; k++) {
                            float back = 24f + (float) Math.random() * 14f;
                            float px = m.getLocation().x - (float) Math.cos(er) * back;
                            float py = m.getLocation().y - (float) Math.sin(er) * back;
                            Vector2f fv = new Vector2f(
                                    -(float) Math.cos(er) * (70f + (float) Math.random() * 90f),
                                    -(float) Math.sin(er) * (70f + (float) Math.random() * 90f));
                            engine.addHitParticle(new Vector2f(px, py), fv,
                                    3f + (float) Math.random() * 3f,
                                    0.80f,
                                    0.10f + (float) Math.random() * 0.10f,
                                    new java.awt.Color(190, 185, 255, 230));
                        }
                    }

                    ShipAPI tgt2 = getLockedTarget(engine, m);
                    float dist = (tgt2 == null) ? 99999f
                            : com.fs.starfarer.api.util.Misc.getDistance(
                                    m.getLocation(), tgt2.getLocation());

                    // 越过敌舰检测：本帧之前还在目标前方，这一帧跑到后方 → 立即抛体
                    boolean overshot = false;
                    if (tgt2 != null) {
                        float ax = m.getLocation().x - tgt2.getLocation().x;
                        float ay = m.getLocation().y - tgt2.getLocation().y;
                        float along = ax * (float) Math.cos(er) + ay * (float) Math.sin(er);
                        Float prevAlong = YJ_ALONG.get(m);
                        if (prevAlong != null && prevAlong < 0f && along >= 0f) {
                            overshot = true;
                        }
                        YJ_ALONG.put(m, along);
                    }

                    // 到达 500su 内 / 越过敌舰 / 推进满 BOOST_TIME → 都抛体
                    Vector2f aimPt = YJ_AIM.get(m);
                    float dAim = (aimPt == null) ? 99999f
                            : com.fs.starfarer.api.util.Misc.getDistance(m.getLocation(), aimPt);
                    boolean reachedAim = (aimPt != null && dAim <= AIM_REACH);

                    // 到达瞄准点 / 越过目标 / 推进超时 → 进入战斗部
                    if (reachedAim || overshot || elapsed >= BOOST_TIME) {
                        // 抛体：标记先行，杜绝重复生成
                        YJ_PHASE.put(m, 3);
                        Vector2f loc = new Vector2f(m.getLocation());

                        // 分离小爆炸
                        engine.spawnExplosion(loc, new Vector2f(),
                                new java.awt.Color(255, 225, 175, 255), 26f, 0.3f);

                        // 用无引擎残骸替换弹体：火焰/拖尾立刻消失，残骸靠惯性漂走
                        try {
                            Vector2f deadVel = new Vector2f(m.getVelocity());
                            if (deadVel.length() < 60f) {
                                float rr = (float) Math.toRadians(locked);
                                deadVel.set((float) Math.cos(rr) * 400f, (float) Math.sin(rr) * 400f);
                            }
                            // 分离冲击：给残骸一个随机侧推力，不再沿原弹道直飞
                            float kickAngle = (float) (Math.random() * 360.0);
                            float kickMag = 260f + (float) Math.random() * 160f;
                            float kr = (float) Math.toRadians(kickAngle);
                            deadVel.x += (float) Math.cos(kr) * kickMag;
                            deadVel.y += (float) Math.sin(kr) * kickMag;
                            com.fs.starfarer.api.combat.DamagingProjectileAPI dead =
                                    (com.fs.starfarer.api.combat.DamagingProjectileAPI)
                                            engine.spawnProjectile(m.getSource(), this.weapon,
                                                    "NXN_yj_dead_launcher", loc, locked, deadVel);
                            if (dead != null) dead.getVelocity().set(deadVel.x, deadVel.y);
                            if (yjDiagLog++ < 60) {
                                float realV = (dead == null) ? -1f : dead.getVelocity().length();
                            }
                        } catch (Throwable tt) {
                        }
                        engine.removeEntity(m);

                        // 战斗部重新对准目标本体（不是那个偏移点）
                        float fireAngle = locked;
                        if (tgt2 != null) {
                            fireAngle = com.fs.starfarer.api.util.Misc.getAngleInDegrees(
                                    loc, tgt2.getLocation());
                        }
                        float fr = (float) Math.toRadians(fireAngle);
                        Vector2f vel = new Vector2f(
                                (float) Math.cos(fr) * WARHEAD_SPEED,
                                (float) Math.sin(fr) * WARHEAD_SPEED);
                        com.fs.starfarer.api.combat.DamagingProjectileAPI wp =
                                (com.fs.starfarer.api.combat.DamagingProjectileAPI)
                                        engine.spawnProjectile(m.getSource(), this.weapon,
                                                "NXN_yj_warhead_launcher", loc, fireAngle, vel);
                        if (wp != null) {
                            // 引擎可能忽略 spawnProjectile 的速度参数，这里强制设定
                            wp.getVelocity().set(vel.x, vel.y);
                        }
                        if (yjDiagLog++ < 40) {
                        }
                        engine.addSmoothParticle(loc, new Vector2f(), 40f, 2f, 0.15f,
                                new java.awt.Color(255, 220, 170, 200));
                        // 保留弹体（phase 3 不再处理，靠 maxFlightTime 自然消失）
                    }
                    continue;
                }
            }
        } catch (Throwable t) {
            if (yjErrLogged++ < 3) {
            }
        }
    }

    private static int yjErrLogged = 0;
    private static int yjDiagLog = 0;

    /**
     * 战斗部拖尾：以 1/10 速度留下灰白粒子，弹体高速飞走 → 拖尾被甩在身后，
     * 形成"拉得很长"的高速感（参考狐狐纳米侵蚀鱼雷的 contrail 做法）。
     */
    private void manageWarheadTrail(CombatEngineAPI engine, float amount) {
        try {
            for (DamagingProjectileAPI p : engine.getProjectiles()) {
                if (p == null) continue;
                if (!"NXN_yj_warhead".equals(p.getProjectileSpecId())) continue;

                Vector2f v = p.getVelocity();
                float speed = v.length();
                if (speed < 30f) continue;
                float er = (float) Math.toRadians(p.getFacing());
                float cos = (float) Math.cos(er);
                float sin = (float) Math.sin(er);

                // 这一帧走过的距离，沿线补粒子，避免高速时断成虚线
                float step = speed * amount;
                for (int i = 0; i < TRAIL_PER_FRAME; i++) {
                    float back = step * (i + (float) Math.random()) / TRAIL_PER_FRAME;
                    float px = p.getLocation().x - cos * back
                            + (float) (Math.random() * 2.0 - 1.0) * 1.6f;
                    float py = p.getLocation().y - sin * back
                            + (float) (Math.random() * 2.0 - 1.0) * 1.6f;
                    // 只留 10% 速度 → 粒子被弹体远远甩开
                    Vector2f pv = new Vector2f(v.x * 0.10f, v.y * 0.10f);
                    boolean core = Math.random() < 0.35f;
                    engine.addHitParticle(new Vector2f(px, py), pv,
                            core ? (5.0f + (float) Math.random() * 4.0f)
                                 : (3.2f + (float) Math.random() * 3.0f),
                            core ? 1.15f : 0.85f,
                            TRAIL_LIFE * (0.7f + (float) Math.random() * 0.6f),
                            core ? TRAIL_CORE : TRAIL_COLOR);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 手动位移：引擎对自定义导弹的速度写入不可靠，直接改坐标 */
    /** 残骸随机自转（被分离冲击打飞后的翻滚感） */
    private void manageDebris(CombatEngineAPI engine, float amount) {
        try {
            for (com.fs.starfarer.api.combat.MissileAPI m : engine.getMissiles()) {
                if (m == null || m.isExpired()) continue;
                if (!"NXN_yj_dead".equals(m.getProjectileSpecId())) continue;
                Float rate = DEAD_SPIN.get(m);
                if (rate == null) {
                    float mag = 140f + (float) Math.random() * 260f;
                    rate = (Math.random() < 0.5f) ? -mag : mag;
                    DEAD_SPIN.put(m, rate);
                }
                m.setFacing(m.getFacing() + rate * amount);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void move(com.fs.starfarer.api.combat.MissileAPI m,
                            float vx, float vy, float amount) {
        try {
            Vector2f loc = m.getLocation();
            loc.x += vx * amount;
            loc.y += vy * amount;
        } catch (Throwable ignored) {
        }
    }

    private static float rnd() {
        return (float) Math.random() * 2f - 1f;
    }

    /** 取锁定目标：已锁定则一直用同一个；目标死了才重新找 */
    private ShipAPI getLockedTarget(CombatEngineAPI engine,
                                    com.fs.starfarer.api.combat.MissileAPI m) {
        ShipAPI t = YJ_TGT.get(m);
        if (t != null && t.isAlive() && !t.isHulk()) return t;
        t = findEnemy(engine, m.getLocation(), 3000f, m.getOwner());
        if (t != null) YJ_TGT.put(m, t);
        return t;
    }

    private ShipAPI findEnemy(CombatEngineAPI engine, Vector2f from, float range, int owner) {
        ShipAPI best = null;
        float bestD = range;
        for (ShipAPI s : engine.getShips()) {
            if (s == null || !s.isAlive() || s.isFighter() || s.isDrone()) continue;
            if (s.getOwner() == owner) continue;
            float d = com.fs.starfarer.api.util.Misc.getDistance(from, s.getLocation());
            if (d < bestD) { bestD = d; best = s; }
        }
        return best;
    }

    private void setFrame(WeaponAPI weapon, int f) {
        try {
            if (f < 0) f = 0;
            if (f >= FRAMES) f = FRAMES - 1;
            weapon.getAnimation().setFrame(f);
        } catch (Throwable ignored) {
        }
    }

    private void launch(CombatEngineAPI engine, ShipAPI ship, WeaponAPI weapon, int idx) {
        try {
            float[] port = PORTS[idx % PORTS.length];
            float rad = (float) Math.toRadians(ship.getFacing());
            float fx = (float) Math.cos(rad);
            float fy = (float) Math.sin(rad);
            // 局部 (前向, 横向) -> 世界
            float px = weapon.getLocation().x + fx * port[1] - fy * port[0];
            float py = weapon.getLocation().y + fy * port[1] + fx * port[0];

            // 垂直于舰体发射（左右巢方向相反）
            boolean left = isLeftSide();
            float angle = ship.getFacing() + (left ? 90f : -90f)
                    + (float) (Math.random() * 16f - 8f);
            Vector2f loc = new Vector2f(px, py);
            float er = (float) Math.toRadians(angle);
            Vector2f ejectVel = new Vector2f(
                    (float) Math.cos(er) * 160f,
                    (float) Math.sin(er) * 160f);
            Vector2f.add(ejectVel, ship.getVelocity(), ejectVel);
            DamagingProjectileAPI p = (DamagingProjectileAPI)
                    engine.spawnProjectile(ship, weapon, MISSILE_ID, loc, angle, ejectVel);
            // 原版导弹发射音
            try {
                Global.getSoundPlayer().playSound("NXN_yj_fire", 1f, 1f, loc, ship.getVelocity());
            } catch (Throwable ignored) {
            }
            // 冷发射烟雾：糊在巢口不散（高继承船速 + 短寿命），掩盖逐帧发射差分
            try {
                float shipVx = ship.getVelocity().x;
                float shipVy = ship.getVelocity().y;

                for (int k = 0; k < 30; k++) {
                    float spread = (float) (Math.random() * 2.0 - 1.0);
                    Vector2f sp = new Vector2f(
                            loc.x + (float) Math.sin(er) * spread * 14f + rnd() * 6f,
                            loc.y - (float) Math.cos(er) * spread * 14f + rnd() * 6f);

                    // 高继承船速 → 烟跟着舰体走，糊在巢口
                    Vector2f sv = new Vector2f(
                            shipVx * SMOKE_VEL_INHERIT
                                    + (float) Math.cos(er) * (10f + (float) Math.random() * 26f)
                                    + rnd() * SMOKE_VEL_JITTER,
                            shipVy * SMOKE_VEL_INHERIT
                                    + (float) Math.sin(er) * (10f + (float) Math.random() * 26f)
                                    + rnd() * SMOKE_VEL_JITTER);

                    float size = 10f + (float) Math.random() * 16f;
                    float bright = 0.60f + (float) Math.random() * 0.30f;
                    float dur = 1.1f + (float) Math.random() * 0.9f;

                    engine.addSmokeParticle(sp, sv, size, bright, dur,
                            Math.random() < 0.65f ? SMOKE_LIGHT : SMOKE_DARK);
                }
            } catch (Throwable ignored) {
            }

            if (p instanceof com.fs.starfarer.api.combat.MissileAPI) {
                com.fs.starfarer.api.combat.MissileAPI m =
                        (com.fs.starfarer.api.combat.MissileAPI) p;
                // 引擎可能忽略 spawnProjectile 传入的速度，这里强制设定
                m.getVelocity().set(ejectVel);
                // AI 改为计时插件处理（见 advance 里的 YJ 管理）
            }
            if (p != null && yjDiagLog++ < 30) {
            }

        } catch (Throwable t) {
        }
    }
}
